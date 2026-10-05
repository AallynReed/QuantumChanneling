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

/** Client → server: add an item tag id to one of {@code emitterPos}'s item subchannel filters. */
public record AddSubchannelTagItemPacket(BlockPos emitterPos, UUID subId, Identifier tagId) implements CustomPacketPayload {
    public static final Type<AddSubchannelTagItemPacket> TYPE = new Type<>(QuantumChanneling.id("add_subchannel_tag_item"));
    public static final StreamCodec<FriendlyByteBuf, AddSubchannelTagItemPacket> STREAM_CODEC =
            StreamCodec.ofMember(AddSubchannelTagItemPacket::encode, AddSubchannelTagItemPacket::decode);

    @Override
    public Type<AddSubchannelTagItemPacket> type() { return TYPE; }

    public static void encode(AddSubchannelTagItemPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeIdentifier(p.tagId);
    }
    public static AddSubchannelTagItemPacket decode(FriendlyByteBuf b) {
        return new AddSubchannelTagItemPacket(b.readBlockPos(), b.readUUID(), b.readIdentifier());
    }
    public static void handle(AddSubchannelTagItemPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
        if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
        if (em.addSubchannelTagEntry(Kind.ITEM, p.subId, p.tagId)) {
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
