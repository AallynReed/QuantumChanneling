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

/** Client → server: rename an item subchannel owned by {@code emitterPos}. */
public record RenameSubchannelPacket(BlockPos emitterPos, UUID subId, String name) implements CustomPacketPayload {
    public static final Type<RenameSubchannelPacket> TYPE = new Type<>(QuantumChanneling.id("rename_subchannel"));
    public static final StreamCodec<FriendlyByteBuf, RenameSubchannelPacket> STREAM_CODEC =
            StreamCodec.ofMember(RenameSubchannelPacket::encode, RenameSubchannelPacket::decode);

    @Override
    public Type<RenameSubchannelPacket> type() { return TYPE; }

    public static void encode(RenameSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeUtf(p.name, ItemSubchannel.NAME_MAX);
    }
    public static RenameSubchannelPacket decode(FriendlyByteBuf b) {
        return new RenameSubchannelPacket(b.readBlockPos(), b.readUUID(), b.readUtf(ItemSubchannel.NAME_MAX));
    }
    public static void handle(RenameSubchannelPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        if (em.renameSubchannel(Kind.ITEM, p.subId, p.name)) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
