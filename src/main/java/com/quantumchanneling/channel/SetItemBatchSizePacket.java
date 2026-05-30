package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: set the per-channel items-mode batch size (items per active tick). */
public record SetItemBatchSizePacket(UUID channelId, int batchSize) {
    public static void encode(SetItemBatchSizePacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeVarInt(p.batchSize);
    }
    public static SetItemBatchSizePacket decode(FriendlyByteBuf b) {
        return new SetItemBatchSizePacket(b.readUUID(), b.readVarInt());
    }
    public static void handle(SetItemBatchSizePacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.setItemBatchSize(p.channelId, player.getUUID(), p.batchSize)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
