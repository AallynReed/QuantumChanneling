package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.Set;

/** Fluid filter used by fluid subchannels and per-emitter void filters. */
public class FluidFilter extends IdTagFilter {
    public FluidFilter() { this(true); }
    public FluidFilter(boolean whitelist) { super(whitelist); }

    public Set<Identifier> fluids() { return ids(); }

    public static FluidFilter load(CompoundTag tag) { return load(tag, new FluidFilter()); }
    public static FluidFilter read(FriendlyByteBuf buf) { return read(buf, new FluidFilter()); }
}
