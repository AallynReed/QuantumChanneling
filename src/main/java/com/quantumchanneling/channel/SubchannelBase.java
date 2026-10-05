package com.quantumchanneling.channel;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.UUID;

/**
 * A dynamically-created subchannel on an emitter. Identified by a stable {@link UUID} (so renames
 * and reorders don't break subscriptions) and owning a single filter.
 *
 * <p>The filter defaults to <b>whitelist mode with no entries</b> — it matches nothing — so a
 * brand-new subchannel does no routing until the user adds entries. "Create then forget" can't
 * accidentally vacuum everything off the trunk.
 *
 * <p>{@link #color()} is purely cosmetic: 0 = none, otherwise packed 0xRRGGBB. The throughput
 * counters are transient (reset on world reload, never saved).
 */
public abstract class SubchannelBase<F extends IdTagFilter> implements Subchannel {
    public static final int NAME_MAX = 32;
    private static final int WINDOW_TICKS = 60;

    private final UUID id;
    private String name;
    private final F filter;
    private int color;

    private long routedTotal;
    private int routedRecent;
    private int recentTickCounter;
    private int routedLastWindow;

    protected SubchannelBase(UUID id, String name, F filter, int color) {
        this.id = Objects.requireNonNull(id, "subchannel id");
        this.name = clampName(name);
        this.filter = Objects.requireNonNull(filter, "filter");
        this.color = color & 0xFFFFFF;
    }

    @Override public UUID id() { return id; }
    @Override public String name() { return name; }
    public void setName(String n) { this.name = clampName(n); }
    @Override public F filter() { return filter; }
    @Override public int color() { return color; }
    public void setColor(int rgb) { this.color = rgb & 0xFFFFFF; }

    @Override public long routedTotal() { return routedTotal; }
    @Override public int routedLastWindow() { return routedLastWindow; }

    @Override
    public void recordRouted(int amount) {
        if (amount <= 0) return;
        routedTotal += amount;
        routedRecent += amount;
    }

    /** Called once per emitter tick so the rolling window advances even when nothing moves. */
    public void tickRoutingWindow() {
        if (++recentTickCounter >= WINDOW_TICKS) {
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
        tag.store("Id", UUIDUtil.CODEC, id);
        tag.putString("Name", name);
        tag.put("Filter", filter.save());
        if (color != 0) tag.putInt("Color", color);
        return tag;
    }

    protected static UUID loadId(CompoundTag tag) {
        return tag.read("Id", UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, NAME_MAX);
        filter.write(buf);
        buf.writeInt(color);
        buf.writeVarLong(routedTotal);
        buf.writeVarInt(routedLastWindow);
    }

    /** Restores the display-only counters a {@link #write} carried to the client. */
    protected void readCounters(FriendlyByteBuf buf) {
        routedTotal = buf.readVarLong();
        routedLastWindow = buf.readVarInt();
    }
}
