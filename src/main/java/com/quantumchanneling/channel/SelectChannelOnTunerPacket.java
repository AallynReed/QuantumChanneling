package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.item.QuantumTunerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client→server. Set the selected network on the player's currently-held tuner. */
public record SelectChannelOnTunerPacket(UUID id) {

    public static void encode(SelectChannelOnTunerPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.id);
    }

    public static SelectChannelOnTunerPacket decode(FriendlyByteBuf buf) {
        return new SelectChannelOnTunerPacket(buf.readUUID());
    }

    public static void handle(SelectChannelOnTunerPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            QuantumChannel net = data.getChannel(pkt.id);
            if (net == null) return;
            // Find a held tuner (main hand preferred, then off-hand).
            ItemStack tuner = pickTuner(player);
            if (tuner.isEmpty()) return;
            QuantumTunerItem.setStoredChannel(tuner, pkt.id);
            player.displayClientMessage(
                    Component.translatable("message.quantumchanneling.tuner.selected", net.name())
                            .withStyle(ChatFormatting.AQUA),
                    true);
        });
        ctx.setPacketHandled(true);
    }

    private static ItemStack pickTuner(ServerPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.getItem() == QuantumChanneling.QUANTUM_TUNER.get()) return main;
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.getItem() == QuantumChanneling.QUANTUM_TUNER.get()) return off;
        return ItemStack.EMPTY;
    }
}
