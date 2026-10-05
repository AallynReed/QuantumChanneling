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

public record AddEmitterGasVoidPacket(BlockPos pos, Identifier gasId) implements CustomPacketPayload {
    public static final Type<AddEmitterGasVoidPacket> TYPE = new Type<>(QuantumChanneling.id("add_emitter_gas_void"));
    public static final StreamCodec<FriendlyByteBuf, AddEmitterGasVoidPacket> STREAM_CODEC =
            StreamCodec.ofMember(AddEmitterGasVoidPacket::encode, AddEmitterGasVoidPacket::decode);

    @Override
    public Type<AddEmitterGasVoidPacket> type() { return TYPE; }

    public static void encode(AddEmitterGasVoidPacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); b.writeIdentifier(p.gasId); }
    public static AddEmitterGasVoidPacket decode(FriendlyByteBuf b) { return new AddEmitterGasVoidPacket(b.readBlockPos(), b.readIdentifier()); }
    public static void handle(AddEmitterGasVoidPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.pos);
        if (!(dev instanceof PhotonEmitterBlockEntity emitter)) return;
        if (emitter.gasVoidFilter().add(p.gasId)) {
            emitter.bumpLocalEdit();
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
