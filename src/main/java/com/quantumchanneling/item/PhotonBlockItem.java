package com.quantumchanneling.item;

import com.quantumchanneling.block.PhotonStorageBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * {@link BlockItem} variant that attaches a hover-tooltip in two parts:
 * a translated description (its lang key is {@code <baseKey>.description}) and, for storage tiers,
 * a dynamic capacity line read from {@link com.quantumchanneling.client.ClientServerConfig#storageCapacities}.
 *
 * <p>For the emitter and receiver items, a custom client-side
 * {@link IClientItemExtensions#getCustomRenderer custom renderer} is provided so the GLSL black-hole
 * + accretion shader effect is visible everywhere the item is drawn (hotbar, inventory slot, item
 * frame, hand, dropped on the ground). The other photon block items don't use the custom shader
 * and fall back to vanilla model rendering.
 */
public class PhotonBlockItem extends BlockItem {
    private final String descriptionKey;

    public PhotonBlockItem(Block block, Properties properties, String descriptionKey) {
        super(block, properties);
        this.descriptionKey = descriptionKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY));
        if (getBlock() instanceof PhotonStorageBlock psb) {
            int tier = psb.getTier();
            long[] caps = com.quantumchanneling.client.ClientServerConfig.storageCapacities;
            long cap = (caps != null && tier - 1 < caps.length) ? caps[tier - 1] : 0L;
            String formatted = formatFE(cap);
            tooltip.add(Component.translatable("tooltip.quantumchanneling.storage.capacity", formatted)
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        // Only emitter + receiver get the custom shader renderer. Storage tiers + manager keep
        // vanilla model rendering. Block identity is checked at render-time inside the renderer
        // (it compares the stack against the registered emitter item), so handing the same
        // renderer to receiver here is safe — non-shader items just fall through to vanilla.
        if (isShaderRenderedBlock()) {
            consumer.accept(new IClientItemExtensions() {
                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    return com.quantumchanneling.client.render.PhotonItemRenderer.INSTANCE;
                }
            });
        }
    }

    /** True when this item wraps an emitter or receiver block — the two devices that use the BER. */
    private boolean isShaderRenderedBlock() {
        Block b = getBlock();
        return b instanceof com.quantumchanneling.block.PhotonEmitterBlock
                || b instanceof com.quantumchanneling.block.PhotonReceiverBlock;
    }

    private static String formatFE(long fe) {
        if (Math.abs(fe) < 1_000L) return Long.toString(fe) + " FE";
        String[] suffixes = { "K", "M", "G", "T", "P", "E" };
        int idx = 0;
        double v = fe;
        while (Math.abs(v) >= 1_000.0 && idx < suffixes.length) { v /= 1_000.0; idx++; }
        return String.format("%.2f%s FE", v, suffixes[idx - 1]);
    }
}
