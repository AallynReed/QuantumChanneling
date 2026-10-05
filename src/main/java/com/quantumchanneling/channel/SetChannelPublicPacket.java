package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record SetChannelPublicPacket(UUID id, boolean publicAccess) implements CustomPacketPayload {
    public static final Type<SetChannelPublicPacket> TYPE = new Type<>(QuantumChanneling.id("set_channel_public"));
    public static final StreamCodec<FriendlyByteBuf, SetChannelPublicPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetChannelPublicPacket::encode, SetChannelPublicPacket::decode);

    @Override
    public Type<SetChannelPublicPacket> type() { return TYPE; }

    public static void encode(SetChannelPublicPacket p, FriendlyByteBuf b) { b.writeUUID(p.id); b.writeBoolean(p.publicAccess); }
    public static SetChannelPublicPacket decode(FriendlyByteBuf b) { return new SetChannelPublicPacket(b.readUUID(), b.readBoolean()); }
    public static void handle(SetChannelPublicPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        var server = player.level().getServer();
        ChannelData data = ChannelData.get(server);
        // Going private drops the channel off ex-public-viewers' screens — capture them first so
        // they get an updated (channel-less) list even though broadcastListTo won't reach them.
        java.util.List<ServerPlayer> before = server.getPlayerList().getPlayers().stream()
                .filter(pl -> data.visibleTo(pl).stream().anyMatch(n -> n.id().equals(p.id)))
                .toList();
        if (data.setPublic(p.id, player.getUUID(), p.publicAccess)) {
            CreateChannelPacket.broadcastListTo(server, p.id);
            for (ServerPlayer pl : before) CreateChannelPacket.sendListBackTo(pl);
        }
        CreateChannelPacket.sendListBackTo(player);
    }
}
