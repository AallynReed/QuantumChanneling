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

/** Client → server: create a new fluid subchannel on the emitter at {@code emitterPos}. */
public record CreateFluidSubchannelPacket(BlockPos emitterPos, String name) implements CustomPacketPayload {
    public static final Type<CreateFluidSubchannelPacket> TYPE = new Type<>(QuantumChanneling.id("create_fluid_subchannel"));
    public static final StreamCodec<FriendlyByteBuf, CreateFluidSubchannelPacket> STREAM_CODEC =
            StreamCodec.ofMember(CreateFluidSubchannelPacket::encode, CreateFluidSubchannelPacket::decode);

    @Override
    public Type<CreateFluidSubchannelPacket> type() { return TYPE; }

    public static void encode(CreateFluidSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUtf(p.name, FluidSubchannel.NAME_MAX);
    }
    public static CreateFluidSubchannelPacket decode(FriendlyByteBuf b) {
        return new CreateFluidSubchannelPacket(b.readBlockPos(), b.readUtf(FluidSubchannel.NAME_MAX));
    }
    public static void handle(CreateFluidSubchannelPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        if (em.createSubchannel(Kind.FLUID, p.name) != null) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
