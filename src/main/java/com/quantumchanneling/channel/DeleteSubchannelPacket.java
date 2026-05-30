package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: delete a subchannel. Existing subscriptions to it become dangling no-ops. */
public record DeleteSubchannelPacket(UUID channelId, UUID subId) {
    public static void encode(DeleteSubchannelPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUUID(p.subId);
    }
    public static DeleteSubchannelPacket decode(FriendlyByteBuf b) {
        return new DeleteSubchannelPacket(b.readUUID(), b.readUUID());
    }
    public static void handle(DeleteSubchannelPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.deleteSubchannel(p.channelId, player.getUUID(), p.subId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
