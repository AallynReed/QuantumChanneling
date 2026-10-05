package com.quantumchanneling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.quantumchanneling.QuantumChanneling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the active collapse effects spawned by the Star Shaper's Hammer when it crushes White
 * Dwarfs, and draws each as a two-phase animation:
 *
 * <ol>
 *   <li><b>Supernova flash</b> ({@value #EXPLOSION_LIFETIME}s) — a white light burst that pops
 *       to the blast radius, plus 12 white-with-rainbow-fringe refraction beams shooting outward.
 *       Additive, brilliant.</li>
 *   <li><b>Implosion vortex</b> ({@value #IMPLOSION_LIFETIME}s) — dark blobs spiralling into a
 *       central void while a vacuum swirl rotates inward. Alpha-blended, so it darkens the scene.</li>
 * </ol>
 *
 * <p>Effects live only on the client; a dimension change or level unload drops any in flight.
 */
@EventBusSubscriber(modid = QuantumChanneling.MODID, value = Dist.CLIENT)
public final class PhotonBurstRenderer {
    private PhotonBurstRenderer() {}

    private record Burst(double x, double y, double z, float maxRadius, int rgb, long spawnTick, String dimension) {}

    /** Phase 1 — match {@code StarShapersHammerBlock.UCB_HOVER_TICKS} when changing either phase. */
    private static final float EXPLOSION_LIFETIME = 0.75f;
    /** Phase 2 — follows immediately after the flash. */
    private static final float IMPLOSION_LIFETIME = 1.2f;
    private static final float LIFETIME_SECONDS = EXPLOSION_LIFETIME + IMPLOSION_LIFETIME;
    /** The flash pops out to full radius almost instantly, then lingers and fades. */
    private static final float GROW_SECONDS = 0.18f;
    /** Ribbons reach this many burst radii from the centre — a visible halo of beams. */
    private static final float RIBBON_REACH = 1.6f;
    /** Ribbon half-thickness scales with the burst, so a water-amplified burst gets thicker beams. */
    private static final float RIBBON_WIDTH_PER_RADIUS = 0.04f;
    /** Fixed directions so every burst has the same structure: 8 horizontal, 4 angled upward. */
    private static final Vector3f[] RIBBON_DIRECTIONS = buildRibbonDirections();

    private static Vector3f[] buildRibbonDirections() {
        Vector3f[] out = new Vector3f[12];
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8.0;
            out[i] = new Vector3f((float) Math.cos(a), 0.0f, (float) Math.sin(a));
        }
        // 35° elevation, offset 22.5° from the horizontal set so no two rays merge into one fat beam.
        float upSin = (float) Math.sin(Math.toRadians(35));
        float upCos = (float) Math.cos(Math.toRadians(35));
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 2 * i / 4.0 + Math.PI / 8.0;
            out[8 + i] = new Vector3f((float) Math.cos(a) * upCos, upSin, (float) Math.sin(a) * upCos);
        }
        return out;
    }

    /** Only touched on the client thread: the payload handler and level rendering. */
    private static final List<Burst> ACTIVE = new ArrayList<>();

    /** Payload handler entry. Schedules a new burst for rendering. */
    public static void addBurst(double x, double y, double z, float maxRadius, int rgb, String dimension) {
        Minecraft mc = Minecraft.getInstance();
        long tick = mc.level != null ? mc.level.getGameTime() : 0L;
        ACTIVE.add(new Burst(x, y, z, maxRadius, rgb, tick, dimension));
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) ACTIVE.clear();
    }

    @SubscribeEvent
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty()) return;

        long currentTick = mc.level.getGameTime();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        String currentDim = mc.level.dimension().identifier().toString();
        // Drop expired bursts and ones from a dimension we've left.
        ACTIVE.removeIf(b -> !currentDim.equals(b.dimension)
                || (currentTick - b.spawnTick + partialTick) / 20.0f > LIFETIME_SECONDS);

        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        for (Burst b : ACTIVE) {
            float age = (currentTick - b.spawnTick + partialTick) / 20.0f;
            if (age < 0.0f) continue;
            poseStack.pushPose();
            poseStack.translate(b.x - camera.pos.x, b.y - camera.pos.y, b.z - camera.pos.z);
            if (age < EXPLOSION_LIFETIME) submitExplosion(collector, poseStack, camera, b, age);
            else submitImplosion(collector, poseStack, camera, b, age - EXPLOSION_LIFETIME);
            poseStack.popPose();
        }
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    /**
     * Phase 1 — the flash pops out to {@code maxRadius} on a snappy eased curve, holds bright, then
     * fades over the back portion of the lifetime. The ribbons extend past the flash edge and fade with it.
     */
    private static void submitExplosion(SubmitNodeCollector collector, PoseStack poseStack, CameraRenderState camera,
                                        Burst b, float age) {
        float radius = b.maxRadius * easeOutCubic(Math.min(1.0f, age / GROW_SECONDS));
        float fadeStart = 0.45f * EXPLOSION_LIFETIME;
        float alpha = age <= fadeStart ? 1.0f
                : Math.max(0.0f, 1.0f - (age - fadeStart) / (EXPLOSION_LIFETIME - fadeStart));
        int alphaByte = (int) (alpha * 255);
        if (alphaByte <= 1) return;

        submitBillboard(collector, poseStack, camera, PhotonRenderTypes.PHOTON_BURST, radius, b.rgb, alphaByte);

        float ribbonLength = radius * RIBBON_REACH;
        if (ribbonLength <= 0.05f) return;
        float ribbonWidth = b.maxRadius * RIBBON_WIDTH_PER_RADIUS;
        Vector3f viewDir = new Vector3f((float) (b.x - camera.pos.x), (float) (b.y - camera.pos.y), (float) (b.z - camera.pos.z));
        if (viewDir.lengthSquared() < 1.0e-6f) viewDir.set(0.0f, 0.0f, -1.0f);
        viewDir.normalize();
        collector.submitCustomGeometry(poseStack, PhotonRenderTypes.PHOTON_RIBBON,
                (pose, vc) -> drawRibbons(pose.pose(), vc, viewDir, ribbonLength, ribbonWidth, b.rgb, alphaByte));
    }

    /**
     * Phase 2 — the inward motion all lives in the {@code photon_implosion} shader; this only
     * drives the envelope: alpha ramps in over the first 12%, holds, and fades over the last 35%.
     * A small establishing grow (0.82 → 1.0) avoids a hard pop when the flash hands off.
     */
    private static void submitImplosion(SubmitNodeCollector collector, PoseStack poseStack, CameraRenderState camera,
                                        Burst b, float age) {
        float t = Math.min(1.0f, age / IMPLOSION_LIFETIME);
        float radius = b.maxRadius * (t < 0.12f ? 0.82f + 0.18f * (t / 0.12f) : 1.0f);
        float alpha = t < 0.12f ? t / 0.12f : t < 0.65f ? 1.0f : Math.max(0.0f, 1.0f - (t - 0.65f) / 0.35f);
        int alphaByte = (int) (alpha * 255);
        if (alphaByte <= 1) return;
        // The shader paints the palette; white vertices keep it untinted, alpha carries the fade.
        submitBillboard(collector, poseStack, camera, PhotonRenderTypes.PHOTON_IMPLOSION, radius, 0xFFFFFF, alphaByte);
    }

    private static void submitBillboard(SubmitNodeCollector collector, PoseStack poseStack, CameraRenderState camera,
                                        RenderType type, float h, int rgb, int alpha) {
        poseStack.pushPose();
        poseStack.mulPose(camera.orientation);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, bl = rgb & 0xFF;
        collector.submitCustomGeometry(poseStack, type, (pose, vc) -> {
            vc.addVertex(pose, -h, -h, 0).setUv(0.0f, 0.0f).setColor(r, g, bl, alpha);
            vc.addVertex(pose,  h, -h, 0).setUv(1.0f, 0.0f).setColor(r, g, bl, alpha);
            vc.addVertex(pose,  h,  h, 0).setUv(1.0f, 1.0f).setColor(r, g, bl, alpha);
            vc.addVertex(pose, -h,  h, 0).setUv(0.0f, 1.0f).setColor(r, g, bl, alpha);
        });
        poseStack.popPose();
    }

    /**
     * Twelve viewer-aligned strips: each strip's in-plane axis is {@code direction × viewDir}, so
     * it always shows its flat side. u runs along the ray (0 at the centre), v across it.
     */
    private static void drawRibbons(Matrix4f m, VertexConsumer vc, Vector3f viewDir,
                                    float length, float width, int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        Vector3f perp = new Vector3f();
        for (Vector3f dir : RIBBON_DIRECTIONS) {
            perp.set(dir).cross(viewDir);
            float perpLen = perp.length();
            // A ray pointing straight at the viewer would be edge-on whatever we do.
            if (perpLen < 1.0e-3f) continue;
            perp.mul(width / perpLen);
            float ex = dir.x * length, ey = dir.y * length, ez = dir.z * length;
            vc.addVertex(m, -perp.x, -perp.y, -perp.z).setUv(0.0f, 0.0f).setColor(r, g, b, alpha);
            vc.addVertex(m, perp.x, perp.y, perp.z).setUv(0.0f, 1.0f).setColor(r, g, b, alpha);
            vc.addVertex(m, ex + perp.x, ey + perp.y, ez + perp.z).setUv(1.0f, 1.0f).setColor(r, g, b, alpha);
            vc.addVertex(m, ex - perp.x, ey - perp.y, ez - perp.z).setUv(1.0f, 0.0f).setColor(r, g, b, alpha);
        }
    }
}
