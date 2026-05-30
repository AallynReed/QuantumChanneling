package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: rename a subchannel. */
public record RenameSubchannelPacket(UUID channelId, UUID subId, String name) {
    public static void encode(RenameSubchannelPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUUID(p.subId);
        b.writeUtf(p.name, ItemSubchannel.NAME_MAX);
    }
    public static RenameSubchannelPacket decode(FriendlyByteBuf b) {
        return new RenameSubchannelPacket(b.readUUID(), b.readUUID(), b.readUtf(ItemSubchannel.NAME_MAX));
    }
    public static void handle(RenameSubchannelPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.renameSubchannel(p.channelId, player.getUUID(), p.subId, p.name)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
