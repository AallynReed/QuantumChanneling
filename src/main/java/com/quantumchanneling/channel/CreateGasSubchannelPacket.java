package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record CreateGasSubchannelPacket(UUID channelId, String name) {
    public static void encode(CreateGasSubchannelPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId); b.writeUtf(p.name, GasSubchannel.NAME_MAX);
    }
    public static CreateGasSubchannelPacket decode(FriendlyByteBuf b) {
        return new CreateGasSubchannelPacket(b.readUUID(), b.readUtf(GasSubchannel.NAME_MAX));
    }
    public static void handle(CreateGasSubchannelPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            UUID newId = data.createGasSubchannel(p.channelId, player.getUUID(), p.name);
            if (newId != null) CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
