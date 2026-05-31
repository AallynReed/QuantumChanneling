package com.quantumchanneling.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.quantumchanneling.QuantumChanneling;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;

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

    public static ShaderInstance photonVoidShader;
    public static ShaderInstance photonHaloShader;
    public static ShaderInstance photonBeamShader;
    public static ShaderInstance photonGyroscopeShader;
    public static ShaderInstance photonBoltShader;

    /** Mod-event-bus listener — wires the shaders into Minecraft's shader registry on resource load. */
    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(QuantumChanneling.MODID, "photon_void"),
                            DefaultVertexFormat.POSITION_COLOR_TEX),
                    instance -> photonVoidShader = instance);

            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(QuantumChanneling.MODID, "photon_halo"),
                            DefaultVertexFormat.POSITION_COLOR_TEX),
                    instance -> photonHaloShader = instance);

            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(QuantumChanneling.MODID, "photon_beam"),
                            DefaultVertexFormat.POSITION_COLOR_TEX),
                    instance -> photonBeamShader = instance);

            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(QuantumChanneling.MODID, "photon_gyroscope"),
                            DefaultVertexFormat.POSITION_COLOR_TEX),
                    instance -> photonGyroscopeShader = instance);

            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(QuantumChanneling.MODID, "photon_bolt"),
                            DefaultVertexFormat.POSITION_COLOR_TEX),
                    instance -> photonBoltShader = instance);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Quantum Channeling photon shaders", e);
        }
    }

    public static ShaderInstance getVoidShader()       { return photonVoidShader; }
    public static ShaderInstance getHaloShader()       { return photonHaloShader; }
    public static ShaderInstance getBeamShader()       { return photonBeamShader; }
    public static ShaderInstance getGyroscopeShader()  { return photonGyroscopeShader; }
    public static ShaderInstance getBoltShader()       { return photonBoltShader; }
}
