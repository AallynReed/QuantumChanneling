package com.quantumchanneling.channel;

import net.minecraft.core.BlockPos;
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
}
