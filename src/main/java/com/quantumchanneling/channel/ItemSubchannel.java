package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One of {@link ItemChannelConfig#SUBCHANNEL_COUNT} virtual sub-pipes within a channel.
 * A sub-channel is a named filter; receivers bound to its index will only see items that match it.
 * Useful for routing — e.g. a "Valuables" sub-channel whose filter matches diamond ore, with a
 * receiver bound to that sub-channel feeding a vault chest.
 */
public class ItemSubchannel {
    public static final int NAME_MAX = 32;

    private String name;
    private final ItemFilter filter;

    public ItemSubchannel(String name) { this(name, new ItemFilter(true)); }

    public ItemSubchannel(String name, ItemFilter filter) {
        this.name = clampName(name);
        this.filter = filter == null ? new ItemFilter(true) : filter;
    }

    public String name() { return name; }
    public void setName(String n) { this.name = clampName(n); }
    public ItemFilter filter() { return filter; }

    private static String clampName(String n) {
        if (n == null) return "";
        String trimmed = n.trim();
        return trimmed.length() <= NAME_MAX ? trimmed : trimmed.substring(0, NAME_MAX);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.put("Filter", filter.save());
        return tag;
    }

    public static ItemSubchannel load(CompoundTag tag) {
        ItemFilter f = tag.contains("Filter", Tag.TAG_COMPOUND)
                ? ItemFilter.load(tag.getCompound("Filter")) : new ItemFilter(true);
        return new ItemSubchannel(tag.getString("Name"), f);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(name, NAME_MAX);
        filter.write(buf);
    }

    public static ItemSubchannel read(FriendlyByteBuf buf) {
        String n = buf.readUtf(NAME_MAX);
        ItemFilter f = ItemFilter.read(buf);
        return new ItemSubchannel(n, f);
    }
}
