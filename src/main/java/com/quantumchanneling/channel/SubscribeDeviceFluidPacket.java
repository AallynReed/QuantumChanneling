package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.api.IQuantumSubchannelView.Kind;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client → server: toggle a receiver's subscription to a fluid subchannel. */
public record SubscribeDeviceFluidPacket(BlockPos pos, UUID subId, boolean subscribe) implements CustomPacketPayload {
    public static final Type<SubscribeDeviceFluidPacket> TYPE = new Type<>(QuantumChanneling.id("subscribe_device_fluid"));
    public static final StreamCodec<FriendlyByteBuf, SubscribeDeviceFluidPacket> STREAM_CODEC =
            StreamCodec.ofMember(SubscribeDeviceFluidPacket::encode, SubscribeDeviceFluidPacket::decode);

    @Override
    public Type<SubscribeDeviceFluidPacket> type() { return TYPE; }

    public static void encode(SubscribeDeviceFluidPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeUUID(p.subId);
        b.writeBoolean(p.subscribe);
    }
    public static SubscribeDeviceFluidPacket decode(FriendlyByteBuf b) {
        return new SubscribeDeviceFluidPacket(b.readBlockPos(), b.readUUID(), b.readBoolean());
    }
    public static void handle(SubscribeDeviceFluidPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.usableDevice(player, p.pos);
        if (!(dev instanceof PhotonReceiverBlockEntity rcv)) return;
        boolean changed = p.subscribe
                ? rcv.addSubscription(Kind.FLUID, p.subId)
                : rcv.removeSubscription(Kind.FLUID, p.subId);
        if (changed) CreateChannelPacket.sendListBackTo(player);
    }
}
