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

/** Client → server: create a new item subchannel on the emitter at {@code emitterPos}. */
public record CreateSubchannelPacket(BlockPos emitterPos, String name) implements CustomPacketPayload {
    public static final Type<CreateSubchannelPacket> TYPE = new Type<>(QuantumChanneling.id("create_subchannel"));
    public static final StreamCodec<FriendlyByteBuf, CreateSubchannelPacket> STREAM_CODEC =
            StreamCodec.ofMember(CreateSubchannelPacket::encode, CreateSubchannelPacket::decode);

    @Override
    public Type<CreateSubchannelPacket> type() { return TYPE; }

    public static void encode(CreateSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUtf(p.name, ItemSubchannel.NAME_MAX);
    }
    public static CreateSubchannelPacket decode(FriendlyByteBuf b) {
        return new CreateSubchannelPacket(b.readBlockPos(), b.readUtf(ItemSubchannel.NAME_MAX));
    }
    public static void handle(CreateSubchannelPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        if (em.createSubchannel(Kind.ITEM, p.name) != null) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
