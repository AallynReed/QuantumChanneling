package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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
            Level level = player.level();
            // Range check: the player must be reasonably close to the device they're toggling.
            double dx = pkt.pos.getX() + 0.5 - player.getX();
            double dy = pkt.pos.getY() + 0.5 - player.getY();
            double dz = pkt.pos.getZ() + 0.5 - player.getZ();
            if (dx * dx + dy * dy + dz * dz > 64.0) return;
            BlockEntity be = level.getBlockEntity(pkt.pos);
            if (be instanceof ChannelBoundBlockEntity bound) {
                bound.setChunkLoadForced(pkt.enabled);
            }
        });
        ctx.setPacketHandled(true);
    }
}
