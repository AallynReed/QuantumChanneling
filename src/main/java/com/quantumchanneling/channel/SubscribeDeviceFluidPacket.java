package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: toggle a receiver's subscription to a fluid subchannel. */
public record SubscribeDeviceFluidPacket(BlockPos pos, UUID subId, boolean subscribe) {
    public static void encode(SubscribeDeviceFluidPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeUUID(p.subId);
        b.writeBoolean(p.subscribe);
    }
    public static SubscribeDeviceFluidPacket decode(FriendlyByteBuf b) {
        return new SubscribeDeviceFluidPacket(b.readBlockPos(), b.readUUID(), b.readBoolean());
    }
    public static void handle(SubscribeDeviceFluidPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !PacketUtil.withinReach(player, p.pos)) return;
            BlockEntity be = player.level().getBlockEntity(p.pos);
            if (!(be instanceof PhotonReceiverBlockEntity rcv)) return;
            boolean changed = p.subscribe
                    ? rcv.addSubscribedFluidSubchannel(p.subId)
                    : rcv.removeSubscribedFluidSubchannel(p.subId);
            if (changed) CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
