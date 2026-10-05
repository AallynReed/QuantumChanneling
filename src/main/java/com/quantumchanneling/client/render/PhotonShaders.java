package com.quantumchanneling.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.quantumchanneling.QuantumChanneling;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.IOException;

/**
 * Static holders + registration callbacks for the custom GLSL shaders used by the photon-glow
 * renderer. Two programs: {@code photon_void} (dark sphere, normal alpha blend) and
 * {@code photon_halo} (bright accretion-disk halo, additive blend). Both share the same vertex
 * shader.
 *
 * <p>Held as plain static fields rather than {@link java.util.function.Supplier}s so the render
 * type's {@link net.minecraft.client.renderer.RenderStateShard.ShaderStateShard} can read them
 * directly per-frame without going through a lambda.
 */
public final class PhotonShaders {
    private PhotonShaders() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    public static ShaderInstance photonVoidShader;
    public static ShaderInstance photonHaloShader;
    public static ShaderInstance photonBeamShader;
    public static ShaderInstance photonGyroscopeShader;
    public static ShaderInstance photonBoltShader;
    public static ShaderInstance photonImplosionShader;
    public static ShaderInstance photonSupernovaShader;
    public static ShaderInstance photonRefractionShader;
    public static ShaderInstance photonWhiteDwarfShader;

    /** Mod-event-bus listener — wires the shaders into Minecraft's shader registry on resource load.
     *  Each program loads in isolation: a failure logs and leaves that field null so one broken
     *  shader can't hard-crash the client (including F3+T reloads); the render types fall back to a
     *  stock shader when a field is null. */
    public static void register(RegisterShadersEvent event) {
        load(event, "photon_void",       instance -> photonVoidShader = instance);
        load(event, "photon_halo",       instance -> photonHaloShader = instance);
        load(event, "photon_beam",       instance -> photonBeamShader = instance);
        load(event, "photon_gyroscope",  instance -> photonGyroscopeShader = instance);
        load(event, "photon_bolt",       instance -> photonBoltShader = instance);
        load(event, "photon_implosion",  instance -> photonImplosionShader = instance);
        load(event, "photon_supernova",  instance -> photonSupernovaShader = instance);
        load(event, "photon_refraction", instance -> photonRefractionShader = instance);
        load(event, "photon_white_dwarf", instance -> photonWhiteDwarfShader = instance);
    }

    private static void load(RegisterShadersEvent event, String name,
                             java.util.function.Consumer<ShaderInstance> sink) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(QuantumChanneling.MODID, name),
                            DefaultVertexFormat.POSITION_COLOR_TEX),
                    sink::accept);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load Quantum Channeling photon shader '{}'; it will be disabled", name, e);
        }
    }

    public static ShaderInstance getVoidShader()       { return photonVoidShader; }
    public static ShaderInstance getHaloShader()       { return photonHaloShader; }
    public static ShaderInstance getBeamShader()       { return photonBeamShader; }
    public static ShaderInstance getGyroscopeShader()  { return photonGyroscopeShader; }
    public static ShaderInstance getBoltShader()       { return photonBoltShader; }
    public static ShaderInstance getImplosionShader()  { return photonImplosionShader; }
    public static ShaderInstance getSupernovaShader()  { return photonSupernovaShader; }
    public static ShaderInstance getRefractionShader() { return photonRefractionShader; }
    public static ShaderInstance getWhiteDwarfShader() { return photonWhiteDwarfShader; }
}
