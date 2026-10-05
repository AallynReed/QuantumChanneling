package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.api.IQuantumSubchannelView.Kind;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: flip the per-device fluids participation flag. */
public record SetFluidEnabledPacket(BlockPos pos, boolean enabled) implements CustomPacketPayload {
    public static final Type<SetFluidEnabledPacket> TYPE = new Type<>(QuantumChanneling.id("set_fluid_enabled"));
    public static final StreamCodec<FriendlyByteBuf, SetFluidEnabledPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetFluidEnabledPacket::encode, SetFluidEnabledPacket::decode);

    @Override
    public Type<SetFluidEnabledPacket> type() { return TYPE; }

    public static void encode(SetFluidEnabledPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos); b.writeBoolean(p.enabled);
    }
    public static SetFluidEnabledPacket decode(FriendlyByteBuf b) {
        return new SetFluidEnabledPacket(b.readBlockPos(), b.readBoolean());
    }
    public static void handle(SetFluidEnabledPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity bound = PacketUtil.usableDevice(player, p.pos);
        if (bound != null) {
            bound.setEnabled(Kind.FLUID, p.enabled);
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
