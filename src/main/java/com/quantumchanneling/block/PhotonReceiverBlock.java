package com.quantumchanneling.block;

import com.mojang.serialization.MapCodec;
import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class PhotonReceiverBlock extends PhotonDeviceBlock {
    public static final MapCodec<PhotonReceiverBlock> CODEC = simpleCodec(PhotonReceiverBlock::new);

    public PhotonReceiverBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<PhotonReceiverBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhotonReceiverBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return serverTicker(level, type, QuantumChanneling.PHOTON_RECEIVER_BE.get(), PhotonReceiverBlockEntity::serverTick);
    }
}
