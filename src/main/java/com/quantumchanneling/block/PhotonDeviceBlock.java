package com.quantumchanneling.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A wall-mountable photon device (emitter, receiver, storage): the {@link PhotonShape} core with
 * per-side connector arms. {@code FACING} is the clicked face at placement.
 */
public abstract class PhotonDeviceBlock extends ChannelDeviceBlock {
    protected PhotonDeviceBlock(Properties properties) {
        super(properties);
        registerDefaultState(PhotonShape.defaultStateOff(stateDefinition.any()));
    }

    /** Whether arms grow toward routable neighbours. Storage only talks to the channel, so it doesn't. */
    protected boolean showsConnections() { return true; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        PhotonShape.registerProps(b);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Arms are filled in by onPlace once the world can be queried.
        return defaultBlockState().setValue(PhotonShape.FACING, ctx.getClickedFace());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide() || !showsConnections()) return;
        level.setBlock(pos, PhotonShape.refreshConnections(level, pos, state), Block.UPDATE_ALL);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (level.isClientSide() || !showsConnections()) return;
        BlockState updated = PhotonShape.refreshConnections(level, pos, state);
        if (updated != state) level.setBlock(pos, updated, Block.UPDATE_ALL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return PhotonShape.shape(state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(PhotonShape.FACING, rot.rotate(state.getValue(PhotonShape.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(PhotonShape.FACING)));
    }
}
