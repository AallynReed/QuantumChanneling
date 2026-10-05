package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.api.IQuantumSubchannelView.Kind;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client → server: drop a gas tag id from one of {@code emitterPos}'s gas subchannel filters. */
public record RemoveSubchannelTagGasPacket(BlockPos emitterPos, UUID subId, Identifier tagId) implements CustomPacketPayload {
    public static final Type<RemoveSubchannelTagGasPacket> TYPE = new Type<>(QuantumChanneling.id("remove_subchannel_tag_gas"));
    public static final StreamCodec<FriendlyByteBuf, RemoveSubchannelTagGasPacket> STREAM_CODEC =
            StreamCodec.ofMember(RemoveSubchannelTagGasPacket::encode, RemoveSubchannelTagGasPacket::decode);

    @Override
    public Type<RemoveSubchannelTagGasPacket> type() { return TYPE; }

    public static void encode(RemoveSubchannelTagGasPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeIdentifier(p.tagId);
    }
    public static RemoveSubchannelTagGasPacket decode(FriendlyByteBuf b) {
        return new RemoveSubchannelTagGasPacket(b.readBlockPos(), b.readUUID(), b.readIdentifier());
    }
    public static void handle(RemoveSubchannelTagGasPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        if (em.removeSubchannelTagEntry(Kind.GAS, p.subId, p.tagId)) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
