package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record DeleteGasSubchannelPacket(UUID channelId, UUID subId) {
    public static void encode(DeleteGasSubchannelPacket p, FriendlyByteBuf b) { b.writeUUID(p.channelId); b.writeUUID(p.subId); }
    public static DeleteGasSubchannelPacket decode(FriendlyByteBuf b) { return new DeleteGasSubchannelPacket(b.readUUID(), b.readUUID()); }
    public static void handle(DeleteGasSubchannelPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.deleteGasSubchannel(p.channelId, player.getUUID(), p.subId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
