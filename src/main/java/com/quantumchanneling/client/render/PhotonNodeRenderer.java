package com.quantumchanneling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.quantumchanneling.block.PhotonShape;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonManagerBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Custom-shader renderer for every photon device. Up to five programs per device:
 *
 * <ol>
 *   <li>{@code photon_halo} — bright accretion disk + lensing rings as one additive billboard.</li>
 *   <li>{@code photon_beam} — tapered glowing tube from the orb edge to each connected face port,
 *       as two perpendicular quads per direction. Emitter / receiver only; the UV layout flips
 *       so the flow reads face → orb for emitters (pulling) and orb → face for receivers.</li>
 *   <li>{@code photon_gyroscope} + {@code photon_bolt} — the manager's tumbling rings and the
 *       lightning arcs to its corner vaults.</li>
 *   <li>{@code photon_void} — the dark event horizon, drawn last over the halo.</li>
 * </ol>
 *
 * <h3>Sizing</h3>
 * <p>A viewer-facing billboard reaches {@code half × √2} from the block centre at its corners. To
 * stay inside the block from every angle, {@code half ≤ 0.354}; {@link #HALO_QUAD_HALF} is 0.32
 * so neighbouring blocks never clip the halo.
 */
public class PhotonNodeRenderer<T extends ChannelBoundBlockEntity>
        implements BlockEntityRenderer<T, PhotonNodeRenderer.State> {

    /** Half-size of the halo billboard. 0.32 × √2 = 0.452, well inside the block's 0.5 half-extent. */
    private static final float HALO_QUAD_HALF = 0.32f;
    private static final float VOID_QUAD_HALF = 0.14f;
    /** Ring quad half-size. Its corners poke past the block, but the shader discards them and the
     *  ring band itself sits at radius ≈ 0.31. */
    private static final float GYRO_QUAD_HALF = 0.38f;

    /** Warm gold rings around the manager's violet core — two-tone instead of monochrome. */
    private static final int GYRO_RGB = 0xFFD860;
    /** Violet bolts matching {@link PhotonAccent#MANAGER}; the shader whitens the hot core. */
    private static final int BOLT_RGB = 0xB07BFF;

    /** Physical half-width of each bolt's tube quad; the shader picks how much of it the bolt fills. */
    private static final float BOLT_HALF_WIDTH = 0.10f;
    /** Block centre to a corner vault's inner corner (voxel 3/16 from each face) on every axis. */
    private static final float BOLT_END_OFFSET = 0.3125f;

    /** Beam half-width at the orb end; the shader tapers it to fit the port frame. */
    private static final float BEAM_HALF_WIDTH = 0.13f;
    /** Beam endpoint, pulled 0.02 back from the face to avoid z-fighting with the neighbour. */
    private static final float BEAM_FACE_Y = 0.48f;

    public static final class State extends BlockEntityRenderState {
        int accent;
        boolean emitter;
        boolean manager;
        boolean beams;
        /** Bit {@link Direction#get3DDataValue()} set = that side shows a beam. */
        int connections;
        float seconds;
    }

    public PhotonNodeRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T be, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
        state.accent = PhotonAccent.colorFor(be);
        state.emitter = be instanceof PhotonEmitterBlockEntity;
        state.manager = be instanceof PhotonManagerBlockEntity;
        state.beams = PhotonAccent.rendersBeams(be);
        state.connections = 0;
        if (state.beams) {
            BlockState block = be.getBlockState();
            for (Direction d : Direction.values()) {
                if (block.getValue(PhotonShape.connProp(d))) state.connections |= 1 << d.get3DDataValue();
            }
        }
        state.seconds = be.getLevel() == null ? 0.0f : (be.getLevel().getGameTime() + partialTicks) / 20.0f;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        if (state.connections != 0) {
            int connections = state.connections;
            int rgb = state.accent;
            boolean emitter = state.emitter;
            collector.submitCustomGeometry(poseStack, PhotonRenderTypes.PHOTON_BEAM,
                    (pose, vc) -> drawBeams(pose.pose(), vc, connections, rgb, emitter));
        }
        submitOrb(collector, poseStack, state.accent, state.manager, state.seconds);
        poseStack.popPose();
    }

    /**
     * The orb shared by placed devices and their items: halo (+ the manager's rings and bolts),
     * then the dark void on a later order so it overlays them.
     */
    public static void submitOrb(SubmitNodeCollector collector, PoseStack poseStack, int rgb,
                                 boolean managerExtras, float seconds) {
        collector.submitCustomGeometry(poseStack, PhotonRenderTypes.PHOTON_HALO,
                (pose, vc) -> PhotonOrb.draw(pose, vc, HALO_QUAD_HALF, r(rgb), g(rgb), b(rgb), 255, 0.0f));
        if (managerExtras) {
            collector.submitCustomGeometry(poseStack, PhotonRenderTypes.PHOTON_GYROSCOPE,
                    (pose, vc) -> drawGyroscope(pose.pose(), vc, seconds));
            collector.submitCustomGeometry(poseStack, PhotonRenderTypes.PHOTON_BOLT,
                    (pose, vc) -> drawCornerBolts(pose.pose(), vc));
        }
        int voidRgb = (clampByte(r(rgb) / 10 + 4) << 16) | (clampByte(g(rgb) / 10 + 4) << 8) | clampByte(b(rgb) / 10 + 6);
        // Small toward-viewer bias so the coplanar void wins the depth test over the halo.
        collector.order(1).submitCustomGeometry(poseStack, PhotonRenderTypes.PHOTON_VOID,
                (pose, vc) -> PhotonOrb.draw(pose, vc, VOID_QUAD_HALF, r(voidRgb), g(voidRgb), b(voidRgb), 255, 0.003f));
    }

    private static int r(int rgb) { return (rgb >> 16) & 0xFF; }
    private static int g(int rgb) { return (rgb >> 8) & 0xFF; }
    private static int b(int rgb) { return rgb & 0xFF; }
    private static int clampByte(int v) { return Math.max(0, Math.min(255, v)); }

    private static void drawBeams(Matrix4f base, VertexConsumer vc, int connections, int rgb, boolean emitter) {
        for (Direction d : Direction.values()) {
            if ((connections & (1 << d.get3DDataValue())) == 0) continue;
            Matrix4f m = new Matrix4f(base).rotate(beamRotation(d));
            // Start just outside the halo so the beam plugs into the accretion edge.
            float y0 = HALO_QUAD_HALF * 0.5f;
            float y1 = BEAM_FACE_Y;
            float w = BEAM_HALF_WIDTH;
            // The shader scrolls flow toward +u and tapers from u=0 to u=1, so receivers (orb → face)
            // get u=0 at the orb, and emitters — a funnel pulling inward — get u=0 at the face.
            float uOrb = emitter ? 1.0f : 0.0f;
            float uFace = emitter ? 0.0f : 1.0f;
            // Two perpendicular quads for a cylindrical look.
            beamQuad(vc, m, -w, y0, 0, w, y0, 0, w, y1, 0, -w, y1, 0, rgb, uOrb, uFace);
            beamQuad(vc, m, 0, y0, -w, 0, y0, w, 0, y1, w, 0, y1, -w, rgb, uOrb, uFace);
        }
    }

    /** Rotates +Y onto {@code dir}. */
    private static Quaternionf beamRotation(Direction dir) {
        return switch (dir) {
            case UP -> new Quaternionf();
            case DOWN -> new Quaternionf().rotationX((float) Math.PI);
            case NORTH -> new Quaternionf().rotationX(-(float) Math.PI / 2);
            case SOUTH -> new Quaternionf().rotationX((float) Math.PI / 2);
            case EAST -> new Quaternionf().rotationZ(-(float) Math.PI / 2);
            case WEST -> new Quaternionf().rotationZ((float) Math.PI / 2);
        };
    }

    /** Vertices 1–2 sit on the orb side, 3–4 on the face side; v picks the perpendicular edge. */
    private static void beamQuad(VertexConsumer vc, Matrix4f m,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float x3, float y3, float z3, float x4, float y4, float z4,
                                 int rgb, float uOrb, float uFace) {
        vc.addVertex(m, x1, y1, z1).setUv(uOrb, 0.0f).setColor(r(rgb), g(rgb), b(rgb), 255);
        vc.addVertex(m, x2, y2, z2).setUv(uOrb, 1.0f).setColor(r(rgb), g(rgb), b(rgb), 255);
        vc.addVertex(m, x3, y3, z3).setUv(uFace, 1.0f).setColor(r(rgb), g(rgb), b(rgb), 255);
        vc.addVertex(m, x4, y4, z4).setUv(uFace, 0.0f).setColor(r(rgb), g(rgb), b(rgb), 255);
    }

    /**
     * Three interlocking rings, each tumbling around a different axis. The periods (≈ 14 s,
     * ≈ 9.7 s, ≈ 7.4 s) are deliberately non-commensurate so the cage never settles into a
     * repeating pose; the shader animates bright spots within each ring on top.
     */
    private static void drawGyroscope(Matrix4f base, VertexConsumer vc, float t) {
        // Ring 1 — lies flat, tilting left-right.
        ringQuad(vc, new Matrix4f(base).rotate(Axis.ZP.rotation(t * 0.45f)).rotate(Axis.XP.rotation((float) (Math.PI / 2))));
        // Ring 2 — vertical in XY, tilting up-down.
        ringQuad(vc, new Matrix4f(base).rotate(Axis.XP.rotation(t * 0.65f)));
        // Ring 3 — vertical in YZ, tilting front-back.
        ringQuad(vc, new Matrix4f(base).rotate(Axis.YP.rotation(t * 0.85f + (float) (Math.PI / 2))));
    }

    /** The shader draws the annulus from UVs; this quad is only the canvas. */
    private static void ringQuad(VertexConsumer vc, Matrix4f m) {
        float h = GYRO_QUAD_HALF;
        vc.addVertex(m, -h, -h, 0).setUv(0.0f, 0.0f).setColor(r(GYRO_RGB), g(GYRO_RGB), b(GYRO_RGB), 255);
        vc.addVertex(m,  h, -h, 0).setUv(1.0f, 0.0f).setColor(r(GYRO_RGB), g(GYRO_RGB), b(GYRO_RGB), 255);
        vc.addVertex(m,  h,  h, 0).setUv(1.0f, 1.0f).setColor(r(GYRO_RGB), g(GYRO_RGB), b(GYRO_RGB), 255);
        vc.addVertex(m, -h,  h, 0).setUv(0.0f, 1.0f).setColor(r(GYRO_RGB), g(GYRO_RGB), b(GYRO_RGB), 255);
    }

    /**
     * Eight bolts from the centre to the corner vaults, each as two perpendicular tube quads so it
     * has thickness from any angle. The bolt index rides in the alpha channel (index / 8) so the
     * shader can desynchronise the strobes.
     */
    private static void drawCornerBolts(Matrix4f m, VertexConsumer vc) {
        int index = 0;
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dy = -1; dy <= 1; dy += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    Vector3f dir = new Vector3f(dx, dy, dz).mul(BOLT_END_OFFSET);
                    // No bolt is vertical (every corner has an X and Z component), so crossing with
                    // world-up always yields a usable perpendicular.
                    Vector3f w1 = new Vector3f(dir).cross(0.0f, 1.0f, 0.0f).normalize().mul(BOLT_HALF_WIDTH);
                    Vector3f w2 = new Vector3f(dir).cross(w1).normalize().mul(BOLT_HALF_WIDTH);
                    int phase = index++ * 255 / 8;
                    boltQuad(vc, m, w1, dir, phase);
                    boltQuad(vc, m, w2, dir, phase);
                }
            }
        }
    }

    /** u = 0 at the centre, 1 at the corner; v spans the tube width where the shader's jagged
     *  centreline meanders. */
    private static void boltQuad(VertexConsumer vc, Matrix4f m, Vector3f w, Vector3f dir, int phase) {
        vc.addVertex(m, -w.x, -w.y, -w.z).setUv(0.0f, 0.0f).setColor(r(BOLT_RGB), g(BOLT_RGB), b(BOLT_RGB), phase);
        vc.addVertex(m, w.x, w.y, w.z).setUv(0.0f, 1.0f).setColor(r(BOLT_RGB), g(BOLT_RGB), b(BOLT_RGB), phase);
        vc.addVertex(m, dir.x + w.x, dir.y + w.y, dir.z + w.z).setUv(1.0f, 1.0f).setColor(r(BOLT_RGB), g(BOLT_RGB), b(BOLT_RGB), phase);
        vc.addVertex(m, dir.x - w.x, dir.y - w.y, dir.z - w.z).setUv(1.0f, 0.0f).setColor(r(BOLT_RGB), g(BOLT_RGB), b(BOLT_RGB), phase);
    }
}
