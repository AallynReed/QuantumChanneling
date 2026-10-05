package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetDevicePriorityPacket(BlockPos pos, int priority) implements CustomPacketPayload {
    public static final Type<SetDevicePriorityPacket> TYPE = new Type<>(QuantumChanneling.id("set_device_priority"));
    public static final StreamCodec<FriendlyByteBuf, SetDevicePriorityPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetDevicePriorityPacket::encode, SetDevicePriorityPacket::decode);

    @Override
    public Type<SetDevicePriorityPacket> type() { return TYPE; }

    public static void encode(SetDevicePriorityPacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); b.writeVarInt(p.priority); }
    public static SetDevicePriorityPacket decode(FriendlyByteBuf b) { return new SetDevicePriorityPacket(b.readBlockPos(), b.readVarInt()); }
    public static void handle(SetDevicePriorityPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity bound = PacketUtil.manageableDevice(player, p.pos);
        if (bound != null) bound.setPriority(p.priority);
    }
}
