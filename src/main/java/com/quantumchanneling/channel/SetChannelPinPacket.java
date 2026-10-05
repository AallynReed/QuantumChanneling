package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: admin sets/clears the join PIN for a channel. Empty string clears it. */
public record SetChannelPinPacket(UUID channelId, String pin) {
    public static void encode(SetChannelPinPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUtf(p.pin, 32);
    }
    public static SetChannelPinPacket decode(FriendlyByteBuf b) {
        return new SetChannelPinPacket(b.readUUID(), b.readUtf(32));
    }
    public static void handle(SetChannelPinPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            var server = player.serverLevel().getServer();
            ChannelData data = ChannelData.get(server);
            // Clearing the PIN drops the channel off PIN-only viewers' screens — capture them first
            // so they get an updated (channel-less) list even though broadcastListTo won't reach them.
            java.util.List<ServerPlayer> before = server.getPlayerList().getPlayers().stream()
                    .filter(pl -> data.visibleTo(pl).stream().anyMatch(n -> n.id().equals(p.channelId)))
                    .toList();
            if (data.setPin(p.channelId, player.getUUID(), p.pin)) {
                CreateChannelPacket.broadcastListTo(server, p.channelId);
                for (ServerPlayer pl : before) CreateChannelPacket.sendListBackTo(pl);
            }
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
