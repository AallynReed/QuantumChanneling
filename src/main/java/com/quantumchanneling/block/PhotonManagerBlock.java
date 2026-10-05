package com.quantumchanneling.block;

import com.mojang.serialization.MapCodec;
import com.quantumchanneling.blockentity.PhotonManagerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Photon Manager — a passive gate that, when present on a channel and loaded, enables wireless
 * charging for that channel's subscribers. Doesn't transmit energy, doesn't face a wall, doesn't
 * grow connection arms.
 */
public class PhotonManagerBlock extends ChannelDeviceBlock {
    public static final MapCodec<PhotonManagerBlock> CODEC = simpleCodec(PhotonManagerBlock::new);

    /** Slightly inset from a full cube so it reads as "machine-like" rather than terrain. */
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 15, 15);

    public PhotonManagerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<PhotonManagerBlock> codec() { return CODEC; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhotonManagerBlockEntity(pos, state);
    }
}
