package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.UUID;

/**
 * Fluid analog of {@link ItemSubchannel}. Stable {@link UUID} identity + a single
 * {@link FluidFilter}, plus optional color tag and runtime throughput counters.
 */
public class FluidSubchannel {
    public static final int NAME_MAX = 32;

    private final UUID id;
    private String name;
    private final FluidFilter filter;
    private int color = 0;

    private long routedTotal = 0L;
    private int routedRecent = 0;
    private int recentTickCounter = 0;
    private int routedLastWindow = 0;

    public FluidSubchannel(UUID id, String name) {
        this(id, name, new FluidFilter(true), 0);
    }

    public FluidSubchannel(UUID id, String name, FluidFilter filter) {
        this(id, name, filter, 0);
    }

    public FluidSubchannel(UUID id, String name, FluidFilter filter, int color) {
        this.id = Objects.requireNonNull(id, "subchannel id");
        this.name = clampName(name);
        this.filter = filter == null ? new FluidFilter(true) : filter;
        this.color = color & 0xFFFFFF;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public void setName(String n) { this.name = clampName(n); }
    public FluidFilter filter() { return filter; }
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

    public static FluidSubchannel load(CompoundTag tag) {
        UUID id = tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID();
        FluidFilter f = tag.contains("Filter", Tag.TAG_COMPOUND)
                ? FluidFilter.load(tag.getCompound("Filter")) : new FluidFilter(true);
        int c = tag.contains("Color") ? tag.getInt("Color") : 0;
        return new FluidSubchannel(id, tag.getString("Name"), f, c);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, NAME_MAX);
        filter.write(buf);
        buf.writeInt(color);
        buf.writeVarLong(routedTotal);
        buf.writeVarInt(routedLastWindow);
    }

    public static FluidSubchannel read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String n = buf.readUtf(NAME_MAX);
        FluidFilter f = FluidFilter.read(buf);
        int c = buf.readInt();
        FluidSubchannel s = new FluidSubchannel(id, n, f, c);
        s.routedTotal = buf.readVarLong();
        s.routedLastWindow = buf.readVarInt();
        return s;
    }
}
