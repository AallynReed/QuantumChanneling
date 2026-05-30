package com.quantumchanneling.compat.mekanism;

import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.heat.IHeatHandler;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/**
 * Holder for the Mekanism gas + heat capabilities, resolved via Forge's {@link CapabilityToken}
 * mechanism so we don't need the {@code mekanism.common.capabilities.Capabilities} class (which
 * isn't shipped in Mekanism's API jar).
 *
 * <p><b>Class loading rule:</b> this class touches {@code mekanism.api.*} types, so it must NEVER
 * be loaded unless Mekanism is on the runtime classpath. Every caller goes through
 * {@link com.quantumchanneling.client.Compat#mekanismLoaded()} first; the JVM only resolves this
 * class when an actual reference is evaluated, so the gate is sufficient.
 */
public final class MekanismCaps {
    private MekanismCaps() {}

    public static final Capability<IGasHandler> GAS = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<IHeatHandler> HEAT = CapabilityManager.get(new CapabilityToken<>() {});
}
