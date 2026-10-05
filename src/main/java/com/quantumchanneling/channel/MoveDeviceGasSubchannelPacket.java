package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.api.IQuantumSubchannelView.Kind;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client → server: reorder a gas subchannel on {@code emitterPos} in routing-priority order. */
public record MoveDeviceGasSubchannelPacket(BlockPos emitterPos, UUID subId, int direction) implements CustomPacketPayload {
    public static final Type<MoveDeviceGasSubchannelPacket> TYPE = new Type<>(QuantumChanneling.id("move_device_gas_subchannel"));
    public static final StreamCodec<FriendlyByteBuf, MoveDeviceGasSubchannelPacket> STREAM_CODEC =
            StreamCodec.ofMember(MoveDeviceGasSubchannelPacket::encode, MoveDeviceGasSubchannelPacket::decode);

    @Override
    public Type<MoveDeviceGasSubchannelPacket> type() { return TYPE; }

    public static void encode(MoveDeviceGasSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeVarInt(p.direction);
    }
    public static MoveDeviceGasSubchannelPacket decode(FriendlyByteBuf b) {
        return new MoveDeviceGasSubchannelPacket(b.readBlockPos(), b.readUUID(), b.readVarInt());
    }
    public static void handle(MoveDeviceGasSubchannelPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        if (em.moveSubchannel(Kind.GAS, p.subId, p.direction)) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
