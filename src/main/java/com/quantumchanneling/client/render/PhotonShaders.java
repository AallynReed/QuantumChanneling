package com.quantumchanneling.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.quantumchanneling.QuantumChanneling;
import net.minecraft.client.renderer.RenderPipelines;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.List;
import java.util.Optional;

/**
 * Render pipelines for the photon effects. Every program shares the {@code photon_glow} vertex
 * shader (position + color + a 0..1 quad UV the fragment shader treats as a canvas) and reads only
 * the standard uniform blocks: transforms, projection and globals ({@code GameTime}).
 *
 * <p>Depth policy:
 * <ul>
 *   <li>Halo, void, beam and white dwarf write depth, so block-entity renderers drawn later at the
 *       same pixels (a chest lid, say) fail the depth test instead of painting over the effect.
 *       Pixels the shader discards write nothing, so the claim is exactly the visible disc.</li>
 *   <li>Gyroscope and bolts interleave in 3D — depth writes would let one ring or bolt cut holes
 *       in another — so they only test depth.</li>
 *   <li>The collapse burst passes are transient overlays: no depth test, no depth write.</li>
 * </ul>
 */
public final class PhotonShaders {
    private PhotonShaders() {}

    private static final DepthStencilState TEST_ONLY = new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false);
    private static final DepthStencilState NONE = new DepthStencilState(CompareOp.ALWAYS_PASS, false);

    public static final RenderPipeline HALO = pipeline("photon_halo", BlendFunction.LIGHTNING, DepthStencilState.DEFAULT);
    public static final RenderPipeline VOID = pipeline("photon_void", BlendFunction.TRANSLUCENT, DepthStencilState.DEFAULT);
    public static final RenderPipeline BEAM = pipeline("photon_beam", BlendFunction.LIGHTNING, DepthStencilState.DEFAULT);
    public static final RenderPipeline GYROSCOPE = pipeline("photon_gyroscope", BlendFunction.LIGHTNING, TEST_ONLY);
    public static final RenderPipeline BOLT = pipeline("photon_bolt", BlendFunction.LIGHTNING, TEST_ONLY);
    public static final RenderPipeline SUPERNOVA = pipeline("photon_supernova", BlendFunction.LIGHTNING, NONE);
    public static final RenderPipeline REFRACTION = pipeline("photon_refraction", BlendFunction.LIGHTNING, NONE);
    /** Darkens the scene rather than brightening it, hence alpha blending. */
    public static final RenderPipeline IMPLOSION = pipeline("photon_implosion", BlendFunction.TRANSLUCENT, NONE);
    /** A sun is an opaque body: alpha blend so darker surface detail doesn't let the background through. */
    public static final RenderPipeline WHITE_DWARF = pipeline("photon_white_dwarf", BlendFunction.TRANSLUCENT, DepthStencilState.DEFAULT);

    private static final List<RenderPipeline> ALL =
            List.of(HALO, VOID, BEAM, GYROSCOPE, BOLT, SUPERNOVA, REFRACTION, IMPLOSION, WHITE_DWARF);

    private static RenderPipeline pipeline(String name, BlendFunction blend, DepthStencilState depth) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
                .withLocation(QuantumChanneling.id("pipeline/" + name))
                .withVertexShader(QuantumChanneling.id("core/photon_glow"))
                .withFragmentShader(QuantumChanneling.id("core/" + name))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(Optional.of(blend), ColorTargetState.WRITE_ALL))
                .withDepthStencilState(depth)
                .withCull(false)
                .build();
    }

    /** Mod-bus listener — precompiles the pipelines with the rest of the game's. */
    public static void register(RegisterRenderPipelinesEvent event) {
        ALL.forEach(event::registerPipeline);
    }
}
