package com.quantumchanneling.compat.jei;

import com.quantumchanneling.client.PhotonNodeScreen;
import com.quantumchanneling.compat.mekanism.ChemicalCompat;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Ghost-ingredient handler for the item, fluid and gas filter grids on the Photon Node screen.
 * Returns one {@link Target} per visible filter slot so JEI highlights them individually while the
 * user drags an ingredient. Accepts:
 * <ul>
 *   <li><b>Items</b> — item drags; the item's registry id goes into the filter.</li>
 *   <li><b>Fluids</b> — fluid drags, and item drags of anything holding a fluid (buckets, tanks).
 *       The container's fluid is used, never the container item itself.</li>
 *   <li><b>Gas</b> — Mekanism chemical drags (identified through JEI's ingredient helper, so no
 *       Mekanism types are needed), and filled chemical tanks or canisters.</li>
 * </ul>
 */
public class PhotonNodeGhostHandler implements IGhostIngredientHandler<PhotonNodeScreen> {
    private @Nullable IIngredientManager ingredients;

    void setIngredientManager(@Nullable IIngredientManager ingredients) {
        this.ingredients = ingredients;
    }

    @Override
    public <I> List<Target<I>> getTargetsTyped(PhotonNodeScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        IIngredientType<I> type = ingredient.getType();
        if (screen.isItemsModeActive() && type == VanillaTypes.ITEM_STACK) {
            return targets(screen.getItemsSlotCount(), screen::getItemsSlotRect, ing -> itemId(ing),
                    screen::acceptDroppedFilterItem);
        }
        if (screen.isFluidsModeActive()) {
            if (type == NeoForgeTypes.FLUID_STACK) {
                return targets(screen.getFluidsSlotCount(), screen::getFluidsSlotRect, ing -> fluidStackId(ing),
                        screen::acceptDroppedFilterFluid);
            }
            if (type == VanillaTypes.ITEM_STACK) {
                return targets(screen.getFluidsSlotCount(), screen::getFluidsSlotRect, ing -> containedFluidId(ing),
                        screen::acceptDroppedFilterFluid);
            }
        }
        if (screen.isGasModeActive() && ChemicalCompat.isAvailable()) {
            Function<I, Identifier> read = type == VanillaTypes.ITEM_STACK
                    ? ing -> ing instanceof ItemStack stack ? ChemicalCompat.chemicalIn(stack) : null
                    : ing -> chemicalId(type, ing);
            return targets(screen.getGasesSlotCount(), screen::getGasesSlotRect, read, screen::acceptDroppedFilterGas);
        }
        return Collections.emptyList();
    }

    private interface Drop {
        void accept(Identifier id, int slot);
    }

    private static <I> List<Target<I>> targets(int count, IntFunction<@Nullable Rect2i> rects,
                                               Function<I, @Nullable Identifier> read, Drop drop) {
        List<Target<I>> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int slot = i;
            Rect2i rect = rects.apply(slot);
            if (rect == null) continue;
            out.add(new Target<>() {
                @Override public Rect2i getArea() { return rect; }

                @Override
                public void accept(I ingredient) {
                    Identifier id = read.apply(ingredient);
                    if (id != null) drop.accept(id, slot);
                }
            });
        }
        return out;
    }

    private static @Nullable Identifier itemId(Object ingredient) {
        return ingredient instanceof ItemStack stack && !stack.isEmpty()
                ? BuiltInRegistries.ITEM.getKey(stack.getItem()) : null;
    }

    private static @Nullable Identifier fluidStackId(Object ingredient) {
        return ingredient instanceof FluidStack fs && !fs.isEmpty()
                ? BuiltInRegistries.FLUID.getKey(fs.getFluid()) : null;
    }

    private static @Nullable Identifier containedFluidId(Object ingredient) {
        if (!(ingredient instanceof ItemStack stack) || stack.isEmpty()) return null;
        Fluid fluid = PhotonNodeScreen.fluidIn(stack);
        return fluid == null || fluid == Fluids.EMPTY ? null : BuiltInRegistries.FLUID.getKey(fluid);
    }

    private <I> @Nullable Identifier chemicalId(IIngredientType<I> type, I ingredient) {
        if (ingredients == null) return null;
        Identifier id = ingredients.getIngredientHelper(type).getIdentifier(ingredient);
        return ChemicalCompat.isChemical(id) ? id : null;
    }

    @Override
    public void onComplete() {}
}
