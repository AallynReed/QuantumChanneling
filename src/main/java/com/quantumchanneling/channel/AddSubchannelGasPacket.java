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

/** Client → server: add a gas id to one of {@code emitterPos}'s gas subchannel filters. */
public record AddSubchannelGasPacket(BlockPos emitterPos, UUID subId, Identifier gasId) implements CustomPacketPayload {
    public static final Type<AddSubchannelGasPacket> TYPE = new Type<>(QuantumChanneling.id("add_subchannel_gas"));
    public static final StreamCodec<FriendlyByteBuf, AddSubchannelGasPacket> STREAM_CODEC =
            StreamCodec.ofMember(AddSubchannelGasPacket::encode, AddSubchannelGasPacket::decode);

    @Override
    public Type<AddSubchannelGasPacket> type() { return TYPE; }

    public static void encode(AddSubchannelGasPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeIdentifier(p.gasId);
    }
    public static AddSubchannelGasPacket decode(FriendlyByteBuf b) {
        return new AddSubchannelGasPacket(b.readBlockPos(), b.readUUID(), b.readIdentifier());
    }
    public static void handle(AddSubchannelGasPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        if (em.addSubchannelEntry(Kind.GAS, p.subId, p.gasId)) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
