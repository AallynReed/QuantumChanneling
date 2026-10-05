package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToggleChunkLoadPacket(BlockPos pos, boolean enabled) implements CustomPacketPayload {
    public static final Type<ToggleChunkLoadPacket> TYPE = new Type<>(QuantumChanneling.id("toggle_chunk_load"));
    public static final StreamCodec<FriendlyByteBuf, ToggleChunkLoadPacket> STREAM_CODEC =
            StreamCodec.ofMember(ToggleChunkLoadPacket::encode, ToggleChunkLoadPacket::decode);

    @Override
    public Type<ToggleChunkLoadPacket> type() { return TYPE; }


    public static void encode(ToggleChunkLoadPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeBoolean(pkt.enabled);
    }

    public static ToggleChunkLoadPacket decode(FriendlyByteBuf buf) {
        return new ToggleChunkLoadPacket(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(ToggleChunkLoadPacket pkt, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity bound = PacketUtil.manageableDevice(player, pkt.pos);
        if (bound != null) {
            bound.setChunkLoadForced(pkt.enabled);
        }
    }
}
