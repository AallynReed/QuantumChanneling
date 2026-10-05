package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.Set;

/** Item filter used by item subchannels and per-emitter void filters. */
public class ItemFilter extends IdTagFilter {
    public ItemFilter() { this(true); }
    public ItemFilter(boolean whitelist) { super(whitelist); }

    public Set<Identifier> items() { return ids(); }

    public static ItemFilter load(CompoundTag tag) { return load(tag, new ItemFilter()); }
    public static ItemFilter read(FriendlyByteBuf buf) { return read(buf, new ItemFilter()); }
}
