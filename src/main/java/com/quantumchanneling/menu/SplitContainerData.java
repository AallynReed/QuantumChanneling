package com.quantumchanneling.menu;

import net.minecraft.world.inventory.ContainerData;

/**
 * Presents a logical {@link ContainerData} as twice as many slots, each logical int split into a
 * low and high 16-bit half. Vanilla's data-slot sync ({@code ClientboundContainerSetDataPacket})
 * writes every value with {@code writeShort}, so anything outside ±32767 is truncated in transit —
 * which corrupts FE throughput, caps, stored energy, and the graph buckets, all of which routinely
 * run into the millions. Splitting keeps each half inside 16 bits so the client recombines the full
 * 32-bit value. Logical index {@code i} maps to physical slots {@code 2i} (low) and {@code 2i+1} (high).
 */
final class SplitContainerData implements ContainerData {
    private final ContainerData delegate;

    SplitContainerData(ContainerData delegate) {
        this.delegate = delegate;
    }

    @Override
    public int get(int index) {
        int v = delegate.get(index >> 1);
        return ((index & 1) == 0) ? (v & 0xFFFF) : ((v >>> 16) & 0xFFFF);
    }

    @Override
    public void set(int index, int value) {
        int logical = index >> 1;
        int cur = delegate.get(logical);
        if ((index & 1) == 0) delegate.set(logical, (cur & ~0xFFFF) | (value & 0xFFFF));
        else delegate.set(logical, (cur & 0xFFFF) | ((value & 0xFFFF) << 16));
    }

    @Override
    public int getCount() {
        return delegate.getCount() * 2;
    }
}
