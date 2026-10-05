package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: delete a gas subchannel owned by {@code emitterPos}. */
public record DeleteGasSubchannelPacket(BlockPos emitterPos, UUID subId) {
    public static void encode(DeleteGasSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
    }
    public static DeleteGasSubchannelPacket decode(FriendlyByteBuf b) {
        return new DeleteGasSubchannelPacket(b.readBlockPos(), b.readUUID());
    }
    public static void handle(DeleteGasSubchannelPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
            if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
            em.sweepReceiverSubscriptionsForGasSubchannel(p.subId);
            if (em.deleteGasSubchannel(p.subId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
