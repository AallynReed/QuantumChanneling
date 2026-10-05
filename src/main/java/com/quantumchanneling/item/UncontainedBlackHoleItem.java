package com.quantumchanneling.item;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * Crafting-progression item that's immune to explosion damage as an item entity. Vanilla calls
 * {@link net.minecraft.world.item.Item#canBeHurtBy} from {@code ItemEntity.hurt} for every damage
 * event, so returning {@code false} for {@link DamageTypeTags#IS_EXPLOSION} sources is enough to
 * make a stack of these survive TNT, creepers, and the Star Shaper's Hammer's own collapse burst
 * (which is what motivates this — the hammer crushes a White Dwarf, the burst that ensues would
 * otherwise vaporise the freshly-spawned Uncontained Black Holes sitting at ground zero).
 *
 * <p>Fire / lava / general damage still apply.
 *
 * <p>Renders with the same GLSL black-hole halo + dark-void shader as the emitter / receiver
 * devices, but with no block model under it — the item IS the singularity effect, free-floating.
 * The {@code builtin/entity} parent on its item model triggers Forge's BEWLR path, which dispatches
 * to {@link com.quantumchanneling.client.render.PhotonItemRenderer} (it detects this item and draws
 * shader-only).
 */
public class UncontainedBlackHoleItem extends TooltipItem {
    public UncontainedBlackHoleItem(Properties properties, String descriptionKey) {
        super(properties, descriptionKey);
    }

    @Override
    public boolean canBeHurtBy(DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return false;
        return super.canBeHurtBy(source);
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

    /** Surface-level helper for code paths that want to ask "is this stack explosion-proof?"
     *  without instanceof-ing the Item subclass. */
    public static boolean isExplosionProof(ItemStack stack) {
        return stack.getItem() instanceof UncontainedBlackHoleItem;
    }
}
