package com.quantumchanneling.compat.jei;

import com.quantumchanneling.client.PhotonNodeScreen;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ghost-ingredient handler for both the items and fluids filter grids on the Photon Node screen.
 * Returns one {@link Target} per visible filter slot so JEI highlights them individually while the
 * user drags an ingredient.
 *
 * <p>The handler accepts:
 * <ul>
 *   <li><b>Items panel</b> — {@link VanillaTypes#ITEM_STACK} drags. Adds the item's registry id
 *       to the filter via {@link PhotonNodeScreen#acceptDroppedFilterItem}.</li>
 *   <li><b>Fluids panel</b> — {@link ForgeTypes#FLUID_STACK} drags AND item drags of containers
 *       that hold a fluid (filled buckets, mod tanks, capsules — any item exposing the fluid via
 *       {@link FluidUtil#getFluidContained}). The container's fluid id is used; the bucket item
 *       itself is never added to the filter.</li>
 * </ul>
 */
public class PhotonNodeGhostHandler implements IGhostIngredientHandler<PhotonNodeScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(PhotonNodeScreen screen,
                                               ITypedIngredient<I> ingredient,
                                               boolean doStart) {
        IIngredientType<I> type = ingredient.getType();
        if (screen.isItemsModeActive() && type == VanillaTypes.ITEM_STACK) {
            return buildItemTargets(screen);
        }
        if (screen.isFluidsModeActive()) {
            if (type == ForgeTypes.FLUID_STACK) return buildFluidTargets(screen);
            if (type == VanillaTypes.ITEM_STACK) return buildFluidFromContainerTargets(screen);
        }
        if (screen.isGasModeActive()) {
            // Two paths: direct gas drag (Mekanism's JEI gas type) — caught by checking the
            // ingredient's runtime class without importing Mekanism JEI's type registration; OR
            // an item drag that's a filled Mekanism chemical tank / canister, read via the gas
            // item cap.
            if (type == VanillaTypes.ITEM_STACK) return buildGasFromContainerTargets(screen);
            return buildGasTargetsForUnknownType(screen);
        }
        return Collections.emptyList();
    }

    private <I> List<Target<I>> buildItemTargets(PhotonNodeScreen screen) {
        int n = screen.getItemsSlotCount();
        List<Target<I>> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final int slotIdx = i;
            Rect2i rect = screen.getItemsSlotRect(slotIdx);
            if (rect == null) continue;
            targets.add(new Target<>() {
                @Override public Rect2i getArea() { return rect; }
                @Override public void accept(I ing) {
                    if (ing instanceof ItemStack stack && !stack.isEmpty()) {
                        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                        screen.acceptDroppedFilterItem(id, slotIdx);
                    }
                }
            });
        }
        return targets;
    }

    private <I> List<Target<I>> buildFluidTargets(PhotonNodeScreen screen) {
        int n = screen.getFluidsSlotCount();
        List<Target<I>> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final int slotIdx = i;
            Rect2i rect = screen.getFluidsSlotRect(slotIdx);
            if (rect == null) continue;
            targets.add(new Target<>() {
                @Override public Rect2i getArea() { return rect; }
                @Override public void accept(I ing) {
                    if (ing instanceof FluidStack fs && !fs.isEmpty()) {
                        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fs.getFluid());
                        if (id != null) screen.acceptDroppedFilterFluid(id, slotIdx);
                    }
                }
            });
        }
        return targets;
    }

    private <I> List<Target<I>> buildFluidFromContainerTargets(PhotonNodeScreen screen) {
        // Only offer slot targets when the dragged item ACTUALLY contains a fluid. Filtering out
        // non-fluid items here means JEI won't highlight our slots for, say, a stick or a sword.
        int n = screen.getFluidsSlotCount();
        List<Target<I>> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final int slotIdx = i;
            Rect2i rect = screen.getFluidsSlotRect(slotIdx);
            if (rect == null) continue;
            targets.add(new Target<>() {
                @Override public Rect2i getArea() { return rect; }
                @Override public void accept(I ing) {
                    if (!(ing instanceof ItemStack stack) || stack.isEmpty()) return;
                    Fluid fluid = fluidFromContainer(stack);
                    if (fluid == null || fluid == Fluids.EMPTY) return;
                    ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
                    if (id != null) screen.acceptDroppedFilterFluid(id, slotIdx);
                }
            });
        }
        return targets;
    }

    private <I> List<Target<I>> buildGasFromContainerTargets(PhotonNodeScreen screen) {
        int n = screen.getGasesSlotCount();
        List<Target<I>> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final int slotIdx = i;
            Rect2i rect = screen.getGasesSlotRect(slotIdx);
            if (rect == null) continue;
            targets.add(new Target<>() {
                @Override public Rect2i getArea() { return rect; }
                @Override public void accept(I ing) {
                    if (!(ing instanceof ItemStack stack) || stack.isEmpty()) return;
                    if (!com.quantumchanneling.client.Compat.mekanismLoaded()) return;
                    ResourceLocation id = com.quantumchanneling.compat.mekanism.GasItemRead.read(stack);
                    if (id != null) screen.acceptDroppedFilterGas(id, slotIdx);
                }
            });
        }
        return targets;
    }

    /**
     * Fallback target list for ingredients of unknown type — used when JEI hands us a Mekanism
     * gas ingredient. We don't import Mekanism's JEI plugin types, so we accept by runtime
     * instance check inside the callback.
     */
    private <I> List<Target<I>> buildGasTargetsForUnknownType(PhotonNodeScreen screen) {
        if (!com.quantumchanneling.client.Compat.mekanismLoaded()) return Collections.emptyList();
        int n = screen.getGasesSlotCount();
        List<Target<I>> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final int slotIdx = i;
            Rect2i rect = screen.getGasesSlotRect(slotIdx);
            if (rect == null) continue;
            targets.add(new Target<>() {
                @Override public Rect2i getArea() { return rect; }
                @Override public void accept(I ing) {
                    ResourceLocation id = com.quantumchanneling.compat.mekanism.GasIngredientRead.tryRead(ing);
                    if (id != null) screen.acceptDroppedFilterGas(id, slotIdx);
                }
            });
        }
        return targets;
    }

    /**
     * Pulls a {@link Fluid} out of a container ItemStack. Tries the standard Forge bucket path
     * ({@link FluidUtil#getFluidContained}) first; that covers vanilla water/lava buckets, milk,
     * potion-like fluid items, Mekanism Pressurized Tubes' creative tanks, etc.
     */
    private static Fluid fluidFromContainer(ItemStack stack) {
        return FluidUtil.getFluidContained(stack)
                .filter(fs -> !fs.isEmpty())
                .map(FluidStack::getFluid)
                .orElse(null);
    }

    @Override
    public void onComplete() { /* no-op */ }
}
