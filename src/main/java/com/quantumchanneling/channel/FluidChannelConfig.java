package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** Fluid analog of {@link ItemChannelConfig}. Batch size is in millibuckets per cycle. */
public class FluidChannelConfig {
    public static final int MAX_SUBCHANNELS = 32;
    public static final int DEFAULT_BATCH = 5000;
    public static final int MIN_BATCH = 1;
    public static final int MAX_BATCH = 64_000;

    private int batchSize = DEFAULT_BATCH;
    private int maskVersion = 0;

    public int batchSize() { return batchSize; }
    public void setBatchSize(int b) {
        int v = Math.max(MIN_BATCH, Math.min(MAX_BATCH, b));
        if (v != batchSize) { batchSize = v; maskVersion++; }
    }

    public int maskVersion() { return maskVersion; }

    /** See {@link ItemChannelConfig#bumpRoutingVersion()}. */
    public void bumpRoutingVersion() { maskVersion++; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("BatchSize", batchSize);
        tag.putInt("MaskVersion", maskVersion);
        return tag;
    }

    public static FluidChannelConfig load(CompoundTag tag) {
        FluidChannelConfig c = new FluidChannelConfig();
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

    public static FluidChannelConfig read(FriendlyByteBuf buf) {
        FluidChannelConfig c = new FluidChannelConfig();
        c.batchSize = Math.max(MIN_BATCH, Math.min(MAX_BATCH, buf.readVarInt()));
        c.maskVersion = buf.readVarInt();
        return c;
    }

    public void copyFrom(FluidChannelConfig other) {
        if (other == null) return;
        this.batchSize = other.batchSize;
        this.maskVersion = other.maskVersion;
    }
}
