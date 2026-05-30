package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client-bound projection of a {@link QuantumChannel}. */
public record ChannelInfo(
        UUID id,
        String name,
        @Nullable UUID ownerId,
        String ownerName,
        int memberCount,
        boolean canManage,
        boolean isPublic,
        int chargingModeOrdinal,
        boolean subscribed,
        List<PlayerEntry> players
) {

    public record PlayerEntry(UUID id, String name, Permission permission) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name);
            buf.writeVarInt(permission.ordinal());
        }
        public static PlayerEntry read(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            String name = buf.readUtf();
            int o = buf.readVarInt();
            Permission p = (o >= 0 && o < Permission.values().length) ? Permission.values()[o] : Permission.USER;
            return new PlayerEntry(id, name, p);
        }
    }

    public ChargingMode charging() { return ChargingMode.byOrdinal(chargingModeOrdinal); }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name);
        buf.writeBoolean(ownerId != null);
        if (ownerId != null) buf.writeUUID(ownerId);
        buf.writeUtf(ownerName);
        buf.writeVarInt(memberCount);
        buf.writeBoolean(canManage);
        buf.writeBoolean(isPublic);
        buf.writeVarInt(chargingModeOrdinal);
        buf.writeBoolean(subscribed);
        buf.writeVarInt(players.size());
        for (PlayerEntry e : players) e.write(buf);
    }

    public static ChannelInfo read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf();
        UUID owner = buf.readBoolean() ? buf.readUUID() : null;
        String ownerName = buf.readUtf();
        int members = buf.readVarInt();
        boolean canManage = buf.readBoolean();
        boolean isPublic = buf.readBoolean();
        int charging = buf.readVarInt();
        boolean subscribed = buf.readBoolean();
        int pn = buf.readVarInt();
        List<PlayerEntry> ps = new ArrayList<>(pn);
        for (int i = 0; i < pn; i++) ps.add(PlayerEntry.read(buf));
        return new ChannelInfo(id, name, owner, ownerName, members, canManage, isPublic, charging, subscribed, ps);
    }

    public static ChannelInfo from(QuantumChannel net, @Nullable UUID viewerId, boolean subscribed) {
        List<PlayerEntry> ps = new ArrayList<>();
        net.playerPermissions().forEach((pid, perm) ->
                ps.add(new PlayerEntry(pid, net.playerNames().getOrDefault(pid, ""), perm)));
        return new ChannelInfo(
                net.id(), net.name(), net.ownerId(), net.ownerName(),
                net.memberCount(), net.canManage(viewerId),
                net.isPublic(), net.chargingMode().ordinal(), subscribed, ps);
    }
}
