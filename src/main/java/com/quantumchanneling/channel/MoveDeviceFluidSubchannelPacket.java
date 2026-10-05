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

/** Client → server: reorder a fluid subchannel on {@code emitterPos} in routing-priority order. */
public record MoveDeviceFluidSubchannelPacket(BlockPos emitterPos, UUID subId, int direction) implements CustomPacketPayload {
    public static final Type<MoveDeviceFluidSubchannelPacket> TYPE = new Type<>(QuantumChanneling.id("move_device_fluid_subchannel"));
    public static final StreamCodec<FriendlyByteBuf, MoveDeviceFluidSubchannelPacket> STREAM_CODEC =
            StreamCodec.ofMember(MoveDeviceFluidSubchannelPacket::encode, MoveDeviceFluidSubchannelPacket::decode);

    @Override
    public Type<MoveDeviceFluidSubchannelPacket> type() { return TYPE; }

    public static void encode(MoveDeviceFluidSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeVarInt(p.direction);
    }
    public static MoveDeviceFluidSubchannelPacket decode(FriendlyByteBuf b) {
        return new MoveDeviceFluidSubchannelPacket(b.readBlockPos(), b.readUUID(), b.readVarInt());
    }
    public static void handle(MoveDeviceFluidSubchannelPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        if (em.moveSubchannel(Kind.FLUID, p.subId, p.direction)) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
