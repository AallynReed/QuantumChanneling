package com.quantumchanneling;

import com.quantumchanneling.channel.FluidChannelConfig;
import com.quantumchanneling.channel.GasChannelConfig;
import com.quantumchanneling.channel.ItemChannelConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Server-authoritative settings. Lives at {@code world/serverconfig/quantumchanneling-server.toml}
 * on a dedicated server and inside the world folder for single-player. Admins use this to disable
 * whole pipelines or clamp how much a single emitter is allowed to move per cycle, without
 * touching individual channel state.
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
        // On a Reloading event the server is already running — fan the fresh snapshot out to every
        // connected player so their UI mirrors update without waiting for a relog.
        if (event instanceof ModConfigEvent.Reloading) {
            net.minecraft.server.MinecraftServer srv = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (srv != null) com.quantumchanneling.channel.SyncServerConfigPacket.sendToAll(srv);
        }
    }

    private static void applyTo(final ModConfigEvent event) {
        // Field assignment lives here so onLoad can wrap it with the reload-sync hook.
        allowCrossDimension    = ALLOW_CROSS_DIMENSION.get();
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
