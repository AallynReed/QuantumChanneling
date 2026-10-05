package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ToggleChunkLoadPacket(BlockPos pos, boolean enabled) {

    public static void encode(ToggleChunkLoadPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeBoolean(pkt.enabled);
    }

    public static ToggleChunkLoadPacket decode(FriendlyByteBuf buf) {
        return new ToggleChunkLoadPacket(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(ToggleChunkLoadPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity bound = PacketUtil.manageableDevice(player, pkt.pos);
            if (bound != null) {
                bound.setChunkLoadForced(pkt.enabled);
            }
        });
        ctx.setPacketHandled(true);
    }
}
