package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: admin blocks or unblocks a player's wireless charging on a channel. */
public record SetChannelChargeBlockedPacket(UUID channelId, UUID targetPlayerId, boolean blocked) {
    public static void encode(SetChannelChargeBlockedPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUUID(p.targetPlayerId);
        b.writeBoolean(p.blocked);
    }
    public static SetChannelChargeBlockedPacket decode(FriendlyByteBuf b) {
        return new SetChannelChargeBlockedPacket(b.readUUID(), b.readUUID(), b.readBoolean());
    }
    public static void handle(SetChannelChargeBlockedPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.setChargingBlocked(p.channelId, player.getUUID(), p.targetPlayerId, p.blocked)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
