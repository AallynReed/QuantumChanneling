package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: drop a gas tag id from one of {@code emitterPos}'s gas subchannel filters. */
public record RemoveSubchannelTagGasPacket(BlockPos emitterPos, UUID subId, ResourceLocation tagId) {
    public static void encode(RemoveSubchannelTagGasPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeResourceLocation(p.tagId);
    }
    public static RemoveSubchannelTagGasPacket decode(FriendlyByteBuf b) {
        return new RemoveSubchannelTagGasPacket(b.readBlockPos(), b.readUUID(), b.readResourceLocation());
    }
    public static void handle(RemoveSubchannelTagGasPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !PacketUtil.withinReach(player, p.emitterPos)) return;
            BlockEntity be = player.level().getBlockEntity(p.emitterPos);
            if (!(be instanceof PhotonEmitterBlockEntity em)) return;
            if (em.removeGasSubchannelTagEntry(p.subId, p.tagId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
