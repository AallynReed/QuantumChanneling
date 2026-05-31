package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.UUID;

/**
 * A dynamically-created subchannel on an emitter's items config. Each subchannel is identified by
 * a stable {@link UUID} (so renames / reorders don't break subscriptions) and owns a single
 * {@link ItemFilter}.
 *
 * <p>The filter defaults to <b>whitelist mode with no entries</b> — i.e. matches nothing — so a
 * brand-new subchannel does no routing until the user adds items. This keeps "create then forget"
 * from accidentally vacuuming everything off the trunk.
 *
 * <p>The optional {@link #color()} is purely cosmetic — a quick visual tag in the subchannel list
 * so users can scan a large rule book at a glance. 0 means "no color", anything else is packed
 * 0xRRGGBB. Throughput counters {@link #routedRecent()} / {@link #routedTotal()} live here too;
 * they're transient (reset on world reload, not saved).
 */
public class ItemSubchannel {
    public static final int NAME_MAX = 32;

    private final UUID id;
    private String name;
    private final ItemFilter filter;
    /** 0 = uncolored; otherwise an RGB triple packed as 0xRRGGBB. Persisted. */
    private int color = 0;

    /** Total items routed through this subchannel since world load. Transient. */
    private long routedTotal = 0L;
    /** Item count moved during the current rolling 60-tick window. */
    private int routedRecent = 0;
    /** Tick counter — when {@code >= 60} the recent counter rolls over to the next window. */
    private int recentTickCounter = 0;
    /** Last full window's count — what the UI displays as items/3s. */
    private int routedLastWindow = 0;

    public ItemSubchannel(UUID id, String name) {
        this(id, name, new ItemFilter(true), 0);
    }

    public ItemSubchannel(UUID id, String name, ItemFilter filter) {
        this(id, name, filter, 0);
    }

    public ItemSubchannel(UUID id, String name, ItemFilter filter, int color) {
        this.id = Objects.requireNonNull(id, "subchannel id");
        this.name = clampName(name);
        this.filter = filter == null ? new ItemFilter(true) : filter;
        this.color = color & 0xFFFFFF;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public void setName(String n) { this.name = clampName(n); }
    public ItemFilter filter() { return filter; }
    public int color() { return color; }
    public void setColor(int rgb) { this.color = rgb & 0xFFFFFF; }

    public long routedTotal() { return routedTotal; }
    public int routedLastWindow() { return routedLastWindow; }

    /** Called from the routing helpers when items actually move. */
    public void recordRouted(int count) {
        if (count <= 0) return;
        routedTotal += count;
        routedRecent += count;
    }

    /** Called once per emitter tick from the items-mode tick so the rolling window advances. */
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

    public static ItemSubchannel load(CompoundTag tag) {
        UUID id = tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID();
        ItemFilter f = tag.contains("Filter", Tag.TAG_COMPOUND)
                ? ItemFilter.load(tag.getCompound("Filter")) : new ItemFilter(true);
        int c = tag.contains("Color") ? tag.getInt("Color") : 0;
        return new ItemSubchannel(id, tag.getString("Name"), f, c);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, NAME_MAX);
        filter.write(buf);
        buf.writeInt(color);
        buf.writeVarLong(routedTotal);
        buf.writeVarInt(routedLastWindow);
    }

    public static ItemSubchannel read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String n = buf.readUtf(NAME_MAX);
        ItemFilter f = ItemFilter.read(buf);
        int c = buf.readInt();
        ItemSubchannel s = new ItemSubchannel(id, n, f, c);
        s.routedTotal = buf.readVarLong();
        s.routedLastWindow = buf.readVarInt();
        return s;
    }
}
