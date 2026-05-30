package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: create a new subchannel with {@code name} on {@code channelId}. */
public record CreateSubchannelPacket(UUID channelId, String name) {
    public static void encode(CreateSubchannelPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUtf(p.name, ItemSubchannel.NAME_MAX);
    }
    public static CreateSubchannelPacket decode(FriendlyByteBuf b) {
        return new CreateSubchannelPacket(b.readUUID(), b.readUtf(ItemSubchannel.NAME_MAX));
    }
    public static void handle(CreateSubchannelPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            UUID newId = data.createSubchannel(p.channelId, player.getUUID(), p.name);
            if (newId != null) CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
