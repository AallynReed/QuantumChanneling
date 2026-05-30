package com.quantumchanneling.block;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class PhotonEmitterBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final Map<Direction, VoxelShape> SHAPES = Util.make(new EnumMap<>(Direction.class), m -> {
        m.put(Direction.UP,    Block.box(2, 0, 2, 14, 3, 14));
        m.put(Direction.DOWN,  Block.box(2, 13, 2, 14, 16, 14));
        m.put(Direction.NORTH, Block.box(2, 2, 13, 14, 14, 16));
        m.put(Direction.SOUTH, Block.box(2, 2, 0, 14, 14, 3));
        m.put(Direction.EAST,  Block.box(13, 2, 2, 16, 14, 14));
        m.put(Direction.WEST,  Block.box(0, 2, 2, 3, 14, 14));
    });

    public PhotonEmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhotonEmitterBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return tickerFor(type, QuantumChanneling.PHOTON_EMITTER_BE.get(),
                (lvl, p, s, be) -> be.serverTick((ServerLevel) lvl));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof PhotonEmitterBlockEntity emitter && player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, emitter, buf -> {
                buf.writeBlockPos(pos);
                buf.writeUtf(emitter.resolveChannelName());
                buf.writeUtf(emitter.resolveChannelOwner());
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
