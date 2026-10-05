package com.quantumchanneling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.quantumchanneling.QuantumChanneling;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Holds the active collapse effects spawned by the Star Shaper's Hammer when it crushes White
 * Dwarfs, and draws each one as a two-phase animation on a camera-facing billboard:
 *
 * <ol>
 *   <li><b>Supernova flash</b> ({@value #EXPLOSION_LIFETIME}s) — a white/gold light burst that
 *       pops to the blast radius, plus 12 white-with-rainbow-fringe refraction beams shooting
 *       outward. Additive, brilliant.</li>
 *   <li><b>Implosion vortex</b> ({@value #IMPLOSION_LIFETIME}s) — dark/dark-purple stretched
 *       blobs spiralling into a central void while a vacuum swirl rotates inward. Translucent
 *       (darkens the scene).</li>
 * </ol>
 *
 * <p>The render hook fires on {@link RenderLevelStageEvent.Stage#AFTER_TRANSLUCENT_BLOCKS} —
 * after the world is solid + translucent blocks have been drawn but before particles / GUI, so the
 * effect appears in front of opaque scenery without competing with hand-rendering or HUD elements.
 *
 * <p>Effects are stateful only on the client (no save/load); a server-side restart or chunk
 * reload simply drops any in-flight effects.
 */
@Mod.EventBusSubscriber(modid = QuantumChanneling.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class PhotonBurstRenderer {
    private PhotonBurstRenderer() {}

    /** A single burst — world position, max radius, accent color, spawn tick, source dimension. */
    private static final class Burst {
        final double x, y, z;
        final float maxRadius;
        final int r, g, b;
        final long spawnTick;
        final String dimension;

        Burst(double x, double y, double z, float maxRadius, int rgb, long spawnTick,
              String dimension) {
            this.x = x; this.y = y; this.z = z;
            this.maxRadius = maxRadius;
            this.r = (rgb >> 16) & 0xFF;
            this.g = (rgb >> 8)  & 0xFF;
            this.b = rgb & 0xFF;
            this.spawnTick = spawnTick;
            this.dimension = dimension;
        }
    }

    /** Phase 1 — the white/gold supernova flash. Match with the
     *  {@link com.quantumchanneling.block.StarShapersHammerBlock} UCB_HOVER_TICKS constant. */
    private static final float EXPLOSION_LIFETIME = 0.75f;
    /** Phase 2 — the dark/dark-purple implosion vortex. Follows immediately after the flash. */
    private static final float IMPLOSION_LIFETIME = 1.2f;
    /** Total effect lifetime — explosion plus implosion. Bursts are pruned past this point. */
    private static final float LIFETIME_SECONDS = EXPLOSION_LIFETIME + IMPLOSION_LIFETIME;
    /** Time at which the flash reaches its full {@code maxRadius}. Snappy — the supernova should
     *  pop out to the radius almost instantly, then linger and fade. */
    private static final float GROW_SECONDS = 0.18f;
    /** Ribbons extend this many times the burst radius past the central sphere. The user asked for
     *  "ribbons coming out a bit further like beams of light" — 1.6× past the burst edge gives a
     *  visible halo of beams without making the effect feel infinitely large. */
    private static final float RIBBON_REACH = 1.6f;
    /** Half-thickness of each ribbon. Scales with burst radius so a 30-block water burst's beams
     *  are thicker than the 10-block dry version's. */
    private static final float RIBBON_WIDTH_PER_RADIUS = 0.04f;
    /** Number of ribbons per burst. Distributed as a fixed (deterministic) set of 3D directions
     *  so every burst looks structurally the same — 8 horizontal beams covering the cardinal /
     *  ordinal axes plus 4 angled upward. */
    private static final Vector3f[] RIBBON_DIRECTIONS = buildRibbonDirections();

    private static Vector3f[] buildRibbonDirections() {
        Vector3f[] out = new Vector3f[12];
        // 8 horizontal — every 45° around the Y axis.
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8.0;
            out[i] = new Vector3f((float) Math.cos(a), 0.0f, (float) Math.sin(a));
        }
        // 4 angled upward at 35° elevation, offset 22.5° from the horizontal ones so they don't
        // overlap with the cardinal beams (would look like one fat beam instead of two distinct
        // rays).
        float upSin = (float) Math.sin(Math.toRadians(35));
        float upCos = (float) Math.cos(Math.toRadians(35));
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 2 * i / 4.0 + Math.PI / 8.0;
            out[8 + i] = new Vector3f(
                    (float) Math.cos(a) * upCos,
                    upSin,
                    (float) Math.sin(a) * upCos);
        }
        return out;
    }

    private static final List<Burst> ACTIVE = new ArrayList<>();

    /** Server-to-client packet handler entry. Schedules a new burst for rendering. */
    public static void addBurst(double x, double y, double z, float maxRadius, int rgb,
                                String dimension) {
        long tick = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime() : 0L;
        synchronized (ACTIVE) {
            ACTIVE.add(new Burst(x, y, z, maxRadius, rgb, tick, dimension));
        }
    }

    /** Drops every in-flight burst — called on client level/dimension unload so a burst can't
     *  linger across a dimension transition. */
    public static void clear() {
        synchronized (ACTIVE) {
            ACTIVE.clear();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (ACTIVE.isEmpty()) return;

        long currentTick = mc.level.getGameTime();
        float partialTick = event.getPartialTick();
        Camera camera = event.getCamera();
        var camPos = camera.getPosition();

        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource =
                mc.renderBuffers().bufferSource();
        VertexConsumer haloVc      = bufferSource.getBuffer(PhotonRenderTypes.PHOTON_BURST);
        VertexConsumer ribbonVc    = bufferSource.getBuffer(PhotonRenderTypes.PHOTON_RIBBON);
        VertexConsumer implosionVc = bufferSource.getBuffer(PhotonRenderTypes.PHOTON_IMPLOSION);

        String currentDim = mc.level.dimension().location().toString();

        // Iterate a snapshot — bursts can be added by the packet handler on a different thread.
        Burst[] snapshot;
        synchronized (ACTIVE) {
            snapshot = ACTIVE.toArray(new Burst[0]);
        }

        for (Burst b : snapshot) {
            if (!currentDim.equals(b.dimension)) continue;   // wrong dimension — pruned below
            float ageTicks = (currentTick - b.spawnTick) + partialTick;
            float ageSeconds = ageTicks / 20.0f;
            if (ageSeconds < 0.0f) continue;
            if (ageSeconds > LIFETIME_SECONDS) continue;

            if (ageSeconds < EXPLOSION_LIFETIME) {
                renderExplosion(b, ageSeconds, pose, camPos, camera, haloVc, ribbonVc);
            } else {
                float implosionAge = ageSeconds - EXPLOSION_LIFETIME;
                renderImplosion(b, implosionAge, pose, camPos, camera, implosionVc);
            }
        }

        bufferSource.endBatch(PhotonRenderTypes.PHOTON_BURST);
        bufferSource.endBatch(PhotonRenderTypes.PHOTON_RIBBON);
        bufferSource.endBatch(PhotonRenderTypes.PHOTON_IMPLOSION);

        // Garbage-collect expired and wrong-dimension bursts.
        synchronized (ACTIVE) {
            Iterator<Burst> it = ACTIVE.iterator();
            while (it.hasNext()) {
                Burst b = it.next();
                float ageSeconds = ((currentTick - b.spawnTick) + partialTick) / 20.0f;
                if (ageSeconds > LIFETIME_SECONDS || !currentDim.equals(b.dimension)) it.remove();
            }
        }
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    /**
     * Phase 1 — the white/gold supernova flash + 12 radial refraction beams. The flash pops out
     * to {@code maxRadius} on a snappy eased curve, holds bright, then fades over the back portion
     * of the explosion lifetime. The beams extend past the flash edge and fade with it.
     */
    private static void renderExplosion(Burst b, float ageSeconds,
                                        PoseStack pose,
                                        net.minecraft.world.phys.Vec3 camPos, Camera camera,
                                        VertexConsumer haloVc, VertexConsumer ribbonVc) {
        float growT = Math.min(1.0f, ageSeconds / GROW_SECONDS);
        float radius = b.maxRadius * easeOutCubic(growT);

        float alpha = 1.0f;
        float fadeStart = 0.45f * EXPLOSION_LIFETIME;
        if (ageSeconds > fadeStart) {
            alpha = 1.0f - ((ageSeconds - fadeStart) / (EXPLOSION_LIFETIME - fadeStart));
            alpha = Math.max(0.0f, alpha);
        }
        int alphaByte = (int) (alpha * 255);
        if (alphaByte <= 1) return;

        // ---- central expanding halo ----
        pose.pushPose();
        pose.translate(b.x - camPos.x, b.y - camPos.y, b.z - camPos.z);
        Quaternionf cam = camera.rotation();
        pose.mulPose(cam);
        Matrix4f m = pose.last().pose();

        float h = radius;
        haloVc.vertex(m, -h, -h, 0).color(b.r, b.g, b.b, alphaByte).uv(0.0f, 0.0f).endVertex();
        haloVc.vertex(m,  h, -h, 0).color(b.r, b.g, b.b, alphaByte).uv(1.0f, 0.0f).endVertex();
        haloVc.vertex(m,  h,  h, 0).color(b.r, b.g, b.b, alphaByte).uv(1.0f, 1.0f).endVertex();
        haloVc.vertex(m, -h,  h, 0).color(b.r, b.g, b.b, alphaByte).uv(0.0f, 1.0f).endVertex();
        pose.popPose();

        // ---- light ribbons ----
        float ribbonLength = radius * RIBBON_REACH;
        if (ribbonLength > 0.05f) {
            float ribbonWidth = b.maxRadius * RIBBON_WIDTH_PER_RADIUS;
            renderRibbons(ribbonVc, pose, b, camPos, camera, ribbonLength, ribbonWidth, alphaByte);
        }
    }

    /**
     * Phase 2 — the dark implosion vortex. ALL of the inward motion (stretched blobs falling in,
     * the vacuum swirl) lives in the {@code photon_implosion} shader, which animates on GameTime.
     * The billboard itself is a stable full-radius canvas; this method only drives the fade
     * envelope so the vortex doesn't pop in or out:
     *
     * <ul>
     *   <li>{@code 0..0.12} of lifetime: alpha ramps 0 → 1 (establish).</li>
     *   <li>middle: hold at full alpha while the shader does the swirling + infall.</li>
     *   <li>last 0.35: alpha fades 1 → 0 as the singularity finishes consuming everything.</li>
     * </ul>
     *
     * <p>A small establishing grow on the radius (0.82 → 1.0 over the first 0.12 of life) avoids a
     * hard pop when the flash hands off to the vortex; after that the radius holds so the blobs
     * have room to fall the full distance.
     */
    private static void renderImplosion(Burst b, float age,
                                        PoseStack pose,
                                        net.minecraft.world.phys.Vec3 camPos, Camera camera,
                                        VertexConsumer vc) {
        float t = Math.min(1.0f, age / IMPLOSION_LIFETIME);

        // Radius: brief establishing grow, then hold full.
        float radius = b.maxRadius * (t < 0.12f ? (0.82f + 0.18f * (t / 0.12f)) : 1.0f);

        // Alpha: ramp in, hold, fade out.
        float alpha;
        if (t < 0.12f) {
            alpha = t / 0.12f;
        } else if (t < 0.65f) {
            alpha = 1.0f;
        } else {
            alpha = 1.0f - (t - 0.65f) / 0.35f;
            alpha = Math.max(0.0f, alpha);
        }
        int alphaByte = (int) (alpha * 255);
        if (alphaByte <= 1) return;

        pose.pushPose();
        pose.translate(b.x - camPos.x, b.y - camPos.y, b.z - camPos.z);
        Quaternionf cam = camera.rotation();
        pose.mulPose(cam);
        Matrix4f m = pose.last().pose();

        // The implosion is fully coloured on the shader side (black → dark-purple). White vertex
        // color so ColorModulator doesn't tint the shader's palette; alpha carries the fade.
        float h = radius;
        vc.vertex(m, -h, -h, 0).color(255, 255, 255, alphaByte).uv(0.0f, 0.0f).endVertex();
        vc.vertex(m,  h, -h, 0).color(255, 255, 255, alphaByte).uv(1.0f, 0.0f).endVertex();
        vc.vertex(m,  h,  h, 0).color(255, 255, 255, alphaByte).uv(1.0f, 1.0f).endVertex();
        vc.vertex(m, -h,  h, 0).color(255, 255, 255, alphaByte).uv(0.0f, 1.0f).endVertex();
        pose.popPose();
    }

    /**
     * Renders the 12 fixed-direction refraction beams radiating from a flash. Each beam is a
     * camera-aligned strip — the per-beam perpendicular is computed as {@code (direction ×
     * viewDir)} so the strip always presents its flat side to the camera. The {@link
     * PhotonRenderTypes#PHOTON_RIBBON} render type pairs with the {@code photon_refraction}
     * shader, which draws a white-cored ray with a rainbow-fringe along its u axis.
     */
    private static void renderRibbons(VertexConsumer vc, PoseStack pose, Burst b,
                                      net.minecraft.world.phys.Vec3 camPos,
                                      Camera camera, float length, float width, int alphaByte) {
        pose.pushPose();
        pose.translate(b.x - camPos.x, b.y - camPos.y, b.z - camPos.z);
        Matrix4f m = pose.last().pose();

        // View direction from camera to the burst origin (the burst's local origin in pose
        // space is now the camera-relative position). Normalised so subsequent crosses are
        // well-scaled.
        Vector3f viewDir = new Vector3f(
                (float) (b.x - camPos.x),
                (float) (b.y - camPos.y),
                (float) (b.z - camPos.z)).normalize();
        if (!viewDir.isFinite() || viewDir.lengthSquared() < 1.0e-6f) {
            viewDir.set(0.0f, 0.0f, -1.0f);
        }

        Vector3f tmp = new Vector3f();
        for (Vector3f dir : RIBBON_DIRECTIONS) {
            // perpendicular = direction × viewDir → the in-plane axis of the camera-facing strip
            // for this ribbon. If the ribbon points straight at the camera, the cross collapses
            // to zero — in that case the strip would be edge-on regardless, so we just skip it.
            tmp.set(dir).cross(viewDir);
            float perpLen = tmp.length();
            if (perpLen < 1.0e-3f) continue;
            tmp.mul(width / perpLen);

            float sx = 0.0f,             sy = 0.0f,             sz = 0.0f;
            float ex = dir.x * length,   ey = dir.y * length,   ez = dir.z * length;
            float px = tmp.x,            py = tmp.y,            pz = tmp.z;

            // Beam shader: u along length (0 at center, 1 at tip), v perpendicular (0..1).
            vc.vertex(m, sx - px, sy - py, sz - pz).color(b.r, b.g, b.b, alphaByte).uv(0.0f, 0.0f).endVertex();
            vc.vertex(m, sx + px, sy + py, sz + pz).color(b.r, b.g, b.b, alphaByte).uv(0.0f, 1.0f).endVertex();
            vc.vertex(m, ex + px, ey + py, ez + pz).color(b.r, b.g, b.b, alphaByte).uv(1.0f, 1.0f).endVertex();
            vc.vertex(m, ex - px, ey - py, ez - pz).color(b.r, b.g, b.b, alphaByte).uv(1.0f, 0.0f).endVertex();
        }

        pose.popPose();
    }
}
