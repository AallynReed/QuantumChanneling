package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: assign a custom display name to a device. */
public record RenameDevicePacket(BlockPos pos, String name) {
    public static void encode(RenameDevicePacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeUtf(p.name, 64);
    }
    public static RenameDevicePacket decode(FriendlyByteBuf b) {
        return new RenameDevicePacket(b.readBlockPos(), b.readUtf(64));
    }
    public static void handle(RenameDevicePacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity bound = PacketUtil.manageableDevice(player, p.pos);
            if (bound != null) bound.setCustomName(p.name);
        });
        ctx.setPacketHandled(true);
    }
}
