package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Set a Photon Emitter/Receiver per-device throughput cap. -1 = unlimited (use global config). */
public record SetDeviceThroughputPacket(BlockPos pos, int cap) {
    public static void encode(SetDeviceThroughputPacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); b.writeVarInt(p.cap); }
    public static SetDeviceThroughputPacket decode(FriendlyByteBuf b) { return new SetDeviceThroughputPacket(b.readBlockPos(), b.readVarInt()); }
    public static void handle(SetDeviceThroughputPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            // Range check + BE type check.
            double dx = p.pos.getX() + 0.5 - player.getX();
            double dy = p.pos.getY() + 0.5 - player.getY();
            double dz = p.pos.getZ() + 0.5 - player.getZ();
            if (dx * dx + dy * dy + dz * dz > 64.0) return;
            BlockEntity be = player.level().getBlockEntity(p.pos);
            if (be instanceof ChannelBoundBlockEntity bound) bound.setThroughputCap(p.cap);
        });
        ctx.setPacketHandled(true);
    }
}
