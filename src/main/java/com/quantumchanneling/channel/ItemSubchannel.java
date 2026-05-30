package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.UUID;

/**
 * A dynamically-created subchannel on a channel's items config. Each subchannel is identified by
 * a stable {@link UUID} (so renames / reorders don't break subscriptions) and owns a single
 * {@link ItemFilter}.
 *
 * <p>The filter defaults to <b>whitelist mode with no entries</b> — i.e. matches nothing — so a
 * brand-new subchannel does no routing until the user adds items. This keeps "create then forget"
 * from accidentally vacuuming everything off the trunk.
 */
public class ItemSubchannel {
    public static final int NAME_MAX = 32;

    private final UUID id;
    private String name;
    private final ItemFilter filter;

    public ItemSubchannel(UUID id, String name) {
        this(id, name, new ItemFilter(true));   // whitelist default
    }

    public ItemSubchannel(UUID id, String name, ItemFilter filter) {
        this.id = Objects.requireNonNull(id, "subchannel id");
        this.name = clampName(name);
        this.filter = filter == null ? new ItemFilter(true) : filter;
    }

    public UUID id() { return id; }
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
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.put("Filter", filter.save());
        return tag;
    }

    public static ItemSubchannel load(CompoundTag tag) {
        UUID id = tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID();
        ItemFilter f = tag.contains("Filter", Tag.TAG_COMPOUND)
                ? ItemFilter.load(tag.getCompound("Filter")) : new ItemFilter(true);
        return new ItemSubchannel(id, tag.getString("Name"), f);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, NAME_MAX);
        filter.write(buf);
    }

    public static ItemSubchannel read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String n = buf.readUtf(NAME_MAX);
        ItemFilter f = ItemFilter.read(buf);
        return new ItemSubchannel(id, n, f);
    }
}
