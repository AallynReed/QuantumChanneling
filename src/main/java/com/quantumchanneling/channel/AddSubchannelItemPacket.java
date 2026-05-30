package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: add {@code itemId} to one subchannel's filter. */
public record AddSubchannelItemPacket(UUID channelId, UUID subId, ResourceLocation itemId) {
    public static void encode(AddSubchannelItemPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUUID(p.subId);
        b.writeResourceLocation(p.itemId);
    }
    public static AddSubchannelItemPacket decode(FriendlyByteBuf b) {
        return new AddSubchannelItemPacket(b.readUUID(), b.readUUID(), b.readResourceLocation());
    }
    public static void handle(AddSubchannelItemPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.addSubchannelItem(p.channelId, player.getUUID(), p.subId, p.itemId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
