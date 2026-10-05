package com.quantumchanneling.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Camera-facing billboard for the radial photon shaders (halo, void, white-dwarf sun), with the
 * facing derived from the <i>modelview matrix</i> rather than the world camera.
 *
 * <p>The original billboard used {@code EntityRenderDispatcher.cameraOrientation()} — the world
 * camera — which is correct in the world but meaningless inside the inventory GUI (there the item
 * has its own view, so the quad went edge-on and vanished). Deriving the screen-right / screen-up
 * axes from the current modelview instead makes the quad face the viewer in <b>every</b> context:
 * world, inventory slot, hand, item frame, dropped entity.
 *
 * <p>One clean quad per pass means no z-fighting and no flicker. The shader's radial shading does
 * the 3D illusion (a glowing sphere / disc). Coplanar passes at the same center (halo + void) are
 * separated by a small {@code depthBias} that nudges the later pass toward the camera so it wins
 * the depth test deterministically.
 */
public final class PhotonOrb {
    private PhotonOrb() {}

    /**
     * Emits one camera-facing quad of half-size {@code h}, centered at the current pose origin.
     *
     * @param depthBias model-space distance to pull the quad toward the camera (0 for the base
     *                  pass; a small positive value, e.g. 0.003, for an overlay like the void so it
     *                  renders in front of the coplanar halo).
     */
    public static void draw(Matrix4f pose, VertexConsumer vc, float h,
                            int r, int g, int b, int a, float depthBias) {
        // Invert the modelview rotation to get the camera's screen axes expressed in model space.
        Matrix3f inv = new Matrix3f(pose);
        inv.invert();

        // Screen-right / screen-up in model space, each normalized then scaled to the half-size so
        // the quad respects whatever scale the pose carries (e.g. the GUI display transform).
        Vector3f right = inv.transform(new Vector3f(1.0f, 0.0f, 0.0f)).normalize().mul(h);
        Vector3f up    = inv.transform(new Vector3f(0.0f, 1.0f, 0.0f)).normalize().mul(h);

        // Toward-camera offset (+Z in view space) for the depth bias.
        float fx = 0.0f, fy = 0.0f, fz = 0.0f;
        if (depthBias != 0.0f) {
            Vector3f fwd = inv.transform(new Vector3f(0.0f, 0.0f, 1.0f)).normalize().mul(depthBias);
            fx = fwd.x; fy = fwd.y; fz = fwd.z;
        }

        // Corners: center (+bias) ± right ± up.
        float blx = fx - right.x - up.x, bly = fy - right.y - up.y, blz = fz - right.z - up.z;
        float brx = fx + right.x - up.x, bry = fy + right.y - up.y, brz = fz + right.z - up.z;
        float trx = fx + right.x + up.x, try_ = fy + right.y + up.y, trz = fz + right.z + up.z;
        float tlx = fx - right.x + up.x, tly = fy - right.y + up.y, tlz = fz - right.z + up.z;

        vc.vertex(pose, blx, bly, blz).color(r, g, b, a).uv(0.0f, 0.0f).endVertex();
        vc.vertex(pose, brx, bry, brz).color(r, g, b, a).uv(1.0f, 0.0f).endVertex();
        vc.vertex(pose, trx, try_, trz).color(r, g, b, a).uv(1.0f, 1.0f).endVertex();
        vc.vertex(pose, tlx, tly, tlz).color(r, g, b, a).uv(0.0f, 1.0f).endVertex();
    }
}
