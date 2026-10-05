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

/** Client → server: flip whitelist/blacklist on an item subchannel owned by {@code emitterPos}. */
public record SetSubchannelFilterModePacket(BlockPos emitterPos, UUID subId, boolean whitelist) implements CustomPacketPayload {
    public static final Type<SetSubchannelFilterModePacket> TYPE = new Type<>(QuantumChanneling.id("set_subchannel_filter_mode"));
    public static final StreamCodec<FriendlyByteBuf, SetSubchannelFilterModePacket> STREAM_CODEC =
            StreamCodec.ofMember(SetSubchannelFilterModePacket::encode, SetSubchannelFilterModePacket::decode);

    @Override
    public Type<SetSubchannelFilterModePacket> type() { return TYPE; }

    public static void encode(SetSubchannelFilterModePacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeBoolean(p.whitelist);
    }
    public static SetSubchannelFilterModePacket decode(FriendlyByteBuf b) {
        return new SetSubchannelFilterModePacket(b.readBlockPos(), b.readUUID(), b.readBoolean());
    }
    public static void handle(SetSubchannelFilterModePacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        if (em.setSubchannelFilterMode(Kind.ITEM, p.subId, p.whitelist)) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
