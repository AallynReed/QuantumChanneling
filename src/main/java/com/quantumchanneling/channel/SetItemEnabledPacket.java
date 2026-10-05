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

/** Client → server: flip the per-device items participation flag. */
public record SetItemEnabledPacket(BlockPos pos, boolean enabled) implements CustomPacketPayload {
    public static final Type<SetItemEnabledPacket> TYPE = new Type<>(QuantumChanneling.id("set_item_enabled"));
    public static final StreamCodec<FriendlyByteBuf, SetItemEnabledPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetItemEnabledPacket::encode, SetItemEnabledPacket::decode);

    @Override
    public Type<SetItemEnabledPacket> type() { return TYPE; }

    public static void encode(SetItemEnabledPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos); b.writeBoolean(p.enabled);
    }
    public static SetItemEnabledPacket decode(FriendlyByteBuf b) {
        return new SetItemEnabledPacket(b.readBlockPos(), b.readBoolean());
    }
    public static void handle(SetItemEnabledPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity bound = PacketUtil.usableDevice(player, p.pos);
        if (bound != null) {
            bound.setEnabled(Kind.ITEM, p.enabled);
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
