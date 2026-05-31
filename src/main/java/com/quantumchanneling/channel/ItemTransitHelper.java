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
            if (!rcv.isResourceGate(rcv.isItemsEnabled())) continue;
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
            if (!rcv.isResourceGate(rcv.isItemsEnabled())) continue;
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
     *
     * <p>The capability-pushed entry point (hopper, AE2 import bus, etc.) has no concept of a
     * "source block we pulled from," so loop detection is bypassed here. Loop detection only
     * applies to {@link #pullFromAdjacentAndRoute}.
     */
    public static ItemStack route(ServerLevel level, PhotonEmitterBlockEntity emitter,
                                  QuantumChannel channel, ItemStack stack, boolean simulate) {
        Decision d = emitter.decide(channel, stack);
        switch (d.kind) {
            case VOID: return ItemStack.EMPTY;
            case REJECT: return stack;
            case ROUTE: {
                ItemStack leftover = pushToReceivers(level.getServer(), emitter, channel, d.subchannelId, stack, simulate, null);
                if (!simulate) {
                    int moved = stack.getCount() - leftover.getCount();
                    if (moved > 0) {
                        ItemSubchannel sub = emitter.itemSubchannel(d.subchannelId);
                        if (sub != null) sub.recordRouted(moved);
                        emitter.recordItemsRouted(moved);
                    }
                }
                return leftover;
            }
            default: return stack;
        }
    }

    /**
     * Pushes {@code stack} to the channel's subscribed receivers.
     *
     * @param sourceBlockPos The block this batch was pulled from, or {@code null} when the entry
     *                       point was a capability push (no known source). When non-null, the push
     *                       refuses to deliver back into a receiver-side inventory at the same
     *                       position — that would be a same-block loop, and the user has explicitly
     *                       asked the mod to halt + warn instead of churning items through the
     *                       routing pipeline for no net movement.
     */
    private static ItemStack pushToReceivers(MinecraftServer server,
                                             PhotonEmitterBlockEntity emitter,
                                             QuantumChannel channel,
                                             UUID subchannelId, ItemStack stack, boolean simulate,
                                             @Nullable BlockPos sourceBlockPos) {
        List<PhotonReceiverBlockEntity> targets = loadedReceiversFor(server, channel, subchannelId);
        if (targets.isEmpty()) return stack;
        targets = applyEmitterDispatch(emitter, targets, simulate);
        long now = (emitter.getLevel() != null) ? emitter.getLevel().getGameTime() : 0L;
        ItemStack remaining = stack;
        for (PhotonReceiverBlockEntity rcv : targets) {
            if (remaining.isEmpty()) break;
            long key = rcv.getBlockPos().asLong();
            if (emitter.isItemReceiverCooledDown(key, now)) continue;
            int before = remaining.getCount();
            // Same-dimension check pairs with the BlockPos check below — a coincidentally-equal
            // BlockPos in a different dimension is a totally different block and should still route.
            boolean sameDimension = sourceBlockPos != null
                    && emitter.getLevel() != null && rcv.getLevel() != null
                    && emitter.getLevel().dimension().equals(rcv.getLevel().dimension());
            BlockPos forbidden = sameDimension ? sourceBlockPos : null;
            remaining = pushToAdjacentInventory(rcv, remaining, simulate, emitter, forbidden);
            if (!simulate) {
                int delivered = before - remaining.getCount();
                if (delivered > 0) {
                    emitter.clearItemReceiverCooldown(key);
                    rcv.recordItemsRouted(delivered);
                } else {
                    emitter.markItemReceiverRejected(key, now);
                }
            }
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
        return pushToAdjacentInventory(origin, stack, simulate, null, null);
    }

    /**
     * Loop-aware push. If {@code forbiddenPos} matches a candidate neighbor block, the push skips
     * that neighbor and marks {@code reportingEmitter} (when non-null) so the UI surfaces a "loop
     * detected" warning. Both args are ignored when null — they're the same-block-as-pull-source
     * fast-path; callers that don't have a source (capability pushes) just pass nulls.
     */
    public static ItemStack pushToAdjacentInventory(ChannelBoundBlockEntity origin, ItemStack stack, boolean simulate,
                                                    @Nullable PhotonEmitterBlockEntity reportingEmitter,
                                                    @Nullable BlockPos forbiddenPos) {
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
            if (!origin.isItemSideArmed(side)) continue;        // per-side mask gates push-sides
            BlockPos neighborPos = origin.getBlockPos().relative(side);
            // Loop guard — refuse to push back into the block this batch was pulled from on the
            // same channel. The emitter would just pull the same items out next tick: net zero,
            // CPU non-zero. Mark the emitter so the UI lights the loop warning.
            if (forbiddenPos != null && forbiddenPos.equals(neighborPos)) {
                if (!simulate && reportingEmitter != null) reportingEmitter.markLoopDetected();
                continue;
            }
            BlockEntity neighbor = level.getBlockEntity(neighborPos);
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
            if (!emitter.isItemSideArmed(side)) continue;       // per-side mask gates pull-sides
            BlockPos neighborPos = origin.relative(side);
            BlockEntity neighbor = level.getBlockEntity(neighborPos);
            if (neighbor == null) continue;
            IItemHandler src = neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).orElse(null);
            if (src == null) continue;
            int moved = scanAndRoute(level, emitter, channel, src, budget, neighborPos);
            if (moved > 0) return moved;
        }
        return 0;
    }

    private static int scanAndRoute(ServerLevel level, PhotonEmitterBlockEntity emitter,
                                    QuantumChannel channel, IItemHandler src, int budget,
                                    BlockPos sourceBlockPos) {
        for (int slot = 0; slot < src.getSlots(); slot++) {
            // Cheap empty check first. getStackInSlot is a direct read of the handler's internal
            // array — no allocation, no extraction logic. Saves the extract-simulate path on
            // every empty slot of a chest, which is the steady-state cost when an emitter is
            // adjacent to a partially-empty inventory.
            if (src.getStackInSlot(slot).isEmpty()) continue;
            ItemStack peek = src.extractItem(slot, budget, true);
            if (peek.isEmpty()) continue;
            Decision d = emitter.decide(channel, peek);
            switch (d.kind) {
                case REJECT -> { continue; }
                case VOID -> {
                    ItemStack taken = src.extractItem(slot, peek.getCount(), false);
                    int moved = taken.getCount();
                    if (moved > 0) emitter.recordItemsRouted(moved);
                    return moved;
                }
                case ROUTE -> {
                    ItemStack simulated = pushToReceivers(level.getServer(), emitter, channel, d.subchannelId, peek, true, sourceBlockPos);
                    int wouldMove = peek.getCount() - simulated.getCount();
                    if (wouldMove <= 0) continue;
                    ItemStack taken = src.extractItem(slot, wouldMove, false);
                    if (taken.isEmpty()) continue;
                    ItemStack leftover = pushToReceivers(level.getServer(), emitter, channel, d.subchannelId, taken, false, sourceBlockPos);
                    if (!leftover.isEmpty()) src.insertItem(slot, leftover, false);
                    int moved = taken.getCount() - leftover.getCount();
                    if (moved > 0) {
                        emitter.recordItemsRouted(moved);
                        ItemSubchannel sub = emitter.itemSubchannel(d.subchannelId);
                        if (sub != null) {
                            sub.recordRouted(moved);
                            fireRoutedEvent(emitter, sub, com.quantumchanneling.api.IQuantumSubchannelView.Kind.ITEM, moved);
                        }
                    }
                    return moved;
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

    /** Posts a {@link com.quantumchanneling.api.event.ChannelRoutedEvent} on the Forge bus. */
    static void fireRoutedEvent(PhotonEmitterBlockEntity emitter, ItemSubchannel sub,
                                com.quantumchanneling.api.IQuantumSubchannelView.Kind kind, int amount) {
        try {
            var view = com.quantumchanneling.api.QuantumChannelingAPI.deviceAt(
                    (ServerLevel) emitter.getLevel(), emitter.getBlockPos()).orElse(null);
            // The deviceAt() view exposes the subchannel via itemSubchannels(); find the entry that
            // matches our id so the event carries the same view shape external mods see elsewhere.
            com.quantumchanneling.api.IQuantumSubchannelView subView = null;
            if (view != null) {
                for (var s : view.itemSubchannels()) if (s.id().equals(sub.id())) { subView = s; break; }
            }
            if (subView == null) return;   // shouldn't happen, but a missed event is better than NPE
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new com.quantumchanneling.api.event.ChannelRoutedEvent(
                            emitter.getChannelId(), emitter.globalPos(), subView, kind, amount));
        } catch (Throwable ignored) {
            // Defensive — never let an API event consumer crash the routing tick.
        }
    }
}
