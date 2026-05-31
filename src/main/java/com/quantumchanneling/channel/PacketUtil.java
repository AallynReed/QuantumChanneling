package com.quantumchanneling.channel;

import com.quantumchanneling.compat.ftbchunks.ClaimGate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Shared range guard for device-targeted packets. */
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
     * Range + claim check rolled together. Use this on packets that mutate a device — both reach
     * and (if FTB Chunks is installed and the chunk is claimed) team ownership are required.
     * Falls back to {@link #withinReach} alone when FTB Chunks is absent.
     */
    static boolean canEdit(ServerPlayer player, BlockPos pos) {
        if (!withinReach(player, pos)) return false;
        if (!(player.level() instanceof ServerLevel sl)) return false;
        return ClaimGate.canEditAt(player, sl, pos);
    }
}
