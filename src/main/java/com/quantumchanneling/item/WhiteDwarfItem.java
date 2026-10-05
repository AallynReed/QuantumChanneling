package com.quantumchanneling.item;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * The White Dwarf — an intermediate crafting product (Nether Star → 2 White Dwarfs → Uncontained
 * Black Hole via the Star Shaper's Hammer). Renders as a free-floating white stellar disc using
 * the dedicated {@code photon_white_dwarf} sun shader, with no block model.
 *
 * <p>The {@code builtin/entity} parent on its item model triggers Forge's BEWLR path, which
 * dispatches to {@link com.quantumchanneling.client.render.PhotonItemRenderer} (it detects this
 * item and draws the star shader only).
 */
public class WhiteDwarfItem extends TooltipItem {
    public WhiteDwarfItem(Properties properties, String descriptionKey) {
        super(properties, descriptionKey);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.quantumchanneling.client.render.PhotonItemRenderer.INSTANCE;
            }
        });
    }
}
