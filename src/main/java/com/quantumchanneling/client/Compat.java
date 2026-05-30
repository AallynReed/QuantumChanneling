package com.quantumchanneling.client;

import net.minecraftforge.fml.ModList;

/** Tiny runtime feature-detection helper. Kept on the client because the Charge tab is the only user. */
public final class Compat {
    private Compat() {}

    /** True when the Curios mod is loaded. Treated as "Baubles" by the UI. */
    public static boolean curiosLoaded() {
        return ModList.get().isLoaded("curios");
    }
}
