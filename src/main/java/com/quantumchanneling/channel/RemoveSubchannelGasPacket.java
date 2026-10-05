package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: remove a gas id from one of {@code emitterPos}'s gas subchannel filters. */
public record RemoveSubchannelGasPacket(BlockPos emitterPos, UUID subId, ResourceLocation gasId) {
    public static void encode(RemoveSubchannelGasPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeResourceLocation(p.gasId);
    }
    public static RemoveSubchannelGasPacket decode(FriendlyByteBuf b) {
        return new RemoveSubchannelGasPacket(b.readBlockPos(), b.readUUID(), b.readResourceLocation());
    }
    public static void handle(RemoveSubchannelGasPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
            if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
            if (em.removeGasSubchannelEntry(p.subId, p.gasId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
