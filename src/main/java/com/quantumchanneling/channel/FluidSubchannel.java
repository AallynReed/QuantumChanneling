package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public class FluidSubchannel extends SubchannelBase<FluidFilter> {
    public FluidSubchannel(UUID id, String name) { this(id, name, new FluidFilter(true), 0); }

    public FluidSubchannel(UUID id, String name, FluidFilter filter, int color) {
        super(id, name, filter, color);
    }

    public static FluidSubchannel load(CompoundTag tag) {
        FluidFilter filter = tag.getCompound("Filter").map(FluidFilter::load).orElseGet(() -> new FluidFilter(true));
        return new FluidSubchannel(loadId(tag), tag.getStringOr("Name", ""), filter, tag.getIntOr("Color", 0));
    }

    public static FluidSubchannel read(FriendlyByteBuf buf) {
        FluidSubchannel s = new FluidSubchannel(buf.readUUID(), buf.readUtf(NAME_MAX), FluidFilter.read(buf), buf.readInt());
        s.readCounters(buf);
        return s;
    }
}
