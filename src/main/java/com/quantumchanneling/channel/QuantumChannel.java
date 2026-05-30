package com.quantumchanneling.channel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A named quantum channel. Owns its identity, members, permissions, and charging mode. */
public class QuantumChannel {
    private static final String LEGACY_PREFIX = "Legacy ";

    private final UUID id;
    private String name;
    private final @Nullable UUID ownerId;
    private String ownerName;
    private boolean publicAccess = false;
    private ChargingMode chargingMode = ChargingMode.OFF;

    private final Set<GlobalPos> members = new HashSet<>();
    private final Map<UUID, Permission> playerPermissions = new HashMap<>();
    private final Map<UUID, String> playerNames = new HashMap<>(); // cached display names

    public QuantumChannel(UUID id, String name, @Nullable UUID ownerId, String ownerName) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public @Nullable UUID ownerId() { return ownerId; }
    public String ownerName() { return ownerName; }
    public boolean isPublic() { return publicAccess; }
    public ChargingMode chargingMode() { return chargingMode; }
    public Set<GlobalPos> members() { return Collections.unmodifiableSet(members); }
    public int memberCount() { return members.size(); }
    public Map<UUID, Permission> playerPermissions() { return Collections.unmodifiableMap(playerPermissions); }
    public Map<UUID, String> playerNames() { return Collections.unmodifiableMap(playerNames); }

    public boolean isOwnedBy(@Nullable UUID playerId) {
        return ownerId != null && ownerId.equals(playerId);
    }

    public Permission permissionFor(@Nullable UUID playerId) {
        if (playerId == null) return publicAccess ? Permission.USER : null;
        if (isOwnedBy(playerId)) return Permission.ADMIN;
        Permission p = playerPermissions.get(playerId);
        if (p != null) return p;
        return publicAccess ? Permission.USER : null;
    }

    public boolean canUse(@Nullable UUID playerId) {
        return permissionFor(playerId) != null;
    }

    public boolean canManage(@Nullable UUID playerId) {
        Permission p = permissionFor(playerId);
        return p != null && p.canManage();
    }

    public void rename(String newName) { this.name = newName; }
    public void setPublic(boolean publicAccess) { this.publicAccess = publicAccess; }
    public void setChargingMode(ChargingMode mode) { this.chargingMode = mode; }

    public void setPermission(UUID playerId, String playerName, Permission p) {
        playerPermissions.put(playerId, p);
        if (playerName != null && !playerName.isEmpty()) playerNames.put(playerId, playerName);
    }

    public void removePermission(UUID playerId) {
        playerPermissions.remove(playerId);
        playerNames.remove(playerId);
    }

    public void refreshOwnerName(String n) { if (n != null) this.ownerName = n; }

    boolean addMember(GlobalPos pos) { return members.add(pos); }
    boolean removeMember(GlobalPos pos) { return members.remove(pos); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        if (ownerId != null) tag.putUUID("OwnerId", ownerId);
        if (!ownerName.isEmpty()) tag.putString("OwnerName", ownerName);
        if (publicAccess) tag.putBoolean("Public", true);
        if (chargingMode != ChargingMode.OFF) tag.putInt("Charging", chargingMode.ordinal());

        ListTag mem = new ListTag();
        for (GlobalPos gp : members) {
            CompoundTag m = new CompoundTag();
            m.putString("Dim", gp.dimension().location().toString());
            m.putLong("Pos", gp.pos().asLong());
            mem.add(m);
        }
        tag.put("Members", mem);

        if (!playerPermissions.isEmpty()) {
            ListTag perms = new ListTag();
            for (var e : playerPermissions.entrySet()) {
                CompoundTag p = new CompoundTag();
                p.putUUID("Id", e.getKey());
                p.putString("Name", playerNames.getOrDefault(e.getKey(), ""));
                p.putString("Role", e.getValue().name());
                perms.add(p);
            }
            tag.put("Permissions", perms);
        }
        return tag;
    }

    public static QuantumChannel load(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        String name = tag.contains("Name") ? tag.getString("Name") : LEGACY_PREFIX + id.toString().substring(0, 8);
        UUID owner = tag.hasUUID("OwnerId") ? tag.getUUID("OwnerId") : null;
        String ownerName = tag.contains("OwnerName") ? tag.getString("OwnerName") : "";
        QuantumChannel net = new QuantumChannel(id, name, owner, ownerName);
        net.publicAccess = tag.getBoolean("Public");
        net.chargingMode = ChargingMode.byOrdinal(tag.getInt("Charging"));

        ListTag mem = tag.getList("Members", Tag.TAG_COMPOUND);
        for (int i = 0; i < mem.size(); i++) {
            CompoundTag m = mem.getCompound(i);
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(m.getString("Dim")));
            net.members.add(GlobalPos.of(dim, BlockPos.of(m.getLong("Pos"))));
        }

        if (tag.contains("Permissions", Tag.TAG_LIST)) {
            ListTag perms = tag.getList("Permissions", Tag.TAG_COMPOUND);
            for (int i = 0; i < perms.size(); i++) {
                CompoundTag p = perms.getCompound(i);
                UUID pid = p.getUUID("Id");
                String pname = p.getString("Name");
                Permission role = parseRole(p.getString("Role"));
                net.playerPermissions.put(pid, role);
                if (!pname.isEmpty()) net.playerNames.put(pid, pname);
            }
        }
        return net;
    }

    public static QuantumChannel fromLegacyChannel(UUID id, Set<GlobalPos> members) {
        QuantumChannel net = new QuantumChannel(id, LEGACY_PREFIX + id.toString().substring(0, 8), null, "");
        net.members.addAll(members);
        return net;
    }

    private static Permission parseRole(String s) {
        try { return Permission.valueOf(s); } catch (Exception e) { return Permission.USER; }
    }

    @Override public boolean equals(Object o) { return o instanceof QuantumChannel n && Objects.equals(n.id, id); }
    @Override public int hashCode() { return id.hashCode(); }
}
