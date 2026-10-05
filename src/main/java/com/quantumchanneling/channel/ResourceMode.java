package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;

/**
 * The kind of resource a channel device handles. ENERGY is the default — the original wireless-FE
 * pipeline. ITEMS is implemented (see {@link ItemChannelConfig}); FLUIDS and GASES are implemented
 * too, with GASES gated on Mekanism being present. Only HEAT remains a placeholder.
 */
public enum ResourceMode {
    ENERGY, ITEMS, FLUIDS, GASES, HEAT;

    public static ResourceMode safe(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : ENERGY;
    }

    public static ResourceMode read(FriendlyByteBuf buf) {
        return safe(buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(ordinal());
    }

    public static ResourceMode fromName(String name) {
        if (name == null || name.isEmpty()) return ENERGY;
        try { return ResourceMode.valueOf(name); } catch (Exception e) { return ENERGY; }
    }
}
