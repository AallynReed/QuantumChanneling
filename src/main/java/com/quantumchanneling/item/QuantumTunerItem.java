package com.quantumchanneling.item;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class QuantumTunerItem extends Item {
    private static final String TAG_CHANNEL = "Channel";

    public QuantumTunerItem(Properties properties) {
        super(properties);
    }

    public static @Nullable UUID getStoredChannel(ItemStack stack) {
        return stack.hasTag() && stack.getTag().hasUUID(TAG_CHANNEL) ? stack.getTag().getUUID(TAG_CHANNEL) : null;
    }

    public static void setStoredChannel(ItemStack stack, @Nullable UUID id) {
        if (id == null) {
            if (stack.hasTag()) stack.getTag().remove(TAG_CHANNEL);
        } else {
            stack.getOrCreateTag().putUUID(TAG_CHANNEL, id);
        }
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockEntity be = level.getBlockEntity(ctx.getClickedPos());
        if (!(be instanceof ChannelBoundBlockEntity bound)) return InteractionResult.PASS;

        if (level.isClientSide) return InteractionResult.SUCCESS;

        ItemStack stack = ctx.getItemInHand();
        UUID tunerChannel = getStoredChannel(stack);
        UUID deviceChannel = bound.getChannelId();

        if (player.isShiftKeyDown()) {
            if (deviceChannel != null) {
                bound.setChannelId(null);
                player.displayClientMessage(
                        Component.translatable("message.quantumchanneling.tuner.unbound", shortId(deviceChannel)),
                        true);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.quantumchanneling.tuner.already_unbound"), true);
            }
            return InteractionResult.CONSUME;
        }

        if (tunerChannel != null) {
            bound.setChannelId(tunerChannel);
            player.displayClientMessage(
                    Component.translatable("message.quantumchanneling.tuner.bound", shortId(tunerChannel)),
                    true);
            return InteractionResult.CONSUME;
        }

        if (deviceChannel != null) {
            setStoredChannel(stack, deviceChannel);
            player.displayClientMessage(
                    Component.translatable("message.quantumchanneling.tuner.copied", shortId(deviceChannel)),
                    true);
            return InteractionResult.CONSUME;
        }

        player.displayClientMessage(
                Component.translatable("message.quantumchanneling.tuner.empty_on_unbound"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        UUID newId = UUID.randomUUID();
        setStoredChannel(stack, newId);
        player.displayClientMessage(
                Component.translatable("message.quantumchanneling.tuner.created", shortId(newId)), true);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        UUID id = getStoredChannel(stack);
        if (id != null) {
            tooltip.add(Component.translatable("tooltip.quantumchanneling.tuner.channel", shortId(id))
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.quantumchanneling.tuner.no_channel")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.quantumchanneling.tuner.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
