package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client → server: admin sets the priority of one charging slot group. */
public record SetChannelSlotPriorityPacket(UUID channelId, int slotBit, int priority) implements CustomPacketPayload {
    public static final Type<SetChannelSlotPriorityPacket> TYPE = new Type<>(QuantumChanneling.id("set_channel_slot_priority"));
    public static final StreamCodec<FriendlyByteBuf, SetChannelSlotPriorityPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetChannelSlotPriorityPacket::encode, SetChannelSlotPriorityPacket::decode);

    @Override
    public Type<SetChannelSlotPriorityPacket> type() { return TYPE; }

    public static void encode(SetChannelSlotPriorityPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeVarInt(p.slotBit);
        b.writeVarInt(p.priority);
    }
    public static SetChannelSlotPriorityPacket decode(FriendlyByteBuf b) {
        return new SetChannelSlotPriorityPacket(b.readUUID(), b.readVarInt(), b.readVarInt());
    }
    public static void handle(SetChannelSlotPriorityPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        var server = player.level().getServer();
        ChannelData data = ChannelData.get(server);
        if (data.setSlotPriority(p.channelId, player.getUUID(), p.slotBit, p.priority)) {
            CreateChannelPacket.broadcastListTo(server, p.channelId);
        }
        CreateChannelPacket.sendListBackTo(player);
    }
}
