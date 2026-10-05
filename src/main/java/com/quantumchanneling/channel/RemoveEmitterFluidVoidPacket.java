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

/** Client → server: remove {@code fluidId} from one emitter's fluid void filter. */
public record RemoveEmitterFluidVoidPacket(BlockPos pos, Identifier fluidId) implements CustomPacketPayload {
    public static final Type<RemoveEmitterFluidVoidPacket> TYPE = new Type<>(QuantumChanneling.id("remove_emitter_fluid_void"));
    public static final StreamCodec<FriendlyByteBuf, RemoveEmitterFluidVoidPacket> STREAM_CODEC =
            StreamCodec.ofMember(RemoveEmitterFluidVoidPacket::encode, RemoveEmitterFluidVoidPacket::decode);

    @Override
    public Type<RemoveEmitterFluidVoidPacket> type() { return TYPE; }

    public static void encode(RemoveEmitterFluidVoidPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeIdentifier(p.fluidId);
    }
    public static RemoveEmitterFluidVoidPacket decode(FriendlyByteBuf b) {
        return new RemoveEmitterFluidVoidPacket(b.readBlockPos(), b.readIdentifier());
    }
    public static void handle(RemoveEmitterFluidVoidPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.pos);
        if (!(dev instanceof PhotonEmitterBlockEntity emitter)) return;
        if (emitter.fluidVoidFilter().remove(p.fluidId)) {
            emitter.bumpLocalEdit();
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
