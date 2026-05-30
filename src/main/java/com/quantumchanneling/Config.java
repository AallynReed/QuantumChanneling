package com.quantumchanneling;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = QuantumChanneling.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ALLOW_CROSS_DIMENSION = BUILDER
            .comment("Whether a quantum channel may link emitters and receivers across dimensions.")
            .define("allowCrossDimension", true);

    private static final ForgeConfigSpec.IntValue EMITTER_PUSH_RATE = BUILDER
            .comment("Maximum FE a Photon Emitter pushes through its channel to receivers per tick.",
                     "0 = unlimited (default). FE is the universal energy currency in Forge 1.20.1;",
                     "mods advertising RF support use the same IEnergyStorage capability and interop.")
            .defineInRange("emitterPushRate", 0, 0, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue RECEIVER_OUTPUT_RATE = BUILDER
            .comment("Maximum FE a Photon Receiver outputs to adjacent machines per tick.",
                     "0 = unlimited (default).")
            .defineInRange("receiverOutputRate", 0, 0, Integer.MAX_VALUE);

    private static ForgeConfigSpec.LongValue tierCapacity(String suffix, String label, long defaultValue) {
        return BUILDER
                .comment("Photon Storage " + label + " FE capacity. Default = " + defaultValue + " FE.",
                         "Range: 1 .. Long.MAX_VALUE (~9.2 quintillion). Values above 2^63-1 are not representable.")
                .defineInRange("storage.tier" + suffix + "Capacity", defaultValue, 1L, Long.MAX_VALUE);
    }
    private static final ForgeConfigSpec.LongValue STORAGE_CAP_T1 = tierCapacity("1", "I (Copper)",    1L << 16);
    private static final ForgeConfigSpec.LongValue STORAGE_CAP_T2 = tierCapacity("2", "II (Iron)",     1L << 20);
    private static final ForgeConfigSpec.LongValue STORAGE_CAP_T3 = tierCapacity("3", "III (Gold)",    1L << 24);
    private static final ForgeConfigSpec.LongValue STORAGE_CAP_T4 = tierCapacity("4", "IV (Diamond)",  1L << 28);
    private static final ForgeConfigSpec.LongValue STORAGE_CAP_T5 = tierCapacity("5", "V (Emerald)",   1L << 32);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean allowCrossDimension;
    public static int emitterPushRate;
    public static int receiverOutputRate;
    /** Index = tier - 1. Populated on config load. */
    public static long[] storageCapacities = new long[]{ 1L << 16, 1L << 20, 1L << 24, 1L << 28, 1L << 32 };

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        allowCrossDimension = ALLOW_CROSS_DIMENSION.get();
        emitterPushRate = EMITTER_PUSH_RATE.get();
        receiverOutputRate = RECEIVER_OUTPUT_RATE.get();
        storageCapacities = new long[]{
                STORAGE_CAP_T1.get(), STORAGE_CAP_T2.get(), STORAGE_CAP_T3.get(),
                STORAGE_CAP_T4.get(), STORAGE_CAP_T5.get()
        };
    }
}
