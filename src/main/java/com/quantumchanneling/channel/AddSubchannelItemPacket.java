package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.api.IQuantumSubchannelView.Kind;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client → server: add an item id to one of {@code emitterPos}'s item subchannel filters. */
public record AddSubchannelItemPacket(BlockPos emitterPos, UUID subId, Identifier itemId) implements CustomPacketPayload {
    public static final Type<AddSubchannelItemPacket> TYPE = new Type<>(QuantumChanneling.id("add_subchannel_item"));
    public static final StreamCodec<FriendlyByteBuf, AddSubchannelItemPacket> STREAM_CODEC =
            StreamCodec.ofMember(AddSubchannelItemPacket::encode, AddSubchannelItemPacket::decode);

    @Override
    public Type<AddSubchannelItemPacket> type() { return TYPE; }

    public static void encode(AddSubchannelItemPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeIdentifier(p.itemId);
    }
    public static AddSubchannelItemPacket decode(FriendlyByteBuf b) {
        return new AddSubchannelItemPacket(b.readBlockPos(), b.readUUID(), b.readIdentifier());
    }
    public static void handle(AddSubchannelItemPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        if (em.addSubchannelEntry(Kind.ITEM, p.subId, p.itemId)) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
