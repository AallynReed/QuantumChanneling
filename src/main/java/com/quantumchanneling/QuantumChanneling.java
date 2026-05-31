package com.quantumchanneling;

import com.mojang.logging.LogUtils;
import com.quantumchanneling.block.PhotonManagerBlock;
import com.quantumchanneling.block.PhotonEmitterBlock;
import com.quantumchanneling.block.PhotonReceiverBlock;
import com.quantumchanneling.block.PhotonStorageBlock;
import com.quantumchanneling.blockentity.PhotonManagerBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import com.quantumchanneling.blockentity.PhotonStorageBlockEntity;
import com.quantumchanneling.item.PhotonBlockItem;
import com.quantumchanneling.item.TooltipItem;
import com.quantumchanneling.client.PhotonNodeScreen;
import com.quantumchanneling.menu.PhotonNodeMenu;
import com.quantumchanneling.channel.ModMessages;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import com.quantumchanneling.channel.ChannelData;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(QuantumChanneling.MODID)
public class QuantumChanneling {
    public static final String MODID = "quantumchanneling";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Item> QUANTUM_CORE = ITEMS.register("quantum_core",
            () -> new TooltipItem(new Item.Properties(), "tooltip.quantumchanneling.quantum_core"));

    /** Portable device-config "blueprint" — captures and applies emitter/receiver settings. */
    public static final RegistryObject<Item> PHOTON_CONFIG_CARD = ITEMS.register("photon_config_card",
            () -> new com.quantumchanneling.item.PhotonConfigCard(new Item.Properties().stacksTo(1)));

