package com.quantumchanneling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.quantumchanneling.QuantumChanneling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * BlockEntityWithoutLevelRenderer for the emitter and receiver items. Mirrors the
 * {@link PhotonNodeRenderer} server-side BER: renders the baked block model first (the dark shell
 * + corner posts) then overlays the same GLSL halo + dark-void shaders on top. This way the
 * black-hole + accretion effect is visible in the hotbar, inventory slot, item frame, hand, and
 * dropped form — not just in the world.
 *
 * <p>The shader uses a camera-facing billboard which would normally look correct only when the
 * world camera is set up. For item rendering Minecraft establishes a per-transform orientation
 * (e.g. inventory items rotate to a standard isometric view); the camera-orientation quaternion
 * is still defined and produces a plausibly-oriented billboard. Beams aren't drawn — they only
 * make sense on a placed device with connected neighbors.
 */
public class PhotonItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final PhotonItemRenderer INSTANCE = new PhotonItemRenderer();

    /** Same accent colors as the BER. Blue for emitter, red for receiver. */
    private static final int EMITTER_RGB  = 0x4FA0FF;
    private static final int RECEIVER_RGB = 0xFF5560;

    /** Sized identically to the BER quads so the in-world and item visuals match. */
    private static final float HALO_QUAD_HALF = 0.32f;
    private static final float VOID_QUAD_HALF = 0.14f;

    private PhotonItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
              Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context,
                             PoseStack pose, MultiBufferSource buffer,
                             int packedLight, int packedOverlay) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return;

        // 1) Render the baked block model — the dark shell, accent ring segments, corner posts.
        //    No ports because no neighbors are connected from the item.
        BlockState state = blockItem.getBlock().defaultBlockState();
        Minecraft.getInstance().getBlockRenderer()
                .renderSingleBlock(state, pose, buffer, packedLight, packedOverlay);

        // 2) Render the shader effect on top — same halo + void passes as the BER.
        boolean isEmitter = stack.is(QuantumChanneling.PHOTON_EMITTER_ITEM.get());
        int rgb = isEmitter ? EMITTER_RGB : RECEIVER_RGB;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);

        VertexConsumer halo = buffer.getBuffer(PhotonRenderTypes.PHOTON_HALO);
        drawShadedBillboard(pose, halo, HALO_QUAD_HALF, r, g, b, 255, 0.0f);

        // Flush halo before drawing the void so the dark center overlays the bright halo.
        if (buffer instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(PhotonRenderTypes.PHOTON_HALO);
        }

        int voidR = clampByte((int) (r * 0.10f) + 4);
        int voidG = clampByte((int) (g * 0.10f) + 4);
        int voidB = clampByte((int) (b * 0.10f) + 6);
        VertexConsumer dark = buffer.getBuffer(PhotonRenderTypes.PHOTON_VOID);
        // See PhotonNodeRenderer — negative bias pulls toward the camera (local +Z after the
        // camera rotation points away from the viewer in Minecraft's PoseStack convention).
        drawShadedBillboard(pose, dark, VOID_QUAD_HALF, voidR, voidG, voidB, 255, -0.003f);

        pose.popPose();
    }

    /** Camera-facing quad with UV (0,0)..(1,1) corners. {@code depthBias} pulls the quad toward
     *  the camera in local +Z after the billboard rotation — used to break coplanar-billboard
     *  z-fighting (the dark void over the bright halo). */
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
}
