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

/**
 * Client → server: set the color swatch on one of an emitter's subchannels.
 * {@code kind = 0} = item, {@code 1} = fluid, {@code 2} = gas. {@code rgb} is a packed 0xRRGGBB
 * value; 0 means "no color".
 */
public record SetSubchannelColorPacket(BlockPos emitterPos, byte kind, UUID subId, int rgb) implements CustomPacketPayload {
    public static final Type<SetSubchannelColorPacket> TYPE = new Type<>(QuantumChanneling.id("set_subchannel_color"));
    public static final StreamCodec<FriendlyByteBuf, SetSubchannelColorPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetSubchannelColorPacket::encode, SetSubchannelColorPacket::decode);

    @Override
    public Type<SetSubchannelColorPacket> type() { return TYPE; }

    public static void encode(SetSubchannelColorPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeByte(p.kind);
        b.writeUUID(p.subId);
        b.writeInt(p.rgb);
    }
    public static SetSubchannelColorPacket decode(FriendlyByteBuf b) {
        return new SetSubchannelColorPacket(b.readBlockPos(), b.readByte(), b.readUUID(), b.readInt());
    }
    public static void handle(SetSubchannelColorPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        boolean ok = switch (p.kind) {
            case 0 -> em.setSubchannelColor(Kind.ITEM, p.subId, p.rgb);
            case 1 -> em.setSubchannelColor(Kind.FLUID, p.subId, p.rgb);
            case 2 -> em.setSubchannelColor(Kind.GAS, p.subId, p.rgb);
            default -> false;
        };
        if (ok) CreateChannelPacket.sendListBackTo(player);
    }
}
