package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.UUID;

/** Gas analog of {@link FluidSubchannel}. Stable id + name + {@link GasFilter} + color + counters. */
public class GasSubchannel {
    public static final int NAME_MAX = 32;

    private final UUID id;
    private String name;
    private final GasFilter filter;
    private int color = 0;

    private long routedTotal = 0L;
    private int routedRecent = 0;
    private int recentTickCounter = 0;
    private int routedLastWindow = 0;

    public GasSubchannel(UUID id, String name) { this(id, name, new GasFilter(true), 0); }
    public GasSubchannel(UUID id, String name, GasFilter filter) { this(id, name, filter, 0); }

    public GasSubchannel(UUID id, String name, GasFilter filter, int color) {
        this.id = Objects.requireNonNull(id, "subchannel id");
        this.name = clampName(name);
        this.filter = filter == null ? new GasFilter(true) : filter;
        this.color = color & 0xFFFFFF;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public void setName(String n) { this.name = clampName(n); }
    public GasFilter filter() { return filter; }
    public int color() { return color; }
    public void setColor(int rgb) { this.color = rgb & 0xFFFFFF; }

    public long routedTotal() { return routedTotal; }
    public int routedLastWindow() { return routedLastWindow; }

    public void recordRouted(int mb) {
        if (mb <= 0) return;
        routedTotal += mb;
        routedRecent += mb;
    }

    public void tickRoutingWindow() {
        if (++recentTickCounter >= 60) {
            routedLastWindow = routedRecent;
            routedRecent = 0;
            recentTickCounter = 0;
        }
    }

    private static String clampName(String n) {
        if (n == null) return "";
        String trimmed = n.trim();
        return trimmed.length() <= NAME_MAX ? trimmed : trimmed.substring(0, NAME_MAX);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.put("Filter", filter.save());
        if (color != 0) tag.putInt("Color", color);
        return tag;
    }

    public static GasSubchannel load(CompoundTag tag) {
        UUID id = tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID();
        GasFilter f = tag.contains("Filter", Tag.TAG_COMPOUND)
                ? GasFilter.load(tag.getCompound("Filter")) : new GasFilter(true);
        int c = tag.contains("Color") ? tag.getInt("Color") : 0;
        return new GasSubchannel(id, tag.getString("Name"), f, c);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, NAME_MAX);
        filter.write(buf);
        buf.writeInt(color);
        buf.writeVarLong(routedTotal);
        buf.writeVarInt(routedLastWindow);
    }
    public static GasSubchannel read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String n = buf.readUtf(NAME_MAX);
        GasFilter f = GasFilter.read(buf);
        int c = buf.readInt();
        GasSubchannel s = new GasSubchannel(id, n, f, c);
        s.routedTotal = buf.readVarLong();
        s.routedLastWindow = buf.readVarInt();
        return s;
    }
}
