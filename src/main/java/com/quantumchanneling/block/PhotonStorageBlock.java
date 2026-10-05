package com.quantumchanneling.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.PhotonStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Photon Storage block — one of five tiers (Copper / Iron / Gold / Diamond / Emerald) with
 * increasing FE capacity. {@link #LEVEL} tracks fill in nine buckets (0..8) so the model can paint
 * fill cubes that grow as the buffer fills.
 *
 * <p>Storage exposes no energy capability — it only participates through the channel (see
 * {@link PhotonStorageBlockEntity}) — so it never grows connection arms.
 */
public class PhotonStorageBlock extends PhotonDeviceBlock {
    public static final MapCodec<PhotonStorageBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            propertiesCodec(),
            Codec.intRange(1, 5).fieldOf("tier").forGetter(PhotonStorageBlock::getTier)
    ).apply(i, PhotonStorageBlock::new));

    /** 0 = empty, 8 = full — 9 buckets give 12.5%-granular fill visualization on the model. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 8);

    private final int tier;

    public PhotonStorageBlock(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(LEVEL, 0));
    }

    @Override
    protected MapCodec<PhotonStorageBlock> codec() { return CODEC; }

    /** 1..5. Capacity for this tier comes from {@link com.quantumchanneling.ServerConfig#storageCapacities}. */
    public int getTier() { return tier; }

    @Override
    protected boolean showsConnections() { return false; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(LEVEL);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhotonStorageBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return serverTicker(level, type, QuantumChanneling.PHOTON_STORAGE_BE.get(), PhotonStorageBlockEntity::serverTick);
    }
}
