package com.quantumchanneling.item;

import com.quantumchanneling.block.PhotonStorageBlock;
import com.quantumchanneling.client.ClientServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * {@link BlockItem} with a translated description line and, for storage tiers, a capacity line
 * read from {@link ClientServerConfig#storageCapacities} (the server's values once synced).
 */
public class PhotonBlockItem extends BlockItem {
    private final String descriptionKey;

    public PhotonBlockItem(Block block, Properties properties, String descriptionKey) {
        super(block, properties);
        this.descriptionKey = descriptionKey;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY));
        if (getBlock() instanceof PhotonStorageBlock psb) {
            int tier = psb.getTier();
            long[] caps = ClientServerConfig.storageCapacities;
            long cap = tier - 1 < caps.length ? caps[tier - 1] : 0L;
            tooltip.accept(Component.translatable("tooltip.quantumchanneling.storage.capacity", formatFE(cap))
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    private static String formatFE(long fe) {
        if (Math.abs(fe) < 1_000L) return fe + " FE";
        String[] suffixes = { "K", "M", "G", "T", "P", "E" };
        int idx = 0;
        double v = fe;
        while (Math.abs(v) >= 1_000.0 && idx < suffixes.length) { v /= 1_000.0; idx++; }
        return String.format("%.2f%s FE", v, suffixes[idx - 1]);
    }
}
