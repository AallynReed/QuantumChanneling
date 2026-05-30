package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record CreateChannelPacket(String name) {

    public static void encode(CreateChannelPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.name);
    }

    public static CreateChannelPacket decode(FriendlyByteBuf buf) {
        return new CreateChannelPacket(buf.readUtf(64));
    }

    public static void handle(CreateChannelPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            String name = pkt.name.trim();
            if (name.isEmpty() || name.length() > 32) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            data.createChannel(player, name);
            sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }

    public static void sendListBackTo(ServerPlayer player) {
        ChannelData data = ChannelData.get(player.serverLevel().getServer());
        java.util.UUID sub = data.getChargingSubscription(player.getUUID());
        var list = data.visibleTo(player).stream()
                .map(n -> ChannelInfo.from(n, player.getUUID(), n.id().equals(sub)))
                .toList();
        ModMessages.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ShowChannelsListPacket(list));
    }
}
