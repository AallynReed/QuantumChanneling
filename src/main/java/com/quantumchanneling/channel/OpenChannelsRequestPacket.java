package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client→server. Asks the server for the list of networks visible to this player. */
public record OpenChannelsRequestPacket() {

    public static void encode(OpenChannelsRequestPacket pkt, FriendlyByteBuf buf) {}

    public static OpenChannelsRequestPacket decode(FriendlyByteBuf buf) {
        return new OpenChannelsRequestPacket();
    }

    public static void handle(OpenChannelsRequestPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
