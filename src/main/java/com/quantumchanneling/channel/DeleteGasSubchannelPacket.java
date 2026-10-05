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

/** Client → server: delete a gas subchannel owned by {@code emitterPos}. */
public record DeleteGasSubchannelPacket(BlockPos emitterPos, UUID subId) implements CustomPacketPayload {
    public static final Type<DeleteGasSubchannelPacket> TYPE = new Type<>(QuantumChanneling.id("delete_gas_subchannel"));
    public static final StreamCodec<FriendlyByteBuf, DeleteGasSubchannelPacket> STREAM_CODEC =
            StreamCodec.ofMember(DeleteGasSubchannelPacket::encode, DeleteGasSubchannelPacket::decode);

    @Override
    public Type<DeleteGasSubchannelPacket> type() { return TYPE; }

    public static void encode(DeleteGasSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
    }
    public static DeleteGasSubchannelPacket decode(FriendlyByteBuf b) {
        return new DeleteGasSubchannelPacket(b.readBlockPos(), b.readUUID());
    }
    public static void handle(DeleteGasSubchannelPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        em.sweepReceiverSubscriptions(Kind.GAS, p.subId);
        if (em.deleteSubchannel(Kind.GAS, p.subId)) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
