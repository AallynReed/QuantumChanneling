package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: set the device's redstone gating mode (ordinal of {@code RedstoneMode}). */
public record SetRedstoneModePacket(BlockPos devicePos, byte modeOrdinal) implements CustomPacketPayload {
    public static final Type<SetRedstoneModePacket> TYPE = new Type<>(QuantumChanneling.id("set_redstone_mode"));
    public static final StreamCodec<FriendlyByteBuf, SetRedstoneModePacket> STREAM_CODEC =
            StreamCodec.ofMember(SetRedstoneModePacket::encode, SetRedstoneModePacket::decode);

    @Override
    public Type<SetRedstoneModePacket> type() { return TYPE; }

    public static void encode(SetRedstoneModePacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.devicePos);
        b.writeByte(p.modeOrdinal);
    }
    public static SetRedstoneModePacket decode(FriendlyByteBuf b) {
        return new SetRedstoneModePacket(b.readBlockPos(), b.readByte());
    }
    public static void handle(SetRedstoneModePacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity bound = PacketUtil.manageableDevice(player, p.devicePos);
        if (bound == null) return;
        bound.setRedstoneMode(ChannelBoundBlockEntity.RedstoneMode.byOrdinal(p.modeOrdinal));
        CreateChannelPacket.sendListBackTo(player);
    }
}
