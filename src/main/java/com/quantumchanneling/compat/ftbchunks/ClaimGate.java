package com.quantumchanneling.compat.ftbchunks;

import com.quantumchanneling.client.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

/**
 * Claim-system gate for FTB Chunks. Consulted by device-mutation packets — when a device sits in a
 * claimed chunk, only members of the owning team may edit it. When FTB Chunks isn't loaded, or the
 * device is in unclaimed wilderness, the gate allows everything.
 *
 * <p>The lookup is reflective so this class compiles without a hard FTB dependency. It <b>fails
 * open</b> on any reflective hiccup or when membership can't be determined — the channel's own
 * USER/ADMIN permission system is the primary protection, and locking a claim owner out of their
 * own device is far worse than the secondary claim gate occasionally allowing an edit.
 */
public final class ClaimGate {
    private ClaimGate() {}

    private static volatile boolean reflectionPrepared = false;
    private static Method apiMethod;          // FTBChunksAPI.api()
    private static Method getManagerMethod;   // API -> getManager
    private static Method getChunkMethod;     // ClaimedChunkManager#getChunk(ChunkDimPos)
    private static Method getTeamDataMethod;  // ClaimedChunk#getTeamData
    private static Constructor<?> ctorKeyIntInt; // ChunkDimPos(ResourceKey, int, int)
    private static Constructor<?> ctorKeyChunkPos; // ChunkDimPos(ResourceKey, ChunkPos)

    /** True when {@code player} may edit the device at {@code pos}. Fails open on any hiccup. */
    public static boolean canEditAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null) return false;
        if (!Compat.ftbChunksLoaded()) return true;   // mod absent
        if (player.hasPermissions(2)) return true;    // OP bypass

        prepareReflectionIfNeeded();
        if (apiMethod == null) return true;            // surface changed → permissive

        try {
            Object api = apiMethod.invoke(null);
            if (api == null) return true;
            Object manager = getManagerMethod.invoke(api);
            if (manager == null) return true;
            Object chunkDimPos = newChunkDimPos(level, pos);
            if (chunkDimPos == null) return true;
            Object chunk = getChunkMethod.invoke(manager, chunkDimPos);
            if (chunk == null) return true;            // wilderness
            Object teamData = getTeamDataMethod.invoke(chunk);
            if (teamData == null) return true;
            Boolean member = isMemberOf(teamData, player.getUUID());
            // Undeterminable membership → allow (never lock the owner out of their own claim).
            return member == null || member;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * True when the chunk containing {@code pos} is claimed by any FTB Chunks team. False when FTB
     * Chunks is absent or on any reflective hiccup. Used by area effects (e.g. the Star Shaper's
     * collapse burst) to spare claimed builds. Callers should memoize per-chunk — this reflects.
     */
    public static boolean isChunkClaimed(ServerLevel level, BlockPos pos) {
        if (!Compat.ftbChunksLoaded()) return false;
        prepareReflectionIfNeeded();
        if (apiMethod == null) return false;
        try {
            Object api = apiMethod.invoke(null);
            if (api == null) return false;
            Object manager = getManagerMethod.invoke(api);
            if (manager == null) return false;
            Object chunkDimPos = newChunkDimPos(level, pos);
            if (chunkDimPos == null) return false;
            return getChunkMethod.invoke(manager, chunkDimPos) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Asks the claim's team object whether {@code pid} is a member. null = couldn't determine. */
    private static Boolean isMemberOf(Object team, UUID pid) {
        for (String m : new String[]{"isMember", "isTeamMember"}) {
            try {
                Method mm = team.getClass().getMethod(m, UUID.class);
                Object r = mm.invoke(team, pid);
                if (r instanceof Boolean b) return b;
            } catch (Throwable ignored) {}
        }
        for (String m : new String[]{"getMembers", "getMemberIds"}) {
            try {
                Method gm = team.getClass().getMethod(m);
                Object r = gm.invoke(team);
                if (r instanceof Collection<?> c) return c.contains(pid);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object newChunkDimPos(ServerLevel level, BlockPos pos) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        try {
            if (ctorKeyIntInt != null) return ctorKeyIntInt.newInstance(level.dimension(), cx, cz);
            if (ctorKeyChunkPos != null) return ctorKeyChunkPos.newInstance(level.dimension(), new ChunkPos(cx, cz));
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
                Class<?> chunkDimPosClass = Class.forName("dev.ftb.mods.ftblibrary.math.ChunkDimPos");
                // Constructor shape varies across FTB Library builds — probe both known forms.
                try { ctorKeyIntInt = chunkDimPosClass.getConstructor(
                        net.minecraft.resources.ResourceKey.class, int.class, int.class); }
                catch (NoSuchMethodException ignored) {}
                try { ctorKeyChunkPos = chunkDimPosClass.getConstructor(
                        net.minecraft.resources.ResourceKey.class, ChunkPos.class); }
                catch (NoSuchMethodException ignored) {}
                getChunkMethod = managerClass.getMethod("getChunk", chunkDimPosClass);
                Class<?> claimedChunkClass = getChunkMethod.getReturnType();
                getTeamDataMethod = claimedChunkClass.getMethod("getTeamData");
                if (ctorKeyIntInt == null && ctorKeyChunkPos == null) apiMethod = null;
            } catch (Throwable ignored) {
                apiMethod = null;   // any miss disables the gate, leaving it permissive
            }
            reflectionPrepared = true;
        }
    }
}
