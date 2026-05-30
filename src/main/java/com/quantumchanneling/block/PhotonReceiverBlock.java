package com.quantumchanneling.block;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class PhotonReceiverBlock extends Block implements EntityBlock {
    public PhotonReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(PhotonShape.defaultStateOff(stateDefinition.any()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        PhotonShape.registerProps(b);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(PhotonShape.FACING, ctx.getClickedFace());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide) return;
        level.setBlock(pos, PhotonShape.refreshConnections(level, pos, state, PhotonShape.ConnectionMode.SINKS), 3);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean moving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, moving);
        if (level.isClientSide) return;
        BlockState updated = PhotonShape.refreshConnections(level, pos, state, PhotonShape.ConnectionMode.SINKS);
        if (updated != state) level.setBlock(pos, updated, 3);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return PhotonShape.shape(state);
    }

    /** Red accent particles drifting around the orb — receiver counterpart to the emitter's blue. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(8) != 0) return;
        Vector3f color = new Vector3f(1.0f, 0.38f, 0.38f);    // receiver accent (red)
        DustParticleOptions opts = new DustParticleOptions(color, 1.0f);
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.55;
        double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.45;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.55;
        level.addParticle(opts, x, y, z, 0, 0.015, 0);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(PhotonShape.FACING, rot.rotate(state.getValue(PhotonShape.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(PhotonShape.FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhotonReceiverBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return tickerFor(type, QuantumChanneling.PHOTON_RECEIVER_BE.get(),
                (lvl, p, s, be) -> be.serverTick((ServerLevel) lvl));
    }

    @Override
    public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!oldState.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof com.quantumchanneling.blockentity.ChannelBoundBlockEntity bound) {
            bound.unbindFromChannelOnBreak();
        }
        super.onRemove(oldState, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof PhotonReceiverBlockEntity receiver && player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, receiver, buf -> {
                buf.writeBlockPos(pos);
                java.util.UUID id = receiver.getChannelId();
                buf.writeBoolean(id != null);
                if (id != null) buf.writeUUID(id);
                buf.writeUtf(receiver.resolveChannelName());
                buf.writeUtf(receiver.resolveChannelOwner());
                buf.writeUtf(receiver.getCustomName());
                buf.writeLong(0L);
            });
        }
        return InteractionResult.CONSUME;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> tickerFor(
            BlockEntityType<A> actual, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return actual == expected ? (BlockEntityTicker<A>) ticker : null;
    }
}
