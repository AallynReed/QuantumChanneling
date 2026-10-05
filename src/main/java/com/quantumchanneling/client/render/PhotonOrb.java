package com.quantumchanneling.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Viewer-facing billboard for the radial photon shaders (halo, void, white-dwarf sun).
 *
 * <p>The facing comes from the full model → view transform (the active model-view matrix times
 * the pose), not from the world camera, so the quad faces the viewer in every context: world,
 * inventory slot, hand, item frame, dropped entity. Inside the GUI the world camera is meaningless
 * and a camera-oriented quad would go edge-on.
 *
 * <p>One clean quad per pass, so no z-fighting. The shader's radial shading supplies the 3D look.
 * Coplanar passes at the same centre (halo + void) are separated by a small {@code depthBias}
 * that nudges the later pass toward the viewer so it wins the depth test deterministically.
 *
 * <p>Only call this from inside a geometry callback: the model-view matrix is read at draw time.
 */
public final class PhotonOrb {
    private PhotonOrb() {}

    /**
     * Emits one viewer-facing quad of half-size {@code h}, centred at the pose origin.
     *
     * @param depthBias model-space distance to pull the quad toward the viewer (0 for the base
     *                  pass; a small positive value, e.g. 0.003, for an overlay like the void).
     */
    public static void draw(PoseStack.Pose pose, VertexConsumer vc, float h,
                            int r, int g, int b, int a, float depthBias) {
        Matrix4f modelToView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.pose());
        // Inverting the rotation gives the viewer's screen axes expressed in model space.
        Matrix3f inv = new Matrix3f(modelToView).invert();

        // Normalised then scaled to the half-size, so the quad keeps whatever scale the pose
        // carries (e.g. the GUI display transform).
        Vector3f right = inv.transform(new Vector3f(1.0f, 0.0f, 0.0f)).normalize().mul(h);
        Vector3f up    = inv.transform(new Vector3f(0.0f, 1.0f, 0.0f)).normalize().mul(h);
        Vector3f bias  = depthBias == 0.0f ? new Vector3f()
                : inv.transform(new Vector3f(0.0f, 0.0f, 1.0f)).normalize().mul(depthBias);

        corner(pose, vc, bias, right, up, -1, -1, r, g, b, a, 0.0f, 0.0f);
        corner(pose, vc, bias, right, up,  1, -1, r, g, b, a, 1.0f, 0.0f);
        corner(pose, vc, bias, right, up,  1,  1, r, g, b, a, 1.0f, 1.0f);
        corner(pose, vc, bias, right, up, -1,  1, r, g, b, a, 0.0f, 1.0f);
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer vc, Vector3f bias, Vector3f right, Vector3f up,
                               int sx, int sy, int r, int g, int b, int a, float u, float v) {
        vc.addVertex(pose,
                        bias.x + sx * right.x + sy * up.x,
                        bias.y + sx * right.y + sy * up.y,
                        bias.z + sx * right.z + sy * up.z)
                .setUv(u, v)
                .setColor(r, g, b, a);
    }
}
