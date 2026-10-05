package com.quantumchanneling.channel;

import com.quantumchanneling.api.IQuantumSubchannelView.Kind;
import com.quantumchanneling.api.QuantumChannelingAPI;
import com.quantumchanneling.api.event.ChannelRoutedEvent;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import com.quantumchanneling.compat.mekanism.ChemicalCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.RegisteredResource;
import net.neoforged.neoforge.transfer.transaction.RootCommitJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Moves items, fluids and Mekanism chemicals through a channel. The algorithm is the same for all
 * three:
 * <ol>
 *   <li>The emitter's void filter wins first — a voided resource is accepted and destroyed.</li>
 *   <li>The emitter's subchannels are walked in order; the first whose filter accepts the
 *       resource and that has a loaded, enabled, subscribed receiver gets it.</li>
 *   <li>The resource goes straight into that receiver's adjacent handlers. No channel buffer.</li>
 * </ol>
 *
 * <p>Every move runs inside a transaction, so a push that can't complete rolls back with nothing
 * lost. Bookkeeping that must not happen for a rolled-back move — throughput stats, receiver
 * cooldowns, round-robin cursors, routed events — is deferred until the outermost transaction
 * commits.
 */
public final class ResourceRouter<R extends RegisteredResource<?>> {
    public static final ResourceRouter<ItemResource> ITEMS = new ResourceRouter<>(Kind.ITEM, Capabilities.Item.BLOCK);
    public static final ResourceRouter<FluidResource> FLUIDS = new ResourceRouter<>(Kind.FLUID, Capabilities.Fluid.BLOCK);
    public static final ResourceRouter<RegisteredResource<?>> CHEMICALS = new ResourceRouter<>(Kind.GAS, ChemicalCompat.BLOCK);

    private final Kind kind;
    private final BlockCapability<ResourceHandler<R>, @Nullable Direction> capability;

    private ResourceRouter(Kind kind, BlockCapability<ResourceHandler<R>, @Nullable Direction> capability) {
        this.kind = kind;
        this.capability = capability;
    }

    public static ResourceRouter<?> of(Kind kind) {
        return switch (kind) {
            case ITEM -> ITEMS;
            case FLUID -> FLUIDS;
            case GAS -> CHEMICALS;
        };
    }

    public Kind kind() { return kind; }

    /** Registry id of a resource type — the key emitters cache decisions under. */
    public static @Nullable Identifier idOf(Holder<?> type) {
        return type.unwrapKey().map(ResourceKey::identifier).orElse(null);
    }

    /**
     * Pure decision — no mutation, no caching. Server tick paths go through
     * {@link PhotonEmitterBlockEntity#decide} so cache invalidation is handled. A null
     * {@code server} skips the live-receiver check.
     */
    public RouteDecision evaluate(@Nullable MinecraftServer server, PhotonEmitterBlockEntity emitter,
                                  QuantumChannel channel, Holder<?> type) {
        // Void filter is a blacklist — matches()=false means the user marked this for voiding.
        if (!emitter.voidFilter(kind).matches(type)) return RouteDecision.VOID;
        for (Subchannel sub : emitter.subchannels(kind)) {
            if (!sub.filter().matches(type)) continue;
            if (server != null && !hasLoadedReceiverFor(server, channel, sub.id())) continue;
            return RouteDecision.route(sub.id());
        }
        return RouteDecision.REJECT;
    }

    /** Any loaded, enabled receiver on {@code channel} subscribed to {@code subchannelId}? */
    public boolean hasLoadedReceiverFor(MinecraftServer server, QuantumChannel channel, UUID subchannelId) {
        for (GlobalPos gp : channel.members()) {
            if (loadedBlockEntity(server, gp) instanceof PhotonReceiverBlockEntity rcv
                    && rcv.isActive(kind) && rcv.isSubscribed(kind, subchannelId)) return true;
        }
        return false;
    }

