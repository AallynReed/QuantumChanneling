package com.quantumchanneling;

import com.mojang.logging.LogUtils;
import com.quantumchanneling.block.PhotonEmitterBlock;
import com.quantumchanneling.block.PhotonManagerBlock;
import com.quantumchanneling.block.PhotonReceiverBlock;
import com.quantumchanneling.block.PhotonShape;
import com.quantumchanneling.block.PhotonStorageBlock;
import com.quantumchanneling.block.StarShapersHammerBlock;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonManagerBlockEntity;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import com.quantumchanneling.blockentity.PhotonStorageBlockEntity;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.channel.ModMessages;
import com.quantumchanneling.compat.mekanism.ChemicalCompat;
import com.quantumchanneling.item.PhotonBlockItem;
import com.quantumchanneling.item.PhotonConfigCard;
import com.quantumchanneling.item.TooltipItem;
import com.quantumchanneling.menu.PhotonNodeMenu;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.EmptyResourceHandler;
import org.slf4j.Logger;

@Mod(QuantumChanneling.MODID)
public class QuantumChanneling {
    public static final String MODID = "quantumchanneling";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    /** Forced-chunk tickets for devices with chunk loading enabled. Each device re-syncs its own
     *  ticket on load, so the validation callback keeps everything. */
    public static final TicketController CHUNK_TICKETS = new TicketController(id("device"));

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    public static final DeferredItem<TooltipItem> QUANTUM_CORE = ITEMS.registerItem("quantum_core",
            p -> new TooltipItem(p, "tooltip.quantumchanneling.quantum_core"));

    /** Emitter settings captured on a {@link PhotonConfigCard}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> CONFIG_CARD_DATA =
            DATA_COMPONENTS.registerComponentType("config_card", b -> b
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG));

    /** Portable device-config "blueprint" — captures and applies emitter/receiver settings. */
    public static final DeferredItem<PhotonConfigCard> PHOTON_CONFIG_CARD = ITEMS.registerItem("photon_config_card",
            PhotonConfigCard::new, p -> p.stacksTo(1));

    /** True when any of the 6 PhotonShape connection booleans is set — drives the lit/dim glow on emitter/receiver. */
    private static boolean hasAnyConnection(BlockState state) {
        for (Direction d : Direction.values()) {
            if (state.getValue(PhotonShape.connProp(d))) return true;
        }
        return false;
    }

