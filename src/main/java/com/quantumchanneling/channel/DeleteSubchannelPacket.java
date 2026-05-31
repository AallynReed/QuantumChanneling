package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → server: delete an item subchannel owned by {@code emitterPos}. Sweeps the dangling
 * subscription off every receiver on the same channel before the subchannel disappears.
 */
public record DeleteSubchannelPacket(BlockPos emitterPos, UUID subId) {
    public static void encode(DeleteSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
    }
    public static DeleteSubchannelPacket decode(FriendlyByteBuf b) {
        return new DeleteSubchannelPacket(b.readBlockPos(), b.readUUID());
    }
    public static void handle(DeleteSubchannelPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !PacketUtil.withinReach(player, p.emitterPos)) return;
            BlockEntity be = player.level().getBlockEntity(p.emitterPos);
            if (!(be instanceof PhotonEmitterBlockEntity em)) return;
            // Sweep first so receivers can't briefly claim "subscribed" to a now-dead UUID.
            em.sweepReceiverSubscriptionsForItemSubchannel(p.subId);
            if (em.deleteItemSubchannel(p.subId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
