package com.quantumchanneling.channel;

public enum ChargingMode {
    OFF,        // no charging
    HAND,       // mainhand + offhand
    HOTBAR,     // 9 hotbar slots
    INVENTORY,  // 36 main-inventory slots
    ARMOR,      // 4 armor slots
    ALL;        // hand + hotbar + inventory + armor

    public static ChargingMode byOrdinal(int i) {
        ChargingMode[] vs = values();
        return (i >= 0 && i < vs.length) ? vs[i] : OFF;
    }
}
