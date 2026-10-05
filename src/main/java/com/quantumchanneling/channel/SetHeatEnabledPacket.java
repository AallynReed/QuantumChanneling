package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client → server: flip the per-channel heat thermal-wire master enable. */
public record SetHeatEnabledPacket(UUID channelId, boolean enabled) implements CustomPacketPayload {
    public static final Type<SetHeatEnabledPacket> TYPE = new Type<>(QuantumChanneling.id("set_heat_enabled"));
    public static final StreamCodec<FriendlyByteBuf, SetHeatEnabledPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetHeatEnabledPacket::encode, SetHeatEnabledPacket::decode);

    @Override
    public Type<SetHeatEnabledPacket> type() { return TYPE; }

    public static void encode(SetHeatEnabledPacket p, FriendlyByteBuf b) { b.writeUUID(p.channelId); b.writeBoolean(p.enabled); }
    public static SetHeatEnabledPacket decode(FriendlyByteBuf b) { return new SetHeatEnabledPacket(b.readUUID(), b.readBoolean()); }
    public static void handle(SetHeatEnabledPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        var server = player.level().getServer();
        ChannelData data = ChannelData.get(server);
        if (data.setHeatEnabled(p.channelId, player.getUUID(), p.enabled)) {
            CreateChannelPacket.broadcastListTo(server, p.channelId);
        }
        CreateChannelPacket.sendListBackTo(player);
    }
}
