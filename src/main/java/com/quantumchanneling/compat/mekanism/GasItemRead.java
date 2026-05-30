package com.quantumchanneling.compat.mekanism;

import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Reads the gas registry id stored inside an item (filled chemical tank, gas canister, etc.) via
 * Mekanism's per-item {@link IGasHandler} capability. Mekanism-gated — only loaded when the host
 * has Mekanism present (the JVM lazy-resolves this class on first call).
 */
public final class GasItemRead {
    private GasItemRead() {}

    public static ResourceLocation read(ItemStack stack) {
        if (stack.isEmpty()) return null;
        IGasHandler handler = stack.getCapability(MekanismCaps.GAS).orElse(null);
        if (handler == null) return null;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            GasStack gas = handler.getChemicalInTank(tank);
            if (gas == null || gas.isEmpty()) continue;
            return MekanismAPI.gasRegistry().getKey(gas.getType());
        }
        return null;
    }
}
