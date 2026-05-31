package com.quantumchanneling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.quantumchanneling.block.PhotonShape;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonManagerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Custom-shader renderer for emitter + receiver. Three GLSL programs handle the visuals:
 *
 * <ol>
 *   <li>{@code photon_halo} — bright accretion-disk + gravitational-lensing rings, drawn as one
 *       additive billboard quad. Five analytical Gaussian bands (photon sphere, hot edge, main
 *       ring, Einstein ring, atmospheric halo) in a single pass.</li>
 *   <li>{@code photon_void} — dark event-horizon sphere with bright photon-sphere lensing rim,
 *       drawn as one translucent billboard quad ON TOP of the halo to carve out the dark center
 *       and overlay the bright lensing band.</li>
 *   <li>{@code photon_beam} — tapered glowing tube from ball edge to face port, drawn as two
 *       perpendicular shader quads per direction. The shader computes Gaussian perpendicular
 *       falloff and scrolling flow patterns; the renderer flips the UV layout to make flow
 *       direction match the device kind.</li>
 * </ol>
 *
 * <h3>Sizing constraints</h3>
 * <p>Camera-facing billboard quads extend up to {@code half × √2} from the block center (corners).
 * For the quad to stay strictly inside the block's unit cube regardless of camera angle:
 * {@code half × √2 ≤ 0.5}, so {@code half ≤ 0.354}. {@link #HALO_QUAD_HALF} is set to 0.32 with
 * comfortable margin so adjacent blocks never visually clip the halo.
 *
 * <h3>Flow direction</h3>
 * <p>Receivers push energy out to neighbors — beam flow is ball → face. Emitters pull energy in
 * from neighbors — beam flow is face → ball. The shader scrolls toward +u; flipping the UV layout
 * for emitters makes the same scroll direction read as face → ball spatially. The shader's taper
 * (wide at u=0, narrow at u=1) also flips correctly: receivers get wide-at-ball / narrow-at-face,
 * emitters get wide-at-face / narrow-at-ball (a funnel pulling material inward).
 */
public class PhotonNodeRenderer<T extends ChannelBoundBlockEntity> implements BlockEntityRenderer<T> {

    /** Half-size of the halo billboard. 0.32 × √2 = 0.452, well inside the block's 0.5 half-extent. */
    private static final float HALO_QUAD_HALF = 0.32f;
    /** Half-size of the void billboard. */
    private static final float VOID_QUAD_HALF = 0.14f;
    /** Half-size of one gyroscope ring quad. 0.38 × √2 = 0.537 — the corners poke past the block
     *  edge, but the shader's {@code if (d > 1.0) discard} cuts those corners, and the actual ring
     *  band sits at d=0.82 (world radius 0.38 × 0.82 ≈ 0.31), well inside the block. */
    private static final float GYRO_QUAD_HALF = 0.38f;

    /** Gyroscope ring color — warm gold. Distinct from the manager's central violet halo so the
     *  device reads as two-tone (gold cage + violet core) instead of monochrome. */
    private static final int GYRO_R = 0xFF;
    private static final int GYRO_G = 0xD8;
    private static final int GYRO_B = 0x60;

    /** Bolt accent color — violet matching {@code PhotonAccent.MANAGER}. The shader blends toward
     *  white at the bolt's hot core so the visual reads as "violet electricity with a white-hot
     *  centre," tying the bolts back to the central black-hole's color. */
    private static final int BOLT_R = 0xB0;
    private static final int BOLT_G = 0x7B;
    private static final int BOLT_B = 0xFF;

    /** Half-width of each bolt's tube quad. Sets the quad's physical width in world space; the
     *  shader picks what fraction of that width the visible bolt occupies. Bumped from 0.06 to
     *  0.10 — combined with the shader's wider centerline wobble + thicker halfWidth, this gives
     *  the bolt enough room to swing without clipping at the quad edges and reads as a meaty
     *  arc instead of a thin spark. */
    private static final float BOLT_HALF_WIDTH = 0.10f;
    /** World-space offset from block center to a corner vault's inner corner. The vaults are
     *  3×3×3 voxels at the block corners; their inner corner is at voxel (3,3,3) / (13,3,3) /
     *  etc. = 3/16 from the nearest face = 0.3125 from block center on each axis. */
    private static final float BOLT_END_OFFSET = 0.3125f;

    /** Half-width of the beam quad at the ball end. Shader handles the visual tapering
     *  internally so the beam still narrows enough to pass through the ring's 2x2 side hollow
     *  (0.125 world units) and the port frame at the face. At 0.13 the beam reads as a chunky
     *  cylinder at the ball end and tapers cleanly into the port. */
    private static final float BEAM_HALF_WIDTH = 0.13f;
    /** Beam endpoint inside the block face. Pulled back 0.02 from the face to avoid z-fighting
     *  with the adjacent block's bottom face when the device is connected on that side. */
    private static final float BEAM_FACE_Y = 0.48f;

    public PhotonNodeRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(T be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (be.getLevel() == null) return;

        boolean isEmitter   = be instanceof PhotonEmitterBlockEntity;
        boolean drawsBeams  = PhotonAccent.rendersBeams(be);
        int rgb = PhotonAccent.colorFor(be);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);

        // ---- accretion halo (custom additive shader) ----
        VertexConsumer halo = buffer.getBuffer(PhotonRenderTypes.PHOTON_HALO);
        drawShadedBillboard(pose, halo, HALO_QUAD_HALF, r, g, b, 255, 0.0f);

        // ---- beams (custom additive shader) ----
        // Manager and storage devices are network-control / battery roles — they don't route a
        // directional flow through ports, so they skip the beam pass entirely. The PhotonShape
        // connection properties on those blocks remain at default-false so the loop would no-op
        // anyway, but the explicit guard avoids reading state we know isn't meaningful.
        if (drawsBeams) {
            var state = be.getBlockState();
            VertexConsumer beam = buffer.getBuffer(PhotonRenderTypes.PHOTON_BEAM);
            for (Direction d : Direction.values()) {
                if (!state.getValue(PhotonShape.connProp(d))) continue;
                renderBeam(pose, beam, d, r, g, b, isEmitter);
            }
        }

        // ---- gyroscope rings + bolts (manager-only, custom additive shaders) ----
        // Gyroscope: three interlocking ring quads tumbling on different axes. Bolts: eight
        // lightning tubes arcing from the central orb to each of the corner vaults, strobing
        // asynchronously. Both are drawn BEFORE the void so the dark central sphere still
        // overlays the inner endpoints of the bolts/rings correctly.
        if (be instanceof PhotonManagerBlockEntity) {
            VertexConsumer gyro = buffer.getBuffer(PhotonRenderTypes.PHOTON_GYROSCOPE);
            renderGyroscope(pose, gyro, GYRO_R, GYRO_G, GYRO_B,
                    be.getLevel().getGameTime(), partialTick);

            VertexConsumer bolt = buffer.getBuffer(PhotonRenderTypes.PHOTON_BOLT);
            renderCornerBolts(pose, bolt, BOLT_R, BOLT_G, BOLT_B);
        }

        // Flush all additive passes BEFORE the void layer so the dark sphere overlays them
        // instead of being painted underneath. BufferSource batches by render type — explicit
        // endBatch here decides the layering order.
        if (buffer instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(PhotonRenderTypes.PHOTON_HALO);
            if (drawsBeams) bs.endBatch(PhotonRenderTypes.PHOTON_BEAM);
            if (be instanceof PhotonManagerBlockEntity) {
                bs.endBatch(PhotonRenderTypes.PHOTON_GYROSCOPE);
                bs.endBatch(PhotonRenderTypes.PHOTON_BOLT);
            }
        }

        // ---- dark void (custom translucent shader) ----
        int voidR = clampByte((int) (r * 0.10f) + 4);
        int voidG = clampByte((int) (g * 0.10f) + 4);
        int voidB = clampByte((int) (b * 0.10f) + 6);
        VertexConsumer dark = buffer.getBuffer(PhotonRenderTypes.PHOTON_VOID);
        // Pull the void quad slightly toward the camera so its depth is deterministically less
        // than the halo's. Without this bias the two billboards have nearly-identical depth and
        // LEQUAL flips at random with floating-point noise as the camera moves, producing the
        // "static" flicker on the dark center. Local +Z after mulPose(cameraOrientation) points
        // into the scene (away from camera), so we use a NEGATIVE bias to pull toward the viewer.
        // -0.003 is enough to win the test every frame without visibly shifting the screen
        // position of the void.
        drawShadedBillboard(pose, dark, VOID_QUAD_HALF, voidR, voidG, voidB, 255, -0.003f);

        pose.popPose();
    }

    /**
     * Camera-facing quad with UV (0,0)..(1,1) corners. The bound shader does all visual work.
     * {@code depthBias} translates the quad along local +Z after the camera rotation — in
     * Minecraft's PoseStack the local +Z after {@code mulPose(cameraOrientation)} points back
     * toward the viewer, so positive values pull the quad closer to the camera and produce a
     * smaller depth value. Used to break ties between coplanar billboards.
     */
    private static void drawShadedBillboard(PoseStack pose, VertexConsumer vc, float halfSize,
                                            int red, int green, int blue, int alpha, float depthBias) {
        pose.pushPose();
        Quaternionf cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        pose.mulPose(cam);
        if (depthBias != 0.0f) pose.translate(0.0f, 0.0f, depthBias);
        Matrix4f m = pose.last().pose();

        vc.vertex(m, -halfSize, -halfSize, 0).color(red, green, blue, alpha).uv(0.0f, 0.0f).endVertex();
        vc.vertex(m,  halfSize, -halfSize, 0).color(red, green, blue, alpha).uv(1.0f, 0.0f).endVertex();
        vc.vertex(m,  halfSize,  halfSize, 0).color(red, green, blue, alpha).uv(1.0f, 1.0f).endVertex();
        vc.vertex(m, -halfSize,  halfSize, 0).color(red, green, blue, alpha).uv(0.0f, 1.0f).endVertex();

        pose.popPose();
    }

    private static int clampByte(int v) { return Math.max(0, Math.min(255, v)); }

    /**
     * Two perpendicular shader-driven quads per beam. UV is flipped based on {@code isEmitter}
     * so the shader's "u increases over time" scroll reads as face → ball for emitters (pulling)
     * and ball → face for receivers (pushing).
     */
    private static void renderBeam(PoseStack pose, VertexConsumer vc, Direction dir,
                                   int red, int green, int blue, boolean isEmitter) {
        pose.pushPose();
        switch (dir) {
            case UP    -> {}
            case DOWN  -> pose.mulPose(new Quaternionf().rotationX((float) Math.PI));
            case NORTH -> pose.mulPose(new Quaternionf().rotationX(-(float) Math.PI / 2));
            case SOUTH -> pose.mulPose(new Quaternionf().rotationX( (float) Math.PI / 2));
            case EAST  -> pose.mulPose(new Quaternionf().rotationZ(-(float) Math.PI / 2));
            case WEST  -> pose.mulPose(new Quaternionf().rotationZ( (float) Math.PI / 2));
        }

        Matrix4f m = pose.last().pose();
        // Start just outside the halo so the beam plugs into the accretion edge.
        float y0 = HALO_QUAD_HALF * 0.50f;
        float y1 = BEAM_FACE_Y;
        float w  = BEAM_HALF_WIDTH;

        // UV layout. The shader's flow scrolls toward +u over time, so:
        //   - Receiver (ball → face): u=0 at ball, u=1 at face → scroll direction = ball to face ✓
        //   - Emitter  (face → ball): u=0 at face, u=1 at ball → scroll direction = face to ball ✓
        // The shader's taper (wide at u=0, narrow at u=1) flips with it: receiver beam is wide at
        // the ball and tapers toward the face; emitter beam is wide at the face (funnel intake)
        // and tapers toward the ball (compressed point of capture).
        float uBall = isEmitter ? 1.0f : 0.0f;
        float uFace = isEmitter ? 0.0f : 1.0f;

        // Quad 1 — width along X (XY plane).
        beamQuad(vc, m, -w, y0, 0,  w, y0, 0,  w, y1, 0,  -w, y1, 0,
                 red, green, blue, uBall, uFace);
        // Quad 2 — width along Z (YZ plane), perpendicular to quad 1 for cylindrical look.
        beamQuad(vc, m, 0, y0, -w,  0, y0, w,  0, y1, w,  0, y1, -w,
                 red, green, blue, uBall, uFace);

        pose.popPose();
    }

    /**
     * Issues a single beam quad. Vertex order: ball-side first (y = y0), then face-side
     * (y = y1). UV layout follows {@code uBall}/{@code uFace} parameters so the renderer can
     * pick the flow direction without changing the shader.
     */
    private static void beamQuad(VertexConsumer vc, Matrix4f m,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float x4, float y4, float z4,
                                 int r, int g, int b, float uBall, float uFace) {
        int a = 255;
        // vertex 1, 2 = ball-side; vertex 3, 4 = face-side. v = 0 / 1 picks the perpendicular edge.
        vc.vertex(m, x1, y1, z1).color(r, g, b, a).uv(uBall, 0.0f).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, a).uv(uBall, 1.0f).endVertex();
        vc.vertex(m, x3, y3, z3).color(r, g, b, a).uv(uFace, 1.0f).endVertex();
        vc.vertex(m, x4, y4, z4).color(r, g, b, a).uv(uFace, 0.0f).endVertex();
    }

    /**
     * Three interlocking ring quads tumbling on different axes. Each ring's base orientation is
     * applied first (lay flat / vertical XY / vertical YZ) then a slow time-driven rotation tilts
     * the whole plane. The shader handles the orbital bright-spot animation within each ring's
     * surface, so the visual movement is two-layer: the plane tumbles in 3D AND the surface
     * pulses internally.
     *
     * <p>The three rotation periods are deliberately non-commensurate (≈ 14 s, ≈ 9.7 s, ≈ 7.4 s)
     * so the three rings never resynchronise — the structure stays visually "alive" instead of
     * settling into a repeating pose. {@code partialTick} smooths the rotation across sub-tick
     * frames so the motion isn't strobed at 20 Hz.
     */
    public static void renderGyroscope(PoseStack pose, VertexConsumer ring,
                                       int r, int g, int b, long gameTime, float partialTick) {
        float t = (gameTime + partialTick) / 20.0f; // seconds

        // Ring 1 — originally horizontal (lying flat in XZ). Tumble around Z axis so the plane
        // tilts left-right over time.
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotation(t * 0.45f));
        pose.mulPose(Axis.XP.rotation((float) (Math.PI / 2.0)));
        drawRingQuad(pose, ring, r, g, b);
        pose.popPose();

        // Ring 2 — originally vertical, in the XY plane (facing camera by default). Tumble around
        // X axis so the plane tilts up-down.
        pose.pushPose();
        pose.mulPose(Axis.XP.rotation(t * 0.65f));
        drawRingQuad(pose, ring, r, g, b);
        pose.popPose();

        // Ring 3 — originally vertical, in the YZ plane (facing sideways). Tumble around Y so the
        // plane tilts front-back.
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(t * 0.85f));
        pose.mulPose(Axis.YP.rotation((float) (Math.PI / 2.0)));
        drawRingQuad(pose, ring, r, g, b);
        pose.popPose();
    }

    /** A single ring quad in its current local frame. The shader draws the annular ring shape
     *  from UV coordinates — this quad just provides the canvas + accent color. */
    private static void drawRingQuad(PoseStack pose, VertexConsumer vc, int r, int g, int b) {
        float h = GYRO_QUAD_HALF;
        Matrix4f m = pose.last().pose();
        vc.vertex(m, -h, -h, 0).color(r, g, b, 255).uv(0.0f, 0.0f).endVertex();
        vc.vertex(m,  h, -h, 0).color(r, g, b, 255).uv(1.0f, 0.0f).endVertex();
        vc.vertex(m,  h,  h, 0).color(r, g, b, 255).uv(1.0f, 1.0f).endVertex();
        vc.vertex(m, -h,  h, 0).color(r, g, b, 255).uv(0.0f, 1.0f).endVertex();
    }

    /**
     * Eight lightning bolts from the block center to each of the eight corner vaults. Each bolt
     * is rendered as two perpendicular tube quads (same volume trick as photon_beam) so the bolt
     * has visible thickness from any view direction. The bolt phase is encoded in the alpha
     * channel — {@code (boltIndex / 8) * 255} gives eight distinct phases the shader uses to
     * stagger its jagged centerline + strobe so the eight bolts never fire in sync.
     */
    public static void renderCornerBolts(PoseStack pose, VertexConsumer vc, int r, int g, int b) {
        Matrix4f m = pose.last().pose();
        int boltIndex = 0;
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dy = -1; dy <= 1; dy += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    drawBolt(vc, m, dx, dy, dz, r, g, b, boltIndex++);
                }
            }
        }
    }

    /**
     * One lightning bolt. {@code dx,dy,dz} ∈ {-1, +1} picks which corner the bolt arcs toward;
     * {@code boltIndex} ∈ 0..7 maps to a per-bolt phase (encoded as alpha) so the shader can
     * desynchronise the eight bolts' strobes.
     *
     * <p>Geometry: compute the bolt direction (corner − center), pick two perpendicular axes by
     * crossing with world-up (or world-X when the bolt is vertical), and build two perpendicular
     * tube quads. Each quad's u = 0 at the center, u = 1 at the corner; v spans 0..1 across the
     * tube width, where the shader's jagged centerline meanders.
     */
    private static void drawBolt(VertexConsumer vc, Matrix4f m, int dx, int dy, int dz,
                                 int r, int g, int b, int boltIndex) {
        float off = BOLT_END_OFFSET;
        Vector3f dir = new Vector3f(dx * off, dy * off, dz * off);
        // Pick a vector not parallel to the bolt for the first perpendicular cross — bolts never
        // run purely along Y (all 8 corners have a Y component), so world-up is always safe.
        Vector3f w1 = new Vector3f(dir).cross(0.0f, 1.0f, 0.0f).normalize().mul(BOLT_HALF_WIDTH);
        Vector3f w2 = new Vector3f(dir).cross(w1).normalize().mul(BOLT_HALF_WIDTH);

        // Phase = boltIndex / 8 in alpha space (0, 32, 64, ... 224). Shader reads alpha/255 and
        // multiplies by 2π to get an angular offset that varies per bolt.
        int phaseAlpha = (boltIndex * 255) / 8;

        // Quad 1 — width along w1.
        boltQuad(vc, m, w1, dir, r, g, b, phaseAlpha);
        // Quad 2 — width along w2 (perpendicular to w1).
        boltQuad(vc, m, w2, dir, r, g, b, phaseAlpha);
    }

    /** Writes a single bolt-quad's four vertices. The "start" end (center of block) is at the
     *  origin in current pose coordinates; the "end" is at {@code dir}. The quad spans ±w around
     *  the bolt axis. */
    private static void boltQuad(VertexConsumer vc, Matrix4f m,
                                 Vector3f w, Vector3f dir,
                                 int r, int g, int b, int phaseAlpha) {
        // Start vertices (u = 0).
        vc.vertex(m, -w.x, -w.y, -w.z).color(r, g, b, phaseAlpha).uv(0.0f, 0.0f).endVertex();
        vc.vertex(m,  w.x,  w.y,  w.z).color(r, g, b, phaseAlpha).uv(0.0f, 1.0f).endVertex();
        // End vertices (u = 1).
        vc.vertex(m, dir.x + w.x, dir.y + w.y, dir.z + w.z).color(r, g, b, phaseAlpha).uv(1.0f, 1.0f).endVertex();
        vc.vertex(m, dir.x - w.x, dir.y - w.y, dir.z - w.z).color(r, g, b, phaseAlpha).uv(1.0f, 0.0f).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(T be) { return false; }

    @Override
    public int getViewDistance() { return 64; }
}
