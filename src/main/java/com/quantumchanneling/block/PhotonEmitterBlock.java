package com.quantumchanneling.block;

import com.mojang.serialization.MapCodec;
import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

// No animateTick — the BER provides the idle animation (pulsing glow + flowing beams); particles on
// top of the additive glow made the block read as dusty rather than energized.
public class PhotonEmitterBlock extends PhotonDeviceBlock {
    public static final MapCodec<PhotonEmitterBlock> CODEC = simpleCodec(PhotonEmitterBlock::new);

    public PhotonEmitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<PhotonEmitterBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhotonEmitterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return serverTicker(level, type, QuantumChanneling.PHOTON_EMITTER_BE.get(), PhotonEmitterBlockEntity::serverTick);
    }
}
