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
 * Client → server: delete an item subchannel owned by {@code emitterPos}. Sweeps the dangling
 * subscription off every receiver on the same channel before the subchannel disappears.
 */
public record DeleteSubchannelPacket(BlockPos emitterPos, UUID subId) implements CustomPacketPayload {
    public static final Type<DeleteSubchannelPacket> TYPE = new Type<>(QuantumChanneling.id("delete_subchannel"));
    public static final StreamCodec<FriendlyByteBuf, DeleteSubchannelPacket> STREAM_CODEC =
            StreamCodec.ofMember(DeleteSubchannelPacket::encode, DeleteSubchannelPacket::decode);

    @Override
    public Type<DeleteSubchannelPacket> type() { return TYPE; }

    public static void encode(DeleteSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
    }
    public static DeleteSubchannelPacket decode(FriendlyByteBuf b) {
        return new DeleteSubchannelPacket(b.readBlockPos(), b.readUUID());
    }
    public static void handle(DeleteSubchannelPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        // Sweep first so receivers can't briefly claim "subscribed" to a now-dead UUID.
        em.sweepReceiverSubscriptions(Kind.ITEM, p.subId);
        if (em.deleteSubchannel(Kind.ITEM, p.subId)) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