    /** True when any of the 6 PhotonShape connection booleans is set — drives the lit/dim glow on emitter/receiver. */
    private static boolean hasAnyConnection(net.minecraft.world.level.block.state.BlockState state) {
        try {
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                if (state.getValue(com.quantumchanneling.block.PhotonShape.connProp(d))) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static final RegistryObject<Block> PHOTON_EMITTER = BLOCKS.register("photon_emitter",
            () -> new PhotonEmitterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    // Emits a soft glow when any connection arm is live.
                    .lightLevel(state -> hasAnyConnection(state) ? 7 : 3)));

    public static final RegistryObject<Item> PHOTON_EMITTER_ITEM = ITEMS.register("photon_emitter",
            () -> new PhotonBlockItem(PHOTON_EMITTER.get(), new Item.Properties(),
                    "tooltip.quantumchanneling.photon_emitter"));

    public static final RegistryObject<Block> PHOTON_RECEIVER = BLOCKS.register("photon_receiver",
            () -> new PhotonReceiverBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> hasAnyConnection(state) ? 7 : 3)));

    public static final RegistryObject<Item> PHOTON_RECEIVER_ITEM = ITEMS.register("photon_receiver",
            () -> new PhotonBlockItem(PHOTON_RECEIVER.get(), new Item.Properties(),
                    "tooltip.quantumchanneling.photon_receiver"));

    // Photon Storage — five tiers (Copper / Iron / Gold / Diamond / Emerald). Capacities live in
    // Config (Config.storageCapacities, indexed by tier-1) so they're user-tunable up to Long.MAX_VALUE
    // (~9.2 quintillion). Storage is NOT exposed as an IEnergyStorage capability — see
    // PhotonStorageBlockEntity — so the long ceiling is real, not bound by the int-based FE interface.
    private static RegistryObject<Block> registerStorageTier(String id, int tier, MapColor mc) {
        RegistryObject<Block> block = BLOCKS.register(id, () -> new PhotonStorageBlock(
                BlockBehaviour.Properties.of()
                        .mapColor(mc)
                        .strength(3.0f, 6.0f)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops()
                        // Bright proportional to fill bucket (0..8) — full storage = light level 15.
                        .lightLevel(state -> {
                            try { return Math.min(15, state.getValue(PhotonStorageBlock.LEVEL) * 2); }
                            catch (Exception e) { return 0; }
                        }),
                tier));
        ITEMS.register(id, () -> new PhotonBlockItem(block.get(), new Item.Properties(),
                "tooltip.quantumchanneling.photon_storage"));
        return block;
    }

    public static final RegistryObject<Block> PHOTON_STORAGE_T1 =
            registerStorageTier("photon_storage_1", 1, MapColor.COLOR_ORANGE);
    public static final RegistryObject<Block> PHOTON_STORAGE_T2 =
            registerStorageTier("photon_storage_2", 2, MapColor.COLOR_LIGHT_GRAY);
    public static final RegistryObject<Block> PHOTON_STORAGE_T3 =
            registerStorageTier("photon_storage_3", 3, MapColor.COLOR_YELLOW);
    public static final RegistryObject<Block> PHOTON_STORAGE_T4 =
            registerStorageTier("photon_storage_4", 4, MapColor.DIAMOND);
    public static final RegistryObject<Block> PHOTON_STORAGE_T5 =
            registerStorageTier("photon_storage_5", 5, MapColor.EMERALD);

    public static final RegistryObject<Block> PHOTON_MANAGER = BLOCKS.register("photon_manager",
            () -> new PhotonManagerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    // Manager always glows softly — it's the brain of the channel.
                    .lightLevel(state -> 10)));

    public static final RegistryObject<Item> PHOTON_MANAGER_ITEM = ITEMS.register("photon_manager",
            () -> new PhotonBlockItem(PHOTON_MANAGER.get(), new Item.Properties(),
                    "tooltip.quantumchanneling.photon_manager"));

    public static final RegistryObject<BlockEntityType<PhotonEmitterBlockEntity>> PHOTON_EMITTER_BE =
            BLOCK_ENTITIES.register("photon_emitter",
                    () -> BlockEntityType.Builder.of(PhotonEmitterBlockEntity::new, PHOTON_EMITTER.get()).build(null));

    public static final RegistryObject<BlockEntityType<PhotonReceiverBlockEntity>> PHOTON_RECEIVER_BE =
            BLOCK_ENTITIES.register("photon_receiver",
                    () -> BlockEntityType.Builder.of(PhotonReceiverBlockEntity::new, PHOTON_RECEIVER.get()).build(null));

    public static final RegistryObject<BlockEntityType<PhotonStorageBlockEntity>> PHOTON_STORAGE_BE =
            BLOCK_ENTITIES.register("photon_storage",
                    () -> BlockEntityType.Builder.of(PhotonStorageBlockEntity::new,
                            PHOTON_STORAGE_T1.get(), PHOTON_STORAGE_T2.get(), PHOTON_STORAGE_T3.get(),
                            PHOTON_STORAGE_T4.get(), PHOTON_STORAGE_T5.get()).build(null));

    public static final RegistryObject<BlockEntityType<PhotonManagerBlockEntity>> PHOTON_MANAGER_BE =
            BLOCK_ENTITIES.register("photon_manager",
                    () -> BlockEntityType.Builder.of(PhotonManagerBlockEntity::new, PHOTON_MANAGER.get()).build(null));

    public static final RegistryObject<MenuType<PhotonNodeMenu>> PHOTON_NODE_MENU = MENU_TYPES.register("photon_node",
            () -> IForgeMenuType.create(PhotonNodeMenu::new));

    public static final RegistryObject<CreativeModeTab> QUANTUM_TAB = CREATIVE_MODE_TABS.register("quantum_tab",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(Component.translatable("itemGroup.quantumchanneling"))
                    .icon(() -> QUANTUM_CORE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(QUANTUM_CORE.get());
                        output.accept(PHOTON_CONFIG_CARD.get());
                        output.accept(PHOTON_EMITTER_ITEM.get());
                        output.accept(PHOTON_RECEIVER_ITEM.get());
                        output.accept(PHOTON_STORAGE_T1.get());
                        output.accept(PHOTON_STORAGE_T2.get());
                        output.accept(PHOTON_STORAGE_T3.get());
                        output.accept(PHOTON_STORAGE_T4.get());
                        output.accept(PHOTON_STORAGE_T5.get());
                        output.accept(PHOTON_MANAGER_ITEM.get());
                    }).build());

    public QuantumChanneling(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        // Mekanism integration: gas + heat capability attachment. The class lives in
        // com.quantumchanneling.compat.mekanism and imports mekanism.api.* types, so we touch it
        // ONLY when Mekanism is present. The JVM lazy-loads referenced classes on first use, so
        // this single conditional reference is the load gate.
        if (net.minecraftforge.fml.ModList.get().isLoaded("mekanism")) {
            com.quantumchanneling.compat.mekanism.MekanismCapAttacher.register();
            LOGGER.info("Quantum Channeling: Mekanism detected — gas + heat caps active.");
        }

        // COMMON type so the file lives at config/quantumchanneling-common.toml — locally editable
        // with any text editor, applies to single-player by default. On multiplayer the dedicated
        // server has its OWN copy of this file; clients receive the server's values via
        // SyncServerConfigPacket on join (overriding the client's local mirror), and on logout
        // ServerConfigSync re-applies the local values so single-player picks up where you left off.
        context.registerConfig(ModConfig.Type.COMMON, ServerConfig.SPEC);

        // Validate forced-chunk tickets on world load. Keep all of them — owning BlockEntities
        // re-sync their own ticket state on onLoad(), which cleans up any orphan in either direction.
        ForgeChunkManager.setForcedChunkLoadingCallback(MODID, (level, helper) -> {
            // intentional no-op
        });
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModMessages::register);
        LOGGER.info("Quantum Channeling: common setup complete (settings are server-config; values applied on world load).");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Quantum Channeling: server starting");
    }

    @SubscribeEvent
    public void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        // Apply server-side charging disables to every saved channel now that the overworld
        // is available. Persists across restarts: if an admin disables slot X, every channel's
        // mask gets X cleared once, the SavedData is marked dirty, and the change survives.
        ChannelData.get(event.getServer()).applyChargingSlotConfig();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ChannelData.get(event.getServer()).tickCharging(event.getServer());
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> MenuScreens.register(PHOTON_NODE_MENU.get(), PhotonNodeScreen::new));
        }

        /**
         * Bind the custom BlockEntityRenderer for emitter + receiver. The renderer draws the
         * additive glow ball and the beams into connected faces; the baked block model provides
         * only the dark containment shell + port frames around it.
         */
        @SubscribeEvent
        public static void onRegisterRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(PHOTON_EMITTER_BE.get(),
                    ctx -> new com.quantumchanneling.client.render.PhotonNodeRenderer<>(ctx));
            event.registerBlockEntityRenderer(PHOTON_RECEIVER_BE.get(),
                    ctx -> new com.quantumchanneling.client.render.PhotonNodeRenderer<>(ctx));
        }

        /**
         * Load the custom GLSL programs the BER draws through. The shaders themselves live under
         * assets/quantumchanneling/shaders/core/ — see {@link com.quantumchanneling.client.render.PhotonShaders}.
         */
        @SubscribeEvent
        public static void onRegisterShaders(net.minecraftforge.client.event.RegisterShadersEvent event) {
            com.quantumchanneling.client.render.PhotonShaders.register(event);
        }
    }
}
