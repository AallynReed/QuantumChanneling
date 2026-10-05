package com.quantumchanneling.block;

import com.mojang.serialization.MapCodec;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/** Any block that hosts a channel device: right-click opens the device menu. */
public abstract class ChannelDeviceBlock extends Block implements EntityBlock {
    protected ChannelDeviceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected abstract MapCodec<? extends ChannelDeviceBlock> codec();

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof ChannelBoundBlockEntity device) {
            device.openMenu(sp);
        }
        return InteractionResult.SUCCESS;
    }

    /** Server-side ticker when {@code actual} is {@code expected}; null on the client or for other types. */
    @SuppressWarnings("unchecked")
    protected static <E extends BlockEntity, A extends BlockEntity> @Nullable BlockEntityTicker<A> serverTicker(
            Level level, BlockEntityType<A> actual, BlockEntityType<E> expected, BiConsumer<E, ServerLevel> tick) {
        if (level.isClientSide() || actual != expected) return null;
        return (lvl, pos, state, be) -> tick.accept((E) be, (ServerLevel) lvl);
    }
}
