package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SetDevicePriorityPacket(BlockPos pos, int priority) {
    public static void encode(SetDevicePriorityPacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); b.writeVarInt(p.priority); }
    public static SetDevicePriorityPacket decode(FriendlyByteBuf b) { return new SetDevicePriorityPacket(b.readBlockPos(), b.readVarInt()); }
    public static void handle(SetDevicePriorityPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity bound = PacketUtil.manageableDevice(player, p.pos);
            if (bound != null) bound.setPriority(p.priority);
        });
        ctx.setPacketHandled(true);
    }
}
