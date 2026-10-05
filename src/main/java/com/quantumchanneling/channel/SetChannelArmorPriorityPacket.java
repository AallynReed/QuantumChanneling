package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client → server: admin sets the priority of a single armor piece on the channel.
 * {@code armorIdx} = 0 head, 1 chest, 2 legs, 3 feet.
 */
public record SetChannelArmorPriorityPacket(UUID channelId, int armorIdx, int priority) implements CustomPacketPayload {
    public static final Type<SetChannelArmorPriorityPacket> TYPE = new Type<>(QuantumChanneling.id("set_channel_armor_priority"));
    public static final StreamCodec<FriendlyByteBuf, SetChannelArmorPriorityPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetChannelArmorPriorityPacket::encode, SetChannelArmorPriorityPacket::decode);

    @Override
    public Type<SetChannelArmorPriorityPacket> type() { return TYPE; }

    public static void encode(SetChannelArmorPriorityPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeVarInt(p.armorIdx);
        b.writeVarInt(p.priority);
    }
    public static SetChannelArmorPriorityPacket decode(FriendlyByteBuf b) {
        return new SetChannelArmorPriorityPacket(b.readUUID(), b.readVarInt(), b.readVarInt());
    }
    public static void handle(SetChannelArmorPriorityPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        var server = player.level().getServer();
        ChannelData data = ChannelData.get(server);
        if (data.setArmorPiecePriority(p.channelId, player.getUUID(), p.armorIdx, p.priority)) {
            CreateChannelPacket.broadcastListTo(server, p.channelId);
        }
        CreateChannelPacket.sendListBackTo(player);
    }
}
