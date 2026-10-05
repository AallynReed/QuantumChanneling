package com.quantumchanneling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.block.PhotonManagerBlock;
import org.joml.Matrix4f;

/**
 * BlockEntityWithoutLevelRenderer for every photon device item — emitter, receiver, manager, and
 * the five storage tiers — plus the free-floating Uncontained Black Hole and White Dwarf items.
 * Mirrors the {@link PhotonNodeRenderer} server-side BER: renders the baked block model (where
 * there is one) then overlays the GLSL halo + dark-void shaders. The black-hole + accretion effect
 * is visible in the hotbar, inventory slot, item frame, hand, and dropped form — not just in world.
 *
 * <p>The accent color comes from {@link PhotonAccent} so the in-world and item visuals always
 * stay in sync; updating a tier color in one place updates it everywhere.
 *
 * <p>The shader effects render as 3D volumetric impostors via {@link PhotonOrb} (three
 * perpendicular quads) rather than a single camera-facing billboard. That gives a real 3D look
 * from every angle and — crucially — renders correctly in the inventory GUI, where the world
 * camera orientation a billboard would need is meaningless (the old billboard went edge-on and
 * vanished from some viewing directions). Beams aren't drawn here — they only make sense on a
 * placed emitter/receiver with connected neighbors.
 */
