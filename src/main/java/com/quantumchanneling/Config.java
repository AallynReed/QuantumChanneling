package com.quantumchanneling;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = QuantumChanneling.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue PHOTON_COST_PER_OPERATION = BUILDER
            .comment("Photon Energy cost for each item/fluid/gas transfer through a quantum channel. 1 PE = 8 FE.")
            .defineInRange("photonCostPerOperation", 1, 0, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.BooleanValue ALLOW_CROSS_DIMENSION = BUILDER
            .comment("Whether a quantum channel may link emitters and receivers across dimensions.")
            .define("allowCrossDimension", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int photonCostPerOperation;
    public static boolean allowCrossDimension;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        photonCostPerOperation = PHOTON_COST_PER_OPERATION.get();
        allowCrossDimension = ALLOW_CROSS_DIMENSION.get();
    }
}
