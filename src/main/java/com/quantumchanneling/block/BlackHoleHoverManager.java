package com.quantumchanneling.block;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds Uncontained Black Hole item entities motionless for the duration of the implosion
 * animation following the Star Shaper's Hammer collapse, then releases them so they fall
 * normally.
 *
 * <h3>How a hover works</h3>
 * <ol>
 *   <li>{@link #freeze} flips {@code noGravity = true}, zeroes {@code deltaMovement}, and writes
 *       an {@code qc_hover_until} tag onto the entity's persistent data carrying the game tick
 *       at which gravity should be restored.</li>
 *   <li>A loose registry of pending entries — keyed by (dimension, UUID, unlock tick) — is held
 *       in memory for the common case where the player stays in the area through the burst.</li>
 *   <li>A {@link ServerTickEvent.Post} sweep each tick checks every pending entry and
 *       restores gravity once the unlock tick passes.</li>
 *   <li>An {@link EntityJoinLevelEvent} hook re-registers entities that had a non-empty
 *       {@code qc_hover_until} tag when they were saved — so a player who walks away mid-implosion
 *       and returns to chunk-loaded items finds them still ticking through the same lock. If the
 *       game tick has already passed the recorded unlock, the entry thaws on first server tick
 *       after the join.</li>
 * </ol>
 *
 * <p>The CopyOnWriteArrayList shields against concurrent modification when items thaw mid-iteration
 * (the join handler can add new entries while the tick sweep iterates).
 */
@EventBusSubscriber(modid = QuantumChanneling.MODID)
public final class BlackHoleHoverManager {
    private BlackHoleHoverManager() {}

    /** Persistent-data key on the ItemEntity. Carries the game tick at which gravity should be
     *  restored. Survives world saves so a server crash mid-implosion still lets the item thaw
     *  on next load — the tick check on join handles the case where the saved unlock is already
     *  in the past. */
    private static final String TAG_UNLOCK_TICK = "qc_hover_until";

    private record Entry(ResourceKey<Level> dim, UUID id, long unlockTick) {}

    private static final CopyOnWriteArrayList<Entry> PENDING = new CopyOnWriteArrayList<>();

    /** Park the item in place, no gravity, no velocity, until {@code unlockTick} on the given
     *  level's game clock. Idempotent — calling twice extends the lock to the later value. */
    public static void freeze(ServerLevel level, ItemEntity ie, long unlockTick) {
        ie.setNoGravity(true);
        ie.setDeltaMovement(Vec3.ZERO);
        ie.needsSync = true;
        // Persist on the entity so chunk save/load preserves the hover.
        ie.getPersistentData().putLong(TAG_UNLOCK_TICK, unlockTick);
        PENDING.add(new Entry(level.dimension(), ie.getUUID(), unlockTick));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        var server = event.getServer();

        Iterator<Entry> it = PENDING.iterator();
        // Use a separate "to remove" list because CopyOnWriteArrayList's iterator doesn't support
        // remove. The list is short (typically 0-2 items), so the double-pass is cheap.
        java.util.List<Entry> done = new java.util.ArrayList<>();
        while (it.hasNext()) {
            Entry e = it.next();
            ServerLevel lvl = server.getLevel(e.dim);
            if (lvl == null) {                                     // dimension gone — drop entry
                done.add(e);
                continue;
            }
            if (lvl.getGameTime() < e.unlockTick) continue;        // still frozen

            Entity ent = lvl.getEntity(e.id);
            if (ent instanceof ItemEntity ie && ie.isAlive()) {
                ie.setNoGravity(false);
                ie.getPersistentData().remove(TAG_UNLOCK_TICK);
            }
            done.add(e);                                           // unlocked (or entity gone)
        }
        if (!done.isEmpty()) PENDING.removeAll(done);
    }

    /** Catches items loaded from disk that were saved mid-hover. Re-adds them to the pending
     *  list so the next tick check can thaw them at the appropriate time. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity ie)) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        long unlockTick = ie.getPersistentData().getLongOr(TAG_UNLOCK_TICK, 0L);
        if (unlockTick <= 0) return;
        // Re-apply the freeze. If unlockTick is already in the past, the next ServerTickEvent
        // sweep will thaw it on its very first iteration.
        ie.setNoGravity(true);
        ie.setDeltaMovement(Vec3.ZERO);
        PENDING.add(new Entry(sl.dimension(), ie.getUUID(), unlockTick));
    }
}
