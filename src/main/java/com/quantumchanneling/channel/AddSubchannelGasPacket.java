package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record AddSubchannelGasPacket(UUID channelId, UUID subId, ResourceLocation gasId) {
    public static void encode(AddSubchannelGasPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId); b.writeUUID(p.subId); b.writeResourceLocation(p.gasId);
    }
    public static AddSubchannelGasPacket decode(FriendlyByteBuf b) {
        return new AddSubchannelGasPacket(b.readUUID(), b.readUUID(), b.readResourceLocation());
    }
    public static void handle(AddSubchannelGasPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.addSubchannelGas(p.channelId, player.getUUID(), p.subId, p.gasId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
