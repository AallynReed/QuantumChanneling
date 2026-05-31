package com.quantumchanneling.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;

/**
 * Render types shared by {@link PhotonNodeRenderer}.
 *
 * <p>Three programs:
 * <ul>
 *   <li>{@link #PHOTON_HALO} — additive blend, custom photon_halo fragment shader. Draws the
 *       bright accretion ring + outer atmospheric glow analytically.</li>
 *   <li>{@link #PHOTON_VOID} — translucent alpha blend, custom photon_void fragment shader. Dark
 *       event-horizon sphere + bright photon-sphere lensing rim, drawn on top of the halo to
 *       carve out the dark center.</li>
 *   <li>{@link #PHOTON_BEAM} — additive blend, custom photon_beam fragment shader. Flowing energy
 *       tube from the ball edge to each connected face port.</li>
 * </ul>
 *
 * <h3>Depth write — important</h3>
 * <p>All three passes write depth (COLOR_DEPTH_WRITE) instead of just color. The previous
 * color-only setup left the depth buffer holding the solid pass's value at shader pixels, which
 * allowed other BERs that run after this one (notably {@code ChestRenderer}'s animated lid) to
 * paint over the shader because their LEQUAL test passed against that stale depth. With depth
 * write on, the shader claims its pixels — later BERs at the same screen position fail LEQUAL
 * and don't draw.
 *
 * <p>The pixels that fail the shader's radial {@code if (d > 1.0) discard;} don't write depth
 * either (GLSL discard skips both color and depth output), so the depth claim is exactly the
 * visible disc.
 *
 * <p>The previously legacy ADDITIVE_GLOW render type is retained for backward compatibility but
 * is no longer used.
 */
public class PhotonRenderTypes extends RenderType {
    private PhotonRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                              int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                              Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        throw new IllegalStateException("PhotonRenderTypes is a static holder — do not instantiate.");
    }

    private static final ShaderStateShard POSITION_COLOR_SHADER =
            new ShaderStateShard(GameRenderer::getPositionColorShader);

    private static final ShaderStateShard PHOTON_HALO_SHADER =
            new ShaderStateShard(PhotonShaders::getHaloShader);

    private static final ShaderStateShard PHOTON_VOID_SHADER =
            new ShaderStateShard(PhotonShaders::getVoidShader);

    private static final ShaderStateShard PHOTON_BEAM_SHADER =
            new ShaderStateShard(PhotonShaders::getBeamShader);

    /** Bright accretion ring + atmospheric halo. Additive blend; custom shader; writes depth. */
    public static final RenderType PHOTON_HALO = RenderType.create(
            "quantumchanneling:photon_halo",
            DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,
            2048,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(PHOTON_HALO_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY) // GL_ONE + GL_ONE
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** Dark event-horizon sphere. Translucent alpha blend; custom shader; writes depth. */
    public static final RenderType PHOTON_VOID = RenderType.create(
            "quantumchanneling:photon_void",
            DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,
            1024,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(PHOTON_VOID_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** Beam tube — additive blend, custom shader; writes depth. */
    public static final RenderType PHOTON_BEAM = RenderType.create(
            "quantumchanneling:photon_beam",
            DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,
            2048,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(PHOTON_BEAM_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** Legacy additive position-color. Unused by the current BER, retained for back-compat. */
    public static final RenderType ADDITIVE_GLOW = RenderType.create(
            "quantumchanneling:additive_glow",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            2048,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));
}
