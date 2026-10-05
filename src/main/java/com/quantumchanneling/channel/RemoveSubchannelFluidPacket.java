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

/** Client → server: remove a fluid id from one of {@code emitterPos}'s fluid subchannel filters. */
public record RemoveSubchannelFluidPacket(BlockPos emitterPos, UUID subId, Identifier fluidId) implements CustomPacketPayload {
    public static final Type<RemoveSubchannelFluidPacket> TYPE = new Type<>(QuantumChanneling.id("remove_subchannel_fluid"));
    public static final StreamCodec<FriendlyByteBuf, RemoveSubchannelFluidPacket> STREAM_CODEC =
            StreamCodec.ofMember(RemoveSubchannelFluidPacket::encode, RemoveSubchannelFluidPacket::decode);

    @Override
    public Type<RemoveSubchannelFluidPacket> type() { return TYPE; }

    public static void encode(RemoveSubchannelFluidPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeIdentifier(p.fluidId);
    }
    public static RemoveSubchannelFluidPacket decode(FriendlyByteBuf b) {
        return new RemoveSubchannelFluidPacket(b.readBlockPos(), b.readUUID(), b.readIdentifier());
    }
    public static void handle(RemoveSubchannelFluidPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        if (em.removeSubchannelEntry(Kind.FLUID, p.subId, p.fluidId)) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
