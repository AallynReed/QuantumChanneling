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

/** Client → server: flip the per-device gas participation flag. */
public record SetGasEnabledPacket(BlockPos pos, boolean enabled) implements CustomPacketPayload {
    public static final Type<SetGasEnabledPacket> TYPE = new Type<>(QuantumChanneling.id("set_gas_enabled"));
    public static final StreamCodec<FriendlyByteBuf, SetGasEnabledPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetGasEnabledPacket::encode, SetGasEnabledPacket::decode);

    @Override
    public Type<SetGasEnabledPacket> type() { return TYPE; }

    public static void encode(SetGasEnabledPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos); b.writeBoolean(p.enabled);
    }
    public static SetGasEnabledPacket decode(FriendlyByteBuf b) {
        return new SetGasEnabledPacket(b.readBlockPos(), b.readBoolean());
    }
    public static void handle(SetGasEnabledPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity bound = PacketUtil.usableDevice(player, p.pos);
        if (bound != null) {
            bound.setEnabled(Kind.GAS, p.enabled);
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
