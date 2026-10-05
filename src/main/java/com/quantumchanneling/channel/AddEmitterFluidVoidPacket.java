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

/** Client → server: add {@code fluidId} to one emitter's fluid void filter. */
public record AddEmitterFluidVoidPacket(BlockPos pos, Identifier fluidId) implements CustomPacketPayload {
    public static final Type<AddEmitterFluidVoidPacket> TYPE = new Type<>(QuantumChanneling.id("add_emitter_fluid_void"));
    public static final StreamCodec<FriendlyByteBuf, AddEmitterFluidVoidPacket> STREAM_CODEC =
            StreamCodec.ofMember(AddEmitterFluidVoidPacket::encode, AddEmitterFluidVoidPacket::decode);

    @Override
    public Type<AddEmitterFluidVoidPacket> type() { return TYPE; }

    public static void encode(AddEmitterFluidVoidPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeIdentifier(p.fluidId);
    }
    public static AddEmitterFluidVoidPacket decode(FriendlyByteBuf b) {
        return new AddEmitterFluidVoidPacket(b.readBlockPos(), b.readIdentifier());
    }
    public static void handle(AddEmitterFluidVoidPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.pos);
        if (!(dev instanceof PhotonEmitterBlockEntity emitter)) return;
        if (emitter.fluidVoidFilter().add(p.fluidId)) {
            emitter.bumpLocalEdit();
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
