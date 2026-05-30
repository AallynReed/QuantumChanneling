package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: move a fluid subscription up (-1) or down (+1) in this device's priority list. */
public record MoveDeviceFluidSubchannelPacket(BlockPos pos, UUID subId, int direction) {
    public static void encode(MoveDeviceFluidSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeUUID(p.subId);
        b.writeVarInt(p.direction);
    }
    public static MoveDeviceFluidSubchannelPacket decode(FriendlyByteBuf b) {
        return new MoveDeviceFluidSubchannelPacket(b.readBlockPos(), b.readUUID(), b.readVarInt());
    }
    public static void handle(MoveDeviceFluidSubchannelPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            double dx = p.pos.getX() + 0.5 - player.getX();
            double dy = p.pos.getY() + 0.5 - player.getY();
            double dz = p.pos.getZ() + 0.5 - player.getZ();
            if (dx * dx + dy * dy + dz * dz > 64.0) return;
            BlockEntity be = player.level().getBlockEntity(p.pos);
            if (!(be instanceof ChannelBoundBlockEntity bound)) return;
            if (bound.moveSubscribedFluidSubchannel(p.subId, p.direction)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
