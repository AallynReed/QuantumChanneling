package com.quantumchanneling.compat.ftbchunks;

import com.quantumchanneling.client.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * Claim-system gate for FTB Chunks. Consulted by packet handlers that mutate device state — when
 * a device sits in a claimed chunk, only the claim owner / team members can edit it. When FTB
 * Chunks isn't loaded, or the device is in unclaimed wilderness, all operations are allowed.
 *
 * <p>The lookup is reflective so this class compiles cleanly without a hard FTB Chunks dependency.
 * If FTB Chunks ships an API breaking change, the gate fails-open (allowing the operation) rather
 * than locking everyone out — gating the entire mod on a third-party API surface would be too
 * brittle for production. Players + admins still see the friendly UI; misuse falls back on the
 * channel's own permission system.
 */
public final class ClaimGate {
    private ClaimGate() {}

    /** Cached reflective handles — looked up once on first use. {@code null} after a failed lookup. */
    private static volatile boolean reflectionPrepared = false;
    private static Method apiMethod;       // FTBChunksAPI.api()
    private static Method getManagerMethod; // FTBChunksAPI -> getManager
    private static Method getChunkMethod;   // ClaimedChunkManager#getChunk(ChunkDimPos)
    private static Method getTeamDataMethod; // ClaimedChunk#getTeamData
    private static Method getTeamIdMethod;   // TeamData / Team #getTeamId or #getId
    private static Class<?> chunkDimPosClass; // dev.ftb.mods.ftblibrary.math.ChunkDimPos
    private static java.lang.reflect.Constructor<?> chunkDimPosCtor;

    /**
     * Returns true when {@code player} is allowed to edit / mutate the device at {@code pos}.
     * Fails-open on any reflective hiccup.
     */
    public static boolean canEditAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null) return false;
        if (!Compat.ftbChunksLoaded()) return true;          // mod absent
        if (player.hasPermissions(2)) return true;            // OP bypass

        prepareReflectionIfNeeded();
        if (apiMethod == null) return true;                   // surface changed → permissive

        try {
            Object api = apiMethod.invoke(null);
            if (api == null) return true;
            Object manager = getManagerMethod.invoke(api);
            if (manager == null) return true;
            Object chunkDimPos = chunkDimPosCtor.newInstance(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
            Object chunk = getChunkMethod.invoke(manager, chunkDimPos);
            if (chunk == null) return true;                   // wilderness
            Object teamData = getTeamDataMethod.invoke(chunk);
            if (teamData == null) return true;
            Object teamId = getTeamIdMethod.invoke(teamData);
            if (!(teamId instanceof java.util.UUID claimTeam)) return true;

            // Membership check — the team manager's API has a per-version shape; the simplest
            // permissive heuristic is "is the player's known team id equal to the claim's team id".
            // Players in different teams will be rejected. If the player has no team, we treat
            // them as not-a-member and reject.
            java.util.UUID playerTeam = lookupPlayerTeam(player);
            return playerTeam != null && playerTeam.equals(claimTeam);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static java.util.UUID lookupPlayerTeam(ServerPlayer player) {
        // FTB Teams' API path varies between versions. The simplest fallback is to read the
        // player's persistent NBT key ftbteams.team — present on every player ever assigned to a
        // team. When this fails the player is treated as teamless.
        try {
            var persisted = player.getPersistentData();
            if (persisted.contains("ftbteams.team")) {
                var idStr = persisted.getString("ftbteams.team");
                if (!idStr.isEmpty()) return java.util.UUID.fromString(idStr);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void prepareReflectionIfNeeded() {
        if (reflectionPrepared) return;
        synchronized (ClaimGate.class) {
            if (reflectionPrepared) return;
            try {
                Class<?> apiClass = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
                apiMethod = apiClass.getMethod("api");
                Class<?> ifaceClass = apiMethod.getReturnType();
                getManagerMethod = ifaceClass.getMethod("getManager");
                Class<?> managerClass = getManagerMethod.getReturnType();
                chunkDimPosClass = Class.forName("dev.ftb.mods.ftblibrary.math.ChunkDimPos");
                chunkDimPosCtor = chunkDimPosClass.getConstructor(
                        net.minecraft.resources.ResourceKey.class, int.class, int.class);
                getChunkMethod = managerClass.getMethod("getChunk", chunkDimPosClass);
                Class<?> claimedChunkClass = getChunkMethod.getReturnType();
                getTeamDataMethod = claimedChunkClass.getMethod("getTeamData");
                Class<?> teamDataClass = getTeamDataMethod.getReturnType();
                // Different versions expose getTeamId vs getId — try both, accept first hit.
                Method teamIdMethod;
                try { teamIdMethod = teamDataClass.getMethod("getTeamId"); }
                catch (NoSuchMethodException nse) { teamIdMethod = teamDataClass.getMethod("getId"); }
                getTeamIdMethod = teamIdMethod;
            } catch (Throwable ignored) {
                apiMethod = null;   // any miss disables the gate, leaving it permissive
            }
            reflectionPrepared = true;
        }
    }
}
