package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.Set;

/** Mekanism chemical filter. Plain ids and tag ids, so it loads fine without Mekanism present. */
public class GasFilter extends IdTagFilter {
    public GasFilter() { this(true); }
    public GasFilter(boolean whitelist) { super(whitelist); }

    public Set<Identifier> gases() { return ids(); }

    public static GasFilter load(CompoundTag tag) { return load(tag, new GasFilter()); }
    public static GasFilter read(FriendlyByteBuf buf) { return read(buf, new GasFilter()); }
}
