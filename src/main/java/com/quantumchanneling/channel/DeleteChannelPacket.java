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
            var server = player.serverLevel().getServer();
            ChannelData data = ChannelData.get(server);
            // Capture everyone who can see the channel BEFORE it's gone, so we can resync them
            // afterward and let the deleted channel drop off their screens.
            java.util.List<ServerPlayer> viewers = server.getPlayerList().getPlayers().stream()
                    .filter(pl -> data.visibleTo(pl).stream().anyMatch(n -> n.id().equals(pkt.id)))
                    .toList();
            if (data.deleteChannel(pkt.id, player.getUUID())) {
                for (ServerPlayer pl : viewers) CreateChannelPacket.sendListBackTo(pl);
            }
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
