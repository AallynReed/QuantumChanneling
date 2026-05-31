package com.quantumchanneling.item;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * {@link PhotonBlockItem} variant for the emitter and receiver — the two devices whose item models
 * use {@code builtin/entity} as a parent and rely on a
 * {@link BlockEntityWithoutLevelRenderer BEWLR} to draw the baked block model plus the
 * black-hole + accretion shader effect in every item context (inventory slot, hotbar, hand, item
 * frame, dropped).
 *
 * <h3>Why a separate class</h3>
 * <p>{@code Item.initializeClient} is invoked from the {@code Item} base-class constructor, before
 * {@code BlockItem.block} is assigned. Any check based on the wrapped {@link Block} runs against
 * {@code null} at that point and silently fails — the {@code IClientItemExtensions} never gets
 * registered and the item renders as an empty slot (the {@code builtin/entity} parent contributes
 * no quads, and no custom renderer takes over). Encoding the decision in the subclass type avoids
 * touching any instance state during super construction.
 */
public class PhotonShaderBlockItem extends PhotonBlockItem {
    public PhotonShaderBlockItem(Block block, Properties properties, String descriptionKey) {
        super(block, properties, descriptionKey);
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
