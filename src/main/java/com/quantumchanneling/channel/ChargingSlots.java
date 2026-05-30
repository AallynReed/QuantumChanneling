package com.quantumchanneling.channel;

/**
 * Bit flags for which player slots a channel charges. Stored as a single int on the channel.
 */
public final class ChargingSlots {
    public static final int HAND      = 1 << 0;
    public static final int HOTBAR    = 1 << 1;
    public static final int INVENTORY = 1 << 2;
    public static final int ARMOR     = 1 << 3;
    /** Optional — only meaningful when the Curios mod is loaded. */
    public static final int CURIOS    = 1 << 4;

    public static final int ALL_MASK = HAND | HOTBAR | INVENTORY | ARMOR | CURIOS;

    public static final int OFF = 0;

    /**
     * Translation keys for the individual slot labels used on the Charge Activity
     * tree. Hand and Armor split into per-piece labels; Hotbar / Inventory / Curios
     * are aggregated under a single label per group.
     */
    public static final String SLOT_MAIN_HAND  = "gui.quantumchanneling.charge_slot.main_hand";
    public static final String SLOT_OFF_HAND   = "gui.quantumchanneling.charge_slot.off_hand";
    public static final String SLOT_HOTBAR     = "gui.quantumchanneling.charge_slot.hotbar";
    public static final String SLOT_INVENTORY  = "gui.quantumchanneling.charge_slot.inventory";
    public static final String SLOT_ARMOR_HEAD = "gui.quantumchanneling.charge_slot.armor_head";
    public static final String SLOT_ARMOR_CHEST = "gui.quantumchanneling.charge_slot.armor_chest";
    public static final String SLOT_ARMOR_LEGS = "gui.quantumchanneling.charge_slot.armor_legs";
    public static final String SLOT_ARMOR_FEET = "gui.quantumchanneling.charge_slot.armor_feet";
    public static final String SLOT_CURIOS     = "gui.quantumchanneling.charge_slot.curios";

    private ChargingSlots() {}

    public static boolean has(int mask, int slotBit) {
        return (mask & slotBit) != 0;
    }

    public static int toggle(int mask, int slotBit) {
        return mask ^ slotBit;
    }

    public static int enable(int mask, int slotBit) {
        return mask | slotBit;
    }

    public static int disable(int mask, int slotBit) {
        return mask & ~slotBit;
    }

    public static boolean any(int mask) {
        return (mask & ALL_MASK) != 0;
    }
}
