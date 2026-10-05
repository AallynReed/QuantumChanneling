package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: add {@code itemId} to one emitter's void filter. */
public record AddEmitterVoidItemPacket(BlockPos pos, Identifier itemId) implements CustomPacketPayload {
    public static final Type<AddEmitterVoidItemPacket> TYPE = new Type<>(QuantumChanneling.id("add_emitter_void_item"));
    public static final StreamCodec<FriendlyByteBuf, AddEmitterVoidItemPacket> STREAM_CODEC =
            StreamCodec.ofMember(AddEmitterVoidItemPacket::encode, AddEmitterVoidItemPacket::decode);

    @Override
    public Type<AddEmitterVoidItemPacket> type() { return TYPE; }

    public static void encode(AddEmitterVoidItemPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeIdentifier(p.itemId);
    }
    public static AddEmitterVoidItemPacket decode(FriendlyByteBuf b) {
        return new AddEmitterVoidItemPacket(b.readBlockPos(), b.readIdentifier());
    }
    public static void handle(AddEmitterVoidItemPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.pos);
        if (!(dev instanceof PhotonEmitterBlockEntity emitter)) return;
        if (emitter.voidFilter().add(p.itemId)) {
            emitter.bumpLocalEdit();
            // Push the channel list back so the client's MemberPos.voidFilter snapshot
            // refreshes — without this, the UI keeps showing the pre-edit filter.
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
