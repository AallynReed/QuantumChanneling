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

/**
 * Client → server: update the 6-bit per-side mask for one resource kind on a channel-bound device.
 * {@code kind} = 0 (items), 1 (fluids), 2 (gas). {@code mask} bits 0..5 follow
 * {@link net.minecraft.core.Direction#get3DDataValue()}.
 */
public record SetSideMaskPacket(BlockPos devicePos, byte kind, byte mask) implements CustomPacketPayload {
    public static final Type<SetSideMaskPacket> TYPE = new Type<>(QuantumChanneling.id("set_side_mask"));
    public static final StreamCodec<FriendlyByteBuf, SetSideMaskPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetSideMaskPacket::encode, SetSideMaskPacket::decode);

    @Override
    public Type<SetSideMaskPacket> type() { return TYPE; }

    public static void encode(SetSideMaskPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.devicePos);
        b.writeByte(p.kind);
        b.writeByte(p.mask);
    }
    public static SetSideMaskPacket decode(FriendlyByteBuf b) {
        return new SetSideMaskPacket(b.readBlockPos(), b.readByte(), b.readByte());
    }
    public static void handle(SetSideMaskPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity bound = PacketUtil.manageableDevice(player, p.devicePos);
        if (bound == null) return;
        int v = p.mask & 0x3F;
        switch (p.kind) {
            case 0 -> bound.setSideMask(Kind.ITEM, v);
            case 1 -> bound.setSideMask(Kind.FLUID, v);
            case 2 -> bound.setSideMask(Kind.GAS, v);
            default -> { return; }
        }
        CreateChannelPacket.sendListBackTo(player);
    }
}
