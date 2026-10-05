package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
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
            ChannelBoundBlockEntity bound = PacketUtil.manageableDevice(player, p.pos);
            // Clamp to [-1, MAX]: any negative means "unlimited"; never store an arbitrary crafted value.
            if (bound != null) bound.setThroughputCap(p.cap < -1 ? -1 : p.cap);
        });
        ctx.setPacketHandled(true);
    }
}
