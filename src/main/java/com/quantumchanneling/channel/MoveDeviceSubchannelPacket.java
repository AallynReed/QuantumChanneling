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

/**
 * Client → server: reorder one of {@code emitterPos}'s item subchannels up ({@code direction = -1})
 * or down ({@code direction = +1}) in the iteration order — i.e. the routing priority.
 */
public record MoveDeviceSubchannelPacket(BlockPos emitterPos, UUID subId, int direction) implements CustomPacketPayload {
    public static final Type<MoveDeviceSubchannelPacket> TYPE = new Type<>(QuantumChanneling.id("move_device_subchannel"));
    public static final StreamCodec<FriendlyByteBuf, MoveDeviceSubchannelPacket> STREAM_CODEC =
            StreamCodec.ofMember(MoveDeviceSubchannelPacket::encode, MoveDeviceSubchannelPacket::decode);

    @Override
    public Type<MoveDeviceSubchannelPacket> type() { return TYPE; }

    public static void encode(MoveDeviceSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeVarInt(p.direction);
    }
    public static MoveDeviceSubchannelPacket decode(FriendlyByteBuf b) {
        return new MoveDeviceSubchannelPacket(b.readBlockPos(), b.readUUID(), b.readVarInt());
    }
    public static void handle(MoveDeviceSubchannelPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        if (em.moveSubchannel(Kind.ITEM, p.subId, p.direction)) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
