package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
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

    public boolean setChargingMode(UUID id, @Nullable UUID actor, ChargingMode mode) {
        QuantumChannel net = networks.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.setChargingMode(mode);
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

    public boolean removePermission(UUID id, @Nullable UUID actor, UUID targetPlayerId) {
        QuantumChannel net = networks.get(id);
        if (net == null || !net.canManage(actor)) return false;
        net.removePermission(targetPlayerId);
        chargingSubscriptions.remove(targetPlayerId); // they can no longer charge from here
        setDirty();
        return true;
    }

    public @Nullable QuantumChannel getChannel(UUID id) { return networks.get(id); }

    /** All channels the player can see: owned, allowed via permission, or public. */
    public List<QuantumChannel> visibleTo(ServerPlayer player) {
        UUID pid = player.getUUID();
        List<QuantumChannel> out = new ArrayList<>();
        for (QuantumChannel net : networks.values()) {
            if (net.ownerId() == null || net.canUse(pid)) out.add(net);
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
     * Each server tick: for every online player who has subscribed to a channel and whose channel
     * is set to charge, pull FE from any loaded emitter on that channel and push it into the items
     * matching the channel's charging mode.
     */
    public void tickCharging(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID channelId = chargingSubscriptions.get(player.getUUID());
            if (channelId == null) continue;
            QuantumChannel net = networks.get(channelId);
            if (net == null || net.chargingMode() == ChargingMode.OFF) continue;
            if (!net.canUse(player.getUUID())) continue;

            int demand = collectDemand(player, net.chargingMode());
            if (demand <= 0) continue;

            int pulled = pullFromEmitters(server, net, demand);
            if (pulled <= 0) continue;

            chargeItems(player, net.chargingMode(), pulled);
        }
    }

    private int collectDemand(Player player, ChargingMode mode) {
        int demand = 0;
        for (ItemStack stack : iterSlots(player, mode)) {
            if (stack.isEmpty()) continue;
            IEnergyStorage cap = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
            if (cap == null || !cap.canReceive()) continue;
            demand += cap.receiveEnergy(Integer.MAX_VALUE, true);
            if (demand < 0) return Integer.MAX_VALUE; // overflow guard
        }
        return demand;
    }

    private void chargeItems(Player player, ChargingMode mode, int totalAvailable) {
        int remaining = totalAvailable;
        for (ItemStack stack : iterSlots(player, mode)) {
            if (remaining <= 0) break;
            if (stack.isEmpty()) continue;
            IEnergyStorage cap = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
            if (cap == null || !cap.canReceive()) continue;
            int accepted = cap.receiveEnergy(remaining, false);
            remaining -= accepted;
        }
    }

    private static Iterable<ItemStack> iterSlots(Player player, ChargingMode mode) {
        List<ItemStack> out = new ArrayList<>();
        switch (mode) {
            case OFF -> { /* nothing */ }
            case HAND -> {
                out.add(player.getMainHandItem());
                out.add(player.getOffhandItem());
            }
            case HOTBAR -> {
                for (int i = 0; i < 9; i++) out.add(player.getInventory().getItem(i));
            }
            case INVENTORY -> {
                for (int i = 0; i < 36; i++) out.add(player.getInventory().getItem(i));
            }
            case ARMOR -> {
                for (ItemStack s : player.getInventory().armor) out.add(s);
            }
            case ALL -> {
                for (int i = 0; i < 36; i++) out.add(player.getInventory().getItem(i));
                for (ItemStack s : player.getInventory().armor) out.add(s);
                out.add(player.getOffhandItem());
            }
        }
        return out;
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
