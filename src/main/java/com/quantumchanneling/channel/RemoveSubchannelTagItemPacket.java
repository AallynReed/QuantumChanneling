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

/** Client → server: drop an item tag id from one of {@code emitterPos}'s item subchannel filters. */
public record RemoveSubchannelTagItemPacket(BlockPos emitterPos, UUID subId, ResourceLocation tagId) {
    public static void encode(RemoveSubchannelTagItemPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeResourceLocation(p.tagId);
    }
    public static RemoveSubchannelTagItemPacket decode(FriendlyByteBuf b) {
        return new RemoveSubchannelTagItemPacket(b.readBlockPos(), b.readUUID(), b.readResourceLocation());
    }
    public static void handle(RemoveSubchannelTagItemPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !PacketUtil.withinReach(player, p.emitterPos)) return;
            BlockEntity be = player.level().getBlockEntity(p.emitterPos);
            if (!(be instanceof PhotonEmitterBlockEntity em)) return;
            if (em.removeItemSubchannelTagEntry(p.subId, p.tagId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
