package com.quantumchanneling.compat.mekanism;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.heat.IHeatHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Attaches Mekanism gas + heat capabilities to our emitters and receivers via
 * {@link AttachCapabilitiesEvent}. This class is in the {@code compat.mekanism} package and
 * imports Mekanism API types, so it must NOT be loaded unless Mekanism is present.
 *
 * <p>Call {@link #register()} from the main mod class, gated by
 * {@link com.quantumchanneling.client.Compat#mekanismLoaded()}. Touching this class outside that
 * gate would cause a {@link NoClassDefFoundError} when Mekanism is absent.
 */
public final class MekanismCapAttacher {
    private static final ResourceLocation GAS_KEY = new ResourceLocation(QuantumChanneling.MODID, "gas");
    private static final ResourceLocation HEAT_KEY = new ResourceLocation(QuantumChanneling.MODID, "heat");

    /** Subscribe to AttachCapabilitiesEvent. Idempotent — pass-through safe to call multiple times. */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(MekanismCapAttacher.class);
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        BlockEntity obj = event.getObject();
        // Heat caps intentionally not attached — heat routing is disabled (no-op tick) until the
        // model gets a proper design pass. Re-add the HEAT_KEY entries here to re-enable.
        if (obj instanceof PhotonEmitterBlockEntity emitter) {
            event.addCapability(GAS_KEY, new SingleCapProvider<>(MekanismCaps.GAS, GasIntegration.emitterHandler(emitter)));
        } else if (obj instanceof PhotonReceiverBlockEntity rcv) {
            event.addCapability(GAS_KEY, new SingleCapProvider<>(MekanismCaps.GAS, GasIntegration.receiverHandler()));
        }
    }

    /** Tiny ICapabilityProvider that exposes one cap with a fixed handler instance. */
    private static final class SingleCapProvider<T> implements ICapabilityProvider {
        private final Capability<T> cap;
        private final LazyOptional<T> opt;
        SingleCapProvider(Capability<T> cap, T handler) {
            this.cap = cap;
            this.opt = LazyOptional.of(() -> handler);
        }
        @Override
        public <U> @NotNull LazyOptional<U> getCapability(@NotNull Capability<U> requested, @Nullable net.minecraft.core.Direction side) {
            return requested == cap ? opt.cast() : LazyOptional.empty();
        }
    }
}
