package com.quantumchanneling.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

import java.util.function.Supplier;

/**
 * Render types shared by {@link PhotonNodeRenderer}.
 *
 * <p>Five programs:
 * <ul>
 *   <li>{@link #PHOTON_HALO} — additive blend, custom photon_halo fragment shader. Draws the
 *       bright accretion ring + outer atmospheric glow analytically.</li>
 *   <li>{@link #PHOTON_VOID} — translucent alpha blend, custom photon_void fragment shader. Dark
 *       event-horizon sphere + bright photon-sphere lensing rim, drawn on top of the halo to
 *       carve out the dark center.</li>
 *   <li>{@link #PHOTON_BEAM} — additive blend, custom photon_beam fragment shader. Flowing energy
 *       tube from the ball edge to each connected face port. Emitter / receiver only.</li>
 *   <li>{@link #PHOTON_GYROSCOPE} — additive blend, custom photon_gyroscope fragment shader.
 *       Three interlocking gold rings tumbling on different axes. Manager only.</li>
 *   <li>{@link #PHOTON_BOLT} — additive blend, custom photon_bolt fragment shader. Strobing
 *       lightning bolts arcing from the central orb to each of eight corner vaults.
 *       Manager only.</li>
 * </ul>
 *
 * <h3>Depth write — important</h3>
 * <p>HALO, VOID, and BEAM write depth (COLOR_DEPTH_WRITE) instead of just color. The previous
 * color-only setup left the depth buffer holding the solid pass's value at shader pixels, which
 * allowed other BERs that run after this one (notably {@code ChestRenderer}'s animated lid) to
 * paint over the shader because their LEQUAL test passed against that stale depth. With depth
 * write on, the shader claims its pixels — later BERs at the same screen position fail LEQUAL
 * and don't draw.
 *
 * <p>GYROSCOPE and BOLT use COLOR_WRITE only — they're rendered as multiple interleaving 3D
 * objects (three rings; eight bolts) and depth-writing would let one occlude another at the
 * intersection points, breaking the "interlocked" reading. Depth read stays on so neighbouring
 * solid blocks still occlude correctly.
 *
 * <p>For depth-writing passes, the pixels that fail the shader's radial {@code if (d > 1.0)
 * discard;} don't write depth either (GLSL discard skips both color and depth output), so the
 * depth claim is exactly the visible disc.
 */
public class PhotonRenderTypes extends RenderType {
    private PhotonRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                              int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                              Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        throw new IllegalStateException("PhotonRenderTypes is a static holder — do not instantiate.");
    }

    /** A failed-load photon shader field reads null; without a guard the render loop NPEs deep in
     *  the shader bind. Fall back to a stock position/color/tex program so the pass still draws
     *  (wrong-looking, but no crash). */
    private static Supplier<ShaderInstance> orStock(Supplier<ShaderInstance> custom) {
        return () -> {
            ShaderInstance s = custom.get();
            return s != null ? s : GameRenderer.getPositionColorTexShader();
        };
    }

    private static final ShaderStateShard PHOTON_HALO_SHADER =
            new ShaderStateShard(orStock(PhotonShaders::getHaloShader));

    private static final ShaderStateShard PHOTON_VOID_SHADER =
            new ShaderStateShard(orStock(PhotonShaders::getVoidShader));

    private static final ShaderStateShard PHOTON_BEAM_SHADER =
            new ShaderStateShard(orStock(PhotonShaders::getBeamShader));

    private static final ShaderStateShard PHOTON_GYROSCOPE_SHADER =
            new ShaderStateShard(orStock(PhotonShaders::getGyroscopeShader));

    private static final ShaderStateShard PHOTON_BOLT_SHADER =
            new ShaderStateShard(orStock(PhotonShaders::getBoltShader));

    private static final ShaderStateShard PHOTON_IMPLOSION_SHADER =
            new ShaderStateShard(orStock(PhotonShaders::getImplosionShader));

    private static final ShaderStateShard PHOTON_SUPERNOVA_SHADER =
            new ShaderStateShard(orStock(PhotonShaders::getSupernovaShader));

    private static final ShaderStateShard PHOTON_REFRACTION_SHADER =
            new ShaderStateShard(orStock(PhotonShaders::getRefractionShader));

    private static final ShaderStateShard PHOTON_WHITE_DWARF_SHADER =
            new ShaderStateShard(orStock(PhotonShaders::getWhiteDwarfShader));

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

    /** Gyroscope ring — additive blend, custom shader. Depth WRITE is disabled here on purpose:
     *  the three rings interleave in 3D, so each one must not block the others' pixels via depth
     *  test failures. Depth READ stays on so the rings still get occluded correctly by the block
     *  shell + neighbouring solid blocks. The alpha discard inside the shader keeps the soft
     *  fringe from contributing to the framebuffer. */
    public static final RenderType PHOTON_GYROSCOPE = RenderType.create(
            "quantumchanneling:photon_gyroscope",
            DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,
            2048,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(PHOTON_GYROSCOPE_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** Lightning bolts arcing from the orb to the corner vaults — additive, color-write only
     *  (same rationale as the gyroscope: bolts and rings interleave in 3D, none should depth-block
     *  the others). The strobe + alpha-discard in photon_bolt.fsh keeps the bolts visually thin
     *  and intermittent — they don't pollute the depth even if write were on. */
    public static final RenderType PHOTON_BOLT = RenderType.create(
            "quantumchanneling:photon_bolt",
            DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,
            4096,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(PHOTON_BOLT_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** Phase-1 supernova flash — dedicated white/gold shader (NOT the shared halo, which the
     *  emitter/receiver/manager black holes use). Depth read + write both off: the flash is a
     *  transient overlay that should render over everything and never claim depth pixels. */
    public static final RenderType PHOTON_BURST = RenderType.create(
            "quantumchanneling:photon_burst",
            DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,
            2048,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(PHOTON_SUPERNOVA_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** Phase-1 refraction beams radiating from the flash. Dedicated white-with-rainbow-fringe
     *  shader (NOT the shared beam shader used by emitter/receiver). Same depth policy as the
     *  supernova — additive, no depth read/write. */
    public static final RenderType PHOTON_RIBBON = RenderType.create(
            "quantumchanneling:photon_ribbon",
            DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,
            2048,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(PHOTON_REFRACTION_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** Dark swirling implosion vortex — the "after" pair to PHOTON_BURST. Uses the implosion
     *  shader plus TRANSLUCENT (alpha-blend) transparency so the dark colors actually DARKEN the
     *  framebuffer instead of brightening it like an additive pass would. Depth read/write both
     *  off, same reasoning as the burst — it's a transient overlay and shouldn't fight occlusion
     *  with the world. */
    public static final RenderType PHOTON_IMPLOSION = RenderType.create(
            "quantumchanneling:photon_implosion",
            DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,
            2048,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(PHOTON_IMPLOSION_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** White Dwarf star — TRANSLUCENT (alpha) blend, not additive. A sun is an opaque body, so the
     *  disc renders solid (alpha = 1) with the surface texture varying brightness rather than
     *  transparency; only the corona / glow fades out. Additive blending made the darker surface
     *  detail let the background show through. */
    public static final RenderType PHOTON_WHITE_DWARF = RenderType.create(
            "quantumchanneling:photon_white_dwarf",
            DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,
            2048,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(PHOTON_WHITE_DWARF_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));
}
