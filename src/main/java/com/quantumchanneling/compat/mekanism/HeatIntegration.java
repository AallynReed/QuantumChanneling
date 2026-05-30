package com.quantumchanneling.compat.mekanism;

import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.channel.QuantumChannel;
import mekanism.api.heat.IHeatHandler;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Mekanism heat plumbing — a thermal wire across the channel. Per tick, the emitter samples the
 * temperature of every adjacent heat handler, averages it with every subscribed receiver's
 * adjacent heat handlers (weighted by heat capacity), and applies a fraction of the temperature
 * delta on each side. The math is a coarse approximation of Mekanism's own thermal-equilibrium
 * algorithm — enough to make the channel feel like a thermal conductor.
 */
public final class HeatIntegration {
    private HeatIntegration() {}

    /** Fraction of the temperature delta resolved per tick. Tuned by feel — keep it small. */
    private static final double TRANSFER_FRACTION = 0.05;

    /** Emitter exposes a heat handler with one capacitor that mirrors its hottest neighbor's temp. */
    public static IHeatHandler emitterHandler(PhotonEmitterBlockEntity emitter) {
        return new SampleHandler(emitter);
    }

    public static IHeatHandler receiverHandler(PhotonReceiverBlockEntity receiver) {
        return new SampleHandler(receiver);
    }

    /**
     * One-capacitor sampling handler. Reads/writes the temperature of the device's hottest
     * adjacent neighbor. Lets external thermal-compatible blocks see "what's on this side" of the
     * channel as a normal heat connection.
     */
    private static final class SampleHandler implements IHeatHandler {
        private final com.quantumchanneling.blockentity.ChannelBoundBlockEntity host;
        SampleHandler(com.quantumchanneling.blockentity.ChannelBoundBlockEntity host) { this.host = host; }

        @Override public int getHeatCapacitorCount() { return 1; }
        @Override public double getInverseConduction(int idx) { return 1.0; }
        @Override public double getHeatCapacity(int idx) {
            double sum = 0;
            for (IHeatHandler h : neighborHandlers()) {
                for (int i = 0; i < h.getHeatCapacitorCount(); i++) sum += h.getHeatCapacity(i);
            }
            return sum > 0 ? sum : 1000.0;
        }
        @Override public double getTemperature(int idx) {
            double weighted = 0, totalCap = 0;
            for (IHeatHandler h : neighborHandlers()) {
                for (int i = 0; i < h.getHeatCapacitorCount(); i++) {
                    double c = h.getHeatCapacity(i);
                    weighted += h.getTemperature(i) * c;
                    totalCap += c;
                }
            }
            return totalCap > 0 ? weighted / totalCap : 300.0; // ~room temp fallback
        }
        @Override public void handleHeat(int idx, double transfer) {
            // Distribute the incoming heat across the device's adjacent handlers proportionally
            // to their heat capacity. This is what makes heat "exit" the channel into machines.
            java.util.List<IHeatHandler> neighbors = neighborHandlers();
            if (neighbors.isEmpty()) return;
            double totalCap = 0;
            for (IHeatHandler h : neighbors) {
                for (int i = 0; i < h.getHeatCapacitorCount(); i++) totalCap += h.getHeatCapacity(i);
            }
            if (totalCap <= 0) return;
            for (IHeatHandler h : neighbors) {
                for (int i = 0; i < h.getHeatCapacitorCount(); i++) {
                    double share = transfer * (h.getHeatCapacity(i) / totalCap);
                    h.handleHeat(i, share);
                }
            }
        }

        private java.util.List<IHeatHandler> neighborHandlers() {
            java.util.List<IHeatHandler> out = new java.util.ArrayList<>();
            var level = host.getLevel();
            if (level == null) return out;
            for (Direction side : Direction.values()) {
                BlockEntity neighbor = level.getBlockEntity(host.getBlockPos().relative(side));
                if (neighbor == null) continue;
                IHeatHandler h = neighbor.getCapability(MekanismCaps.HEAT, side.getOpposite()).orElse(null);
                if (h != null) out.add(h);
            }
            return out;
        }
    }

    /**
     * Per-tick equilibration. Called from the emitter's tick when its channel has heat enabled.
     * Averages the emitter's neighbor temperatures with every loaded receiver's, then pushes a
     * fraction of the delta into each side via the existing handleHeat calls on neighbor blocks.
     */
    public static void tick(ServerLevel level, PhotonEmitterBlockEntity emitter, QuantumChannel channel) {
        if (!channel.heatConfig().isEnabled()) return;
        java.util.List<com.quantumchanneling.blockentity.ChannelBoundBlockEntity> participants = new java.util.ArrayList<>();
        participants.add(emitter);
        MinecraftServer server = level.getServer();
        for (GlobalPos gp : channel.members()) {
            ServerLevel lvl = server.getLevel(gp.dimension());
            if (lvl == null || !lvl.isLoaded(gp.pos())) continue;
            BlockEntity be = lvl.getBlockEntity(gp.pos());
            if (be instanceof PhotonReceiverBlockEntity rcv) participants.add(rcv);
        }
        if (participants.size() < 2) return;

        // Weighted mean temperature across every participant's neighbor capacitors.
        double weighted = 0, totalCap = 0;
        for (var p : participants) {
            for (IHeatHandler h : neighborsOf(p)) {
                for (int i = 0; i < h.getHeatCapacitorCount(); i++) {
                    double c = h.getHeatCapacity(i);
                    weighted += h.getTemperature(i) * c;
                    totalCap += c;
                }
            }
        }
        if (totalCap <= 0) return;
        double targetTemp = weighted / totalCap;

        // Push each capacitor a fraction of the way toward the target.
        for (var p : participants) {
            for (IHeatHandler h : neighborsOf(p)) {
                for (int i = 0; i < h.getHeatCapacitorCount(); i++) {
                    double delta = (targetTemp - h.getTemperature(i)) * h.getHeatCapacity(i) * TRANSFER_FRACTION;
                    if (Math.abs(delta) > 1e-6) h.handleHeat(i, delta);
                }
            }
        }
    }

    private static java.util.List<IHeatHandler> neighborsOf(com.quantumchanneling.blockentity.ChannelBoundBlockEntity host) {
        java.util.List<IHeatHandler> out = new java.util.ArrayList<>();
        var level = host.getLevel();
        if (level == null) return out;
        for (Direction side : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(host.getBlockPos().relative(side));
            if (neighbor == null) continue;
            IHeatHandler h = neighbor.getCapability(MekanismCaps.HEAT, side.getOpposite()).orElse(null);
            if (h != null) out.add(h);
        }
        return out;
    }
}
