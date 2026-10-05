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

/**
 * Client → server: toggle a receiver's subscription to an item subchannel. Subchannels live on
 * emitters now, so only receivers carry a subscription set — sending this at an emitter is a no-op.
 */
public record SubscribeDevicePacket(BlockPos pos, UUID subId, boolean subscribe) implements CustomPacketPayload {
    public static final Type<SubscribeDevicePacket> TYPE = new Type<>(QuantumChanneling.id("subscribe_device"));
    public static final StreamCodec<FriendlyByteBuf, SubscribeDevicePacket> STREAM_CODEC =
            StreamCodec.ofMember(SubscribeDevicePacket::encode, SubscribeDevicePacket::decode);

    @Override
    public Type<SubscribeDevicePacket> type() { return TYPE; }

    public static void encode(SubscribeDevicePacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeUUID(p.subId);
        b.writeBoolean(p.subscribe);
    }
    public static SubscribeDevicePacket decode(FriendlyByteBuf b) {
        return new SubscribeDevicePacket(b.readBlockPos(), b.readUUID(), b.readBoolean());
    }
    public static void handle(SubscribeDevicePacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        // Subscribing a receiver is a per-device toggle any channel member may flip on their own
        // device — USER-level, not ADMIN.
        ChannelBoundBlockEntity dev = PacketUtil.usableDevice(player, p.pos);
        if (!(dev instanceof PhotonReceiverBlockEntity rcv)) return;
        boolean changed = p.subscribe
                ? rcv.addSubscription(Kind.ITEM, p.subId)
                : rcv.removeSubscription(Kind.ITEM, p.subId);
        if (changed) CreateChannelPacket.sendListBackTo(player);
    }
}
