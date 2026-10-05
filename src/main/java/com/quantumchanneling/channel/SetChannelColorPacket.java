package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record SetChannelColorPacket(UUID id, int color) implements CustomPacketPayload {
    public static final Type<SetChannelColorPacket> TYPE = new Type<>(QuantumChanneling.id("set_channel_color"));
    public static final StreamCodec<FriendlyByteBuf, SetChannelColorPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetChannelColorPacket::encode, SetChannelColorPacket::decode);

    @Override
    public Type<SetChannelColorPacket> type() { return TYPE; }

    public static void encode(SetChannelColorPacket p, FriendlyByteBuf b) { b.writeUUID(p.id); b.writeInt(p.color); }
    public static SetChannelColorPacket decode(FriendlyByteBuf b) { return new SetChannelColorPacket(b.readUUID(), b.readInt()); }
    public static void handle(SetChannelColorPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        var server = player.level().getServer();
        if (ChannelData.get(server).setColor(p.id, player.getUUID(), p.color)) {
            CreateChannelPacket.broadcastListTo(server, p.id);
        }
        CreateChannelPacket.sendListBackTo(player);
    }
}