    /** Loaded, enabled receivers for one subchannel, by priority (desc) then position. */
    private List<PhotonReceiverBlockEntity> loadedReceiversFor(MinecraftServer server, QuantumChannel channel,
                                                               UUID subchannelId) {
        List<PhotonReceiverBlockEntity> out = new ArrayList<>();
        for (GlobalPos gp : channel.members()) {
            if (loadedBlockEntity(server, gp) instanceof PhotonReceiverBlockEntity rcv
                    && rcv.isActive(kind) && rcv.isSubscribed(kind, subchannelId)) out.add(rcv);
        }
        out.sort(Comparator.comparingInt((PhotonReceiverBlockEntity r) -> -r.getPriority())
                .thenComparingLong(r -> r.getBlockPos().asLong()));
        return out;
    }

    private static @Nullable BlockEntity loadedBlockEntity(MinecraftServer server, GlobalPos gp) {
        ServerLevel lvl = server.getLevel(gp.dimension());
        if (lvl == null || !lvl.isLoaded(gp.pos())) return null;
        return lvl.getBlockEntity(gp.pos());
    }

    /**
     * Capability-pushed entry point (hoppers, pipes, import buses). Returns how much of
     * {@code amount} the channel accepted inside {@code tx}. There's no known source block here,
     * so loop detection doesn't apply.
     */
    public int insert(ServerLevel level, PhotonEmitterBlockEntity emitter, QuantumChannel channel,
                      R resource, int amount, TransactionContext tx) {
        RouteDecision d = emitter.decide(kind, channel, resource.typeHolder());
        return switch (d.kind()) {
            case VOID -> amount;
            case REJECT -> 0;
            case ROUTE -> {
                int moved = pushToReceivers(level, emitter, channel, d.subchannelId(), resource, amount, tx, null);
                if (moved > 0) recordRoute(emitter, d.subchannelId(), moved, tx);
                yield moved;
            }
        };
    }

    /** Active pull: scan the emitter's armed sides and route the first movable batch. Returns the amount moved. */
    public int pullFromAdjacent(ServerLevel level, PhotonEmitterBlockEntity emitter, QuantumChannel channel, int budget) {
        if (budget <= 0) return 0;
        BlockPos origin = emitter.getBlockPos();
        for (Direction side : Direction.values()) {
            if (!emitter.isSideArmed(kind, side)) continue;
            BlockPos sourcePos = origin.relative(side);
            ResourceHandler<R> source = level.getCapability(capability, sourcePos, side.getOpposite());
            if (source == null) continue;
            int moved = scanAndRoute(level, emitter, channel, source, budget, sourcePos);
            if (moved > 0) return moved;
        }
        return 0;
    }

    private int scanAndRoute(ServerLevel level, PhotonEmitterBlockEntity emitter, QuantumChannel channel,
                             ResourceHandler<R> source, int budget, BlockPos sourcePos) {
        for (int index = 0; index < source.size(); index++) {
            R resource = source.getResource(index);
            if (resource.isEmpty() || source.getAmountAsLong(index) <= 0) continue;
            RouteDecision d = emitter.decide(kind, channel, resource.typeHolder());
            if (d.kind() == RouteDecision.Kind.REJECT) continue;

            try (Transaction tx = Transaction.openRoot()) {
                int moved;
                if (d.kind() == RouteDecision.Kind.VOID) {
                    int voided = source.extract(index, resource, budget, tx);
                    if (voided <= 0) continue;
                    onCommit(tx, () -> emitter.recordRouted(kind, voided));
                    moved = voided;
                } else {
                    // Size the batch with a throwaway pass first, so we only ever take from the
                    // source what the receivers will actually accept right now.
                    int deliverable;
                    try (Transaction probe = Transaction.open(tx)) {
                        int taken = source.extract(index, resource, budget, probe);
                        deliverable = taken <= 0 ? 0
                                : pushToReceivers(level, emitter, channel, d.subchannelId(), resource, taken, probe, sourcePos);
                    }
                    if (deliverable <= 0) continue;
                    if (source.extract(index, resource, deliverable, tx) != deliverable) continue;
                    moved = pushToReceivers(level, emitter, channel, d.subchannelId(), resource, deliverable, tx, sourcePos);
                    // Anything short of the probed amount means the world shifted under us — roll
                    // the whole batch back rather than strand extracted resource.
                    if (moved != deliverable) continue;
                    recordRoute(emitter, d.subchannelId(), moved, tx);
                }
                tx.commit();
                return moved;
            }
        }
        return 0;
    }

