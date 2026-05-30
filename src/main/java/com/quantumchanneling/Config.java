package com.quantumchanneling;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = QuantumChanneling.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue PHOTON_COST_PER_OPERATION = BUILDER
            .comment("Photon Energy cost for each item/fluid/gas transfer through a quantum channel. 1 PE = 8 FE.",
                    "Energy itself is not charged — energy IS the photon currency.")
            .defineInRange("photonCostPerOperation", 1, 0, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.BooleanValue ALLOW_CROSS_DIMENSION = BUILDER
            .comment("Whether a quantum channel may link emitters and receivers across dimensions.")
            .define("allowCrossDimension", true);

    private static final ForgeConfigSpec.IntValue EMITTER_PUSH_RATE = BUILDER
            .comment("Maximum FE a Photon Emitter pushes through its channel to receivers per tick.")
            .defineInRange("emitterPushRate", 10_000, 0, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue RECEIVER_OUTPUT_RATE = BUILDER
            .comment("Maximum FE a Photon Receiver outputs to adjacent machines per tick.")
            .defineInRange("receiverOutputRate", 10_000, 0, Integer.MAX_VALUE);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int photonCostPerOperation;
    public static boolean allowCrossDimension;
    public static int emitterPushRate;
    public static int receiverOutputRate;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        photonCostPerOperation = PHOTON_COST_PER_OPERATION.get();
        allowCrossDimension = ALLOW_CROSS_DIMENSION.get();
        emitterPushRate = EMITTER_PUSH_RATE.get();
        receiverOutputRate = RECEIVER_OUTPUT_RATE.get();
    }
}
