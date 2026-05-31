package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Channel-wide item-transfer knobs that stay shared across every emitter on the channel: the per-tick
 * batch size and the routing-mask version counter. Individual subchannels (name + filter + priority)
 * are owned by each emitter — see {@link com.quantumchanneling.blockentity.PhotonEmitterBlockEntity}.
 *
 * <p>{@link #maskVersion} is bumped on any change here so emitters know to invalidate their per-item
 * decision caches.
 */
public class ItemChannelConfig {
    /** Cap kept here so emitter UIs share a single source of truth for the per-emitter sub limit. */
    public static final int MAX_SUBCHANNELS = 32;
    /** Items have no soft cap — the default sits at the hardcoded ceiling. */
    public static final int DEFAULT_BATCH = 500;
    public static final int MIN_BATCH = 1;
    public static final int MAX_BATCH = 500;

    private int batchSize = DEFAULT_BATCH;
    private int maskVersion = 0;

    public int batchSize() { return batchSize; }
    public void setBatchSize(int b) {
        int v = Math.max(MIN_BATCH, Math.min(MAX_BATCH, b));
        if (v != batchSize) { batchSize = v; maskVersion++; }
    }

    public int maskVersion() { return maskVersion; }

    /**
     * External bump for the routing-decision version. Receivers call this when their items-enabled
     * flag or subchannel subscriptions change, so every emitter on the channel notices on its next
     * tick and wipes its decision cache instead of serving stale REJECT/ROUTE verdicts.
     */
    public void bumpRoutingVersion() { maskVersion++; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("BatchSize", batchSize);
        tag.putInt("MaskVersion", maskVersion);
        return tag;
    }

    public static ItemChannelConfig load(CompoundTag tag) {
        ItemChannelConfig c = new ItemChannelConfig();
        if (tag.contains("BatchSize")) {
            c.batchSize = Math.max(MIN_BATCH, Math.min(MAX_BATCH, tag.getInt("BatchSize")));
        }
        if (tag.contains("MaskVersion")) c.maskVersion = tag.getInt("MaskVersion");
        return c;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(batchSize);
        buf.writeVarInt(maskVersion);
    }

    public static ItemChannelConfig read(FriendlyByteBuf buf) {
        ItemChannelConfig c = new ItemChannelConfig();
        c.batchSize = Math.max(MIN_BATCH, Math.min(MAX_BATCH, buf.readVarInt()));
        c.maskVersion = buf.readVarInt();
        return c;
    }

    public void copyFrom(ItemChannelConfig other) {
        if (other == null) return;
        this.batchSize = other.batchSize;
        this.maskVersion = other.maskVersion;
    }
}
