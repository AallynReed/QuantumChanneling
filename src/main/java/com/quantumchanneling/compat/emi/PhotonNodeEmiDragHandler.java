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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * EMI drag-and-drop handler. Supports two panels:
 * <ul>
 *   <li><b>Items</b> — any item drag adds the item id to the items filter slot under the cursor.</li>
 *   <li><b>Fluids</b> — fluid EmiStacks add their fluid id directly. Item drags fall through
 *       {@link FluidUtil#getFluidContained}: filled buckets, mod tanks, fluid capsules — anything
 *       Forge can identify as carrying a fluid — supply their fluid id to the filter without
 *       consuming the source stack.</li>
 * </ul>
 */
public class PhotonNodeEmiDragHandler implements EmiDragDropHandler<PhotonNodeScreen> {

    @Override
    public boolean dropStack(PhotonNodeScreen screen, EmiIngredient stack, int x, int y) {
        if (screen.isItemsModeActive()) {
            int slotIdx = screen.getItemsSlotAt(x, y);
            if (slotIdx < 0) return false;
            ItemStack itemStack = resolveItemStack(stack);
            if (itemStack.isEmpty()) return false;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
            screen.acceptDroppedFilterItem(id, slotIdx);
            return true;
        }
        if (screen.isFluidsModeActive()) {
            int slotIdx = screen.getFluidsSlotAt(x, y);
            if (slotIdx < 0) return false;
            Fluid fluid = resolveFluid(stack);
            if (fluid == null || fluid == Fluids.EMPTY) return false;
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            if (id == null) return false;
            screen.acceptDroppedFilterFluid(id, slotIdx);
            return true;
        }
        if (screen.isGasModeActive()) {
            int slotIdx = screen.getGasesSlotAt(x, y);
            if (slotIdx < 0) return false;
            if (!com.quantumchanneling.client.Compat.mekanismLoaded()) return false;
            ResourceLocation id = resolveGasId(stack);
            if (id == null) return false;
            screen.acceptDroppedFilterGas(id, slotIdx);
            return true;
        }
        return false;
    }

    /** First non-empty gas id this ingredient yields — direct EmiStack key OR contained item. */
    private static ResourceLocation resolveGasId(EmiIngredient ing) {
        List<EmiStack> emiStacks = ing.getEmiStacks();
        if (emiStacks == null) return null;
        for (EmiStack es : emiStacks) {
            Object key = es.getKey();
            ResourceLocation id = com.quantumchanneling.compat.mekanism.GasIngredientRead.tryRead(key);
            if (id != null) return id;
        }
        for (EmiStack es : emiStacks) {
            ItemStack stack = es.getItemStack();
            if (stack == null || stack.isEmpty()) continue;
            ResourceLocation id = com.quantumchanneling.compat.mekanism.GasItemRead.read(stack);
            if (id != null) return id;
        }
        return null;
    }

    @Override
    public void render(PhotonNodeScreen screen, EmiIngredient dragged, GuiGraphics draw,
                       int mouseX, int mouseY, float delta) {
        if (screen.isItemsModeActive() && !resolveItemStack(dragged).isEmpty()) {
            highlightSlots(draw, mouseX, mouseY, screen.getItemsSlotCount(), screen::getItemsSlotRect,
                    screen.getItemsSlotAt(mouseX, mouseY));
        } else if (screen.isFluidsModeActive()) {
            Fluid f = resolveFluid(dragged);
            if (f != null && f != Fluids.EMPTY) {
                highlightSlots(draw, mouseX, mouseY, screen.getFluidsSlotCount(), screen::getFluidsSlotRect,
                        screen.getFluidsSlotAt(mouseX, mouseY));
            }
        }
    }

    /** Paints the per-slot drag-target overlay. {@code highlightedIdx} gets the bright fill. */
    private static void highlightSlots(GuiGraphics draw, int mouseX, int mouseY,
                                       int slotCount,
                                       java.util.function.IntFunction<Rect2i> rectFn,
                                       int highlightedIdx) {
        for (int i = 0; i < slotCount; i++) {
            Rect2i r = rectFn.apply(i);
            if (r == null) continue;
            int color = (i == highlightedIdx) ? 0x80FFFFFF : 0x20FFFFFF;
            draw.fill(r.getX(), r.getY(), r.getX() + r.getWidth(), r.getY() + r.getHeight(), color);
        }
    }

    /** First non-empty {@link EmiStack} that yields an ItemStack. Used by the items panel. */
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
     * First non-empty {@link Fluid} the ingredient yields. Tries the EmiStack's underlying key
     * (which is a {@link Fluid} for fluid-type stacks), then falls back to extracting a fluid
     * from any contained ItemStack via Forge's container API.
     */
    private static Fluid resolveFluid(EmiIngredient ing) {
        List<EmiStack> emiStacks = ing.getEmiStacks();
        if (emiStacks == null) return null;
        for (EmiStack es : emiStacks) {
            Object key = es.getKey();
            if (key instanceof Fluid fluid && fluid != Fluids.EMPTY) return fluid;
        }
        // Fallback: maybe it's a filled bucket or mod tank item — pull the fluid out via Forge's API.
        for (EmiStack es : emiStacks) {
            ItemStack stack = es.getItemStack();
            if (stack == null || stack.isEmpty()) continue;
            FluidStack contained = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
            if (!contained.isEmpty()) return contained.getFluid();
        }
        return null;
    }

    @SuppressWarnings("unused")
    private List<Bounds> slotBounds(PhotonNodeScreen screen) {
        List<Bounds> out = new ArrayList<>();
        int n = screen.getItemsSlotCount();
        for (int i = 0; i < n; i++) {
            Rect2i r = screen.getItemsSlotRect(i);
            if (r != null) out.add(new Bounds(r.getX(), r.getY(), r.getWidth(), r.getY() + r.getHeight()));
        }
        return out;
    }
}
