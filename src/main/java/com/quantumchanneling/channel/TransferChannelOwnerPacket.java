package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client → server: owner transfers the channel to {@code targetPlayerId}. */
public record TransferChannelOwnerPacket(UUID channelId, UUID targetPlayerId, String targetName) implements CustomPacketPayload {
    public static final Type<TransferChannelOwnerPacket> TYPE = new Type<>(QuantumChanneling.id("transfer_channel_owner"));
    public static final StreamCodec<FriendlyByteBuf, TransferChannelOwnerPacket> STREAM_CODEC =
            StreamCodec.ofMember(TransferChannelOwnerPacket::encode, TransferChannelOwnerPacket::decode);

    @Override
    public Type<TransferChannelOwnerPacket> type() { return TYPE; }

    public static void encode(TransferChannelOwnerPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUUID(p.targetPlayerId);
        b.writeUtf(p.targetName, 32);
    }
    public static TransferChannelOwnerPacket decode(FriendlyByteBuf b) {
        return new TransferChannelOwnerPacket(b.readUUID(), b.readUUID(), b.readUtf(32));
    }
    public static void handle(TransferChannelOwnerPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        var server = player.level().getServer();
        ChannelData data = ChannelData.get(server);
        if (data.transferOwnership(p.channelId, player.getUUID(), p.targetPlayerId, p.targetName)) {
            CreateChannelPacket.broadcastListTo(server, p.channelId);
        }
        CreateChannelPacket.sendListBackTo(player);
    }
}
