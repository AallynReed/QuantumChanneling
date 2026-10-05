package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Set a Photon Emitter/Receiver per-device throughput cap. -1 = unlimited (use global config). */
public record SetDeviceThroughputPacket(BlockPos pos, int cap) implements CustomPacketPayload {
    public static final Type<SetDeviceThroughputPacket> TYPE = new Type<>(QuantumChanneling.id("set_device_throughput"));
    public static final StreamCodec<FriendlyByteBuf, SetDeviceThroughputPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetDeviceThroughputPacket::encode, SetDeviceThroughputPacket::decode);

    @Override
    public Type<SetDeviceThroughputPacket> type() { return TYPE; }

    public static void encode(SetDeviceThroughputPacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); b.writeVarInt(p.cap); }
    public static SetDeviceThroughputPacket decode(FriendlyByteBuf b) { return new SetDeviceThroughputPacket(b.readBlockPos(), b.readVarInt()); }
    public static void handle(SetDeviceThroughputPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity bound = PacketUtil.manageableDevice(player, p.pos);
        // Clamp to [-1, MAX]: any negative means "unlimited"; never store an arbitrary crafted value.
        if (bound != null) bound.setThroughputCap(p.cap < -1 ? -1 : p.cap);
    }
}
