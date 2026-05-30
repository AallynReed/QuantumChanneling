package com.quantumchanneling;

import com.mojang.logging.LogUtils;
import com.quantumchanneling.block.PhotonEmitterBlock;
import com.quantumchanneling.block.PhotonReceiverBlock;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import com.quantumchanneling.item.QuantumTunerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
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
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Item> QUANTUM_CORE = ITEMS.register("quantum_core",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> QUANTUM_TUNER = ITEMS.register("quantum_tuner",
            () -> new QuantumTunerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Block> PHOTON_EMITTER = BLOCKS.register("photon_emitter",
            () -> new PhotonEmitterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Item> PHOTON_EMITTER_ITEM = ITEMS.register("photon_emitter",
            () -> new BlockItem(PHOTON_EMITTER.get(), new Item.Properties()));

    public static final RegistryObject<Block> PHOTON_RECEIVER = BLOCKS.register("photon_receiver",
            () -> new PhotonReceiverBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Item> PHOTON_RECEIVER_ITEM = ITEMS.register("photon_receiver",
            () -> new BlockItem(PHOTON_RECEIVER.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<PhotonEmitterBlockEntity>> PHOTON_EMITTER_BE =
            BLOCK_ENTITIES.register("photon_emitter",
                    () -> BlockEntityType.Builder.of(PhotonEmitterBlockEntity::new, PHOTON_EMITTER.get()).build(null));

    public static final RegistryObject<BlockEntityType<PhotonReceiverBlockEntity>> PHOTON_RECEIVER_BE =
            BLOCK_ENTITIES.register("photon_receiver",
                    () -> BlockEntityType.Builder.of(PhotonReceiverBlockEntity::new, PHOTON_RECEIVER.get()).build(null));

    public static final RegistryObject<CreativeModeTab> QUANTUM_TAB = CREATIVE_MODE_TABS.register("quantum_tab",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(Component.translatable("itemGroup.quantumchanneling"))
                    .icon(() -> QUANTUM_CORE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(QUANTUM_CORE.get());
                        output.accept(QUANTUM_TUNER.get());
                        output.accept(PHOTON_EMITTER_ITEM.get());
                        output.accept(PHOTON_RECEIVER_ITEM.get());
                    }).build());

    public QuantumChanneling(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Quantum Channeling: common setup complete (photon cost per op = {} PE, cross-dim = {})",
                Config.photonCostPerOperation, Config.allowCrossDimension);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Quantum Channeling: server starting");
    }
}
