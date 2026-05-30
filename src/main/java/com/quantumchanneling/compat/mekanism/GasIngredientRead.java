package com.quantumchanneling.compat.mekanism;

import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.GasStack;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime-instance reader for JEI/EMI ingredients that might be Mekanism gas types. Avoids
 * importing Mekanism's JEI plugin classes (which aren't in the API jar) by doing a plain
 * {@code instanceof GasStack} check at the call site.
 *
 * <p>Mekanism-gated — only loaded when the host has Mekanism present.
 */
public final class GasIngredientRead {
    private GasIngredientRead() {}

    public static @Nullable ResourceLocation tryRead(Object ingredient) {
        if (ingredient instanceof GasStack gas && !gas.isEmpty()) {
            return MekanismAPI.gasRegistry().getKey(gas.getType());
        }
        return null;
    }
}
