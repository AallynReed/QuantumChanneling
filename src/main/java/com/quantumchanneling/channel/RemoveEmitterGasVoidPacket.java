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

public record RemoveEmitterGasVoidPacket(BlockPos pos, Identifier gasId) implements CustomPacketPayload {
    public static final Type<RemoveEmitterGasVoidPacket> TYPE = new Type<>(QuantumChanneling.id("remove_emitter_gas_void"));
    public static final StreamCodec<FriendlyByteBuf, RemoveEmitterGasVoidPacket> STREAM_CODEC =
            StreamCodec.ofMember(RemoveEmitterGasVoidPacket::encode, RemoveEmitterGasVoidPacket::decode);

    @Override
    public Type<RemoveEmitterGasVoidPacket> type() { return TYPE; }

    public static void encode(RemoveEmitterGasVoidPacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); b.writeIdentifier(p.gasId); }
    public static RemoveEmitterGasVoidPacket decode(FriendlyByteBuf b) { return new RemoveEmitterGasVoidPacket(b.readBlockPos(), b.readIdentifier()); }
    public static void handle(RemoveEmitterGasVoidPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.pos);
        if (!(dev instanceof PhotonEmitterBlockEntity emitter)) return;
        if (emitter.gasVoidFilter().remove(p.gasId)) {
            emitter.bumpLocalEdit();
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
