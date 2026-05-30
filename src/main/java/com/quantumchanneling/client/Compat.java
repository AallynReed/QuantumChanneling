package com.quantumchanneling.client;

import net.minecraftforge.fml.ModList;

/** Tiny runtime feature-detection helper. Kept on the client because UI tabs are the only consumer. */
public final class Compat {
    private Compat() {}

    /** True when the Curios mod is loaded. Treated as "Baubles" by the UI. */
    public static boolean curiosLoaded() {
        return ModList.get().isLoaded("curios");
    }

    /** True when Mekanism is loaded — provider for both the gas (IGasHandler) and heat (IHeatHandler) APIs. */
    public static boolean mekanismLoaded() {
        return ModList.get().isLoaded("mekanism");
    }

    /**
     * True when any mod that provides a gas capability is loaded. Currently only Mekanism does in
     * the 1.20.1 modding scene; kept as a separate predicate so adding alt providers later (e.g.
     * Pneumaticcraft) is a one-line OR.
     */
    public static boolean gasProviderLoaded() {
        return mekanismLoaded();
    }

    /** True when any mod that provides a heat capability is loaded. Same pattern as gases. */
    public static boolean heatProviderLoaded() {
        return mekanismLoaded();
    }
}
