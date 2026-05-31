package com.quantumchanneling.client;

import net.minecraftforge.fml.ModList;

/**
 * Tiny runtime feature-detection helper. ModList lookups are cheap but happen on hot paths
 * (every emitter tick checks {@link #mekanismLoaded()}), so the results are cached on first
 * access. The set of loaded mods doesn't change after FMLCommonSetupEvent, so caching is safe.
 */
public final class Compat {
    private Compat() {}

    private static Boolean curios;
    private static Boolean mekanism;

    public static boolean curiosLoaded() {
        Boolean v = curios;
        if (v == null) { v = ModList.get().isLoaded("curios"); curios = v; }
        return v;
    }

    /** True when Mekanism is loaded — provider for both the gas (IGasHandler) and heat (IHeatHandler) APIs. */
    public static boolean mekanismLoaded() {
        Boolean v = mekanism;
        if (v == null) { v = ModList.get().isLoaded("mekanism"); mekanism = v; }
        return v;
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
