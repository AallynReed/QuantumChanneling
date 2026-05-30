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
    /** Mutable so ownership can be transferred. */
    private @Nullable UUID ownerId;
    private String ownerName;
    private boolean publicAccess = false;
    private int chargingSlots = ChargingSlots.OFF;
    /** ARGB int (alpha forced to 0xFF on display). Defaults to the panel accent. */
    private int color = 0xFF50DCF0;
    /** Optional join PIN. Empty = disabled. Anyone who supplies the matching PIN gets USER access. */
    private String pin = "";
    /** Per-slot priority for charging dispatch. Higher = served first. Default 0. */
    private int slotPriorityHand = 0;
    private int slotPriorityHotbar = 0;
    private int slotPriorityInventory = 0;
    private int slotPriorityArmor = 0;
    /** Per-armor-piece priorities. Index 0 = head, 1 = chest, 2 = legs, 3 = feet. */
    private final int[] armorPiecePriorities = new int[4];
    /** Per-bauble priority for Curios slots (when the Curios mod is loaded). */
    private int slotPriorityCurios = 0;

    private final Set<GlobalPos> members = new HashSet<>();
    private final Map<UUID, Permission> playerPermissions = new HashMap<>();
    private final Map<UUID, String> playerNames = new HashMap<>(); // cached display names
    /** Players an admin has banned from wireless charging on this channel. Persisted. */
    private final Set<UUID> chargingBlocked = new HashSet<>();

    /** Item-transfer settings: enabled, batch size, dynamic subchannels. Persisted. */
    private final ItemChannelConfig itemConfig = new ItemChannelConfig();

    /**
     * Reverse index: subchannel UUID → set of emitter positions that subscribe to it. A subchannel
     * with an empty entry here is auto-deleted from {@link #itemConfig} (per design: subchannels
     * only exist while at least one emitter routes through them). Persisted so the invariant
     * survives chunk unloads / world restarts.
     */
    private final Map<UUID, Set<GlobalPos>> subchannelEmitters = new HashMap<>();

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
    public int chargingSlots() { return chargingSlots; }
    public int color() { return color; }
    public void setColor(int c) { this.color = (c | 0xFF000000); }
    public String pin() { return pin; }
    public boolean hasPin() { return !pin.isEmpty(); }
    public void setPin(String p) { this.pin = p == null ? "" : p.trim(); }
    public boolean pinMatches(String input) {
        return hasPin() && pin.equals(input == null ? "" : input.trim());
    }
    public int slotPriority(int slotBit) {
        if (slotBit == ChargingSlots.HAND)      return slotPriorityHand;
        if (slotBit == ChargingSlots.HOTBAR)    return slotPriorityHotbar;
        if (slotBit == ChargingSlots.INVENTORY) return slotPriorityInventory;
        if (slotBit == ChargingSlots.ARMOR)     return slotPriorityArmor;
        if (slotBit == ChargingSlots.CURIOS)    return slotPriorityCurios;
        return 0;
    }
    public void setSlotPriority(int slotBit, int priority) {
        if (slotBit == ChargingSlots.HAND)           slotPriorityHand = priority;
        else if (slotBit == ChargingSlots.HOTBAR)    slotPriorityHotbar = priority;
        else if (slotBit == ChargingSlots.INVENTORY) slotPriorityInventory = priority;
        else if (slotBit == ChargingSlots.ARMOR)     slotPriorityArmor = priority;
        else if (slotBit == ChargingSlots.CURIOS)    slotPriorityCurios = priority;
    }

    /** Sub-priority for one armor piece. 0 = head, 1 = chest, 2 = legs, 3 = feet. */
    public int armorPiecePriority(int armorIdx) {
        if (armorIdx < 0 || armorIdx > 3) return 0;
        return armorPiecePriorities[armorIdx];
    }
    public void setArmorPiecePriority(int armorIdx, int priority) {
        if (armorIdx >= 0 && armorIdx < 4) armorPiecePriorities[armorIdx] = priority;
    }
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
    public void setChargingSlots(int mask) { this.chargingSlots = mask & ChargingSlots.ALL_MASK; }

    public void setPermission(UUID playerId, String playerName, Permission p) {
        playerPermissions.put(playerId, p);
        if (playerName != null && !playerName.isEmpty()) playerNames.put(playerId, playerName);
    }

    public void removePermission(UUID playerId) {
        playerPermissions.remove(playerId);
        playerNames.remove(playerId);
        chargingBlocked.remove(playerId);
    }

    public Set<UUID> chargingBlocked() { return Collections.unmodifiableSet(chargingBlocked); }
    public boolean isChargingBlocked(UUID playerId) { return chargingBlocked.contains(playerId); }
    public void setChargingBlocked(UUID playerId, boolean blocked) {
        if (blocked) chargingBlocked.add(playerId);
        else chargingBlocked.remove(playerId);
    }

    /* ---- items ---- */

    public ItemChannelConfig itemConfig() { return itemConfig; }

    /**
     * Records that {@code emitterPos} subscribes to {@code subId}. Called from the subscribe
     * packet handler when the device is an emitter. No-op if the subchannel doesn't exist.
     */
    public void registerEmitterSubscription(GlobalPos emitterPos, UUID subId) {
        if (subId == null || emitterPos == null) return;
        if (!itemConfig.contains(subId)) return;
        subchannelEmitters.computeIfAbsent(subId, k -> new HashSet<>()).add(emitterPos);
    }

    /**
     * Removes {@code emitterPos} from {@code subId}'s subscriber set. If that empties the set,
     * the subchannel is auto-deleted (orphan cleanup).
     */
    public void unregisterEmitterSubscription(GlobalPos emitterPos, UUID subId) {
        if (subId == null || emitterPos == null) return;
        Set<GlobalPos> set = subchannelEmitters.get(subId);
        if (set == null) return;
        if (!set.remove(emitterPos)) return;
        if (set.isEmpty()) {
            subchannelEmitters.remove(subId);
            itemConfig.deleteSubchannel(subId);
        }
    }

    /** Sweeps {@code emitterPos} out of every reverse-index set; auto-deletes any now-orphan subchannels. */
    public void unregisterAllForEmitter(GlobalPos emitterPos) {
        if (emitterPos == null) return;
        var it = subchannelEmitters.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue().remove(emitterPos) && entry.getValue().isEmpty()) {
                UUID sub = entry.getKey();
                it.remove();
                itemConfig.deleteSubchannel(sub);
            }
        }
    }

    /** Number of distinct emitter positions registered against {@code subId}. */
    public int countEmitterSubscribers(UUID subId) {
        Set<GlobalPos> set = subchannelEmitters.get(subId);
        return set == null ? 0 : set.size();
    }

    public void refreshOwnerName(String n) { if (n != null) this.ownerName = n; }

    /**
     * Hand the channel over to a new player. The previous owner is demoted to ADMIN so they keep
     * access — without that step, transferring would lock them out.
     */
    public void transferOwnership(UUID newOwnerId, String newOwnerName) {
        if (newOwnerId == null || newOwnerId.equals(this.ownerId)) return;
        if (this.ownerId != null) {
            playerPermissions.put(this.ownerId, Permission.ADMIN);
            if (this.ownerName != null && !this.ownerName.isEmpty()) {
                playerNames.put(this.ownerId, this.ownerName);
            }
        }
        this.ownerId = newOwnerId;
        this.ownerName = newOwnerName == null ? "" : newOwnerName;
        // The new owner is implicitly ADMIN — no need to keep a row in the permissions map.
        playerPermissions.remove(newOwnerId);
        playerNames.remove(newOwnerId);
    }

    boolean addMember(GlobalPos pos) { return members.add(pos); }
    boolean removeMember(GlobalPos pos) { return members.remove(pos); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        if (ownerId != null) tag.putUUID("OwnerId", ownerId);
        if (!ownerName.isEmpty()) tag.putString("OwnerName", ownerName);
        if (publicAccess) tag.putBoolean("Public", true);
        if (chargingSlots != ChargingSlots.OFF) tag.putInt("ChargingSlots", chargingSlots);
        tag.putInt("Color", color);
        if (!pin.isEmpty()) tag.putString("Pin", pin);
        if (slotPriorityHand      != 0) tag.putInt("SlotPrHand", slotPriorityHand);
        if (slotPriorityHotbar    != 0) tag.putInt("SlotPrHotbar", slotPriorityHotbar);
        if (slotPriorityInventory != 0) tag.putInt("SlotPrInv", slotPriorityInventory);
        if (slotPriorityArmor     != 0) tag.putInt("SlotPrArmor", slotPriorityArmor);
        if (slotPriorityCurios    != 0) tag.putInt("SlotPrCurios", slotPriorityCurios);
        for (int i = 0; i < 4; i++) {
            if (armorPiecePriorities[i] != 0) tag.putInt("SlotPrArmorPiece" + i, armorPiecePriorities[i]);
        }
        if (!chargingBlocked.isEmpty()) {
            ListTag blocked = new ListTag();
            for (UUID pid : chargingBlocked) {
                CompoundTag e = new CompoundTag();
                e.putUUID("Id", pid);
                blocked.add(e);
            }
            tag.put("ChargingBlocked", blocked);
        }
        // Items config (always saved so default-blacklist/empty-void is recoverable).
        tag.put("ItemConfig", itemConfig.save());
        if (!subchannelEmitters.isEmpty()) {
            ListTag idx = new ListTag();
            for (var e : subchannelEmitters.entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Sub", e.getKey());
                ListTag positions = new ListTag();
                for (GlobalPos gp : e.getValue()) {
                    CompoundTag g = new CompoundTag();
                    g.putString("Dim", gp.dimension().location().toString());
                    g.putLong("Pos", gp.pos().asLong());
                    positions.add(g);
                }
                entry.put("Emitters", positions);
                idx.add(entry);
            }
            tag.put("SubchannelEmitters", idx);
        }

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
        net.chargingSlots = tag.getInt("ChargingSlots") & ChargingSlots.ALL_MASK;
        if (tag.contains("Color")) net.color = tag.getInt("Color") | 0xFF000000;
        if (tag.contains("Pin")) net.pin = tag.getString("Pin");
        net.slotPriorityHand      = tag.getInt("SlotPrHand");
        net.slotPriorityHotbar    = tag.getInt("SlotPrHotbar");
        net.slotPriorityInventory = tag.getInt("SlotPrInv");
        net.slotPriorityArmor     = tag.getInt("SlotPrArmor");
        net.slotPriorityCurios    = tag.getInt("SlotPrCurios");
        for (int i = 0; i < 4; i++) {
            net.armorPiecePriorities[i] = tag.getInt("SlotPrArmorPiece" + i);
        }
        if (tag.contains("ChargingBlocked", Tag.TAG_LIST)) {
            ListTag blocked = tag.getList("ChargingBlocked", Tag.TAG_COMPOUND);
            for (int i = 0; i < blocked.size(); i++) {
                net.chargingBlocked.add(blocked.getCompound(i).getUUID("Id"));
            }
        }
        if (tag.contains("ItemConfig", Tag.TAG_COMPOUND)) {
            net.itemConfig.copyFrom(ItemChannelConfig.load(tag.getCompound("ItemConfig")));
        }
        if (tag.contains("SubchannelEmitters", Tag.TAG_LIST)) {
            ListTag idx = tag.getList("SubchannelEmitters", Tag.TAG_COMPOUND);
            for (int i = 0; i < idx.size(); i++) {
                CompoundTag entry = idx.getCompound(i);
                if (!entry.hasUUID("Sub")) continue;
                UUID sub = entry.getUUID("Sub");
                ListTag positions = entry.getList("Emitters", Tag.TAG_COMPOUND);
                Set<GlobalPos> set = new HashSet<>();
                for (int j = 0; j < positions.size(); j++) {
                    CompoundTag g = positions.getCompound(j);
                    ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
                            new ResourceLocation(g.getString("Dim")));
                    set.add(GlobalPos.of(dim, BlockPos.of(g.getLong("Pos"))));
                }
                if (!set.isEmpty()) net.subchannelEmitters.put(sub, set);
            }
        }

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
