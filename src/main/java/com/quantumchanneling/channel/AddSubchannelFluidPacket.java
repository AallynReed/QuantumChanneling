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

/** Client → server: add a fluid id to one of {@code emitterPos}'s fluid subchannel filters. */
public record AddSubchannelFluidPacket(BlockPos emitterPos, UUID subId, Identifier fluidId) implements CustomPacketPayload {
    public static final Type<AddSubchannelFluidPacket> TYPE = new Type<>(QuantumChanneling.id("add_subchannel_fluid"));
    public static final StreamCodec<FriendlyByteBuf, AddSubchannelFluidPacket> STREAM_CODEC =
            StreamCodec.ofMember(AddSubchannelFluidPacket::encode, AddSubchannelFluidPacket::decode);

    @Override
    public Type<AddSubchannelFluidPacket> type() { return TYPE; }

    public static void encode(AddSubchannelFluidPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeIdentifier(p.fluidId);
    }
    public static AddSubchannelFluidPacket decode(FriendlyByteBuf b) {
        return new AddSubchannelFluidPacket(b.readBlockPos(), b.readUUID(), b.readIdentifier());
    }
    public static void handle(AddSubchannelFluidPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        if (em.addSubchannelEntry(Kind.FLUID, p.subId, p.fluidId)) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
