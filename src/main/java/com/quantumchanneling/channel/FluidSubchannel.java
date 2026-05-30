package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.UUID;

/**
 * Fluid analog of {@link ItemSubchannel}. Stable {@link UUID} identity + a single
 * {@link FluidFilter}. Defaults to whitelist mode with no entries — a brand-new subchannel does
 * no routing until the user adds fluids, preventing "create then forget" from accidentally
 * draining everything off the trunk.
 */
public class FluidSubchannel {
    public static final int NAME_MAX = 32;

    private final UUID id;
    private String name;
    private final FluidFilter filter;

    public FluidSubchannel(UUID id, String name) {
        this(id, name, new FluidFilter(true));
    }

    public FluidSubchannel(UUID id, String name, FluidFilter filter) {
        this.id = Objects.requireNonNull(id, "subchannel id");
        this.name = clampName(name);
        this.filter = filter == null ? new FluidFilter(true) : filter;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public void setName(String n) { this.name = clampName(n); }
    public FluidFilter filter() { return filter; }

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

    public static FluidSubchannel load(CompoundTag tag) {
        UUID id = tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID();
        FluidFilter f = tag.contains("Filter", Tag.TAG_COMPOUND)
                ? FluidFilter.load(tag.getCompound("Filter")) : new FluidFilter(true);
        return new FluidSubchannel(id, tag.getString("Name"), f);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, NAME_MAX);
        filter.write(buf);
    }

    public static FluidSubchannel read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String n = buf.readUtf(NAME_MAX);
        FluidFilter f = FluidFilter.read(buf);
        return new FluidSubchannel(id, n, f);
    }
}