public class PhotonItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final PhotonItemRenderer INSTANCE = new PhotonItemRenderer();

    /** Sized identically to the BER quads so the in-world and item visuals match. */
    private static final float HALO_QUAD_HALF = 0.32f;
    private static final float VOID_QUAD_HALF = 0.14f;
    /** White Dwarf star billboard — a touch larger so the sun fills the item slot. */
    private static final float STAR_QUAD_HALF = 0.40f;

    /** Accent for the free-floating Uncontained Black Hole item — gold. */
    private static final int BLACK_HOLE_RGB = 0xFFD060;

    private PhotonItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
              Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context,
                             PoseStack pose, MultiBufferSource buffer,
                             int packedLight, int packedOverlay) {
        // Uncontained Black Hole — the SAME halo + void shader as the emitter/receiver devices,
        // but with NO block model under it. Just the free-floating singularity effect.
        if (stack.is(QuantumChanneling.UNCONTAINED_BLACK_HOLE.get())) {
            renderHaloAndVoid(pose, buffer, BLACK_HOLE_RGB);
            return;
        }

        // White Dwarf — a free-floating white stellar disc (no block model). Its own dedicated
        // sun shader paints the entire effect from a single billboard.
        if (stack.is(QuantumChanneling.WHITE_DWARF.get())) {
            renderWhiteDwarf(pose, buffer);
            return;
        }

        if (!(stack.getItem() instanceof BlockItem blockItem)) return;

        // 1) Render the baked block model — the dark shell, accent ring segments, corner posts.
        //    No ports because no neighbors are connected from the item.
        BlockState state = blockItem.getBlock().defaultBlockState();
        Minecraft.getInstance().getBlockRenderer()
                .renderSingleBlock(state, pose, buffer, packedLight, packedOverlay);

        // 2) Render the shader effect on top — same halo + void passes as the BER.
        int rgb = PhotonAccent.colorFor(blockItem.getBlock());
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);

        VertexConsumer halo = buffer.getBuffer(PhotonRenderTypes.PHOTON_HALO);
        drawOrb(pose, halo, HALO_QUAD_HALF, r, g, b, 255);

        // Manager items get the gyroscope rings + corner-vault lightning bolts on top of the
        // halo. Inventory rendering doesn't have a smooth GameTime feed but the shader reads
        // from the global GameTime uniform so the visuals animate continuously even when the
        // item is in a slot. The Java-side tumble uses the level's gameTime when present, else 0
        // so dropped items + JEI previews still show motion via the shader's internal clocks.
        boolean isManager = blockItem.getBlock() instanceof PhotonManagerBlock;
        if (isManager) {
            VertexConsumer gyro = buffer.getBuffer(PhotonRenderTypes.PHOTON_GYROSCOPE);
            // With no level (main menu / JEI preview) the world clock is frozen, so drive the tumble
            // off wall-clock millis instead — feeding 0 would leave the rings visibly stuck.
            boolean hasLevel = Minecraft.getInstance().level != null;
            long gameTime = hasLevel
                    ? Minecraft.getInstance().level.getGameTime()
                    : (long) (net.minecraft.Util.getMillis() / 1000f * 20f);
            float partial = hasLevel ? Minecraft.getInstance().getFrameTime() : 0.0f;
            PhotonNodeRenderer.renderGyroscope(pose, gyro,
                    0xFF, 0xD8, 0x60, gameTime, partial);

            VertexConsumer bolt = buffer.getBuffer(PhotonRenderTypes.PHOTON_BOLT);
            PhotonNodeRenderer.renderCornerBolts(pose, bolt, 0xB0, 0x7B, 0xFF);
        }

        // Flush halo (and the manager extras when present) before drawing the void so the dark
        // center overlays the bright halo, the gyroscope rings, and the bolts' inner endpoints.
        if (buffer instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(PhotonRenderTypes.PHOTON_HALO);
            if (isManager) {
                bs.endBatch(PhotonRenderTypes.PHOTON_GYROSCOPE);
                bs.endBatch(PhotonRenderTypes.PHOTON_BOLT);
            }
        }

        int voidR = clampByte((int) (r * 0.10f) + 4);
        int voidG = clampByte((int) (g * 0.10f) + 4);
        int voidB = clampByte((int) (b * 0.10f) + 6);
        VertexConsumer dark = buffer.getBuffer(PhotonRenderTypes.PHOTON_VOID);
        // Small toward-camera bias so the coplanar void wins the depth test over the halo.
        drawOrb(pose, dark, VOID_QUAD_HALF, voidR, voidG, voidB, 255, 0.003f);

        pose.popPose();
    }

    /** Draws just the black-hole halo + dark-void shader pair as a camera-facing billboard, with
     *  no block model. Used for the free-floating Uncontained Black Hole item — the same two
     *  passes the device BER and device-item path use, minus the baked geometry and the beams. */
    private static void renderHaloAndVoid(PoseStack pose, MultiBufferSource buffer, int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);

        VertexConsumer halo = buffer.getBuffer(PhotonRenderTypes.PHOTON_HALO);
        drawOrb(pose, halo, HALO_QUAD_HALF, r, g, b, 255);

        // Flush the halo before the void so the dark sphere overlays the bright accretion ring.
        if (buffer instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(PhotonRenderTypes.PHOTON_HALO);
        }

        int voidR = clampByte((int) (r * 0.10f) + 4);
        int voidG = clampByte((int) (g * 0.10f) + 4);
        int voidB = clampByte((int) (b * 0.10f) + 6);
        VertexConsumer dark = buffer.getBuffer(PhotonRenderTypes.PHOTON_VOID);
        drawOrb(pose, dark, VOID_QUAD_HALF, voidR, voidG, voidB, 255, 0.003f);

        pose.popPose();
    }

    /** Draws the White Dwarf star as a camera-facing billboard running the dedicated sun shader. White
     *  vertex color so ColorModulator doesn't tint the shader's own white/blue-white palette;
     *  the whole stellar disc + corona is painted in the fragment shader. */
    private static void renderWhiteDwarf(PoseStack pose, MultiBufferSource buffer) {
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);

        VertexConsumer star = buffer.getBuffer(PhotonRenderTypes.PHOTON_WHITE_DWARF);
        drawOrb(pose, star, STAR_QUAD_HALF, 255, 255, 255, 255);

        if (buffer instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(PhotonRenderTypes.PHOTON_WHITE_DWARF);
        }
        pose.popPose();
    }

    /** Emits a camera-facing {@link PhotonOrb} billboard at the current pose center. */
    private static void drawOrb(PoseStack pose, VertexConsumer vc, float half,
                                int red, int green, int blue, int alpha) {
        drawOrb(pose, vc, half, red, green, blue, alpha, 0.0f);
    }

    private static void drawOrb(PoseStack pose, VertexConsumer vc, float half,
                                int red, int green, int blue, int alpha, float depthBias) {
        Matrix4f m = pose.last().pose();
        PhotonOrb.draw(m, vc, half, red, green, blue, alpha, depthBias);
    }

    private static int clampByte(int v) { return Math.max(0, Math.min(255, v)); }
}
