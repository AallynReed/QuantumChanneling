package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: owner transfers the channel to {@code targetPlayerId}. */
public record TransferChannelOwnerPacket(UUID channelId, UUID targetPlayerId, String targetName) {
    public static void encode(TransferChannelOwnerPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUUID(p.targetPlayerId);
        b.writeUtf(p.targetName, 32);
    }
    public static TransferChannelOwnerPacket decode(FriendlyByteBuf b) {
        return new TransferChannelOwnerPacket(b.readUUID(), b.readUUID(), b.readUtf(32));
    }
    public static void handle(TransferChannelOwnerPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.transferOwnership(p.channelId, player.getUUID(), p.targetPlayerId, p.targetName)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
