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

/** Client → server: remove {@code itemId} from one emitter's void filter. */
public record RemoveEmitterVoidItemPacket(BlockPos pos, Identifier itemId) implements CustomPacketPayload {
    public static final Type<RemoveEmitterVoidItemPacket> TYPE = new Type<>(QuantumChanneling.id("remove_emitter_void_item"));
    public static final StreamCodec<FriendlyByteBuf, RemoveEmitterVoidItemPacket> STREAM_CODEC =
            StreamCodec.ofMember(RemoveEmitterVoidItemPacket::encode, RemoveEmitterVoidItemPacket::decode);

    @Override
    public Type<RemoveEmitterVoidItemPacket> type() { return TYPE; }

    public static void encode(RemoveEmitterVoidItemPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeIdentifier(p.itemId);
    }
    public static RemoveEmitterVoidItemPacket decode(FriendlyByteBuf b) {
        return new RemoveEmitterVoidItemPacket(b.readBlockPos(), b.readIdentifier());
    }
    public static void handle(RemoveEmitterVoidItemPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.pos);
        if (!(dev instanceof PhotonEmitterBlockEntity emitter)) return;
        if (emitter.voidFilter().remove(p.itemId)) {
            emitter.bumpLocalEdit();
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
