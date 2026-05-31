package com.quantumchanneling.block;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.PhotonStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Photon Storage block — one of five tiers (Copper / Iron / Gold / Diamond / Emerald) with
 * increasing FE capacity. The {@link #LEVEL} state property tracks fill in five buckets (0..4) so
 * the model can paint visible fill cubes that grow as the buffer fills up.
 *
 * <p>Storage does <em>not</em> expose an {@link net.minecraftforge.energy.IEnergyStorage} capability
 * to adjacent blocks — it only participates via the channel (see {@link PhotonStorageBlockEntity}).
 * That's also why this block does not run a connection scan: no arms ever appear.
 */
public class PhotonStorageBlock extends Block implements EntityBlock {
    /** 0 = empty, 8 = full — 9 buckets give 12.5%-granular fill visualization on the model. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 8);

    private final int tier;

    public PhotonStorageBlock(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(PhotonShape.defaultStateOff(stateDefinition.any()).setValue(LEVEL, 0));
    }

    /** 1..5. Capacity for this tier is read from {@link com.quantumchanneling.Config#storageCapacities}. */
    public int getTier() { return tier; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        PhotonShape.registerProps(b);
        b.add(LEVEL);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(PhotonShape.FACING, ctx.getClickedFace());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return PhotonShape.shape(state);
    }

    // No animateTick — the BER (PhotonNodeRenderer) provides the visual idle animation via the
    // GLSL shader with the per-tier accent color picked by PhotonAccent. The fill-level model
    // bars still glow emissively to indicate stored amount; dust particles would compete with
    // the shader for visual attention.

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
        return new PhotonStorageBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return tickerFor(type, QuantumChanneling.PHOTON_STORAGE_BE.get(),
                (lvl, p, s, be) -> be.serverTick((ServerLevel) lvl));
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> tickerFor(
            BlockEntityType<A> actual, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return actual == expected ? (BlockEntityTicker<A>) ticker : null;
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
        if (be instanceof PhotonStorageBlockEntity storage && player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, storage, buf -> {
                buf.writeBlockPos(pos);
                java.util.UUID id = storage.getChannelId();
                buf.writeBoolean(id != null);
                if (id != null) buf.writeUUID(id);
                buf.writeUtf(storage.resolveChannelName());
                buf.writeUtf(storage.resolveChannelOwner());
                buf.writeUtf(storage.getCustomName());
                buf.writeLong(storage.getCapacity());
            });
        }
        return InteractionResult.CONSUME;
    }
}
