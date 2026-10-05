package com.quantumchanneling.client;

import net.neoforged.fml.ModList;

/**
 * Tiny runtime feature-detection helper. ModList lookups are cheap but happen on hot paths
 * (every emitter tick checks {@link #mekanismLoaded()}), so the results are cached on first
 * access. The set of loaded mods never changes after loading, so caching is safe.
 */
public final class Compat {
    private Compat() {}

    private static Boolean curios;
    private static Boolean mekanism;
    private static Boolean computercraft;
    private static Boolean ftbchunks;

    public static boolean curiosLoaded() {
        Boolean v = curios;
        if (v == null) { v = ModList.get().isLoaded("curios"); curios = v; }
        return v;
    }

    /** True when Mekanism is loaded — the provider of the chemicals routed as "gas". */
    public static boolean mekanismLoaded() {
        Boolean v = mekanism;
        if (v == null) { v = ModList.get().isLoaded("mekanism"); mekanism = v; }
        return v;
    }

    /** True when any mod that provides a gas capability is loaded. Only Mekanism today. */
    public static boolean gasProviderLoaded() {
        return mekanismLoaded();
    }

    /** True when any mod that provides a heat capability is loaded. Same pattern as gases. */
    public static boolean heatProviderLoaded() {
        return mekanismLoaded();
    }

    /** True when CC:Tweaked is loaded — the QC peripheral attaches in {@code commonSetup}. */
    public static boolean computercraftLoaded() {
        Boolean v = computercraft;
        if (v == null) { v = ModList.get().isLoaded("computercraft"); computercraft = v; }
        return v;
    }

    /** True when FTB Chunks is loaded — the claim-system integration consults it for ownership. */
    public static boolean ftbChunksLoaded() {
        Boolean v = ftbchunks;
        if (v == null) { v = ModList.get().isLoaded("ftbchunks"); ftbchunks = v; }
        return v;
    }
}
