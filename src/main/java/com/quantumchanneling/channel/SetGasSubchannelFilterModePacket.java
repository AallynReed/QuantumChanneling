package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record SetGasSubchannelFilterModePacket(UUID channelId, UUID subId, boolean whitelist) {
    public static void encode(SetGasSubchannelFilterModePacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId); b.writeUUID(p.subId); b.writeBoolean(p.whitelist);
    }
    public static SetGasSubchannelFilterModePacket decode(FriendlyByteBuf b) {
        return new SetGasSubchannelFilterModePacket(b.readUUID(), b.readUUID(), b.readBoolean());
    }
    public static void handle(SetGasSubchannelFilterModePacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.setGasSubchannelFilterMode(p.channelId, player.getUUID(), p.subId, p.whitelist)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
