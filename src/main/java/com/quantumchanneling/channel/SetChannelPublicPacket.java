package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record SetChannelPublicPacket(UUID id, boolean publicAccess) {
    public static void encode(SetChannelPublicPacket p, FriendlyByteBuf b) { b.writeUUID(p.id); b.writeBoolean(p.publicAccess); }
    public static SetChannelPublicPacket decode(FriendlyByteBuf b) { return new SetChannelPublicPacket(b.readUUID(), b.readBoolean()); }
    public static void handle(SetChannelPublicPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            var server = player.serverLevel().getServer();
            ChannelData data = ChannelData.get(server);
            // Going private drops the channel off ex-public-viewers' screens — capture them first so
            // they get an updated (channel-less) list even though broadcastListTo won't reach them.
            java.util.List<ServerPlayer> before = server.getPlayerList().getPlayers().stream()
                    .filter(pl -> data.visibleTo(pl).stream().anyMatch(n -> n.id().equals(p.id)))
                    .toList();
            if (data.setPublic(p.id, player.getUUID(), p.publicAccess)) {
                CreateChannelPacket.broadcastListTo(server, p.id);
                for (ServerPlayer pl : before) CreateChannelPacket.sendListBackTo(pl);
            }
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
