package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client → server: try to gain USER access to {@code channelId} by submitting a PIN. */
public record JoinByPinPacket(UUID channelId, String pin) implements CustomPacketPayload {
    public static final Type<JoinByPinPacket> TYPE = new Type<>(QuantumChanneling.id("join_by_pin"));
    public static final StreamCodec<FriendlyByteBuf, JoinByPinPacket> STREAM_CODEC =
            StreamCodec.ofMember(JoinByPinPacket::encode, JoinByPinPacket::decode);

    @Override
    public Type<JoinByPinPacket> type() { return TYPE; }

    public static void encode(JoinByPinPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUtf(p.pin, 32);
    }
    public static JoinByPinPacket decode(FriendlyByteBuf b) {
        return new JoinByPinPacket(b.readUUID(), b.readUtf(32));
    }
    public static void handle(JoinByPinPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelData data = ChannelData.get(player.level().getServer());
        // Always push the latest list back — joined or not, so the UI clears/refreshes.
        data.joinByPin(player, p.channelId, p.pin);
        CreateChannelPacket.sendListBackTo(player);
    }
}
