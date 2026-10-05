package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Set a channel's charging-slot bitmask (HAND / HOTBAR / INVENTORY / ARMOR). */
public record SetChannelChargingPacket(UUID id, int slotMask) implements CustomPacketPayload {
    public static final Type<SetChannelChargingPacket> TYPE = new Type<>(QuantumChanneling.id("set_channel_charging"));
    public static final StreamCodec<FriendlyByteBuf, SetChannelChargingPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetChannelChargingPacket::encode, SetChannelChargingPacket::decode);

    @Override
    public Type<SetChannelChargingPacket> type() { return TYPE; }

    public static void encode(SetChannelChargingPacket p, FriendlyByteBuf b) { b.writeUUID(p.id); b.writeVarInt(p.slotMask); }
    public static SetChannelChargingPacket decode(FriendlyByteBuf b) { return new SetChannelChargingPacket(b.readUUID(), b.readVarInt()); }
    public static void handle(SetChannelChargingPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        var server = player.level().getServer();
        if (ChannelData.get(server).setChargingSlots(p.id, player.getUUID(), p.slotMask)) {
            CreateChannelPacket.broadcastListTo(server, p.id);
        }
        CreateChannelPacket.sendListBackTo(player);
    }
}
