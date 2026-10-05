package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

/** Add or update a player's permission. Empty role string means "remove". */
public record SetChannelPermissionPacket(UUID channelId, String targetPlayerName, String role) implements CustomPacketPayload {
    public static final Type<SetChannelPermissionPacket> TYPE = new Type<>(QuantumChanneling.id("set_channel_permission"));
    public static final StreamCodec<FriendlyByteBuf, SetChannelPermissionPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetChannelPermissionPacket::encode, SetChannelPermissionPacket::decode);

    @Override
    public Type<SetChannelPermissionPacket> type() { return TYPE; }


    public static void encode(SetChannelPermissionPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUtf(p.targetPlayerName, 32);
        b.writeUtf(p.role, 16);
    }

    public static SetChannelPermissionPacket decode(FriendlyByteBuf b) {
        return new SetChannelPermissionPacket(b.readUUID(), b.readUtf(32), b.readUtf(16));
    }

    public static void handle(SetChannelPermissionPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.level().getServer();
        ChannelData data = ChannelData.get(server);
        String name = p.targetPlayerName.trim();
        if (name.isEmpty()) return;
        Optional<NameAndId> profile = server.services().nameToIdCache().get(name);
        if (profile.isEmpty()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.quantumchanneling.permission.unknown_player", name));
            return;
        }
        UUID targetId = profile.get().id();
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
    }
}
