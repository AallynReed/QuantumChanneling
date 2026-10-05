package com.quantumchanneling.compat.mekanism;

import com.quantumchanneling.client.Compat;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.resource.RegisteredResource;
import org.jetbrains.annotations.Nullable;

/**
 * Mekanism chemicals (the "gas" resource in the UI) through NeoForge's transfer API.
 *
 * <p>Mekanism exposes chemicals as {@code ResourceHandler<ChemicalResource>} under the
 * {@code mekanism:chemical_handler} capability. Capabilities are keyed by name and handler class,
 * so creating the token here yields the same instance Mekanism registers — no compile-time
 * dependency. Every chemical resource is a {@link RegisteredResource}, which is all the routing
 * and filtering code needs.
 */
public final class ChemicalCompat {
    private ChemicalCompat() {}

    private static final Identifier HANDLER = Identifier.fromNamespaceAndPath("mekanism", "chemical_handler");
    private static final Identifier REGISTRY = Identifier.fromNamespaceAndPath("mekanism", "chemical");

    public static final BlockCapability<ResourceHandler<RegisteredResource<?>>, @Nullable Direction> BLOCK =
            BlockCapability.createSided(HANDLER, ResourceHandler.asClass());

    public static final ItemCapability<ResourceHandler<RegisteredResource<?>>, ItemAccess> ITEM =
            ItemCapability.create(HANDLER, ResourceHandler.asClass(), ItemAccess.class);

    private static @Nullable RegisteredResource<?> empty;
    private static boolean emptyResolved;

    public static boolean isAvailable() {
        return Compat.mekanismLoaded();
    }

    /**
     * {@code ChemicalResource.EMPTY} — what an empty chemical slot reports. Read reflectively (the
     * one place this class names a Mekanism type) so the mod still has no hard dependency; null
     * when Mekanism is absent or the field moved.
     */
    public static @Nullable RegisteredResource<?> emptyResource() {
        if (!emptyResolved) {
            emptyResolved = true;
            if (isAvailable()) {
                try {
                    Object value = Class.forName("mekanism.api.chemical.ChemicalResource").getField("EMPTY").get(null);
                    if (value instanceof RegisteredResource<?> resource) empty = resource;
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    // Leave it null — the emitter then simply doesn't expose a chemical input.
                }
            }
        }
        return empty;
    }

    /** True when {@code id} names a registered Mekanism chemical. */
    public static boolean isChemical(Identifier id) {
        if (!isAvailable()) return false;
        Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(REGISTRY);
        return registry != null && registry.containsKey(id);
    }

    /** Registry id of the first chemical held by {@code stack} (a filled tank, canister, …). */
    public static @Nullable Identifier chemicalIn(ItemStack stack) {
        if (stack.isEmpty() || !isAvailable()) return null;
        ResourceHandler<RegisteredResource<?>> handler = ItemAccess.forStack(stack.copy()).getCapability(ITEM);
        if (handler == null) return null;
        for (int i = 0; i < handler.size(); i++) {
            RegisteredResource<?> resource = handler.getResource(i);
            if (resource.isEmpty() || handler.getAmountAsLong(i) <= 0) continue;
            Holder<?> holder = resource.typeHolder();
            return holder.unwrapKey().map(k -> k.identifier()).orElse(null);
        }
        return null;
    }
}
