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

/** Client → server: add a fluid id to one of {@code emitterPos}'s fluid subchannel filters. */
public record AddSubchannelFluidPacket(BlockPos emitterPos, UUID subId, ResourceLocation fluidId) {
    public static void encode(AddSubchannelFluidPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeResourceLocation(p.fluidId);
    }
    public static AddSubchannelFluidPacket decode(FriendlyByteBuf b) {
        return new AddSubchannelFluidPacket(b.readBlockPos(), b.readUUID(), b.readResourceLocation());
    }
    public static void handle(AddSubchannelFluidPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !PacketUtil.withinReach(player, p.emitterPos)) return;
            BlockEntity be = player.level().getBlockEntity(p.emitterPos);
            if (!(be instanceof PhotonEmitterBlockEntity em)) return;
            if (em.addFluidSubchannelEntry(p.subId, p.fluidId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
