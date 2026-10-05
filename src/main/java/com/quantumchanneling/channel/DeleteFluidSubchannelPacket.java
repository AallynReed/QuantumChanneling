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

/** Client → server: delete a fluid subchannel owned by {@code emitterPos}. */
public record DeleteFluidSubchannelPacket(BlockPos emitterPos, UUID subId) implements CustomPacketPayload {
    public static final Type<DeleteFluidSubchannelPacket> TYPE = new Type<>(QuantumChanneling.id("delete_fluid_subchannel"));
    public static final StreamCodec<FriendlyByteBuf, DeleteFluidSubchannelPacket> STREAM_CODEC =
            StreamCodec.ofMember(DeleteFluidSubchannelPacket::encode, DeleteFluidSubchannelPacket::decode);

    @Override
    public Type<DeleteFluidSubchannelPacket> type() { return TYPE; }

    public static void encode(DeleteFluidSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
    }
    public static DeleteFluidSubchannelPacket decode(FriendlyByteBuf b) {
        return new DeleteFluidSubchannelPacket(b.readBlockPos(), b.readUUID());
    }
    public static void handle(DeleteFluidSubchannelPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        em.sweepReceiverSubscriptions(Kind.FLUID, p.subId);
        if (em.deleteSubchannel(Kind.FLUID, p.subId)) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
