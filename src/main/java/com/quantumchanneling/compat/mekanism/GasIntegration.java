package com.quantumchanneling.compat.mekanism;

import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.channel.GasFilter;
import com.quantumchanneling.channel.GasSubchannel;
import com.quantumchanneling.channel.QuantumChannel;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Mekanism gas pipeline. Subchannel-aware: emitter applies its gas void filter, walks subscribed
 * gas subchannels in priority order, picks the first matching one whose subscribers are loaded,
 * and pushes the gas to the highest-priority subscribed receiver's adjacent gas tank.
 *
 * <p>This class imports {@code mekanism.api.*} and must not be loaded without Mekanism present.
 */
public final class GasIntegration {
    private GasIntegration() {}

    public enum DecisionKind { VOID, ROUTE, REJECT }
    public record Decision(DecisionKind kind, @Nullable UUID subchannelId) {
        public static final Decision VOID = new Decision(DecisionKind.VOID, null);
        public static final Decision REJECT = new Decision(DecisionKind.REJECT, null);
        public static Decision route(UUID id) { return new Decision(DecisionKind.ROUTE, id); }
    }

    public static Decision decide(@Nullable MinecraftServer server,
                                  PhotonEmitterBlockEntity emitter,
                                  QuantumChannel channel,
                                  GasStack stack) {
        if (stack == null || stack.isEmpty()) return Decision.REJECT;
        Gas gas = stack.getType();
        ResourceLocation gasId = MekanismAPI.gasRegistry().getKey(gas);
        if (gasId == null) return Decision.REJECT;
        java.util.function.Predicate<ResourceLocation> isInTag = tagId -> gasHasTag(gas, tagId);
        if (!emitter.gasVoidFilter().matchesIdWithTags(gasId, isInTag)) return Decision.VOID;
        if (emitter.gasSubchannelCount() == 0) return Decision.REJECT;
        for (GasSubchannel sub : emitter.gasSubchannels()) {
            if (!sub.filter().matchesIdWithTags(gasId, isInTag)) continue;
            if (server != null && !hasLoadedReceiverFor(server, channel, sub.id())) continue;
            return Decision.route(sub.id());
        }
        return Decision.REJECT;
    }

