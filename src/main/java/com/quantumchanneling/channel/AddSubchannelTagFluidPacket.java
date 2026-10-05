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

/** Client → server: add a fluid tag id to one of {@code emitterPos}'s fluid subchannel filters. */
public record AddSubchannelTagFluidPacket(BlockPos emitterPos, UUID subId, Identifier tagId) implements CustomPacketPayload {
    public static final Type<AddSubchannelTagFluidPacket> TYPE = new Type<>(QuantumChanneling.id("add_subchannel_tag_fluid"));
    public static final StreamCodec<FriendlyByteBuf, AddSubchannelTagFluidPacket> STREAM_CODEC =
            StreamCodec.ofMember(AddSubchannelTagFluidPacket::encode, AddSubchannelTagFluidPacket::decode);

    @Override
    public Type<AddSubchannelTagFluidPacket> type() { return TYPE; }

    public static void encode(AddSubchannelTagFluidPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeIdentifier(p.tagId);
    }
    public static AddSubchannelTagFluidPacket decode(FriendlyByteBuf b) {
        return new AddSubchannelTagFluidPacket(b.readBlockPos(), b.readUUID(), b.readIdentifier());
    }
    public static void handle(AddSubchannelTagFluidPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        if (em.addSubchannelTagEntry(Kind.FLUID, p.subId, p.tagId)) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
