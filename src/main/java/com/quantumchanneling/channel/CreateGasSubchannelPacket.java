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

/** Client → server: create a new gas subchannel on the emitter at {@code emitterPos}. */
public record CreateGasSubchannelPacket(BlockPos emitterPos, String name) implements CustomPacketPayload {
    public static final Type<CreateGasSubchannelPacket> TYPE = new Type<>(QuantumChanneling.id("create_gas_subchannel"));
    public static final StreamCodec<FriendlyByteBuf, CreateGasSubchannelPacket> STREAM_CODEC =
            StreamCodec.ofMember(CreateGasSubchannelPacket::encode, CreateGasSubchannelPacket::decode);

    @Override
    public Type<CreateGasSubchannelPacket> type() { return TYPE; }

    public static void encode(CreateGasSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUtf(p.name, GasSubchannel.NAME_MAX);
    }
    public static CreateGasSubchannelPacket decode(FriendlyByteBuf b) {
        return new CreateGasSubchannelPacket(b.readBlockPos(), b.readUtf(GasSubchannel.NAME_MAX));
    }
    public static void handle(CreateGasSubchannelPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        if (em.createSubchannel(Kind.GAS, p.name) != null) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
