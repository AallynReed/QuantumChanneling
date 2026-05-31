package com.quantumchanneling;

import com.quantumchanneling.channel.FluidChannelConfig;
import com.quantumchanneling.channel.GasChannelConfig;
import com.quantumchanneling.channel.ItemChannelConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Mod-wide settings. Lives at {@code config/quantumchanneling-common.toml} (single-player or
 * dedicated server), locally editable with any text editor. On multiplayer the dedicated server's
 * copy of this file is authoritative — clients receive a snapshot via
 * {@link com.quantumchanneling.channel.SyncServerConfigPacket} on join, and reverts to their own
 * values when they disconnect.
 *
 * <p>The "max batch" settings act as a hard ceiling that emitters honor at read time — channels
 * can still be configured below the cap, but any user-set value above it is silently clamped on
 * use. Lower the cap mid-game and every running channel respects it on the next tick.
 */
@Mod.EventBusSubscriber(modid = QuantumChanneling.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ServerConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    /* ---- world / channel scope ---- */

    private static final ForgeConfigSpec.BooleanValue ALLOW_CROSS_DIMENSION = B
            .comment("Whether a quantum channel may link emitters and receivers across dimensions.")
            .define("world.allowCrossDimension", true);

    /* ---- energy throughput ceilings ---- */

    private static final ForgeConfigSpec.IntValue EMITTER_PUSH_RATE = B
            .comment("Maximum FE a Photon Emitter pushes through its channel to receivers per tick.",
                     "0 = unlimited.")
            .defineInRange("energy.emitterPushRate", 0, 0, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue RECEIVER_OUTPUT_RATE = B
            .comment("Maximum FE a Photon Receiver outputs to adjacent machines per tick.",
                     "0 = unlimited.")
            .defineInRange("energy.receiverOutputRate", 0, 0, Integer.MAX_VALUE);

    /* ---- photon storage capacities (tier 1 .. tier 5) ---- */

    private static ForgeConfigSpec.LongValue tierCapacity(String suffix, String label, long defaultValue) {
        return B
                .comment("Photon Storage " + label + " FE capacity. Default = " + defaultValue + " FE.",
                         "Range: 1 .. Long.MAX_VALUE (~9.2 quintillion).")
                .defineInRange("storage.tier" + suffix + "Capacity", defaultValue, 1L, Long.MAX_VALUE);
    }
    private static final ForgeConfigSpec.LongValue STORAGE_CAP_T1 = tierCapacity("1", "I (Copper)",    1L << 16);
    private static final ForgeConfigSpec.LongValue STORAGE_CAP_T2 = tierCapacity("2", "II (Iron)",     1L << 20);
    private static final ForgeConfigSpec.LongValue STORAGE_CAP_T3 = tierCapacity("3", "III (Gold)",    1L << 24);
    private static final ForgeConfigSpec.LongValue STORAGE_CAP_T4 = tierCapacity("4", "IV (Diamond)",  1L << 28);
    private static final ForgeConfigSpec.LongValue STORAGE_CAP_T5 = tierCapacity("5", "V (Emerald)",   1L << 32);

    /* ---- resource pipelines: master switches + per-cycle caps ---- */

    private static final ForgeConfigSpec.BooleanValue ITEMS_ENABLED = B
            .comment("Allow items to flow through quantum channels. When false, emitters never pull",
                     "or accept items regardless of per-device or per-channel settings.")
            .define("routing.items.enabled", true);
    private static final ForgeConfigSpec.IntValue ITEMS_MAX_BATCH = B
            .comment("Hard upper limit on the items batch size (items moved per cycle, every 2 ticks).",
                     "User-set values above this are clamped on use.")
            .defineInRange("routing.items.maxBatch", ItemChannelConfig.MAX_BATCH,
                    ItemChannelConfig.MIN_BATCH, ItemChannelConfig.MAX_BATCH);
    private static final ForgeConfigSpec.IntValue ITEMS_MAX_SUBS_PER_EMITTER = B
            .comment("Max item subchannels a single emitter can host.")
            .defineInRange("routing.items.maxSubchannelsPerEmitter", 3, 1, ItemChannelConfig.MAX_SUBCHANNELS);
    private static final ForgeConfigSpec.IntValue ITEMS_MAX_SUBS_PER_RECEIVER = B
            .comment("Max item subchannels a single receiver can listen to at once.")
            .defineInRange("routing.items.maxSubscriptionsPerReceiver", 3, 1, ItemChannelConfig.MAX_SUBCHANNELS);
    private static final ForgeConfigSpec.IntValue ITEMS_MAX_SUBS_PER_CHANNEL = B
            .comment("Max item subchannels summed across every loaded emitter on a single channel.")
            .defineInRange("routing.items.maxSubchannelsPerChannel", 9, 1, ItemChannelConfig.MAX_SUBCHANNELS * 16);

    private static final ForgeConfigSpec.BooleanValue FLUIDS_ENABLED = B
            .comment("Allow fluids to flow through quantum channels.")
            .define("routing.fluids.enabled", true);
    private static final ForgeConfigSpec.IntValue FLUIDS_MAX_BATCH = B
            .comment("Hard upper limit on the fluids batch size (mB moved per cycle).")
            .defineInRange("routing.fluids.maxBatch", FluidChannelConfig.MAX_BATCH,
                    FluidChannelConfig.MIN_BATCH, FluidChannelConfig.MAX_BATCH);
    private static final ForgeConfigSpec.IntValue FLUIDS_MAX_SUBS_PER_EMITTER = B
            .comment("Max fluid subchannels a single emitter can host.")
            .defineInRange("routing.fluids.maxSubchannelsPerEmitter", 3, 1, FluidChannelConfig.MAX_SUBCHANNELS);
    private static final ForgeConfigSpec.IntValue FLUIDS_MAX_SUBS_PER_RECEIVER = B
            .comment("Max fluid subchannels a single receiver can listen to at once.")
            .defineInRange("routing.fluids.maxSubscriptionsPerReceiver", 3, 1, FluidChannelConfig.MAX_SUBCHANNELS);
    private static final ForgeConfigSpec.IntValue FLUIDS_MAX_SUBS_PER_CHANNEL = B
            .comment("Max fluid subchannels summed across every loaded emitter on a single channel.")
            .defineInRange("routing.fluids.maxSubchannelsPerChannel", 9, 1, FluidChannelConfig.MAX_SUBCHANNELS * 16);

    private static final ForgeConfigSpec.BooleanValue GASES_ENABLED = B
            .comment("Allow gases to flow through quantum channels (requires Mekanism at runtime).")
            .define("routing.gases.enabled", true);
    private static final ForgeConfigSpec.IntValue GASES_MAX_BATCH = B
            .comment("Hard upper limit on the gas batch size (mB moved per cycle).")
            .defineInRange("routing.gases.maxBatch", GasChannelConfig.MAX_BATCH,
                    GasChannelConfig.MIN_BATCH, GasChannelConfig.MAX_BATCH);
    private static final ForgeConfigSpec.IntValue GASES_MAX_SUBS_PER_EMITTER = B
            .comment("Max gas subchannels a single emitter can host.")
            .defineInRange("routing.gases.maxSubchannelsPerEmitter", 3, 1, GasChannelConfig.MAX_SUBCHANNELS);
    private static final ForgeConfigSpec.IntValue GASES_MAX_SUBS_PER_RECEIVER = B
            .comment("Max gas subchannels a single receiver can listen to at once.")
            .defineInRange("routing.gases.maxSubscriptionsPerReceiver", 3, 1, GasChannelConfig.MAX_SUBCHANNELS);
    private static final ForgeConfigSpec.IntValue GASES_MAX_SUBS_PER_CHANNEL = B
            .comment("Max gas subchannels summed across every loaded emitter on a single channel.")
            .defineInRange("routing.gases.maxSubchannelsPerChannel", 9, 1, GasChannelConfig.MAX_SUBCHANNELS * 16);

    private static final ForgeConfigSpec.BooleanValue HEAT_ENABLED = B
            .comment("Allow heat transfer through quantum channels (placeholder — the thermal-wire",
                     "pipeline is intentionally not yet implemented; flip this on once it ships).")
            .define("routing.heat.enabled", false);

    /* ---- wireless charging (per-slot disable + master switch) ---- */

    private static final ForgeConfigSpec.BooleanValue ENABLE_WIRELESS = B
            .comment("Master switch for the wireless-charging system. When false, no channel will",
                     "dispense FE to subscribed players regardless of the channel's per-slot mask.")
            .define("charging.enabled", true);

    private static final ForgeConfigSpec.BooleanValue ENABLE_HAND = B
            .comment("Allow channels to charge items in the main / off hand.")
            .define("charging.slots.hand", true);
    private static final ForgeConfigSpec.BooleanValue ENABLE_HOTBAR = B
            .comment("Allow channels to charge items in the hotbar.")
            .define("charging.slots.hotbar", true);
    private static final ForgeConfigSpec.BooleanValue ENABLE_INVENTORY = B
            .comment("Allow channels to charge items in the main inventory.")
            .define("charging.slots.inventory", true);
    private static final ForgeConfigSpec.BooleanValue ENABLE_ARMOR = B
            .comment("Allow channels to charge worn armor.")
            .define("charging.slots.armor", true);
    private static final ForgeConfigSpec.BooleanValue ENABLE_CURIOS = B
            .comment("Allow channels to charge Curios slots (only meaningful when Curios is loaded).")
            .define("charging.slots.curios", true);

    public static final ForgeConfigSpec SPEC = B.build();

    /* ---- hot-path mirrors (set inside onLoad) ---- */

    public static boolean allowCrossDimension = true;

    public static int emitterPushRate    = 0;
    public static int receiverOutputRate = 0;
    /** Index = tier - 1. Populated on config load. */
    public static long[] storageCapacities = new long[]{ 1L << 16, 1L << 20, 1L << 24, 1L << 28, 1L << 32 };

    public static boolean itemsRoutingEnabled    = true;
    public static int     itemsMaxBatch          = ItemChannelConfig.MAX_BATCH;
    public static int     itemsMaxSubsPerEmitter = 3;
    public static int     itemsMaxSubsPerReceiver = 3;
    public static int     itemsMaxSubsPerChannel = 9;

    public static boolean fluidsRoutingEnabled    = true;
    public static int     fluidsMaxBatch          = FluidChannelConfig.MAX_BATCH;
    public static int     fluidsMaxSubsPerEmitter = 3;
    public static int     fluidsMaxSubsPerReceiver = 3;
    public static int     fluidsMaxSubsPerChannel = 9;

    public static boolean gasesRoutingEnabled    = true;
    public static int     gasesMaxBatch          = GasChannelConfig.MAX_BATCH;
    public static int     gasesMaxSubsPerEmitter = 3;
    public static int     gasesMaxSubsPerReceiver = 3;
    public static int     gasesMaxSubsPerChannel = 9;

    public static boolean heatRoutingEnabled   = false;

    public static boolean wirelessEnabled       = true;
    public static boolean slotHandEnabled       = true;
    public static boolean slotHotbarEnabled     = true;
    public static boolean slotInventoryEnabled  = true;
    public static boolean slotArmorEnabled      = true;
    public static boolean slotCuriosEnabled     = true;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        applyTo(event);
        // Reloading events fire when an admin edits the toml while the server runs. Push the new
        // snapshot to every connected player so their UI mirrors update without waiting for a relog.
        // getCurrentServer() returns null on a pure client (dedicated multiplayer client), where
        // edits to the local file shouldn't affect the connected server anyway.
        if (event instanceof ModConfigEvent.Reloading) {
            net.minecraft.server.MinecraftServer srv = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (srv != null) {
                // Reapply the disable mask to existing channel data, then push the snapshot so
                // open clients see both the locked UI AND the cleaned channel state.
                com.quantumchanneling.channel.ChannelData.get(srv).applyChargingSlotConfig();
                com.quantumchanneling.channel.SyncServerConfigPacket.sendToAll(srv);
            }
        }
    }

    /**
     * Called from {@link ServerConfigSync} when the client disconnects from a remote server, so
     * the local mirror reverts from the server's overridden values back to the player's own toml.
     * This needs the spec to actually be loaded (it always is by mid-init, but we guard anyway).
     */
    public static void reapplyLocal() {
        try {
            allowCrossDimension    = ALLOW_CROSS_DIMENSION.get();
            emitterPushRate        = EMITTER_PUSH_RATE.get();
            receiverOutputRate     = RECEIVER_OUTPUT_RATE.get();
            storageCapacities      = new long[]{
                    STORAGE_CAP_T1.get(), STORAGE_CAP_T2.get(), STORAGE_CAP_T3.get(),
                    STORAGE_CAP_T4.get(), STORAGE_CAP_T5.get()
            };
            itemsRoutingEnabled     = ITEMS_ENABLED.get();
            itemsMaxBatch           = ITEMS_MAX_BATCH.get();
            itemsMaxSubsPerEmitter  = ITEMS_MAX_SUBS_PER_EMITTER.get();
            itemsMaxSubsPerReceiver = ITEMS_MAX_SUBS_PER_RECEIVER.get();
            itemsMaxSubsPerChannel  = ITEMS_MAX_SUBS_PER_CHANNEL.get();
            fluidsRoutingEnabled    = FLUIDS_ENABLED.get();
            fluidsMaxBatch          = FLUIDS_MAX_BATCH.get();
            fluidsMaxSubsPerEmitter = FLUIDS_MAX_SUBS_PER_EMITTER.get();
            fluidsMaxSubsPerReceiver = FLUIDS_MAX_SUBS_PER_RECEIVER.get();
            fluidsMaxSubsPerChannel = FLUIDS_MAX_SUBS_PER_CHANNEL.get();
            gasesRoutingEnabled     = GASES_ENABLED.get();
            gasesMaxBatch           = GASES_MAX_BATCH.get();
            gasesMaxSubsPerEmitter  = GASES_MAX_SUBS_PER_EMITTER.get();
            gasesMaxSubsPerReceiver = GASES_MAX_SUBS_PER_RECEIVER.get();
            gasesMaxSubsPerChannel  = GASES_MAX_SUBS_PER_CHANNEL.get();
            heatRoutingEnabled      = HEAT_ENABLED.get();
            wirelessEnabled        = ENABLE_WIRELESS.get();
            slotHandEnabled        = ENABLE_HAND.get();
            slotHotbarEnabled      = ENABLE_HOTBAR.get();
            slotInventoryEnabled   = ENABLE_INVENTORY.get();
            slotArmorEnabled       = ENABLE_ARMOR.get();
            slotCuriosEnabled      = ENABLE_CURIOS.get();
        } catch (IllegalStateException notLoadedYet) {
            // Spec hasn't finished loading — happens during very-early init. Static-field defaults
            // are already what we'd write here, so a no-op is correct.
        }
    }

    private static void applyTo(final ModConfigEvent event) {
        // Field assignment lives here so onLoad can wrap it with the reload-sync hook.
        allowCrossDimension    = ALLOW_CROSS_DIMENSION.get();
        emitterPushRate        = EMITTER_PUSH_RATE.get();
        receiverOutputRate     = RECEIVER_OUTPUT_RATE.get();
        storageCapacities      = new long[]{
                STORAGE_CAP_T1.get(), STORAGE_CAP_T2.get(), STORAGE_CAP_T3.get(),
                STORAGE_CAP_T4.get(), STORAGE_CAP_T5.get()
        };
        itemsRoutingEnabled     = ITEMS_ENABLED.get();
        itemsMaxBatch           = ITEMS_MAX_BATCH.get();
        itemsMaxSubsPerEmitter  = ITEMS_MAX_SUBS_PER_EMITTER.get();
        itemsMaxSubsPerReceiver = ITEMS_MAX_SUBS_PER_RECEIVER.get();
        itemsMaxSubsPerChannel  = ITEMS_MAX_SUBS_PER_CHANNEL.get();
        fluidsRoutingEnabled    = FLUIDS_ENABLED.get();
        fluidsMaxBatch          = FLUIDS_MAX_BATCH.get();
        fluidsMaxSubsPerEmitter = FLUIDS_MAX_SUBS_PER_EMITTER.get();
        fluidsMaxSubsPerReceiver = FLUIDS_MAX_SUBS_PER_RECEIVER.get();
        fluidsMaxSubsPerChannel = FLUIDS_MAX_SUBS_PER_CHANNEL.get();
        gasesRoutingEnabled     = GASES_ENABLED.get();
        gasesMaxBatch           = GASES_MAX_BATCH.get();
        gasesMaxSubsPerEmitter  = GASES_MAX_SUBS_PER_EMITTER.get();
        gasesMaxSubsPerReceiver = GASES_MAX_SUBS_PER_RECEIVER.get();
        gasesMaxSubsPerChannel  = GASES_MAX_SUBS_PER_CHANNEL.get();
        heatRoutingEnabled      = HEAT_ENABLED.get();
        wirelessEnabled        = ENABLE_WIRELESS.get();
        slotHandEnabled        = ENABLE_HAND.get();
        slotHotbarEnabled      = ENABLE_HOTBAR.get();
        slotInventoryEnabled   = ENABLE_INVENTORY.get();
        slotArmorEnabled       = ENABLE_ARMOR.get();
        slotCuriosEnabled      = ENABLE_CURIOS.get();
    }
}
