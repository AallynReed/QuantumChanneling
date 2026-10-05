package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client→server. Asks the server for the list of channels visible to this player. */
public record OpenChannelsRequestPacket() implements CustomPacketPayload {
    public static final Type<OpenChannelsRequestPacket> TYPE = new Type<>(QuantumChanneling.id("open_channels_request"));
    public static final StreamCodec<FriendlyByteBuf, OpenChannelsRequestPacket> STREAM_CODEC =
            StreamCodec.ofMember(OpenChannelsRequestPacket::encode, OpenChannelsRequestPacket::decode);

    @Override
    public Type<OpenChannelsRequestPacket> type() { return TYPE; }


    public static void encode(OpenChannelsRequestPacket pkt, FriendlyByteBuf buf) {}

    public static OpenChannelsRequestPacket decode(FriendlyByteBuf buf) {
        return new OpenChannelsRequestPacket();
    }

    public static void handle(OpenChannelsRequestPacket pkt, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        CreateChannelPacket.sendListBackTo(player);
    }
}
