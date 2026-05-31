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

    /** Channel-wide item knobs (batch size, mask version). Subchannels live per-emitter. Persisted. */
    private final ItemChannelConfig itemConfig = new ItemChannelConfig();
    /** Same shape, fluids. */
    private final FluidChannelConfig fluidConfig = new FluidChannelConfig();
    /** Same shape, gases (Mekanism). */
    private final GasChannelConfig gasConfig = new GasChannelConfig();
    /** Heat thermal-wire master-enable (Mekanism IHeatHandler). */
    private final HeatChannelConfig heatConfig = new HeatChannelConfig();

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

    public ItemChannelConfig itemConfig()   { return itemConfig; }
    public FluidChannelConfig fluidConfig() { return fluidConfig; }
    public GasChannelConfig gasConfig()     { return gasConfig; }
    public HeatChannelConfig heatConfig()   { return heatConfig; }

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
        tag.put("ItemConfig", itemConfig.save());
        tag.put("FluidConfig", fluidConfig.save());
        tag.put("GasConfig", gasConfig.save());
        tag.put("HeatConfig", heatConfig.save());

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
        if (tag.contains("FluidConfig", Tag.TAG_COMPOUND)) {
            net.fluidConfig.copyFrom(FluidChannelConfig.load(tag.getCompound("FluidConfig")));
        }
        if (tag.contains("GasConfig", Tag.TAG_COMPOUND)) {
            net.gasConfig.copyFrom(GasChannelConfig.load(tag.getCompound("GasConfig")));
        }
        if (tag.contains("HeatConfig", Tag.TAG_COMPOUND)) {
            net.heatConfig.copyFrom(HeatChannelConfig.load(tag.getCompound("HeatConfig")));
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
