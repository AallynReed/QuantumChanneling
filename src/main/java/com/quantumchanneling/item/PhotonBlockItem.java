package com.quantumchanneling.item;

import com.quantumchanneling.block.PhotonStorageBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@link BlockItem} variant that attaches a hover-tooltip in two parts:
 * a translated description (its lang key is {@code <baseKey>.description}) and, for storage tiers,
 * a dynamic capacity line read from {@link com.quantumchanneling.client.ClientServerConfig#storageCapacities}.
 *
 * <p>The emitter and receiver items use {@link PhotonShaderBlockItem} instead, which adds the
 * GLSL custom renderer. That separation matters because {@code Item.initializeClient} runs from
 * the {@code Item} super-constructor — {@code BlockItem.block} isn't assigned yet at that point,
 * so any decision based on the wrapped block would see {@code null}. Encoding the choice in the
 * subclass type bypasses that problem entirely.
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

    private static String formatFE(long fe) {
        if (Math.abs(fe) < 1_000L) return Long.toString(fe) + " FE";
        String[] suffixes = { "K", "M", "G", "T", "P", "E" };
        int idx = 0;
        double v = fe;
        while (Math.abs(v) >= 1_000.0 && idx < suffixes.length) { v /= 1_000.0; idx++; }
        return String.format("%.2f%s FE", v, suffixes[idx - 1]);
    }
}
