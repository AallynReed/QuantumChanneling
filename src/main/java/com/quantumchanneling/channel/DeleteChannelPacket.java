package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record DeleteChannelPacket(UUID id) {

    public static void encode(DeleteChannelPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.id);
    }

    public static DeleteChannelPacket decode(FriendlyByteBuf buf) {
        return new DeleteChannelPacket(buf.readUUID());
    }

    public static void handle(DeleteChannelPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            data.deleteChannel(pkt.id, player.getUUID());
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
