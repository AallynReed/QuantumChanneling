package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Algorithm + helpers for the dynamic-subchannel item-transfer model. The emitter:
 * <ol>
 *   <li>Applies its own void filter — if {@code !voidFilter.matches(stack)} the item is silently consumed.</li>
 *   <li>Walks its subscribed subchannels in iteration order (the priority "rule book").</li>
 *   <li>For each subchannel, skips when no <em>loaded</em> subscribed receiver exists or when the
 *       subchannel's filter rejects the item.</li>
 *   <li>On a match, pushes the item directly into a subscribed receiver's adjacent IItemHandler.
 *       No channel buffer.</li>
 * </ol>
 *
 * <p>The per-item {@link Decision} cache on the emitter avoids redoing the walk for every stack of
 * the same type. It's keyed by {@link ResourceLocation} and invalidated by either a channel-level
 * {@link ItemChannelConfig#maskVersion()} bump or an emitter-local edit (void / subscriptions).
 */
public final class ItemTransitHelper {
    private ItemTransitHelper() {}

    /** What an emitter decided to do with an item. */
    public static final class Decision {
        public enum Kind { VOID, ROUTE, REJECT }
        public static final Decision VOID = new Decision(Kind.VOID, null);
        public static final Decision REJECT = new Decision(Kind.REJECT, null);

        public final Kind kind;
        public final @Nullable UUID subchannelId;

        private Decision(Kind kind, @Nullable UUID subchannelId) {
            this.kind = kind;
            this.subchannelId = subchannelId;
        }
        public static Decision route(UUID id) { return new Decision(Kind.ROUTE, id); }
    }

    /**
     * Pure decision function — no mutation, no caching. Use {@link PhotonEmitterBlockEntity#decide}
     * from server tick paths so cache invalidation is handled.
     *
     * <p>{@code server} may be null; the live-receiver check is then skipped (any subscribed
     * subchannel whose filter accepts the item will route, even if no receiver is loaded).
     */
    public static Decision evaluate(@Nullable MinecraftServer server,
                                    PhotonEmitterBlockEntity emitter,
                                    QuantumChannel channel,
                                    ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Decision.REJECT;
        // Void filter wins first — matches()=false means the user marked this item for voiding.
        if (!emitter.voidFilter().matches(stack)) return Decision.VOID;
        // Walk the emitter's own subchannels in declaration order. First match with a live
        // subscribed receiver wins.
        if (emitter.itemSubchannelCount() == 0) return Decision.REJECT;
        for (ItemSubchannel sub : emitter.itemSubchannels()) {
            if (!sub.filter().matches(stack)) continue;
            if (server != null && !hasLoadedReceiverFor(server, channel, sub.id())) continue;
            return Decision.route(sub.id());
        }
        return Decision.REJECT;
    }

    /** Any loaded item-mode receiver on {@code channel} subscribed to {@code subchannelId}? */
    public static boolean hasLoadedReceiverFor(MinecraftServer server, QuantumChannel channel, UUID subchannelId) {
        if (server == null || subchannelId == null) return false;
        for (GlobalPos gp : channel.members()) {
            ServerLevel lvl = server.getLevel(gp.dimension());
            if (lvl == null || !lvl.isLoaded(gp.pos())) continue;
            BlockEntity be = lvl.getBlockEntity(gp.pos());
            if (!(be instanceof PhotonReceiverBlockEntity rcv)) continue;
            if (!rcv.isItemsEnabled()) continue;
            if (rcv.isSubscribedTo(subchannelId)) return true;
        }
        return false;
    }

    /** Loaded receivers for one subchannel, sorted by priority desc then position. Skips items-disabled. */
    public static List<PhotonReceiverBlockEntity> loadedReceiversFor(MinecraftServer server,
                                                                     QuantumChannel channel,
                                                                     UUID subchannelId) {
        if (server == null || subchannelId == null) return List.of();
        List<PhotonReceiverBlockEntity> out = new ArrayList<>();
        for (GlobalPos gp : channel.members()) {
            ServerLevel lvl = server.getLevel(gp.dimension());
            if (lvl == null || !lvl.isLoaded(gp.pos())) continue;
            BlockEntity be = lvl.getBlockEntity(gp.pos());
            if (!(be instanceof PhotonReceiverBlockEntity rcv)) continue;
            if (!rcv.isItemsEnabled()) continue;
            if (!rcv.isSubscribedTo(subchannelId)) continue;
            out.add(rcv);
        }
        out.sort(Comparator
                .comparingInt((PhotonReceiverBlockEntity r) -> -r.getPriority())
                .thenComparingLong(r -> r.getBlockPos().asLong()));
        return out;
    }

    /**
     * Routes {@code stack} through {@code emitter}. Returns the unaccepted remainder.
     * VOID consumes the whole stack (returns empty); REJECT returns it unchanged; ROUTE pushes
     * as many items as the receivers' adjacent inventories will accept and returns whatever didn't fit.
     */
    public static ItemStack route(ServerLevel level, PhotonEmitterBlockEntity emitter,
                                  QuantumChannel channel, ItemStack stack, boolean simulate) {
        Decision d = emitter.decide(channel, stack);
        return switch (d.kind) {
            case VOID -> ItemStack.EMPTY;
            case REJECT -> stack;
            case ROUTE -> pushToReceivers(level.getServer(), emitter, channel, d.subchannelId, stack, simulate);
        };
    }

    private static ItemStack pushToReceivers(MinecraftServer server,
                                             PhotonEmitterBlockEntity emitter,
                                             QuantumChannel channel,
                                             UUID subchannelId, ItemStack stack, boolean simulate) {
        List<PhotonReceiverBlockEntity> targets = loadedReceiversFor(server, channel, subchannelId);
        if (targets.isEmpty()) return stack;
        targets = applyEmitterDispatch(emitter, targets, simulate);
        ItemStack remaining = stack;
        for (PhotonReceiverBlockEntity rcv : targets) {
            if (remaining.isEmpty()) break;
            remaining = pushToAdjacentInventory(rcv, remaining, simulate);
        }
        return remaining;
    }

    /** Rotates the target list according to the emitter's item dispatch strategy. */
    private static List<PhotonReceiverBlockEntity> applyEmitterDispatch(
            PhotonEmitterBlockEntity emitter, List<PhotonReceiverBlockEntity> targets, boolean simulate) {
        if (targets.size() <= 1) return targets;
        if (emitter.getItemDispatch() != com.quantumchanneling.channel.DispatchStrategy.ROUND_ROBIN) return targets;
        int start = simulate
                ? Math.floorMod(emitter.takeItemRoundRobinIndex(targets.size()) - 1, targets.size())
                : emitter.takeItemRoundRobinIndex(targets.size());
        List<PhotonReceiverBlockEntity> rotated = new ArrayList<>(targets.size());
        for (int i = 0; i < targets.size(); i++) rotated.add(targets.get((start + i) % targets.size()));
        return rotated;
    }

    /**
     * Pushes {@code stack} into adjacent {@link IItemHandler}s, walking the 6 sides in the order
     * dictated by {@code origin.getItemDispatch()}. SERVE_FIRST keeps the natural Direction order;
     * ROUND_ROBIN rotates the starting side so reach-fills spread out over many ticks.
     */
    public static ItemStack pushToAdjacentInventory(ChannelBoundBlockEntity origin, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return stack;
        var level = origin.getLevel();
        if (level == null) return stack;
        Direction[] sides = Direction.values();
        int startIdx = (origin.getItemDispatch() == com.quantumchanneling.channel.DispatchStrategy.ROUND_ROBIN
                && !simulate)
                ? origin.takeItemRoundRobinIndex(sides.length)
                : 0;
        ItemStack remaining = stack;
        for (int i = 0; i < sides.length; i++) {
            if (remaining.isEmpty()) break;
            Direction side = sides[(startIdx + i) % sides.length];
            BlockEntity neighbor = level.getBlockEntity(origin.getBlockPos().relative(side));
            if (neighbor == null) continue;
            IItemHandler dest = neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).orElse(null);
            if (dest == null) continue;
            remaining = insertAllSlots(dest, remaining, simulate);
        }
        return remaining;
    }

    /** Standard "try each slot" insertion. Returns whatever didn't fit. */
    public static ItemStack insertAllSlots(IItemHandler dest, ItemStack stack, boolean simulate) {
        ItemStack remaining = stack;
        for (int slot = 0; slot < dest.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = dest.insertItem(slot, remaining, simulate);
        }
        return remaining;
    }

    /** Active-pull cycle: scan adjacency, route the first matching slot. Returns items moved. */
    public static int pullFromAdjacentAndRoute(ServerLevel level, PhotonEmitterBlockEntity emitter,
                                               QuantumChannel channel, int budget) {
        if (budget <= 0) return 0;
        if (!emitter.isItemsEnabled()) return 0;
        BlockPos origin = emitter.getBlockPos();
        for (Direction side : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(origin.relative(side));
            if (neighbor == null) continue;
            IItemHandler src = neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).orElse(null);
            if (src == null) continue;
            int moved = scanAndRoute(level, emitter, channel, src, budget);
            if (moved > 0) return moved;
        }
        return 0;
    }

    private static int scanAndRoute(ServerLevel level, PhotonEmitterBlockEntity emitter,
                                    QuantumChannel channel, IItemHandler src, int budget) {
        for (int slot = 0; slot < src.getSlots(); slot++) {
            ItemStack peek = src.extractItem(slot, budget, true);
            if (peek.isEmpty()) continue;
            Decision d = emitter.decide(channel, peek);
            switch (d.kind) {
                case REJECT -> { continue; }
                case VOID -> {
                    ItemStack taken = src.extractItem(slot, peek.getCount(), false);
                    return taken.getCount();
                }
                case ROUTE -> {
                    ItemStack simulated = pushToReceivers(level.getServer(), emitter, channel, d.subchannelId, peek, true);
                    int wouldMove = peek.getCount() - simulated.getCount();
                    if (wouldMove <= 0) continue;
                    ItemStack taken = src.extractItem(slot, wouldMove, false);
                    if (taken.isEmpty()) continue;
                    ItemStack leftover = pushToReceivers(level.getServer(), emitter, channel, d.subchannelId, taken, false);
                    if (!leftover.isEmpty()) src.insertItem(slot, leftover, false);
                    return taken.getCount() - leftover.getCount();
                }
            }
        }
        return 0;
    }

    /** Registry-key lookup used for decision caching. */
    public static ResourceLocation idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}
