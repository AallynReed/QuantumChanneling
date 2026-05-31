package com.quantumchanneling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.quantumchanneling.block.PhotonShape;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

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

    /** Accent colors. Blue for emitter, red for receiver. */
    private static final int EMITTER_RGB  = 0x4FA0FF;
    private static final int RECEIVER_RGB = 0xFF5560;

    /** Half-size of the halo billboard. 0.32 × √2 = 0.452, well inside the block's 0.5 half-extent. */
    private static final float HALO_QUAD_HALF = 0.32f;
    /** Half-size of the void billboard. */
    private static final float VOID_QUAD_HALF = 0.14f;

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

        boolean isEmitter = be instanceof PhotonEmitterBlockEntity;
        int rgb = isEmitter ? EMITTER_RGB : RECEIVER_RGB;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);

        // ---- accretion halo (custom additive shader) ----
        VertexConsumer halo = buffer.getBuffer(PhotonRenderTypes.PHOTON_HALO);
        drawShadedBillboard(pose, halo, HALO_QUAD_HALF, r, g, b, 255, 0.0f);

        // ---- beams (custom additive shader) ----
        var state = be.getBlockState();
        VertexConsumer beam = buffer.getBuffer(PhotonRenderTypes.PHOTON_BEAM);
        for (Direction d : Direction.values()) {
            if (!state.getValue(PhotonShape.connProp(d))) continue;
            renderBeam(pose, beam, d, r, g, b, isEmitter);
        }

        // Flush both additive passes BEFORE the void layer so the dark sphere overlays them
        // instead of being painted underneath. BufferSource batches by render type — explicit
        // endBatch here decides the layering order.
        if (buffer instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(PhotonRenderTypes.PHOTON_HALO);
            bs.endBatch(PhotonRenderTypes.PHOTON_BEAM);
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

    @Override
    public boolean shouldRenderOffScreen(T be) { return false; }

    @Override
    public int getViewDistance() { return 64; }
}