    private static BlockBehaviour.Properties device(BlockBehaviour.Properties p, MapColor color) {
        return p.mapColor(color)
                .strength(3.0f, 6.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    private static <B extends Block> DeferredItem<PhotonBlockItem> blockItem(DeferredBlock<B> block, String tooltipKey) {
        return ITEMS.registerItem(block.getId().getPath(),
                p -> new PhotonBlockItem(block.get(), p, tooltipKey),
                Item.Properties::useBlockDescriptionPrefix);
    }

    public static final DeferredBlock<PhotonEmitterBlock> PHOTON_EMITTER = BLOCKS.registerBlock("photon_emitter",
            PhotonEmitterBlock::new,
            // Emits a soft glow when any connection arm is live.
            p -> device(p, MapColor.COLOR_BLUE).lightLevel(state -> hasAnyConnection(state) ? 7 : 3));
    public static final DeferredItem<PhotonBlockItem> PHOTON_EMITTER_ITEM =
            blockItem(PHOTON_EMITTER, "tooltip.quantumchanneling.photon_emitter");

    public static final DeferredBlock<PhotonReceiverBlock> PHOTON_RECEIVER = BLOCKS.registerBlock("photon_receiver",
            PhotonReceiverBlock::new,
            p -> device(p, MapColor.COLOR_CYAN).lightLevel(state -> hasAnyConnection(state) ? 7 : 3));
    public static final DeferredItem<PhotonBlockItem> PHOTON_RECEIVER_ITEM =
            blockItem(PHOTON_RECEIVER, "tooltip.quantumchanneling.photon_receiver");

    // Photon Storage — five tiers (Copper / Iron / Gold / Diamond / Emerald). Capacities live in
    // ServerConfig.storageCapacities (indexed by tier-1) so they're user-tunable up to Long.MAX_VALUE.
    // Storage is NOT exposed as an energy capability, so the long ceiling is real.
    private static DeferredBlock<PhotonStorageBlock> registerStorageTier(String id, int tier, MapColor mc) {
        DeferredBlock<PhotonStorageBlock> block = BLOCKS.registerBlock(id,
                p -> new PhotonStorageBlock(p, tier),
                // Brightness tracks the fill bucket (0..8) — full storage = light level 15.
                p -> device(p, mc).lightLevel(state -> Math.min(15, state.getValue(PhotonStorageBlock.LEVEL) * 2)));
        blockItem(block, "tooltip.quantumchanneling.photon_storage");
        return block;
    }

    public static final DeferredBlock<PhotonStorageBlock> PHOTON_STORAGE_T1 =
            registerStorageTier("photon_storage_1", 1, MapColor.COLOR_ORANGE);
    public static final DeferredBlock<PhotonStorageBlock> PHOTON_STORAGE_T2 =
            registerStorageTier("photon_storage_2", 2, MapColor.COLOR_LIGHT_GRAY);
    public static final DeferredBlock<PhotonStorageBlock> PHOTON_STORAGE_T3 =
            registerStorageTier("photon_storage_3", 3, MapColor.COLOR_YELLOW);
    public static final DeferredBlock<PhotonStorageBlock> PHOTON_STORAGE_T4 =
            registerStorageTier("photon_storage_4", 4, MapColor.DIAMOND);
    public static final DeferredBlock<PhotonStorageBlock> PHOTON_STORAGE_T5 =
            registerStorageTier("photon_storage_5", 5, MapColor.EMERALD);

    public static final DeferredBlock<PhotonManagerBlock> PHOTON_MANAGER = BLOCKS.registerBlock("photon_manager",
            PhotonManagerBlock::new,
            // Manager always glows softly — it's the brain of the channel.
            p -> device(p, MapColor.COLOR_LIGHT_BLUE).lightLevel(state -> 10));
    public static final DeferredItem<PhotonBlockItem> PHOTON_MANAGER_ITEM =
            blockItem(PHOTON_MANAGER, "tooltip.quantumchanneling.photon_manager");

    /** Diamond/obsidian/glass-pane assembly. First step toward the Photon Manager craft. */
    public static final DeferredItem<TooltipItem> PHOTON_MANAGER_CONTAINER = ITEMS.registerItem("photon_manager_container",
            p -> new TooltipItem(p, "tooltip.quantumchanneling.photon_manager_container"));

    /** Netherite + gold lattice — the ring around the Manager's contained core. */
    public static final DeferredItem<TooltipItem> DYSON_RING = ITEMS.registerItem("dyson_ring",
            p -> new TooltipItem(p, "tooltip.quantumchanneling.dyson_ring"));

    /** Forged-by-hammer top-tier alloy. Recipe ingredient. */
    public static final DeferredBlock<Block> STAR_ALLOY_BLOCK = BLOCKS.registerSimpleBlock("star_alloy_block",
            p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(5.0f, 12.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());
    public static final DeferredItem<PhotonBlockItem> STAR_ALLOY_BLOCK_ITEM =
            blockItem(STAR_ALLOY_BLOCK, "tooltip.quantumchanneling.star_alloy_block");

    /** Placeable crafting station. Dropping it (or right-clicking a placed one) crushes ingredient
     *  item entities below it: Nether Star → 2 White Dwarf, White Dwarf → 1 Uncontained Black Hole.
     *  Only a netherite pickaxe harvests it (block tag {@code neoforge:needs_netherite_tool}). */
    public static final DeferredBlock<StarShapersHammerBlock> STAR_SHAPERS_HAMMER_BLOCK = BLOCKS.registerBlock("star_shapers_hammer",
            StarShapersHammerBlock::new,
            p -> p.mapColor(MapColor.METAL)
                    .strength(6.0f, 14.0f)
                    .sound(SoundType.ANVIL)
                    .requiresCorrectToolForDrops());
    public static final DeferredItem<PhotonBlockItem> STAR_SHAPERS_HAMMER = ITEMS.registerItem("star_shapers_hammer",
            p -> new PhotonBlockItem(STAR_SHAPERS_HAMMER_BLOCK.get(), p, "tooltip.quantumchanneling.star_shapers_hammer"),
            p -> p.useBlockDescriptionPrefix().stacksTo(1));

    /** Intermediate product — what the hammer makes when it smashes a Nether Star. Each White
     *  Dwarf in turn collapses into an Uncontained Black Hole. Renders as a free-floating
     *  white-sun shader (no block model). */
    public static final DeferredItem<TooltipItem> WHITE_DWARF = ITEMS.registerItem("white_dwarf",
            p -> new TooltipItem(p, "tooltip.quantumchanneling.white_dwarf"), p -> p.stacksTo(16));

    /** The volatile core of a Manager. Explosion-proof as a dropped item so the hammer's own
     *  collapse burst (and TNT, creepers…) can't destroy freshly forged stacks. */
    public static final DeferredItem<TooltipItem> UNCONTAINED_BLACK_HOLE = ITEMS.registerItem("uncontained_black_hole",
            p -> new TooltipItem(p, "tooltip.quantumchanneling.uncontained_black_hole"),
            p -> p.stacksTo(16).delayedComponent(DataComponents.DAMAGE_RESISTANT,
                    ctx -> new DamageResistant(ctx.getOrThrow(DamageTypeTags.IS_EXPLOSION))));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhotonEmitterBlockEntity>> PHOTON_EMITTER_BE =
            BLOCK_ENTITIES.register("photon_emitter",
                    () -> new BlockEntityType<>(PhotonEmitterBlockEntity::new, PHOTON_EMITTER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhotonReceiverBlockEntity>> PHOTON_RECEIVER_BE =
            BLOCK_ENTITIES.register("photon_receiver",
                    () -> new BlockEntityType<>(PhotonReceiverBlockEntity::new, PHOTON_RECEIVER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhotonStorageBlockEntity>> PHOTON_STORAGE_BE =
            BLOCK_ENTITIES.register("photon_storage",
                    () -> new BlockEntityType<>(PhotonStorageBlockEntity::new,
                            PHOTON_STORAGE_T1.get(), PHOTON_STORAGE_T2.get(), PHOTON_STORAGE_T3.get(),
                            PHOTON_STORAGE_T4.get(), PHOTON_STORAGE_T5.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhotonManagerBlockEntity>> PHOTON_MANAGER_BE =
            BLOCK_ENTITIES.register("photon_manager",
                    () -> new BlockEntityType<>(PhotonManagerBlockEntity::new, PHOTON_MANAGER.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<PhotonNodeMenu>> PHOTON_NODE_MENU =
            MENU_TYPES.register("photon_node", () -> IMenuTypeExtension.create(PhotonNodeMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> QUANTUM_TAB = CREATIVE_MODE_TABS.register("quantum_tab",
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
                        output.accept(PHOTON_MANAGER_CONTAINER.get());
                        output.accept(DYSON_RING.get());
                        output.accept(STAR_ALLOY_BLOCK_ITEM.get());
                        output.accept(STAR_SHAPERS_HAMMER.get());
                        output.accept(WHITE_DWARF.get());
                        output.accept(UNCONTAINED_BLACK_HOLE.get());
                    }).build());

    public QuantumChanneling(IEventBus modEventBus, ModContainer container) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);

        modEventBus.addListener(ModMessages::register);
        modEventBus.addListener(QuantumChanneling::registerCapabilities);
        modEventBus.addListener((RegisterTicketControllersEvent e) -> e.register(CHUNK_TICKETS));

        NeoForge.EVENT_BUS.addListener(QuantumChanneling::onServerStarted);
        NeoForge.EVENT_BUS.addListener(QuantumChanneling::onServerTick);

        // COMMON so the file lives at config/quantumchanneling-common.toml — locally editable and
        // applied in single-player. On multiplayer the dedicated server's copy is authoritative:
        // clients receive its values via SyncServerConfigPacket on join and revert on logout.
        container.registerConfig(ModConfig.Type.COMMON, ServerConfig.SPEC);
    }

    /** All-in-one transport: emitters accept every resource; receivers are passive drop points. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, PHOTON_EMITTER_BE.get(), (be, side) -> be.energyHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, PHOTON_EMITTER_BE.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, PHOTON_EMITTER_BE.get(), (be, side) -> be.fluidHandler());

        event.registerBlockEntity(Capabilities.Energy.BLOCK, PHOTON_RECEIVER_BE.get(), (be, side) -> be.energyHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, PHOTON_RECEIVER_BE.get(), (be, side) -> EmptyResourceHandler.instance());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, PHOTON_RECEIVER_BE.get(), (be, side) -> EmptyResourceHandler.instance());

        if (ChemicalCompat.isAvailable()) {
            event.registerBlockEntity(ChemicalCompat.BLOCK, PHOTON_EMITTER_BE.get(), (be, side) -> be.chemicalHandler());
            event.registerBlockEntity(ChemicalCompat.BLOCK, PHOTON_RECEIVER_BE.get(), (be, side) -> EmptyResourceHandler.instance());
            LOGGER.info("Quantum Channeling: Mekanism detected — chemical routing active.");
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        // Apply server-side charging disables to every saved channel now that the overworld is
        // available, so an admin-disabled slot is cleared from every channel mask once and persists.
        ChannelData.get(event.getServer()).applyChargingSlotConfig();
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        ChannelData.get(event.getServer()).tickCharging(event.getServer());
    }
}
