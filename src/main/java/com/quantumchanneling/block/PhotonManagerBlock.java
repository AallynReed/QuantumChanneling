package com.quantumchanneling.block;

import com.quantumchanneling.blockentity.PhotonManagerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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
import org.joml.Vector3f;

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

    /**
     * Manager spawns more frequent particles than emitters/receivers — it's "always on" as the
     * channel's brain. Alternates gold (control surface) and blue (brain core) for a richer feel.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) return;
        boolean blueTint = random.nextBoolean();
        Vector3f color = blueTint
                ? new Vector3f(0.31f, 0.63f, 1.0f)        // manager core (blue brain)
                : new Vector3f(1.0f, 0.82f, 0.50f);       // manager belt (gold control surface)
        DustParticleOptions opts = new DustParticleOptions(color, 1.0f);
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
        double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
        level.addParticle(opts, x, y, z, 0, 0.02, 0);
    }

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
