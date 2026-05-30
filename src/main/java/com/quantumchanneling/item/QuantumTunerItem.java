package com.quantumchanneling.item;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.channel.ModMessages;
import com.quantumchanneling.channel.OpenChannelsRequestPacket;
import com.quantumchanneling.channel.QuantumChannel;
import com.quantumchanneling.channel.ChannelData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
    private static final String TAG_NAME = "ChannelName";

    public QuantumTunerItem(Properties properties) {
        super(properties);
    }

    public static @Nullable UUID getStoredChannel(ItemStack stack) {
        return stack.hasTag() && stack.getTag().hasUUID(TAG_CHANNEL) ? stack.getTag().getUUID(TAG_CHANNEL) : null;
    }

    public static String getStoredName(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(TAG_NAME) ? stack.getTag().getString(TAG_NAME) : "";
    }

    /** Sets the channel UUID. Caller should also call {@link #setStoredName(ItemStack, String)}. */
    public static void setStoredChannel(ItemStack stack, @Nullable UUID id) {
        if (id == null) {
            if (stack.hasTag()) {
                stack.getTag().remove(TAG_CHANNEL);
                stack.getTag().remove(TAG_NAME);
            }
        } else {
            stack.getOrCreateTag().putUUID(TAG_CHANNEL, id);
        }
    }

    public static void setStoredName(ItemStack stack, String name) {
        if (name == null || name.isEmpty()) {
            if (stack.hasTag()) stack.getTag().remove(TAG_NAME);
        } else {
            stack.getOrCreateTag().putString(TAG_NAME, name);
        }
    }

    /** Server-side helper: refresh the cached name from ChannelData. */
    public static void refreshCachedName(ItemStack stack, MinecraftServer server) {
        UUID id = getStoredChannel(stack);
        if (id == null) return;
        QuantumChannel net = ChannelData.get(server).getChannel(id);
        setStoredName(stack, net == null ? "" : net.name());
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
        MinecraftServer server = level.getServer();
        ChannelData data = server != null ? ChannelData.get(server) : null;

        if (player.isShiftKeyDown()) {
            if (deviceChannel != null) {
                bound.setChannelId(null);
                player.displayClientMessage(
                        Component.translatable("message.quantumchanneling.tuner.unbound",
                                nameOrShort(data, deviceChannel)),
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
                    Component.translatable("message.quantumchanneling.tuner.bound",
                            nameOrShort(data, tunerChannel)),
                    true);
            return InteractionResult.CONSUME;
        }

        if (deviceChannel != null) {
            setStoredChannel(stack, deviceChannel);
            if (data != null) {
                QuantumChannel dn = data.getChannel(deviceChannel);
                setStoredName(stack, dn == null ? "" : dn.name());
            }
            player.displayClientMessage(
                    Component.translatable("message.quantumchanneling.tuner.copied",
                            nameOrShort(data, deviceChannel)),
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
        // Shift+right-click in air -> open the Networks UI. Request happens client-side so the
        // server replies with a ShowChannelsListPacket that triggers the screen.
        if (level.isClientSide) {
            ModMessages.sendToServer(new OpenChannelsRequestPacket());
        }
        return InteractionResultHolder.success(stack);
    }

    private static Component nameOrShort(@Nullable ChannelData data, UUID id) {
        if (data != null) {
            QuantumChannel net = data.getChannel(id);
            if (net != null) return Component.literal(net.name());
        }
        return Component.literal(shortId(id));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        UUID id = getStoredChannel(stack);
        if (id != null) {
            String name = getStoredName(stack);
            String label = name.isEmpty() ? shortId(id) : name;
            tooltip.add(Component.translatable("tooltip.quantumchanneling.tuner.channel", label)
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.quantumchanneling.tuner.no_channel")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.quantumchanneling.tuner.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
