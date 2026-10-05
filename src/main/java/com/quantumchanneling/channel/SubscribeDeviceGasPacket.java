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

/** Client → server: toggle a receiver's subscription to a gas subchannel. */
public record SubscribeDeviceGasPacket(BlockPos pos, UUID subId, boolean subscribe) implements CustomPacketPayload {
    public static final Type<SubscribeDeviceGasPacket> TYPE = new Type<>(QuantumChanneling.id("subscribe_device_gas"));
    public static final StreamCodec<FriendlyByteBuf, SubscribeDeviceGasPacket> STREAM_CODEC =
            StreamCodec.ofMember(SubscribeDeviceGasPacket::encode, SubscribeDeviceGasPacket::decode);

    @Override
    public Type<SubscribeDeviceGasPacket> type() { return TYPE; }

    public static void encode(SubscribeDeviceGasPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeUUID(p.subId);
        b.writeBoolean(p.subscribe);
    }
    public static SubscribeDeviceGasPacket decode(FriendlyByteBuf b) {
        return new SubscribeDeviceGasPacket(b.readBlockPos(), b.readUUID(), b.readBoolean());
    }
    public static void handle(SubscribeDeviceGasPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.usableDevice(player, p.pos);
        if (!(dev instanceof PhotonReceiverBlockEntity rcv)) return;
        boolean changed = p.subscribe
                ? rcv.addSubscription(Kind.GAS, p.subId)
                : rcv.removeSubscription(Kind.GAS, p.subId);
        if (changed) CreateChannelPacket.sendListBackTo(player);
    }
}
