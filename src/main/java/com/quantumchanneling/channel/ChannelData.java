package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.PhotonManagerBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-save registry of every {@link QuantumChannel}. */
public class ChannelData extends SavedData {
    public static final String NAME = "quantumchanneling_network";

    private final Map<UUID, QuantumChannel> networks = new HashMap<>();
    private final Map<GlobalPos, UUID> memberToNetwork = new HashMap<>();
    /** Player UUID → channel UUID the player has subscribed to for charging. Persisted. */
    private final Map<UUID, UUID> chargingSubscriptions = new HashMap<>();
    /** Per-channel FE dispensed via wireless charging during the previous server tick. */
    private final Map<UUID, Integer> lastTickChargeRate = new HashMap<>();
    /** Per-channel FE accumulator for the current server tick. */
    private final Map<UUID, Integer> currentTickChargeAccumulator = new HashMap<>();
    /**
     * Per-channel per-player per-slot FE delivered during the previous server tick.
     * channelId → playerId → slotKey → FE this tick. Drives the "Charge Activity" tree.
     */
    private final Map<UUID, Map<UUID, Map<String, Integer>>> lastTickPlayerSlotBreakdown = new HashMap<>();
    /** Working accumulator that becomes {@link #lastTickPlayerSlotBreakdown} at the end of each tick. */
    private final Map<UUID, Map<UUID, Map<String, Integer>>> currentTickPlayerSlotBreakdown = new HashMap<>();

