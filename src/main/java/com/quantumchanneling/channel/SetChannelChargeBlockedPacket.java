package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client → server: admin blocks or unblocks a player's wireless charging on a channel. */
public record SetChannelChargeBlockedPacket(UUID channelId, UUID targetPlayerId, boolean blocked) implements CustomPacketPayload {
    public static final Type<SetChannelChargeBlockedPacket> TYPE = new Type<>(QuantumChanneling.id("set_channel_charge_blocked"));
    public static final StreamCodec<FriendlyByteBuf, SetChannelChargeBlockedPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetChannelChargeBlockedPacket::encode, SetChannelChargeBlockedPacket::decode);

    @Override
    public Type<SetChannelChargeBlockedPacket> type() { return TYPE; }

    public static void encode(SetChannelChargeBlockedPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUUID(p.targetPlayerId);
        b.writeBoolean(p.blocked);
    }
    public static SetChannelChargeBlockedPacket decode(FriendlyByteBuf b) {
        return new SetChannelChargeBlockedPacket(b.readUUID(), b.readUUID(), b.readBoolean());
    }
    public static void handle(SetChannelChargeBlockedPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        var server = player.level().getServer();
        ChannelData data = ChannelData.get(server);
        if (data.setChargingBlocked(p.channelId, player.getUUID(), p.targetPlayerId, p.blocked)) {
            CreateChannelPacket.broadcastListTo(server, p.channelId);
        }
        CreateChannelPacket.sendListBackTo(player);
    }
}
