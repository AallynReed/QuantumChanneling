package com.quantumchanneling.block;

import com.quantumchanneling.blockentity.PhotonManagerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Photon Manager — a passive gate that, when present on a channel and loaded, enables wireless
 * charging for that channel's subscribers. Doesn't transmit energy to neighbours, doesn't need to
 * face a wall, doesn't grow connection arms. Just a full-cube block you drop somewhere convenient.
 */
public class PhotonManagerBlock extends Block implements EntityBlock {
    /** Slightly inset from a full cube so it reads as "machine-like" rather than terrain. */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 15, 15);

    public PhotonManagerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    // No animateTick — the BER (PhotonNodeRenderer) now provides the visual idle animation via
    // the GLSL black-hole + accretion shader, with manager-specific gold accent color picked by
    // PhotonAccent.colorFor. Dust particles would compete with the shader for visual attention
    // and don't match the same visual language across the device family.

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhotonManagerBlockEntity(pos, state);
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
        if (be instanceof PhotonManagerBlockEntity mgr && player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, mgr, buf -> {
                buf.writeBlockPos(pos);
                java.util.UUID id = mgr.getChannelId();
                buf.writeBoolean(id != null);
                if (id != null) buf.writeUUID(id);
                buf.writeUtf(mgr.resolveChannelName());
                buf.writeUtf(mgr.resolveChannelOwner());
                buf.writeUtf(mgr.getCustomName());
                buf.writeLong(0L);
            });
        }
        return InteractionResult.CONSUME;
    }
}
