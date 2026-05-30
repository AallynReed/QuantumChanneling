package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: add a fluid id to one fluid subchannel's filter. */
public record AddSubchannelFluidPacket(UUID channelId, UUID subId, ResourceLocation fluidId) {
    public static void encode(AddSubchannelFluidPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUUID(p.subId);
        b.writeResourceLocation(p.fluidId);
    }
    public static AddSubchannelFluidPacket decode(FriendlyByteBuf b) {
        return new AddSubchannelFluidPacket(b.readUUID(), b.readUUID(), b.readResourceLocation());
    }
    public static void handle(AddSubchannelFluidPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.addSubchannelFluid(p.channelId, player.getUUID(), p.subId, p.fluidId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