    /**
     * Pushes up to {@code amount} into the channel's subscribed receivers.
     *
     * @param sourcePos the block this batch was pulled from, or null for capability pushes. A
     *                  receiver-side neighbour at the same position (same dimension) is a loop: the
     *                  emitter would pull the same resource straight back out. It's skipped and
     *                  flagged on the emitter so the UI can warn.
     */
    private int pushToReceivers(ServerLevel level, PhotonEmitterBlockEntity emitter, QuantumChannel channel,
                                UUID subchannelId, R resource, int amount, TransactionContext tx,
                                @Nullable BlockPos sourcePos) {
        List<PhotonReceiverBlockEntity> targets = loadedReceiversFor(level.getServer(), channel, subchannelId);
        if (targets.isEmpty()) return 0;
        boolean rotate = targets.size() > 1 && emitter.getDispatch(kind) == DispatchStrategy.ROUND_ROBIN;
        int start = rotate ? emitter.peekRoundRobin(kind, targets.size()) : 0;
        long now = level.getGameTime();

        int delivered = 0;
        for (int i = 0; i < targets.size() && delivered < amount; i++) {
            PhotonReceiverBlockEntity rcv = targets.get((start + i) % targets.size());
            long key = rcv.getBlockPos().asLong();
            if (emitter.isReceiverCooledDown(kind, key, now)) continue;
            BlockPos forbidden = sourcePos != null && rcv.getLevel() == level ? sourcePos : null;
            int got = pushToAdjacent(rcv, resource, amount - delivered, tx, emitter, forbidden);
            delivered += got;
            if (got > 0) {
                onCommit(tx, () -> {
                    emitter.clearReceiverCooldown(kind, key);
                    rcv.recordRouted(kind, got);
                });
            } else {
                onCommit(tx, () -> emitter.markReceiverRejected(kind, key, now));
            }
        }
        if (rotate && delivered > 0) onCommit(tx, () -> emitter.advanceRoundRobin(kind));
        return delivered;
    }

    /** Inserts into the receiver's armed neighbours, rotating the start side under round-robin. */
    private int pushToAdjacent(PhotonReceiverBlockEntity origin, R resource, int amount, TransactionContext tx,
                               PhotonEmitterBlockEntity reportingEmitter, @Nullable BlockPos forbiddenPos) {
        Level level = origin.getLevel();
        if (level == null) return 0;
        Direction[] sides = Direction.values();
        boolean rotate = origin.getDispatch(kind) == DispatchStrategy.ROUND_ROBIN;
        int start = rotate ? origin.peekRoundRobin(kind, sides.length) : 0;

        int inserted = 0;
        for (int i = 0; i < sides.length && inserted < amount; i++) {
            Direction side = sides[(start + i) % sides.length];
            if (!origin.isSideArmed(kind, side)) continue;
            BlockPos neighborPos = origin.getBlockPos().relative(side);
            if (neighborPos.equals(forbiddenPos)) {
                reportingEmitter.markLoopDetected();
                continue;
            }
            // Never hand off to another photon device — resources travel through the channel, and
            // an adjacent emitter would route the batch straight back in.
            if (level.getBlockEntity(neighborPos) instanceof ChannelBoundBlockEntity) continue;
            ResourceHandler<R> dest = level.getCapability(capability, neighborPos, side.getOpposite());
            if (dest == null) continue;
            inserted += ResourceHandlerUtil.insertStacking(dest, resource, amount - inserted, tx);
        }
        if (rotate && inserted > 0) onCommit(tx, () -> origin.advanceRoundRobin(kind));
        return inserted;
    }

    private void recordRoute(PhotonEmitterBlockEntity emitter, UUID subchannelId, int amount, TransactionContext tx) {
        onCommit(tx, () -> {
            emitter.recordRouted(kind, amount);
            Subchannel sub = emitter.subchannel(kind, subchannelId);
            if (sub == null) return;
            sub.recordRouted(amount);
            try {
                NeoForge.EVENT_BUS.post(new ChannelRoutedEvent(emitter.getChannelId(), emitter.globalPos(),
                        QuantumChannelingAPI.viewOf(sub, kind), kind, amount));
            } catch (Throwable ignored) {
                // A misbehaving listener must never break the routing tick.
            }
        });
    }

    private static void onCommit(TransactionContext tx, Runnable action) {
        new RootCommitJournal(action).updateSnapshots(tx);
    }
}
