package com.quantumchanneling.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Render types over {@link PhotonShaders}' pipelines. See there for the blend and depth rationale. */
public final class PhotonRenderTypes {
    private PhotonRenderTypes() {}

    /** Bright accretion ring + atmospheric halo. */
    public static final RenderType PHOTON_HALO = create("photon_halo", PhotonShaders.HALO, 2048);
    /** Dark event-horizon sphere, drawn over the halo to carve out its centre. */
    public static final RenderType PHOTON_VOID = create("photon_void", PhotonShaders.VOID, 1024);
    /** Flowing energy tube from the orb to each connected face port. */
    public static final RenderType PHOTON_BEAM = create("photon_beam", PhotonShaders.BEAM, 2048);
    /** Manager's three tumbling gold rings. */
    public static final RenderType PHOTON_GYROSCOPE = create("photon_gyroscope", PhotonShaders.GYROSCOPE, 2048);
    /** Manager's lightning arcs to the corner vaults. */
    public static final RenderType PHOTON_BOLT = create("photon_bolt", PhotonShaders.BOLT, 4096);
    /** Collapse phase 1: the white supernova flash. */
    public static final RenderType PHOTON_BURST = create("photon_burst", PhotonShaders.SUPERNOVA, 2048);
    /** Collapse phase 1: refraction beams radiating from the flash. */
    public static final RenderType PHOTON_RIBBON = create("photon_ribbon", PhotonShaders.REFRACTION, 2048);
    /** Collapse phase 2: the dark implosion vortex. */
    public static final RenderType PHOTON_IMPLOSION = create("photon_implosion", PhotonShaders.IMPLOSION, 2048);
    /** White Dwarf star. */
    public static final RenderType PHOTON_WHITE_DWARF = create("photon_white_dwarf", PhotonShaders.WHITE_DWARF, 2048);

    private static RenderType create(String name, RenderPipeline pipeline, int bufferSize) {
        return RenderType.create("quantumchanneling:" + name,
                RenderSetup.builder(pipeline).bufferSize(bufferSize).createRenderSetup());
    }
}
