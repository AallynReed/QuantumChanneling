package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → server: toggle a receiver's subscription to an item subchannel. Subchannels live on
 * emitters now, so only receivers carry a subscription set — sending this at an emitter is a no-op.
 */
public record SubscribeDevicePacket(BlockPos pos, UUID subId, boolean subscribe) {
    public static void encode(SubscribeDevicePacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeUUID(p.subId);
        b.writeBoolean(p.subscribe);
    }
    public static SubscribeDevicePacket decode(FriendlyByteBuf b) {
        return new SubscribeDevicePacket(b.readBlockPos(), b.readUUID(), b.readBoolean());
    }
    public static void handle(SubscribeDevicePacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !PacketUtil.withinReach(player, p.pos)) return;
            BlockEntity be = player.level().getBlockEntity(p.pos);
            if (!(be instanceof PhotonReceiverBlockEntity rcv)) return;
            boolean changed = p.subscribe
                    ? rcv.addSubscribedSubchannel(p.subId)
                    : rcv.removeSubscribedSubchannel(p.subId);
            if (changed) CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