    /**
     * True when {@code gas} belongs to the tag with the given id under Mekanism's gas registry.
     * Returns false on any registry hiccup so a stale tag id can't accidentally pass the void
     * filter. Looked up per-call — the registry's own tag manager handles caching.
     */
    private static boolean gasHasTag(Gas gas, ResourceLocation tagId) {
        if (gas == null || tagId == null) return false;
        try {
            var registry = MekanismAPI.gasRegistry();
            var tagManager = registry.tags();
            if (tagManager == null) return false;
            var tagKey = net.minecraft.tags.TagKey.create(registry.getRegistryKey(), tagId);
            var tag = tagManager.getTag(tagKey);
            return tag != null && tag.contains(gas);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Emitter passthrough — any {@code insertChemical} routes through the channel pipeline. */
    public static IGasHandler emitterHandler(PhotonEmitterBlockEntity emitter) {
        return new IGasHandler() {
            @Override public int getTanks() { return 1; }
            @Override public @NotNull GasStack getChemicalInTank(int tank) { return GasStack.EMPTY; }
            @Override public void setChemicalInTank(int tank, @NotNull GasStack stack) {}
            @Override public long getTankCapacity(int tank) { return Long.MAX_VALUE; }
            @Override public boolean isValid(int tank, @NotNull GasStack stack) { return true; }
            @Override public @NotNull GasStack extractChemical(int tank, long amount, @NotNull Action action) { return GasStack.EMPTY; }
            @Override
            public @NotNull GasStack insertChemical(int tank, @NotNull GasStack stack, @NotNull Action action) {
                if (stack == null || stack.isEmpty()) return stack;
                if (!com.quantumchanneling.ServerConfig.gasesRoutingEnabled) return stack;
                if (!(emitter.getLevel() instanceof ServerLevel sl)) return stack;
                UUID id = emitter.getChannelId();
                if (id == null) return stack;
                QuantumChannel ch = ChannelData.get(sl.getServer()).getChannel(id);
                if (ch == null || !emitter.isGasEnabled()) return stack;
                return route(sl.getServer(), emitter, ch, stack, action);
            }
        };
    }

    /** Receiver-side: passive zero-tank handler. */
    public static IGasHandler receiverHandler() {
        return new IGasHandler() {
            @Override public int getTanks() { return 0; }
            @Override public @NotNull GasStack getChemicalInTank(int tank) { return GasStack.EMPTY; }
            @Override public void setChemicalInTank(int tank, @NotNull GasStack stack) {}
            @Override public long getTankCapacity(int tank) { return 0L; }
            @Override public boolean isValid(int tank, @NotNull GasStack stack) { return false; }
            @Override public @NotNull GasStack insertChemical(int tank, @NotNull GasStack stack, @NotNull Action action) { return stack; }
            @Override public @NotNull GasStack extractChemical(int tank, long amount, @NotNull Action action) { return GasStack.EMPTY; }
        };
    }

    /** Decide → execute. VOID consumes the whole stack; REJECT returns it; ROUTE pushes.
     *  Capability-pushed entry — no source position, loop detection off here. */
    public static GasStack route(MinecraftServer server, PhotonEmitterBlockEntity emitter,
                                 QuantumChannel channel, GasStack stack, Action action) {
        Decision d = decide(server, emitter, channel, stack);
        switch (d.kind) {
            case VOID: return GasStack.EMPTY;
            case REJECT: return stack;
            case ROUTE: {
                GasStack leftover = pushToReceivers(server, emitter, channel, d.subchannelId, stack, action, null);
                if (action == Action.EXECUTE) {
                    long moved = stack.getAmount() - leftover.getAmount();
                    if (moved > 0) {
                        GasSubchannel sub = emitter.gasSubchannel(d.subchannelId);
                        if (sub != null) sub.recordRouted((int) Math.min(moved, Integer.MAX_VALUE));
                        emitter.recordGasRouted((int) Math.min(moved, Integer.MAX_VALUE));
                    }
                }
                return leftover;
            }
            default: return stack;
        }
    }

    public static boolean hasLoadedReceiverFor(MinecraftServer server, QuantumChannel channel, UUID subchannelId) {
        if (server == null || subchannelId == null) return false;
        for (GlobalPos gp : channel.members()) {
            ServerLevel lvl = server.getLevel(gp.dimension());
            if (lvl == null || !lvl.isLoaded(gp.pos())) continue;
            BlockEntity be = lvl.getBlockEntity(gp.pos());
            if (!(be instanceof PhotonReceiverBlockEntity rcv)) continue;
            if (!rcv.isResourceGate(rcv.isGasEnabled())) continue;
            if (rcv.isSubscribedToGas(subchannelId)) return true;
        }
        return false;
    }

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
            if (!rcv.isResourceGate(rcv.isGasEnabled())) continue;
            if (!rcv.isSubscribedToGas(subchannelId)) continue;
            out.add(rcv);
        }
        out.sort(Comparator.comparingInt((PhotonReceiverBlockEntity r) -> -r.getPriority())
                .thenComparingLong(r -> r.getBlockPos().asLong()));
        return out;
    }

    private static GasStack pushToReceivers(MinecraftServer server,
                                            PhotonEmitterBlockEntity emitter,
                                            QuantumChannel channel,
                                            UUID subchannelId, GasStack stack, Action action,
                                            @org.jetbrains.annotations.Nullable net.minecraft.core.BlockPos sourceBlockPos) {
        List<PhotonReceiverBlockEntity> targets = loadedReceiversFor(server, channel, subchannelId);
        if (targets.isEmpty()) return stack;
        if (emitter.getGasDispatch() == com.quantumchanneling.channel.DispatchStrategy.ROUND_ROBIN
                && targets.size() > 1) {
            int start = (action == Action.EXECUTE)
                    ? emitter.takeGasRoundRobinIndex(targets.size())
                    : Math.floorMod(emitter.takeGasRoundRobinIndex(targets.size()) - 1, targets.size());
            List<PhotonReceiverBlockEntity> rotated = new ArrayList<>(targets.size());
            for (int i = 0; i < targets.size(); i++) rotated.add(targets.get((start + i) % targets.size()));
            targets = rotated;
        }
        long now = (emitter.getLevel() != null) ? emitter.getLevel().getGameTime() : 0L;
        GasStack remaining = stack.copy();
        for (PhotonReceiverBlockEntity rcv : targets) {
            if (remaining.isEmpty()) break;
            long key = rcv.getBlockPos().asLong();
            if (emitter.isGasReceiverCooledDown(key, now)) continue;
            long before = remaining.getAmount();
            boolean sameDimension = sourceBlockPos != null
                    && emitter.getLevel() != null && rcv.getLevel() != null
                    && emitter.getLevel().dimension().equals(rcv.getLevel().dimension());
            net.minecraft.core.BlockPos forbidden = sameDimension ? sourceBlockPos : null;
            remaining = pushToAdjacentTanks(rcv, remaining, action, emitter, forbidden);
            if (action == Action.EXECUTE) {
                long delivered = before - remaining.getAmount();
                if (delivered > 0) {
                    emitter.clearGasReceiverCooldown(key);
                    rcv.recordGasRouted((int) Math.min(delivered, Integer.MAX_VALUE));
                } else {
                    emitter.markGasReceiverRejected(key, now);
                }
            }
        }
        return remaining;
    }

