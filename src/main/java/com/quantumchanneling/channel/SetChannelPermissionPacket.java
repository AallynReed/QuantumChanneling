package com.quantumchanneling.channel;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Add or update a player's permission. Empty role string means "remove". */
public record SetChannelPermissionPacket(UUID channelId, String targetPlayerName, String role) {

    public static void encode(SetChannelPermissionPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUtf(p.targetPlayerName, 32);
        b.writeUtf(p.role, 16);
    }

    public static SetChannelPermissionPacket decode(FriendlyByteBuf b) {
        return new SetChannelPermissionPacket(b.readUUID(), b.readUtf(32), b.readUtf(16));
    }

    public static void handle(SetChannelPermissionPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            MinecraftServer server = player.serverLevel().getServer();
            ChannelData data = ChannelData.get(server);
            String name = p.targetPlayerName.trim();
            if (name.isEmpty()) return;
            Optional<GameProfile> profile = server.getProfileCache() == null
                    ? Optional.empty() : server.getProfileCache().get(name);
            if (profile.isEmpty()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.quantumchanneling.permission.unknown_player", name));
                return;
            }
            UUID targetId = profile.get().getId();
            // Snapshot the players who could see the channel before the change: a demotion/removal
            // must reach a target who will no longer see it after and needs it dropped from their UI.
            java.util.List<ServerPlayer> before = server.getPlayerList().getPlayers().stream()
                    .filter(pl -> data.visibleTo(pl).stream().anyMatch(n -> n.id().equals(p.channelId)))
                    .toList();
            boolean changed;
            if (p.role.isEmpty()) {
                changed = data.removePermission(p.channelId, player.getUUID(), targetId);
            } else {
                Permission role;
                try { role = Permission.valueOf(p.role); } catch (Exception e) { role = Permission.USER; }
                changed = data.setPermission(p.channelId, player.getUUID(), targetId, name, role);
            }
            if (changed) {
                CreateChannelPacket.broadcastListTo(server, p.channelId);
                for (ServerPlayer pl : before) CreateChannelPacket.sendListBackTo(pl);
            }
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
