package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.compat.ftbchunks.ClaimGate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Shared authorization guard for device-targeted packets. */
final class PacketUtil {
    private PacketUtil() {}

    /** 8-block reach matches the void-filter / per-device-enable packets. */
    static boolean withinReach(ServerPlayer player, BlockPos pos) {
        double dx = pos.getX() + 0.5 - player.getX();
        double dy = pos.getY() + 0.5 - player.getY();
        double dz = pos.getZ() + 0.5 - player.getZ();
        return dx * dx + dy * dy + dz * dz <= 64.0;
    }

    /**
     * Range + claim check rolled together — both reach and (if FTB Chunks is installed and the chunk
     * is claimed) team ownership are required. Falls back to {@link #withinReach} alone when FTB
     * Chunks is absent.
     */
    static boolean canEdit(ServerPlayer player, BlockPos pos) {
        if (!withinReach(player, pos)) return false;
        if (!(player.level() instanceof ServerLevel sl)) return false;
        return ClaimGate.canEditAt(player, sl, pos);
    }

    /**
     * Resolves a device the player is allowed to <b>configure</b>. Requires reach, the FTB-Chunks
     * claim check, the target actually being a Quantum device, and — when the device is bound to a
     * channel — ADMIN/owner rights on that channel. Unbound devices need only reach + claim (no
     * owner exists to protect yet). Returns {@code null} when any check fails; callers then no-op.
     */
    static @Nullable ChannelBoundBlockEntity manageableDevice(ServerPlayer player, BlockPos pos) {
        if (!canEdit(player, pos)) return null;
        if (!(player.level() instanceof ServerLevel sl)) return null;
        if (!(sl.getBlockEntity(pos) instanceof ChannelBoundBlockEntity bound)) return null;
        UUID channelId = bound.getChannelId();
        if (channelId != null) {
            QuantumChannel ch = ChannelData.get(sl.getServer()).getChannel(channelId);
            if (ch != null && !ch.canManage(player.getUUID())) return null;
        }
        return bound;
    }

    /**
     * Like {@link #manageableDevice} but requires only USER-level access (any channel member).
     * For actions a plain member may take on a bound device without ADMIN — e.g. a personal
     * charging subscription. Unbound devices need only reach + claim.
     */
    static @Nullable ChannelBoundBlockEntity usableDevice(ServerPlayer player, BlockPos pos) {
        if (!canEdit(player, pos)) return null;
        if (!(player.level() instanceof ServerLevel sl)) return null;
        if (!(sl.getBlockEntity(pos) instanceof ChannelBoundBlockEntity bound)) return null;
        UUID channelId = bound.getChannelId();
        if (channelId != null) {
            QuantumChannel ch = ChannelData.get(sl.getServer()).getChannel(channelId);
            if (ch != null && !ch.canUse(player.getUUID())) return null;
        }
        return bound;
    }
}
