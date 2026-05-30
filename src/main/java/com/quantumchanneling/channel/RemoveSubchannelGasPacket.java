package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record RemoveSubchannelGasPacket(UUID channelId, UUID subId, ResourceLocation gasId) {
    public static void encode(RemoveSubchannelGasPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId); b.writeUUID(p.subId); b.writeResourceLocation(p.gasId);
    }
    public static RemoveSubchannelGasPacket decode(FriendlyByteBuf b) {
        return new RemoveSubchannelGasPacket(b.readUUID(), b.readUUID(), b.readResourceLocation());
    }
    public static void handle(RemoveSubchannelGasPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.removeSubchannelGas(p.channelId, player.getUUID(), p.subId, p.gasId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
