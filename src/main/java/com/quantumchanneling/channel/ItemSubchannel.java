package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public class ItemSubchannel extends SubchannelBase<ItemFilter> {
    public ItemSubchannel(UUID id, String name) { this(id, name, new ItemFilter(true), 0); }

    public ItemSubchannel(UUID id, String name, ItemFilter filter, int color) {
        super(id, name, filter, color);
    }

    public static ItemSubchannel load(CompoundTag tag) {
        ItemFilter filter = tag.getCompound("Filter").map(ItemFilter::load).orElseGet(() -> new ItemFilter(true));
        return new ItemSubchannel(loadId(tag), tag.getStringOr("Name", ""), filter, tag.getIntOr("Color", 0));
    }

    public static ItemSubchannel read(FriendlyByteBuf buf) {
        ItemSubchannel s = new ItemSubchannel(buf.readUUID(), buf.readUtf(NAME_MAX), ItemFilter.read(buf), buf.readInt());
        s.readCounters(buf);
        return s;
    }
}
