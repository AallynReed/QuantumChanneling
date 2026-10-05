package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetDeviceSurgePacket(BlockPos pos, boolean surge) implements CustomPacketPayload {
    public static final Type<SetDeviceSurgePacket> TYPE = new Type<>(QuantumChanneling.id("set_device_surge"));
    public static final StreamCodec<FriendlyByteBuf, SetDeviceSurgePacket> STREAM_CODEC =
            StreamCodec.ofMember(SetDeviceSurgePacket::encode, SetDeviceSurgePacket::decode);

    @Override
    public Type<SetDeviceSurgePacket> type() { return TYPE; }

    public static void encode(SetDeviceSurgePacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); b.writeBoolean(p.surge); }
    public static SetDeviceSurgePacket decode(FriendlyByteBuf b) { return new SetDeviceSurgePacket(b.readBlockPos(), b.readBoolean()); }
    public static void handle(SetDeviceSurgePacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity bound = PacketUtil.manageableDevice(player, p.pos);
        if (bound != null) bound.setSurgeMode(p.surge);
    }
}
