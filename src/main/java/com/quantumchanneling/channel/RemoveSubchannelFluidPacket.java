package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: remove a fluid id from one fluid subchannel's filter. */
public record RemoveSubchannelFluidPacket(UUID channelId, UUID subId, ResourceLocation fluidId) {
    public static void encode(RemoveSubchannelFluidPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUUID(p.subId);
        b.writeResourceLocation(p.fluidId);
    }
    public static RemoveSubchannelFluidPacket decode(FriendlyByteBuf b) {
        return new RemoveSubchannelFluidPacket(b.readUUID(), b.readUUID(), b.readResourceLocation());
    }
    public static void handle(RemoveSubchannelFluidPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.removeSubchannelFluid(p.channelId, player.getUUID(), p.subId, p.fluidId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
