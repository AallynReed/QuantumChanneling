package com.quantumchanneling.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.UUID;

/** Read-only view of one subchannel — name, color, filter, and rolling throughput counters. */
public interface IQuantumSubchannelView {
    enum Kind { ITEM, FLUID, GAS }

    UUID id();
    String name();
    Kind kind();
    /** 0 when no color tag is set, otherwise a packed 0xRRGGBB triple. */
    int color();
    boolean isWhitelist();
    Set<ResourceLocation> entryIds();
    Set<ResourceLocation> entryTags();

    /** Lifetime counter (since world load). Items: stack count. Fluids/Gas: mB. */
    long routedTotal();
    /** Routed during the last completed rolling window (≈ 3 seconds). */
    int routedLastWindow();
}
