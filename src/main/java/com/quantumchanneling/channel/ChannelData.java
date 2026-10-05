package com.quantumchanneling.channel;

import com.mojang.serialization.Codec;
import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonManagerBlockEntity;
import com.quantumchanneling.blockentity.PhotonStorageBlockEntity;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
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
    private static final Codec<ChannelData> CODEC = CompoundTag.CODEC.xmap(ChannelData::load, ChannelData::save);

    public static final SavedDataType<ChannelData> TYPE =
            new SavedDataType<>(QuantumChanneling.id("channels"), ChannelData::new, CODEC);

    private final Map<UUID, QuantumChannel> channels = new HashMap<>();
    private final Map<GlobalPos, UUID> memberToChannel = new HashMap<>();
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
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Walks every channel and clears any charging-slot bits that the current server config
     * disables. Runs at server startup and on config reload, so admin-disabled slots stay
     * disabled on existing channels across restarts — a later re-enable in the config requires
     * users to re-opt-in per channel instead of having stale state quietly resume.
     */
    public void applyChargingSlotConfig() {
        int forbiddenMask;
        if (!com.quantumchanneling.ServerConfig.wirelessEnabled) {
            forbiddenMask = ChargingSlots.ALL_MASK;
        } else {
            int m = 0;
            if (!com.quantumchanneling.ServerConfig.slotHandEnabled)      m |= ChargingSlots.HAND;
            if (!com.quantumchanneling.ServerConfig.slotHotbarEnabled)    m |= ChargingSlots.HOTBAR;
            if (!com.quantumchanneling.ServerConfig.slotInventoryEnabled) m |= ChargingSlots.INVENTORY;
            if (!com.quantumchanneling.ServerConfig.slotArmorEnabled)     m |= ChargingSlots.ARMOR;
            if (!com.quantumchanneling.ServerConfig.slotCuriosEnabled)    m |= ChargingSlots.CURIOS;
            forbiddenMask = m;
        }
        if (forbiddenMask == 0) return;
        boolean dirty = false;
        for (QuantumChannel net : channels.values()) {
            int cur = net.chargingSlots();
            int clean = cur & ~forbiddenMask;
            if (clean != cur) {
                net.setChargingSlots(clean);
                dirty = true;
            }
        }
        if (dirty) setDirty();
    }

    /* ---- channel lifecycle ---- */

    public QuantumChannel createChannel(ServerPlayer owner, String name) {
        UUID id = UUID.randomUUID();
        QuantumChannel net = new QuantumChannel(id, name, owner.getUUID(), owner.getGameProfile().name());
        channels.put(id, net);
        setDirty();
        return net;
    }

    public boolean deleteChannel(UUID id, @Nullable UUID actor) {
        QuantumChannel net = channels.get(id);
        if (net == null || !net.canManage(actor)) return false;
        for (GlobalPos m : net.members()) memberToChannel.remove(m);
        chargingSubscriptions.entrySet().removeIf(e -> e.getValue().equals(id));
        channels.remove(id);
        setDirty();
        return true;
    }

    public boolean renameChannel(UUID id, @Nullable UUID actor, String newName) {
        QuantumChannel net = channels.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.rename(newName);
        setDirty();
        return true;
    }

    public boolean setPublic(UUID id, @Nullable UUID actor, boolean value) {
        QuantumChannel net = channels.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.setPublic(value);
        // Going private drops charging subscriptions from players who only reached this channel via
        // public access — otherwise the stale rows linger in save data and their UI keeps showing
        // "subscribed" to a channel they can no longer use.
        if (!value) chargingSubscriptions.entrySet()
                .removeIf(e -> e.getValue().equals(id) && !net.canUse(e.getKey()));
        setDirty();
        return true;
    }

    public boolean setChargingSlots(UUID id, @Nullable UUID actor, int slotMask) {
        QuantumChannel net = channels.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.setChargingSlots(slotMask);
        setDirty();
        return true;
    }

    public boolean setColor(UUID id, @Nullable UUID actor, int color) {
        QuantumChannel net = channels.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.setColor(color);
        setDirty();
        return true;
    }

    public boolean setSlotPriority(UUID id, @Nullable UUID actor, int slotBit, int newPriority) {
        QuantumChannel net = channels.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.setSlotPriority(slotBit, newPriority);
        setDirty();
        return true;
    }

    public boolean setArmorPiecePriority(UUID id, @Nullable UUID actor, int armorIdx, int newPriority) {
        QuantumChannel net = channels.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.setArmorPiecePriority(armorIdx, newPriority);
        setDirty();
        return true;
    }

    public boolean setPin(UUID id, @Nullable UUID actor, String newPin) {
        QuantumChannel net = channels.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.setPin(newPin);
        setDirty();
        return true;
    }

    /** Returns true when the PIN matched and {@code player} was granted USER access. */
    public boolean joinByPin(ServerPlayer player, UUID channelId, String pin) {
        QuantumChannel net = channels.get(channelId);
        if (net == null) return false;
        if (net.canUse(player.getUUID())) return true; // already in
        if (!net.pinMatches(pin)) return false;
        net.setPermission(player.getUUID(), player.getGameProfile().name(), Permission.USER);
        setDirty();
        return true;
    }

    public boolean setPermission(UUID id, @Nullable UUID actor, UUID targetPlayerId, String targetName, Permission p) {
        QuantumChannel net = channels.get(id);
        if (net == null || !net.canManage(actor)) return false;
        if (net.isOwnedBy(targetPlayerId)) return false; // can't demote the owner
        // Only the owner may create or alter ADMINs. An admin can manage USER-level members but
        // cannot promote anyone to ADMIN nor change another admin — otherwise an invited admin
        // could mint peer admins or reshuffle them and lock out the rest.
        if (!net.isOwnedBy(actor)) {
            Permission current = net.playerPermissions().get(targetPlayerId);
            if (p == Permission.ADMIN || current == Permission.ADMIN) return false;
        }
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
        QuantumChannel net = channels.get(id);
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
        QuantumChannel net = channels.get(channelId);
        if (net == null || !net.canManage(actor)) return false;
        net.itemConfig().setBatchSize(batchSize);
        setDirty();
        return true;
    }

    public boolean setHeatEnabled(UUID channelId, @Nullable UUID actor, boolean enabled) {
        QuantumChannel net = channels.get(channelId);
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
        QuantumChannel net = channels.get(id);
        if (net == null) return false;
        if (!net.isOwnedBy(actor)) return false;
        if (targetPlayerId == null || targetPlayerId.equals(actor)) return false;
        net.transferOwnership(targetPlayerId, targetName);
        setDirty();
        return true;
    }

    public boolean removePermission(UUID id, @Nullable UUID actor, UUID targetPlayerId) {
        QuantumChannel net = channels.get(id);
        if (net == null || !net.canManage(actor)) return false;
        // Admins can't strip a peer admin — only the owner can remove another admin.
        if (!net.isOwnedBy(actor) && net.playerPermissions().get(targetPlayerId) == Permission.ADMIN) return false;
        net.removePermission(targetPlayerId);
        // Drop only a subscription that pointed at THIS channel — not one aimed at some other channel.
        chargingSubscriptions.remove(targetPlayerId, id);
        setDirty();
        return true;
    }

    public @Nullable QuantumChannel getChannel(UUID id) { return channels.get(id); }

    /** Unmodifiable view of every known channel. Used by the public API entry point. */
    public Map<UUID, QuantumChannel> getChannels() { return java.util.Collections.unmodifiableMap(channels); }

    /** All channels the player can see: owned, allowed via permission, public, or PIN-gated. */
    public List<QuantumChannel> visibleTo(ServerPlayer player) {
        UUID pid = player.getUUID();
        List<QuantumChannel> out = new ArrayList<>();
        for (QuantumChannel net : channels.values()) {
            // Members/owner/public see the channel; a PIN-gated channel also appears (stripped) so
            // non-members can attempt the PIN — ChannelInfo.from redacts everything but the prompt.
            if (net.canUse(pid) || net.hasPin()) out.add(net);
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    /* ---- membership ---- */

    public void addMember(UUID channelId, GlobalPos pos) {
        QuantumChannel net = channels.get(channelId);
        if (net == null) return;
        UUID existing = memberToChannel.get(pos);
        if (channelId.equals(existing)) return;
        if (existing != null) removeMember(existing, pos);
        if (net.addMember(pos)) {
            memberToChannel.put(pos, channelId);
            setDirty();
        }
    }

    public void removeMember(UUID channelId, GlobalPos pos) {
        QuantumChannel net = channels.get(channelId);
        if (net == null) return;
        if (net.removeMember(pos)) {
            memberToChannel.remove(pos);
            setDirty();
        }
    }

    public Set<GlobalPos> getMembers(UUID channelId) {
        QuantumChannel net = channels.get(channelId);
        return net == null ? Collections.emptySet() : net.members();
    }

    public @Nullable UUID getChannelOf(GlobalPos pos) { return memberToChannel.get(pos); }

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
            QuantumChannel net = channels.get(channelId);
            if (net == null) continue;
            if (!ChargingSlots.any(net.chargingSlots())) continue;
            if (!net.canUse(player.getUUID())) continue;
            if (net.isChargingBlocked(player.getUUID())) continue;  // admin-blocked
            if (!hasLoadedManager(server, net)) continue;
            playersByChannel.computeIfAbsent(channelId, k -> new ArrayList<>()).add(player);
        }

        for (var entry : playersByChannel.entrySet()) {
            QuantumChannel net = channels.get(entry.getKey());
            if (net == null) continue;
            dispatchChannelCharging(server, net, entry.getValue());
        }
    }

    /**
     * Tier-equal-share charging for one channel, as a single transaction:
     * <ol>
     *   <li>Collect every chargeable slot across every online subscriber, with its two-level
     *       priority tuple (group priority, then per-piece priority for armor) and slot label.</li>
     *   <li>Ask the channel's sources how much they could supply toward the total demand.</li>
     *   <li>Walk slots in descending priority. Inside each tier the remaining budget is split
     *       equally (with leftover redistribution, so 99 FE across 2 slots is 50 / 49).</li>
     *   <li>Draw exactly what was delivered from the sources. If they can't cover it after all,
     *       the whole transaction rolls back — no FE is ever created or lost.</li>
     * </ol>
     * Within a tier nothing about which player owns a slot matters: four armor pieces at the same
     * tier get 25% each, and a second subscriber's chestplate at that tier shares equally.
     */
    private void dispatchChannelCharging(MinecraftServer server, QuantumChannel net, List<ServerPlayer> subscribers) {
        try (Transaction tx = Transaction.openRoot()) {
            List<SlotCap> caps = new ArrayList<>();
            long totalDemand = 0;
            for (ServerPlayer player : subscribers) {
                for (SlotIterEntry entry : iterSlots(player, net)) {
                    if (player.getInventory().getItem(entry.slot()).isEmpty()) continue;
                    EnergyHandler handler = ItemAccess.forPlayerSlot(player, entry.slot()).getCapability(Capabilities.Energy.ITEM);
                    if (handler == null) continue;
                    int room = (int) Math.min(Integer.MAX_VALUE, handler.getCapacityAsLong() - handler.getAmountAsLong());
                    if (room <= 0) continue;
                    int accept;
                    try (Transaction probe = Transaction.open(tx)) {
                        accept = handler.insert(room, probe);
                    }
                    if (accept <= 0) continue;
                    caps.add(new SlotCap(player, handler, accept, entry.priorityMain(), entry.prioritySub(), entry.slotKey()));
                    totalDemand += accept;
                }
            }
            if (caps.isEmpty()) return;
            int want = (int) Math.min(totalDemand, Integer.MAX_VALUE);

            int available;
            try (Transaction probe = Transaction.open(tx)) {
                available = pullFromSources(server, net, want, probe);
            }
            if (available <= 0) return;

            caps.sort((a, b) -> {
                int c = Integer.compare(b.priorityMain, a.priorityMain);
                return c != 0 ? c : Integer.compare(b.prioritySub, a.prioritySub);
            });

            Map<UUID, Map<String, Integer>> playerSlotBreakdown = new HashMap<>();
            int remaining = available;
            int idx = 0;
            while (idx < caps.size() && remaining > 0) {
                int tierEnd = idx + 1;
                SlotCap first = caps.get(idx);
                while (tierEnd < caps.size()
                        && caps.get(tierEnd).priorityMain == first.priorityMain
                        && caps.get(tierEnd).prioritySub == first.prioritySub) {
                    tierEnd++;
                }
                // Each round hands every still-active cap an equal floor share; whatever saturated
                // caps couldn't take rolls into the next round. With fewer FE than caps, the share
                // bottoms out at 1 and the remainder dribbles out in order.
                List<SlotCap> active = new ArrayList<>(caps.subList(idx, tierEnd));
                while (remaining > 0 && !active.isEmpty()) {
                    int per = Math.max(1, remaining / active.size());
                    Iterator<SlotCap> it = active.iterator();
                    while (it.hasNext() && remaining > 0) {
                        SlotCap c = it.next();
                        int actual = c.handler.insert(Math.min(per, Math.min(c.room, remaining)), tx);
                        if (actual > 0) {
                            c.room -= actual;
                            remaining -= actual;
                            recordSlotDelivery(playerSlotBreakdown, c, actual);
                        }
                        if (c.room <= 0 || actual <= 0) it.remove();
                    }
                }
                idx = tierEnd;
            }

            int dispensed = available - remaining;
            if (dispensed <= 0 || pullFromSources(server, net, dispensed, tx) != dispensed) return;
            tx.commit();

            currentTickChargeAccumulator.merge(net.id(), dispensed, Integer::sum);
            Map<UUID, Map<String, Integer>> channelMap = currentTickPlayerSlotBreakdown
                    .computeIfAbsent(net.id(), k -> new HashMap<>());
            for (var pe : playerSlotBreakdown.entrySet()) {
                Map<String, Integer> existing = channelMap.computeIfAbsent(pe.getKey(), k -> new HashMap<>());
                pe.getValue().forEach((slot, fe) -> existing.merge(slot, fe, Integer::sum));
            }
        }
    }

    /** One chargeable slot + the metadata needed for tier-equal-share allocation. */
    private static final class SlotCap {
        final ServerPlayer player;
        final EnergyHandler handler;
        int room;
        final int priorityMain;
        final int prioritySub;
        final String slotKey;

        SlotCap(ServerPlayer player, EnergyHandler handler, int room, int priorityMain, int prioritySub, String slotKey) {
            this.player = player;
            this.handler = handler;
            this.room = room;
            this.priorityMain = priorityMain;
            this.prioritySub = prioritySub;
            this.slotKey = slotKey;
        }
    }

    /** Iteration carrier for {@link #iterSlots}: inventory slot + the priority tuple + display label. */
    private record SlotIterEntry(int slot, int priorityMain, int prioritySub, String slotKey) {}

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

    /** Emitters first (live pull from adjacent generators), then storage buffers. */
    private static int pullFromSources(MinecraftServer server, QuantumChannel net, int want, TransactionContext tx) {
        int collected = 0;
        for (GlobalPos gp : net.members()) {
            if (collected >= want) break;
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            if (level.getBlockEntity(gp.pos()) instanceof PhotonEmitterBlockEntity emitter) {
                collected += emitter.pullForExternal(want - collected, tx);
            }
        }
        for (GlobalPos gp : net.members()) {
            if (collected >= want) break;
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            if (level.getBlockEntity(gp.pos()) instanceof PhotonStorageBlockEntity storage) {
                collected += storage.pullForExternal(want - collected, tx);
            }
        }
        return collected;
    }

    /**
     * The player-inventory slots enabled by {@code net.chargingSlots()}. Slot indices follow the
     * player inventory: 0–35 main, 36–39 armor (feet → head), 40 offhand. The same slot is never
     * visited twice when overlapping groups (HOTBAR ⊂ INVENTORY) are both on — the higher-priority
     * group claims it first, so its label sticks.
     *
     * <p>{@code priorityMain} is the group's {@link QuantumChannel#slotPriority(int)};
     * {@code prioritySub} is 0 except for armor, where it's the per-piece priority — a tie-breaker
     * inside a tier, never lifting one group above another.
     */
    private static List<SlotIterEntry> iterSlots(Player player, QuantumChannel net) {
        int mask = net.chargingSlots();
        int[] groups = { ChargingSlots.HAND, ChargingSlots.HOTBAR, ChargingSlots.INVENTORY,
                ChargingSlots.ARMOR, ChargingSlots.CURIOS };
        for (int i = 0; i < groups.length - 1; i++) {
            int best = i;
            for (int j = i + 1; j < groups.length; j++) {
                if (net.slotPriority(groups[j]) > net.slotPriority(groups[best])) best = j;
            }
            if (best != i) { int tmp = groups[i]; groups[i] = groups[best]; groups[best] = tmp; }
        }

        Set<Integer> claimed = new HashSet<>();
        List<SlotIterEntry> out = new ArrayList<>();
        for (int g : groups) {
            if (!ChargingSlots.has(mask, g)) continue;
            // The per-channel mask can still hold a group the server config disables; skip it.
            if (!isSlotGroupAllowedByConfig(g)) continue;
            int main = net.slotPriority(g);
            switch (g) {
                case ChargingSlots.HAND -> {
                    int selected = player.getInventory().getSelectedSlot();
                    if (claimed.add(selected)) out.add(new SlotIterEntry(selected, main, 0, ChargingSlots.SLOT_MAIN_HAND));
                    if (claimed.add(Inventory.SLOT_OFFHAND)) {
                        out.add(new SlotIterEntry(Inventory.SLOT_OFFHAND, main, 0, ChargingSlots.SLOT_OFF_HAND));
                    }
                }
                case ChargingSlots.HOTBAR -> {
                    for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
                        if (claimed.add(i)) out.add(new SlotIterEntry(i, main, 0, ChargingSlots.SLOT_HOTBAR));
                    }
                }
                case ChargingSlots.INVENTORY -> {
                    for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                        if (claimed.add(i)) out.add(new SlotIterEntry(i, main, 0, ChargingSlots.SLOT_INVENTORY));
                    }
                }
                case ChargingSlots.ARMOR -> {
                    // Per-piece priorities are indexed [0=head .. 3=feet]; the inventory stores
                    // armor feet-first starting at slot 36, so head is 39.
                    for (int armorIdx = 0; armorIdx < 4; armorIdx++) {
                        int slot = Inventory.INVENTORY_SIZE + 3 - armorIdx;
                        if (!claimed.add(slot)) continue;
                        String key = switch (armorIdx) {
                            case 0 -> ChargingSlots.SLOT_ARMOR_HEAD;
                            case 1 -> ChargingSlots.SLOT_ARMOR_CHEST;
                            case 2 -> ChargingSlots.SLOT_ARMOR_LEGS;
                            default -> ChargingSlots.SLOT_ARMOR_FEET;
                        };
                        out.add(new SlotIterEntry(slot, main, net.armorPiecePriority(armorIdx), key));
                    }
                }
                case ChargingSlots.CURIOS -> {
                    // Curios charging isn't wired yet; the UI keeps this group locked.
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

    /* ---- save / load ---- */

    private CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (QuantumChannel net : channels.values()) list.add(net.save());
        tag.put("Channels", list);

        ListTag subs = new ListTag();
        for (var e : chargingSubscriptions.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.store("Player", UUIDUtil.CODEC, e.getKey());
            s.store("Channel", UUIDUtil.CODEC, e.getValue());
            subs.add(s);
        }
        tag.put("Subscriptions", subs);
        return tag;
    }

    private static ChannelData load(CompoundTag tag) {
        ChannelData data = new ChannelData();
        for (Tag t : tag.getListOrEmpty("Channels")) {
            if (!(t instanceof CompoundTag ct)) continue;
            QuantumChannel net = QuantumChannel.load(ct);
            data.channels.put(net.id(), net);
            for (GlobalPos m : net.members()) data.memberToChannel.put(m, net.id());
        }
        for (Tag t : tag.getListOrEmpty("Subscriptions")) {
            if (!(t instanceof CompoundTag s)) continue;
            UUID player = s.read("Player", UUIDUtil.CODEC).orElse(null);
            UUID channel = s.read("Channel", UUIDUtil.CODEC).orElse(null);
            if (player != null && channel != null) data.chargingSubscriptions.put(player, channel);
        }
        return data;
    }
}
