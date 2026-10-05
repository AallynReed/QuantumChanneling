package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client → server: admin sets/clears the join PIN for a channel. Empty string clears it. */
public record SetChannelPinPacket(UUID channelId, String pin) implements CustomPacketPayload {
    public static final Type<SetChannelPinPacket> TYPE = new Type<>(QuantumChanneling.id("set_channel_pin"));
    public static final StreamCodec<FriendlyByteBuf, SetChannelPinPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetChannelPinPacket::encode, SetChannelPinPacket::decode);

    @Override
    public Type<SetChannelPinPacket> type() { return TYPE; }

    public static void encode(SetChannelPinPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUtf(p.pin, 32);
    }
    public static SetChannelPinPacket decode(FriendlyByteBuf b) {
        return new SetChannelPinPacket(b.readUUID(), b.readUtf(32));
    }
    public static void handle(SetChannelPinPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        var server = player.level().getServer();
        ChannelData data = ChannelData.get(server);
        // Clearing the PIN drops the channel off PIN-only viewers' screens — capture them first
        // so they get an updated (channel-less) list even though broadcastListTo won't reach them.
        java.util.List<ServerPlayer> before = server.getPlayerList().getPlayers().stream()
                .filter(pl -> data.visibleTo(pl).stream().anyMatch(n -> n.id().equals(p.channelId)))
                .toList();
        if (data.setPin(p.channelId, player.getUUID(), p.pin)) {
            CreateChannelPacket.broadcastListTo(server, p.channelId);
            for (ServerPlayer pl : before) CreateChannelPacket.sendListBackTo(pl);
        }
        CreateChannelPacket.sendListBackTo(player);
    }
}
