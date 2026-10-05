package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Subscribe (or unsubscribe with the all-zero UUID) the sending player to the given channel for charging. */
public record SubscribeChargingPacket(UUID channelId) implements CustomPacketPayload {
    public static final Type<SubscribeChargingPacket> TYPE = new Type<>(QuantumChanneling.id("subscribe_charging"));
    public static final StreamCodec<FriendlyByteBuf, SubscribeChargingPacket> STREAM_CODEC =
            StreamCodec.ofMember(SubscribeChargingPacket::encode, SubscribeChargingPacket::decode);

    @Override
    public Type<SubscribeChargingPacket> type() { return TYPE; }

    public static final UUID NONE = new UUID(0L, 0L);

    public static void encode(SubscribeChargingPacket p, FriendlyByteBuf b) { b.writeUUID(p.channelId); }
    public static SubscribeChargingPacket decode(FriendlyByteBuf b) { return new SubscribeChargingPacket(b.readUUID()); }
    public static void handle(SubscribeChargingPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelData data = ChannelData.get(player.level().getServer());
        if (NONE.equals(p.channelId)) {
            data.setChargingSubscription(player.getUUID(), null);
        } else {
            QuantumChannel net = data.getChannel(p.channelId);
            if (net == null || !net.canUse(player.getUUID())) return;
            data.setChargingSubscription(player.getUUID(), p.channelId);
        }
        CreateChannelPacket.sendListBackTo(player);
    }
}