    private static GasStack pushToAdjacentTanks(PhotonReceiverBlockEntity origin, GasStack stack, Action action,
                                                @org.jetbrains.annotations.Nullable PhotonEmitterBlockEntity reportingEmitter,
                                                @org.jetbrains.annotations.Nullable net.minecraft.core.BlockPos forbiddenPos) {
        if (stack.isEmpty()) return stack;
        var level = origin.getLevel();
        if (level == null) return stack;
        Direction[] sides = Direction.values();
        int startIdx = (origin.getGasDispatch() == com.quantumchanneling.channel.DispatchStrategy.ROUND_ROBIN
                && action == Action.EXECUTE)
                ? origin.takeGasRoundRobinIndex(sides.length)
                : 0;
        GasStack remaining = stack.copy();
        for (int i = 0; i < sides.length; i++) {
            if (remaining.isEmpty()) break;
            Direction side = sides[(startIdx + i) % sides.length];
            if (!origin.isGasSideArmed(side)) continue;
            net.minecraft.core.BlockPos neighborPos = origin.getBlockPos().relative(side);
            if (forbiddenPos != null && forbiddenPos.equals(neighborPos)) {
                if (action == Action.EXECUTE && reportingEmitter != null) reportingEmitter.markLoopDetected();
                continue;
            }
            BlockEntity neighbor = level.getBlockEntity(neighborPos);
            if (neighbor == null) continue;
            IGasHandler dest = neighbor.getCapability(MekanismCaps.GAS, side.getOpposite()).orElse(null);
            if (dest == null) continue;
            for (int tank = 0; tank < dest.getTanks() && !remaining.isEmpty(); tank++) {
                GasStack leftover = dest.insertChemical(tank, remaining, action);
                if (leftover.getAmount() == remaining.getAmount()) continue;
                remaining = leftover;
            }
        }
        return remaining;
    }

    /**
     * Active-pull cycle: scan emitter's neighbors for an IGasHandler source, drain, route.
     * Returns true when something was actually moved (or voided) so the emitter's adaptive
     * tick rate knows whether to coast or stay fast.
     */
    public static boolean pullAndRoute(ServerLevel level, PhotonEmitterBlockEntity emitter,
                                       QuantumChannel channel, int budget) {
        if (budget <= 0 || !emitter.isGasEnabled()) return false;
        for (Direction side : Direction.values()) {
            if (!emitter.isGasSideArmed(side)) continue;
            net.minecraft.core.BlockPos neighborPos = emitter.getBlockPos().relative(side);
            BlockEntity neighbor = level.getBlockEntity(neighborPos);
            if (neighbor == null) continue;
            IGasHandler src = neighbor.getCapability(MekanismCaps.GAS, side.getOpposite()).orElse(null);
            if (src == null) continue;
            for (int tank = 0; tank < src.getTanks(); tank++) {
                // Cheap empty check — see ItemTransitHelper.scanAndRoute for the same idea.
                if (src.getChemicalInTank(tank).isEmpty()) continue;
                GasStack peek = src.extractChemical(tank, budget, Action.SIMULATE);
                if (peek.isEmpty()) continue;
                Decision d = decide(level.getServer(), emitter, channel, peek);
                switch (d.kind) {
                    case REJECT -> { continue; }
                    case VOID -> {
                        GasStack taken = src.extractChemical(tank, peek.getAmount(), Action.EXECUTE);
                        long moved = taken.getAmount();
                        if (moved > 0) emitter.recordGasRouted((int) Math.min(moved, Integer.MAX_VALUE));
                        return true;
                    }
                    case ROUTE -> {
                        GasStack simulated = pushToReceivers(level.getServer(), emitter, channel, d.subchannelId, peek, Action.SIMULATE, neighborPos);
                        long wouldMove = peek.getAmount() - simulated.getAmount();
                        if (wouldMove <= 0) continue;
                        GasStack taken = src.extractChemical(tank, wouldMove, Action.EXECUTE);
                        if (taken.isEmpty()) continue;
                        GasStack leftover = pushToReceivers(level.getServer(), emitter, channel, d.subchannelId, taken, Action.EXECUTE, neighborPos);
                        long moved = taken.getAmount() - leftover.getAmount();
                        if (moved > 0) {
                            emitter.recordGasRouted((int) Math.min(moved, Integer.MAX_VALUE));
                            GasSubchannel sub = emitter.gasSubchannel(d.subchannelId);
                            if (sub != null) sub.recordRouted((int) Math.min(moved, Integer.MAX_VALUE));
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
