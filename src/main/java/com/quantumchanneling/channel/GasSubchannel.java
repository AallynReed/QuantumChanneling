package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public class GasSubchannel extends SubchannelBase<GasFilter> {
    public GasSubchannel(UUID id, String name) { this(id, name, new GasFilter(true), 0); }

    public GasSubchannel(UUID id, String name, GasFilter filter, int color) {
        super(id, name, filter, color);
    }

    public static GasSubchannel load(CompoundTag tag) {
        GasFilter filter = tag.getCompound("Filter").map(GasFilter::load).orElseGet(() -> new GasFilter(true));
        return new GasSubchannel(loadId(tag), tag.getStringOr("Name", ""), filter, tag.getIntOr("Color", 0));
    }

    public static GasSubchannel read(FriendlyByteBuf buf) {
        GasSubchannel s = new GasSubchannel(buf.readUUID(), buf.readUtf(NAME_MAX), GasFilter.read(buf), buf.readInt());
        s.readCounters(buf);
        return s;
    }
}
