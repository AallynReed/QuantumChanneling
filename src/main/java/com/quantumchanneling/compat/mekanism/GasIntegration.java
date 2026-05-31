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
        ResourceLocation gasId = MekanismAPI.gasRegistry().getKey(stack.getType());
        if (gasId == null) return Decision.REJECT;
        if (!emitter.gasVoidFilter().matchesId(gasId)) return Decision.VOID;
        if (emitter.gasSubchannelCount() == 0) return Decision.REJECT;
        for (GasSubchannel sub : emitter.gasSubchannels()) {
            if (!sub.filter().matchesId(gasId)) continue;
            if (server != null && !hasLoadedReceiverFor(server, channel, sub.id())) continue;
            return Decision.route(sub.id());
        }
        return Decision.REJECT;
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

    /** Decide → execute. VOID consumes the whole stack; REJECT returns it; ROUTE pushes. */
    public static GasStack route(MinecraftServer server, PhotonEmitterBlockEntity emitter,
                                 QuantumChannel channel, GasStack stack, Action action) {
        Decision d = decide(server, emitter, channel, stack);
        return switch (d.kind) {
            case VOID -> GasStack.EMPTY;
            case REJECT -> stack;
            case ROUTE -> pushToReceivers(server, emitter, channel, d.subchannelId, stack, action);
        };
    }

    public static boolean hasLoadedReceiverFor(MinecraftServer server, QuantumChannel channel, UUID subchannelId) {
        if (server == null || subchannelId == null) return false;
        for (GlobalPos gp : channel.members()) {
            ServerLevel lvl = server.getLevel(gp.dimension());
            if (lvl == null || !lvl.isLoaded(gp.pos())) continue;
            BlockEntity be = lvl.getBlockEntity(gp.pos());
            if (!(be instanceof PhotonReceiverBlockEntity rcv)) continue;
            if (!rcv.isGasEnabled()) continue;
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
            if (!rcv.isGasEnabled()) continue;
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
                                            UUID subchannelId, GasStack stack, Action action) {
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
        GasStack remaining = stack.copy();
        for (PhotonReceiverBlockEntity rcv : targets) {
            if (remaining.isEmpty()) break;
            remaining = pushToAdjacentTanks(rcv, remaining, action);
        }
        return remaining;
    }

    private static GasStack pushToAdjacentTanks(PhotonReceiverBlockEntity origin, GasStack stack, Action action) {
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
            BlockEntity neighbor = level.getBlockEntity(origin.getBlockPos().relative(side));
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

    /** Active-pull cycle: scan emitter's neighbors for an IGasHandler source, drain, route. */
    public static void pullAndRoute(ServerLevel level, PhotonEmitterBlockEntity emitter, QuantumChannel channel) {
        if (!emitter.isGasEnabled()) return;
        int budget = channel.gasConfig().batchSize();
        for (Direction side : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(emitter.getBlockPos().relative(side));
            if (neighbor == null) continue;
            IGasHandler src = neighbor.getCapability(MekanismCaps.GAS, side.getOpposite()).orElse(null);
            if (src == null) continue;
            for (int tank = 0; tank < src.getTanks(); tank++) {
                GasStack peek = src.extractChemical(tank, budget, Action.SIMULATE);
                if (peek.isEmpty()) continue;
                Decision d = decide(level.getServer(), emitter, channel, peek);
                switch (d.kind) {
                    case REJECT -> { continue; }
                    case VOID -> {
                        src.extractChemical(tank, peek.getAmount(), Action.EXECUTE);
                        return;
                    }
                    case ROUTE -> {
                        GasStack simulated = pushToReceivers(level.getServer(), emitter, channel, d.subchannelId, peek, Action.SIMULATE);
                        long wouldMove = peek.getAmount() - simulated.getAmount();
                        if (wouldMove <= 0) continue;
                        GasStack taken = src.extractChemical(tank, wouldMove, Action.EXECUTE);
                        if (taken.isEmpty()) continue;
                        pushToReceivers(level.getServer(), emitter, channel, d.subchannelId, taken, Action.EXECUTE);
                        return;
                    }
                }
            }
        }
    }
}
