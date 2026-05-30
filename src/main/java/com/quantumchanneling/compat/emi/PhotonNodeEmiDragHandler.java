package com.quantumchanneling.compat.emi;

import com.quantumchanneling.client.PhotonNodeScreen;
import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * EMI's drag-and-drop handler for the Photon Node items panel. EMI hands us the dragged ingredient
 * and the screen-space drop point; we map the point to a filter-grid slot and forward to the
 * screen's shared accept method ({@link PhotonNodeScreen#acceptDroppedFilterItem(ResourceLocation, int)}).
 *
 * <p>The {@link #render} hook draws a faint highlight on every visible slot while the user is
 * dragging, mirroring the JEI ghost-handler UX so drop zones are obvious.
 */
public class PhotonNodeEmiDragHandler implements EmiDragDropHandler<PhotonNodeScreen> {

    @Override
    public boolean dropStack(PhotonNodeScreen screen, EmiIngredient stack, int x, int y) {
        if (!screen.isItemsModeActive()) return false;
        ItemStack itemStack = resolveItemStack(stack);
        if (itemStack.isEmpty()) return false;
        int slotIdx = screen.getItemsSlotAt(x, y);
        if (slotIdx < 0) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        screen.acceptDroppedFilterItem(id, slotIdx);
        return true;
    }

    @Override
    public void render(PhotonNodeScreen screen, EmiIngredient dragged, GuiGraphics draw,
                       int mouseX, int mouseY, float delta) {
        if (!screen.isItemsModeActive()) return;
        if (resolveItemStack(dragged).isEmpty()) return;
        int n = screen.getItemsSlotCount();
        for (int i = 0; i < n; i++) {
            Rect2i r = screen.getItemsSlotRect(i);
            if (r == null) continue;
            // A 1-px highlight overlay so the user sees the legal drop targets while dragging.
            int color = (i == screen.getItemsSlotAt(mouseX, mouseY))
                    ? 0x80FFFFFF : 0x20FFFFFF;
            draw.fill(r.getX(), r.getY(), r.getX() + r.getWidth(), r.getY() + r.getHeight(), color);
        }
    }

    /** EMI's ingredient is a multi-stack; pick the first {@link EmiStack} that's an ItemStack. */
    private static ItemStack resolveItemStack(EmiIngredient ing) {
        List<EmiStack> emiStacks = ing.getEmiStacks();
        if (emiStacks == null || emiStacks.isEmpty()) return ItemStack.EMPTY;
        for (EmiStack es : emiStacks) {
            ItemStack stack = es.getItemStack();
            if (stack != null && !stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Resolves a list of slot bounds for any future API consumer that wants to discover where
     * the drop targets live without driving them by point. Unused by the current EMI integration
     * but kept for parity with the JEI handler's per-slot Target list.
     */
    @SuppressWarnings("unused")
    private List<Bounds> slotBounds(PhotonNodeScreen screen) {
        List<Bounds> out = new ArrayList<>();
        int n = screen.getItemsSlotCount();
        for (int i = 0; i < n; i++) {
            Rect2i r = screen.getItemsSlotRect(i);
            if (r != null) out.add(new Bounds(r.getX(), r.getY(), r.getWidth(), r.getHeight()));
        }
        return out;
    }
}
