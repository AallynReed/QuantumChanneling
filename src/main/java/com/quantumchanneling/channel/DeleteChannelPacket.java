package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record DeleteChannelPacket(UUID id) implements CustomPacketPayload {
    public static final Type<DeleteChannelPacket> TYPE = new Type<>(QuantumChanneling.id("delete_channel"));
    public static final StreamCodec<FriendlyByteBuf, DeleteChannelPacket> STREAM_CODEC =
            StreamCodec.ofMember(DeleteChannelPacket::encode, DeleteChannelPacket::decode);

    @Override
    public Type<DeleteChannelPacket> type() { return TYPE; }


    public static void encode(DeleteChannelPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.id);
    }

    public static DeleteChannelPacket decode(FriendlyByteBuf buf) {
        return new DeleteChannelPacket(buf.readUUID());
    }

    public static void handle(DeleteChannelPacket pkt, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        var server = player.level().getServer();
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
    }
}
