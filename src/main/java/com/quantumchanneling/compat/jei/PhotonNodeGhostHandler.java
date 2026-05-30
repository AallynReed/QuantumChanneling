package com.quantumchanneling.compat.jei;

import com.quantumchanneling.client.PhotonNodeScreen;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ghost-ingredient handler for the Photon Node items panel. Returns one {@link Target} per visible
 * filter slot (a 9×3 grid), so JEI highlights individual slots as the user drags an ingredient
 * over the panel — matching the AE2 view-cell drop UX.
 *
 * <p>The accept callback applies the drop to the channel via
 * {@link PhotonNodeScreen#acceptDroppedFilterItem(ResourceLocation, int)}: empty slots get a new
 * filter entry added, filled slots swap the existing entry for the dropped one.
 */
public class PhotonNodeGhostHandler implements IGhostIngredientHandler<PhotonNodeScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(PhotonNodeScreen screen,
                                               ITypedIngredient<I> ingredient,
                                               boolean doStart) {
        if (ingredient.getType() != VanillaTypes.ITEM_STACK) return Collections.emptyList();
        if (!screen.isItemsModeActive()) return Collections.emptyList();

        int n = screen.getItemsSlotCount();
        List<Target<I>> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final int slotIdx = i;
            Rect2i rect = screen.getItemsSlotRect(slotIdx);
            if (rect == null) continue;
            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() { return rect; }
                @Override
                public void accept(I ing) {
                    if (ing instanceof ItemStack stack && !stack.isEmpty()) {
                        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                        screen.acceptDroppedFilterItem(id, slotIdx);
                    }
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() { /* no-op */ }
}