    public static ChannelData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ChannelData::load, ChannelData::new, NAME);
    }

    /* ---- channel lifecycle ---- */

    public QuantumChannel createChannel(ServerPlayer owner, String name) {
        UUID id = UUID.randomUUID();
        QuantumChannel net = new QuantumChannel(id, name, owner.getUUID(), owner.getGameProfile().getName());
        networks.put(id, net);
        setDirty();
        return net;
    }

    public boolean deleteChannel(UUID id, @Nullable UUID actor) {
        QuantumChannel net = networks.get(id);
        if (net == null || !net.canManage(actor)) return false;
        for (GlobalPos m : net.members()) memberToNetwork.remove(m);
        chargingSubscriptions.entrySet().removeIf(e -> e.getValue().equals(id));
        networks.remove(id);
        setDirty();
        return true;
    }

    public boolean renameChannel(UUID id, @Nullable UUID actor, String newName) {
        QuantumChannel net = networks.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.rename(newName);
        setDirty();
        return true;
    }

    public boolean setPublic(UUID id, @Nullable UUID actor, boolean value) {
        QuantumChannel net = networks.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.setPublic(value);
        setDirty();
        return true;
    }

    public boolean setChargingSlots(UUID id, @Nullable UUID actor, int slotMask) {
        QuantumChannel net = networks.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.setChargingSlots(slotMask);
        setDirty();
        return true;
    }

    public boolean setColor(UUID id, @Nullable UUID actor, int color) {
        QuantumChannel net = networks.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.setColor(color);
        setDirty();
        return true;
    }

    public boolean setSlotPriority(UUID id, @Nullable UUID actor, int slotBit, int newPriority) {
        QuantumChannel net = networks.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.setSlotPriority(slotBit, newPriority);
        setDirty();
        return true;
    }

    public boolean setArmorPiecePriority(UUID id, @Nullable UUID actor, int armorIdx, int newPriority) {
        QuantumChannel net = networks.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.setArmorPiecePriority(armorIdx, newPriority);
        setDirty();
        return true;
    }

    public boolean setPin(UUID id, @Nullable UUID actor, String newPin) {
        QuantumChannel net = networks.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.setPin(newPin);
        setDirty();
        return true;
    }

    /** Returns true when the PIN matched and {@code player} was granted USER access. */
    public boolean joinByPin(ServerPlayer player, UUID channelId, String pin) {
        QuantumChannel net = networks.get(channelId);
        if (net == null) return false;
        if (net.canUse(player.getUUID())) return true; // already in
        if (!net.pinMatches(pin)) return false;
        net.setPermission(player.getUUID(), player.getGameProfile().getName(), Permission.USER);
        setDirty();
        return true;
    }

    public boolean setPermission(UUID id, @Nullable UUID actor, UUID targetPlayerId, String targetName, Permission p) {
        QuantumChannel net = networks.get(id);
        if (net == null || !net.canManage(actor)) return false;
        if (net.isOwnedBy(targetPlayerId)) return false; // can't demote the owner
        net.setPermission(targetPlayerId, targetName, p);
        setDirty();
        return true;
    }

    /**
     * Sets the charging-blocked flag for {@code targetPlayerId}. Authority rules:
     * <ul>
     *   <li>The <b>owner</b> may target themselves or any other member.</li>
     *   <li>An <b>admin</b> may target themselves or USER-level members. Admins cannot target
     *       another admin, and cannot target the owner.</li>
     *   <li>Users cannot reach this at all (gated by {@link QuantumChannel#canManage}).</li>
     * </ul>
     */
    public boolean setChargingBlocked(UUID id, @Nullable UUID actor, UUID targetPlayerId, boolean blocked) {
        QuantumChannel net = networks.get(id);
        if (net == null || !net.canManage(actor)) return false;
        if (targetPlayerId == null || actor == null) return false;

        boolean targetIsOwner = net.isOwnedBy(targetPlayerId);
        boolean actorIsOwner = net.isOwnedBy(actor);
        boolean selfTarget = targetPlayerId.equals(actor);

        if (targetIsOwner) {
            // Only the owner can flip their own block flag. Admins cannot touch the owner.
            if (!actorIsOwner) return false;
        } else if (!actorIsOwner) {
            // Actor is an admin. Permitted targets: self, or another USER-level player.
            // Admin↔admin is disallowed — only the owner can block a peer admin.
            Permission targetPerm = net.playerPermissions().get(targetPlayerId);
            if (!selfTarget && targetPerm == Permission.ADMIN) return false;
        }

        net.setChargingBlocked(targetPlayerId, blocked);
        setDirty();
        return true;
    }

    /* ---- channel-wide batch knobs ---- */

    public boolean setItemBatchSize(UUID channelId, @Nullable UUID actor, int batchSize) {
        QuantumChannel net = networks.get(channelId);
        if (net == null || !net.canManage(actor)) return false;
        net.itemConfig().setBatchSize(batchSize);
        setDirty();
        return true;
    }

    public boolean setHeatEnabled(UUID channelId, @Nullable UUID actor, boolean enabled) {
        QuantumChannel net = networks.get(channelId);
        if (net == null || !net.canManage(actor)) return false;
        net.heatConfig().setEnabled(enabled);
        setDirty();
        return true;
    }

    /* Subchannel CRUD lives on the emitter BE — see PhotonEmitterBlockEntity. The mutation
       packets target the emitter's BlockPos directly and bypass this class. */

    /**
     * Only the current owner can transfer. The previous owner is demoted to ADMIN so they keep
     * channel access (handled by {@link QuantumChannel#transferOwnership}).
     */
    public boolean transferOwnership(UUID id, @Nullable UUID actor, UUID targetPlayerId, String targetName) {
        QuantumChannel net = networks.get(id);
        if (net == null) return false;
        if (!net.isOwnedBy(actor)) return false;
        if (targetPlayerId == null || targetPlayerId.equals(actor)) return false;
        net.transferOwnership(targetPlayerId, targetName);
        setDirty();
        return true;
    }

    public boolean removePermission(UUID id, @Nullable UUID actor, UUID targetPlayerId) {
        QuantumChannel net = networks.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.removePermission(targetPlayerId);
        chargingSubscriptions.remove(targetPlayerId); // they can no longer charge from here
        setDirty();
        return true;
    }

    public @Nullable QuantumChannel getChannel(UUID id) { return networks.get(id); }

    /** All channels the player can see: owned, allowed via permission, public, or PIN-gated. */
    public List<QuantumChannel> visibleTo(ServerPlayer player) {
        UUID pid = player.getUUID();
        List<QuantumChannel> out = new ArrayList<>();
        for (QuantumChannel net : networks.values()) {
            if (net.ownerId() == null || net.canUse(pid) || net.hasPin()) out.add(net);
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    /* ---- membership ---- */

    public void addMember(UUID networkId, GlobalPos pos) {
        QuantumChannel net = networks.get(networkId);
        if (net == null) return;
        UUID existing = memberToNetwork.get(pos);
        if (networkId.equals(existing)) return;
        if (existing != null) removeMember(existing, pos);
        if (net.addMember(pos)) {
            memberToNetwork.put(pos, networkId);
            setDirty();
        }
    }

    public void removeMember(UUID networkId, GlobalPos pos) {
        QuantumChannel net = networks.get(networkId);
        if (net == null) return;
        if (net.removeMember(pos)) {
            memberToNetwork.remove(pos);
            setDirty();
        }
    }

    public Set<GlobalPos> getMembers(UUID networkId) {
        QuantumChannel net = networks.get(networkId);
        return net == null ? Collections.emptySet() : net.members();
    }

    public @Nullable UUID getChannelOf(GlobalPos pos) { return memberToNetwork.get(pos); }

    /* ---- charging subscriptions ---- */

    public void setChargingSubscription(UUID playerId, @Nullable UUID channelId) {
        if (channelId == null) chargingSubscriptions.remove(playerId);
        else chargingSubscriptions.put(playerId, channelId);
        setDirty();
    }

    public @Nullable UUID getChargingSubscription(UUID playerId) {
        return chargingSubscriptions.get(playerId);
    }

    /**
     * Each server tick: for every online player who has subscribed to a channel, pull FE from any
     * loaded emitter on that channel and push it into the items in the slots that the channel's
     * {@code chargingSlots} bitmask enables (HAND / HOTBAR / INVENTORY / ARMOR).
     */
    /** FE dispensed via wireless charging during the previous tick for {@code channelId}. */
    public int getLastTickChargeRate(@Nullable UUID channelId) {
        if (channelId == null) return 0;
        return lastTickChargeRate.getOrDefault(channelId, 0);
    }

    /**
     * Per-player per-slot FE delivered during the previous tick for {@code channelId}.
     * Inner map keys are the {@code ChargingSlots.SLOT_*} translation keys. Returns an
     * empty map when nobody is currently being charged on this channel.
     */
    public Map<UUID, Map<String, Integer>> getLastTickPlayerSlotBreakdown(@Nullable UUID channelId) {
        if (channelId == null) return Collections.emptyMap();
        return lastTickPlayerSlotBreakdown.getOrDefault(channelId, Collections.emptyMap());
    }

    public void tickCharging(MinecraftServer server) {
        // Snapshot the previous tick's accumulators into the public "last tick" maps, then zero them.
        lastTickChargeRate.clear();
        lastTickChargeRate.putAll(currentTickChargeAccumulator);
        currentTickChargeAccumulator.clear();
        lastTickPlayerSlotBreakdown.clear();
        lastTickPlayerSlotBreakdown.putAll(currentTickPlayerSlotBreakdown);
        currentTickPlayerSlotBreakdown.clear();

        // Server-side master switch. The display still zeroes out via the snapshot above so the
        // last-tick rate reads as 0 while disabled.
        if (!com.quantumchanneling.ServerConfig.wirelessEnabled) return;

        // Nobody subscribed → nothing to dispatch. Skips the rest of the per-tick allocations.
        if (chargingSubscriptions.isEmpty()) return;

        // Group subscribed, online players by channel so we can split each channel's source pool
        // fairly across all online subscribers. Offline subscribers are naturally skipped (they
        // aren't in the player list); when one player is alone on the channel they get 100%.
        Map<UUID, List<ServerPlayer>> playersByChannel = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID channelId = chargingSubscriptions.get(player.getUUID());
            if (channelId == null) continue;
            QuantumChannel net = networks.get(channelId);
            if (net == null) continue;
            if (!ChargingSlots.any(net.chargingSlots())) continue;
            if (!net.canUse(player.getUUID())) continue;
            if (net.isChargingBlocked(player.getUUID())) continue;  // admin-blocked
            if (!hasLoadedManager(server, net)) continue;
            playersByChannel.computeIfAbsent(channelId, k -> new ArrayList<>()).add(player);
        }

        for (var entry : playersByChannel.entrySet()) {
            QuantumChannel net = networks.get(entry.getKey());
            if (net == null) continue;
            dispatchChannelCharging(server, net, entry.getValue());
        }
    }

    /**
     * Tier-equal-share charging for one channel.
     *
     * <p>The high-level flow is:
     * <ol>
     *   <li>Collect every chargeable {@link SlotCap} across every online subscriber. Each cap
     *       carries a two-level priority tuple (main group priority, sub priority — used for
     *       armor pieces and 0 for all other groups) plus its translation-key slot label.</li>
     *   <li>Sum demand across all caps to get the channel-wide total demand.</li>
     *   <li>Pull min(totalDemand, available-from-sources) from emitters/storages — ONCE.</li>
     *   <li>Walk caps in descending priority-tuple order. Inside each tier, split the
     *       remaining budget equally across the caps in that tier (with leftover redistribution
     *       so 99 FE split across 2 caps comes out 50 / 49 instead of 49 / 49 + lost FE).</li>
     * </ol>
     *
     * <p>This naturally satisfies "across same level of priority always share evenly":
     * within a tier nothing about which player owns a cap matters. A player with 4 armor
     * pieces all at the same tier gets 25% per piece; if another subscriber has a chestplate
     * at the same tier, every cap (across both players) shares equally.
     */
    private void dispatchChannelCharging(MinecraftServer server, QuantumChannel net, List<ServerPlayer> subscribers) {
        // Step 1: collect every cap across every subscriber.
        List<SlotCap> allCaps = new ArrayList<>();
        long totalDemand = 0;
        for (ServerPlayer player : subscribers) {
            for (SlotIterEntry entry : iterSlots(player, net)) {
                ItemStack stack = entry.stack;
                if (stack.isEmpty()) continue;
                IEnergyStorage cap = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
                if (cap == null || !cap.canReceive()) continue;
                int room = cap.getMaxEnergyStored() - cap.getEnergyStored();
                if (room <= 0) continue;
                int accept = cap.receiveEnergy(room, true);
                if (accept <= 0) continue;
                allCaps.add(new SlotCap(player, cap, accept, entry.priorityMain, entry.prioritySub, entry.slotKey));
                totalDemand += accept;
                if (totalDemand < 0) { totalDemand = Integer.MAX_VALUE; break; }
            }
        }
        if (allCaps.isEmpty() || totalDemand <= 0) return;
        int want = (int) Math.min(totalDemand, (long) Integer.MAX_VALUE);

        // Step 2: pull once from sources.
        int pulled = pullFromSources(server, net, want);
        if (pulled <= 0) return;

        // Step 3: sort caps by priority descending (main first, then sub).
        allCaps.sort((a, b) -> {
            int c = Integer.compare(b.priorityMain, a.priorityMain);
            if (c != 0) return c;
            return Integer.compare(b.prioritySub, a.prioritySub);
        });

        // Step 4: walk by tier, equal-sharing each tier's slice of the remaining budget.
        Map<UUID, Map<String, Integer>> playerSlotBreakdown = new HashMap<>();
        int remaining = pulled;
        int idx = 0;
        while (idx < allCaps.size() && remaining > 0) {
            int tierEnd = idx + 1;
            int tierMain = allCaps.get(idx).priorityMain;
            int tierSub = allCaps.get(idx).prioritySub;
            while (tierEnd < allCaps.size()
                    && allCaps.get(tierEnd).priorityMain == tierMain
                    && allCaps.get(tierEnd).prioritySub == tierSub) {
                tierEnd++;
            }
            // Caps still able to accept this round. Trims down as caps saturate.
            List<SlotCap> active = new ArrayList<>(allCaps.subList(idx, tierEnd));
            // Round-based equal split: each pass distributes a floor share to each active cap.
            // When some caps saturate before reaching the per-cap share, the leftover is rolled
            // into the next round and re-split across the still-active caps.
            while (remaining > 0 && !active.isEmpty()) {
                int per = remaining / active.size();
                if (per == 0) {
                    // Fewer FE remain than active caps — dribble 1 FE at a time until exhausted.
                    Iterator<SlotCap> it = active.iterator();
                    while (it.hasNext() && remaining > 0) {
                        SlotCap c = it.next();
                        int give = Math.min(1, c.room);
                        int actual = give > 0 ? c.cap.receiveEnergy(give, false) : 0;
                        if (actual > 0) {
                            c.room -= actual;
                            remaining -= actual;
                            recordSlotDelivery(playerSlotBreakdown, c, actual);
                        }
                        if (c.room <= 0) it.remove();
                    }
                    break;
                }
                Iterator<SlotCap> it = active.iterator();
                while (it.hasNext() && remaining > 0) {
                    SlotCap c = it.next();
                    int give = Math.min(per, c.room);
                    int actual = give > 0 ? c.cap.receiveEnergy(give, false) : 0;
                    if (actual > 0) {
                        c.room -= actual;
                        remaining -= actual;
                        recordSlotDelivery(playerSlotBreakdown, c, actual);
                    }
                    if (c.room <= 0) it.remove();
                }
            }
            idx = tierEnd;
        }

        int totalDispensed = pulled - remaining;
        if (totalDispensed > 0) {
            currentTickChargeAccumulator.merge(net.id(), totalDispensed, Integer::sum);
            Map<UUID, Map<String, Integer>> channelMap = currentTickPlayerSlotBreakdown
                    .computeIfAbsent(net.id(), k -> new HashMap<>());
            for (var pe : playerSlotBreakdown.entrySet()) {
                Map<String, Integer> existing = channelMap.computeIfAbsent(pe.getKey(), k -> new HashMap<>());
                for (var se : pe.getValue().entrySet()) {
                    existing.merge(se.getKey(), se.getValue(), Integer::sum);
                }
            }
        }
    }

    /** One chargeable capability + the metadata needed for tier-equal-share allocation. */
    private static final class SlotCap {
        final ServerPlayer player;
        final IEnergyStorage cap;
        int room;
        final int priorityMain;
        final int prioritySub;
        final String slotKey;
        SlotCap(ServerPlayer player, IEnergyStorage cap, int room,
                int priorityMain, int prioritySub, String slotKey) {
            this.player = player; this.cap = cap; this.room = room;
            this.priorityMain = priorityMain; this.prioritySub = prioritySub;
            this.slotKey = slotKey;
        }
    }

    /** Iteration carrier for {@link #iterSlots}: stack + the priority tuple + display label. */
    private record SlotIterEntry(ItemStack stack, int priorityMain, int prioritySub, String slotKey) {}

    private static void recordSlotDelivery(Map<UUID, Map<String, Integer>> breakdown, SlotCap c, int amount) {
        breakdown.computeIfAbsent(c.player.getUUID(), k -> new HashMap<>())
                 .merge(c.slotKey, amount, Integer::sum);
    }

    private boolean hasLoadedManager(MinecraftServer server, QuantumChannel net) {
        for (GlobalPos gp : net.members()) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            if (level.getBlockEntity(gp.pos()) instanceof PhotonManagerBlockEntity) return true;
        }
        return false;
    }

    /** Try emitters first (live pull from adjacent generators); then drain storage buffers. */
    private int pullFromSources(MinecraftServer server, QuantumChannel net, int want) {
        int collected = pullFromEmitters(server, net, want);
        if (collected >= want) return collected;
        for (GlobalPos gp : net.members()) {
            if (collected >= want) break;
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            if (level.getBlockEntity(gp.pos()) instanceof PhotonStorageBlockEntity storage) {
                collected += storage.pullForExternal(want - collected);
            }
        }
        return collected;
    }

    /**
     * Iterates the slots enabled by {@code net.chargingSlots()}. The same item slot is never
     * visited twice when overlapping groups (e.g. HOTBAR ⊂ INVENTORY) are both enabled — the
     * higher-priority group claims its slots first, so its label sticks.
     *
     * <p>Each entry carries a two-level priority tuple ({@code main}, {@code sub}) plus the
     * translation-key label for its slot. {@code main} is the group's {@link QuantumChannel#slotPriority(int)};
     * {@code sub} is 0 for everything except armor (where it's the per-piece priority). The
     * caller in {@link #dispatchChannelCharging} sorts by this tuple — sub is a tie-breaker
     * inside a main-priority tier, never lifting one group above another.
     */
    private static List<SlotIterEntry> iterSlots(Player player, QuantumChannel net) {
        int mask = net.chargingSlots();
        // Sort the 5 slot groups by their main slotPriority — highest first. With overlapping
        // groups (HOTBAR ⊂ INVENTORY) the higher-priority one claims its slots first; its label
        // ("Hotbar" vs "Inventory") then sticks to those slots for display purposes.
        int[] groups = { ChargingSlots.HAND, ChargingSlots.HOTBAR, ChargingSlots.INVENTORY,
                ChargingSlots.ARMOR, ChargingSlots.CURIOS };
        for (int i = 0; i < groups.length - 1; i++) {
            int best = i;
            for (int j = i + 1; j < groups.length; j++) {
                if (net.slotPriority(groups[j]) > net.slotPriority(groups[best])) best = j;
            }
            if (best != i) { int tmp = groups[i]; groups[i] = groups[best]; groups[best] = tmp; }
        }

        // Track which inventory slot indices have already been added to avoid double-charging.
        // -1 = offhand sentinel, -10..-13 = armor sentinels.
        Set<Integer> claimed = new HashSet<>();
        List<SlotIterEntry> out = new ArrayList<>();
        for (int g : groups) {
            if (!ChargingSlots.has(mask, g)) continue;
            // Server config can disable individual slot groups. The per-channel mask still lets
            // users toggle them in the UI, but the dispatcher skips disabled groups entirely.
            if (!isSlotGroupAllowedByConfig(g)) continue;
            int main = net.slotPriority(g);
            switch (g) {
                case ChargingSlots.HAND -> {
                    int mainSel = player.getInventory().selected;
                    if (claimed.add(mainSel)) {
                        out.add(new SlotIterEntry(player.getInventory().getItem(mainSel),
                                main, 0, ChargingSlots.SLOT_MAIN_HAND));
                    }
                    if (claimed.add(-1)) {
                        out.add(new SlotIterEntry(player.getOffhandItem(),
                                main, 0, ChargingSlots.SLOT_OFF_HAND));
                    }
                }
                case ChargingSlots.HOTBAR -> {
                    for (int i = 0; i < 9; i++) {
                        if (claimed.add(i)) {
                            out.add(new SlotIterEntry(player.getInventory().getItem(i),
                                    main, 0, ChargingSlots.SLOT_HOTBAR));
                        }
                    }
                }
                case ChargingSlots.INVENTORY -> {
                    for (int i = 0; i < 36; i++) {
                        if (claimed.add(i)) {
                            out.add(new SlotIterEntry(player.getInventory().getItem(i),
                                    main, 0, ChargingSlots.SLOT_INVENTORY));
                        }
                    }
                }
                case ChargingSlots.ARMOR -> {
                    // Player.inventory.armor is indexed [0=feet, 1=legs, 2=chest, 3=head]. Our
                    // channel exposes per-piece priorities indexed [0=head, 1=chest, 2=legs, 3=feet],
                    // so the map is invIdx = 3 - armorIdx.
                    var armorList = player.getInventory().armor;
                    for (int armorIdx = 0; armorIdx < 4; armorIdx++) {
                        int invIdx = 3 - armorIdx;
                        int claimKey = -10 - armorIdx;
                        if (invIdx < 0 || invIdx >= armorList.size()) continue;
                        if (!claimed.add(claimKey)) continue;
                        String key = switch (armorIdx) {
                            case 0 -> ChargingSlots.SLOT_ARMOR_HEAD;
                            case 1 -> ChargingSlots.SLOT_ARMOR_CHEST;
                            case 2 -> ChargingSlots.SLOT_ARMOR_LEGS;
                            case 3 -> ChargingSlots.SLOT_ARMOR_FEET;
                            default -> "";
                        };
                        int sub = net.armorPiecePriority(armorIdx);
                        out.add(new SlotIterEntry(armorList.get(invIdx), main, sub, key));
                    }
                }
                case ChargingSlots.CURIOS -> {
                    // Curios integration is a placeholder — actual slot iteration requires the
                    // Curios API jar. When wired up, label each cap with ChargingSlots.SLOT_CURIOS.
                }
            }
        }
        return out;
    }

    private static boolean isSlotGroupAllowedByConfig(int slotBit) {
        return switch (slotBit) {
            case ChargingSlots.HAND      -> com.quantumchanneling.ServerConfig.slotHandEnabled;
            case ChargingSlots.HOTBAR    -> com.quantumchanneling.ServerConfig.slotHotbarEnabled;
            case ChargingSlots.INVENTORY -> com.quantumchanneling.ServerConfig.slotInventoryEnabled;
            case ChargingSlots.ARMOR     -> com.quantumchanneling.ServerConfig.slotArmorEnabled;
            case ChargingSlots.CURIOS    -> com.quantumchanneling.ServerConfig.slotCuriosEnabled;
            default -> true;
        };
    }

    /** Walk this channel's loaded emitters and pull up to {@code want} FE total. */
    private int pullFromEmitters(MinecraftServer server, QuantumChannel net, int want) {
        int collected = 0;
        for (GlobalPos gp : net.members()) {
            if (collected >= want) break;
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            BlockEntity be = level.getBlockEntity(gp.pos());
            if (!(be instanceof PhotonEmitterBlockEntity emitter)) continue;
            int need = want - collected;
            collected += emitter.pullForExternal(need);
        }
        return collected;
    }

    /* ---- save / load ---- */

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (QuantumChannel net : networks.values()) list.add(net.save());
        tag.put("Networks", list);

        ListTag subs = new ListTag();
        for (var e : chargingSubscriptions.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putUUID("Player", e.getKey());
            s.putUUID("Channel", e.getValue());
            subs.add(s);
        }
        tag.put("Subscriptions", subs);
        return tag;
    }

    public static ChannelData load(CompoundTag tag) {
        ChannelData data = new ChannelData();
        if (tag.contains("Networks", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Networks", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                QuantumChannel net = QuantumChannel.load(list.getCompound(i));
                data.networks.put(net.id(), net);
                for (GlobalPos m : net.members()) data.memberToNetwork.put(m, net.id());
            }
        } else if (tag.contains("Channels", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Channels", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag channel = list.getCompound(i);
                UUID id = channel.getUUID("Id");
                ListTag members = channel.getList("Members", Tag.TAG_COMPOUND);
                Set<GlobalPos> set = new HashSet<>(members.size());
                for (int j = 0; j < members.size(); j++) {
                    CompoundTag m = members.getCompound(j);
                    ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
                            new ResourceLocation(m.getString("Dim")));
                    set.add(GlobalPos.of(dim, BlockPos.of(m.getLong("Pos"))));
                }
                QuantumChannel legacy = QuantumChannel.fromLegacyChannel(id, set);
                data.networks.put(id, legacy);
                for (GlobalPos m : set) data.memberToNetwork.put(m, id);
            }
        }

        if (tag.contains("Subscriptions", Tag.TAG_LIST)) {
            ListTag subs = tag.getList("Subscriptions", Tag.TAG_COMPOUND);
            for (int i = 0; i < subs.size(); i++) {
                CompoundTag s = subs.getCompound(i);
                data.chargingSubscriptions.put(s.getUUID("Player"), s.getUUID("Channel"));
            }
        }
        return data;
    }

}
