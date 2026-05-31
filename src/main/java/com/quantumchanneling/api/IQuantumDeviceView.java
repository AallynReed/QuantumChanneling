package com.quantumchanneling.api;

import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Read-only view of one bound Quantum Channeling device. Returned by the
 * {@link QuantumChannelingAPI#deviceAt(net.minecraft.server.level.ServerLevel, net.minecraft.core.BlockPos)}
 * lookup and from {@link IQuantumChannelView#members()}.
 */
public interface IQuantumDeviceView {
    enum Kind { EMITTER, RECEIVER, STORAGE, MANAGER, UNKNOWN }

    GlobalPos pos();
    Kind kind();
    @Nullable UUID channelId();
    String customName();
    int priority();
    int throughputCap();
    boolean isOverdrive();
    int lastTickThroughputFE();

    /** Per-resource enable flags as seen by the routing pipeline. */
    boolean isItemsEnabled();
    boolean isFluidsEnabled();
    boolean isGasEnabled();

    /**
     * Subchannels HOSTED by this device. Empty for non-emitters. The list is ordered as the
     * emitter walks it during routing (priority "rule book" order).
     */
    List<IQuantumSubchannelView> itemSubchannels();
    List<IQuantumSubchannelView> fluidSubchannels();
    List<IQuantumSubchannelView> gasSubchannels();
}
