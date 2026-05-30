package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.UUID;

/** Gas analog of {@link FluidSubchannel}. Stable id + name + {@link GasFilter}. */
public class GasSubchannel {
    public static final int NAME_MAX = 32;

    private final UUID id;
    private String name;
    private final GasFilter filter;

    public GasSubchannel(UUID id, String name) { this(id, name, new GasFilter(true)); }

    public GasSubchannel(UUID id, String name, GasFilter filter) {
        this.id = Objects.requireNonNull(id, "subchannel id");
        this.name = clampName(name);
        this.filter = filter == null ? new GasFilter(true) : filter;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public void setName(String n) { this.name = clampName(n); }
    public GasFilter filter() { return filter; }

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
        return tag;
    }

    public static GasSubchannel load(CompoundTag tag) {
        UUID id = tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID();
        GasFilter f = tag.contains("Filter", Tag.TAG_COMPOUND)
                ? GasFilter.load(tag.getCompound("Filter")) : new GasFilter(true);
        return new GasSubchannel(id, tag.getString("Name"), f);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, NAME_MAX);
        filter.write(buf);
    }
    public static GasSubchannel read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String n = buf.readUtf(NAME_MAX);
        GasFilter f = GasFilter.read(buf);
        return new GasSubchannel(id, n, f);
    }
}
