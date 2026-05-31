package com.quantumchanneling.blockentity;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.channel.DispatchStrategy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.world.ForgeChunkManager;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import java.util.Objects;
import java.util.UUID;

public abstract class ChannelBoundBlockEntity extends BlockEntity {
    public static final int CAP_UNLIMITED = -1;
    /** Initial per-device throughput cap (FE/t). Reset button in the UI restores this value. */
    public static final int DEFAULT_CAP = 1_000_000;

    private @Nullable UUID channelId;
    private boolean forceChunkLoaded = false;
    /** Initial value = {@link #DEFAULT_CAP}. The UI shows the value and lets the user edit / reset / clear it. */
    private int throughputCap = DEFAULT_CAP;
    /** Higher = receives energy first when an emitter distributes across receivers. */
    private int priority = 0;
    /** When true, ignores throughputCap and runs unbounded (effectiveBudget = MAX_INT). */
    private boolean surgeMode = false;
    /** User-defined label shown in the device's title bar and Nodes list. Empty = use block name. */
    private String customName = "";

    /**
     * Subchannels (by stable {@link java.util.UUID}) this device subscribes to. For emitters the
     * iteration order is the priority "rule book" — earlier subchannels are tried first; for
     * receivers the order is only display-relevant.
     */
    private final LinkedHashSet<java.util.UUID> subscribedSubchannels = new LinkedHashSet<>();
    /**
     * Parallel subscription list for fluid subchannels. Stored independently because each resource
     * type owns its own subchannel namespace — subscribing to "Diamonds" (items) is unrelated to
     * subscribing to "Lava" (fluids), even if a single emitter does both.
     */
    private final LinkedHashSet<java.util.UUID> subscribedFluidSubchannels = new LinkedHashSet<>();
    /** Same shape as the fluid version, but for gas subchannels (Mekanism). */
    private final LinkedHashSet<java.util.UUID> subscribedGasSubchannels = new LinkedHashSet<>();

    /**
     * Per-device participation flags. False = device doesn't pull/push/accept the named resource.
     * Default off so a fresh device routes nothing until the user opts in per resource — keeps
     * inventories from being silently drained by a freshly placed emitter.
     */
    private boolean itemsEnabled = false;
    private boolean fluidsEnabled = false;
    private boolean gasEnabled = false;

    /**
     * Dispatch strategies — one per resource — for distributing across downstream targets. On an
     * emitter this picks among subscribed receivers; on a receiver it picks among adjacent
     * inventories / tanks. Round-robin uses the cursor counters below to remember where it left off.
     */
    private DispatchStrategy itemDispatch  = DispatchStrategy.SERVE_FIRST;
    private DispatchStrategy fluidDispatch = DispatchStrategy.SERVE_FIRST;
    private DispatchStrategy gasDispatch   = DispatchStrategy.SERVE_FIRST;
    private int itemRoundRobinCursor  = 0;
    private int fluidRoundRobinCursor = 0;
    private int gasRoundRobinCursor   = 0;

    /** Bumped by subclasses whenever a local-only setting changes that an emitter's mask must see. */
    private int localEditCount = 0;

    protected ChannelBoundBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public @Nullable UUID getChannelId() { return channelId; }

    public boolean setChannelId(@Nullable UUID id) {
        if (Objects.equals(channelId, id)) return false;
        UUID old = channelId;
        channelId = id;
        if (level != null && !level.isClientSide && level.getServer() != null) {
            ChannelData data = ChannelData.get(level.getServer());
            GlobalPos here = GlobalPos.of(level.dimension(), getBlockPos());
            if (old != null) {
                com.quantumchanneling.channel.QuantumChannel oldCh = data.getChannel(old);
                if (oldCh != null) onLeavingChannel(oldCh);
                data.removeMember(old, here);
            }
            if (id != null) {
                data.addMember(id, here);
                com.quantumchanneling.channel.QuantumChannel newCh = data.getChannel(id);
                if (newCh != null) onJoiningChannel(newCh);
            }
        }
        setChanged();
        return true;
    }

    /** Subclasses override to clean up channel-side state (e.g. emitter reverse index entries). */
    protected void onLeavingChannel(com.quantumchanneling.channel.QuantumChannel channel) {}

    /** Subclasses override to seed channel-side state (e.g. register emitter subscriptions). */
    protected void onJoiningChannel(com.quantumchanneling.channel.QuantumChannel channel) {}

    public boolean isChunkLoadForced() { return forceChunkLoaded; }

    public void setChunkLoadForced(boolean value) {
        if (forceChunkLoaded == value) return;
        forceChunkLoaded = value;
        applyForcedChunk();
        setChanged();
    }

    public int getThroughputCap() { return throughputCap; }

    public void setThroughputCap(int cap) {
        int v = cap < 0 ? CAP_UNLIMITED : cap;
        if (v == throughputCap) return;
        throughputCap = v;
        setChanged();
    }

    public int getPriority() { return priority; }
    public void setPriority(int p) { if (p != priority) { priority = p; setChanged(); } }

    public boolean isSurgeMode() { return surgeMode; }
    public void setSurgeMode(boolean v) { if (v != surgeMode) { surgeMode = v; setChanged(); } }

    public String getCustomName() { return customName; }
    public void setCustomName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() > 32) trimmed = trimmed.substring(0, 32);
        if (!trimmed.equals(customName)) { customName = trimmed; setChanged(); }
    }

    /**
     * Insertion-ordered subscription set. The {@link Set} is unmodifiable; mutate via the
     * {@link #addSubscribedSubchannel} / {@link #removeSubscribedSubchannel} / {@link #moveSubscribedSubchannel}
     * helpers so callers can't bypass the local-edit bookkeeping.
     */
    public Set<java.util.UUID> getSubscribedSubchannels() {
        return Collections.unmodifiableSet(subscribedSubchannels);
    }

    public boolean isSubscribedTo(java.util.UUID subId) {
        return subId != null && subscribedSubchannels.contains(subId);
    }

    public boolean addSubscribedSubchannel(java.util.UUID subId) {
        if (subId == null) return false;
        // Per-receiver cap. Already-subscribed UUIDs aren't counted twice (re-adding a known sub is
        // a no-op anyway and returns false at the set.add below).
        if (!subscribedSubchannels.contains(subId)
                && subscribedSubchannels.size() >= com.quantumchanneling.ServerConfig.itemsMaxSubsPerReceiver) {
            return false;
        }
        if (!subscribedSubchannels.add(subId)) return false;
        bumpLocalEdit();
        return true;
    }

    public boolean removeSubscribedSubchannel(java.util.UUID subId) {
        if (!subscribedSubchannels.remove(subId)) return false;
        bumpLocalEdit();
        return true;
    }

    /** Moves {@code subId} up ({@code direction = -1}) or down ({@code direction = +1}) in iteration order. */
    public boolean moveSubscribedSubchannel(java.util.UUID subId, int direction) {
        if (subId == null || direction == 0 || !subscribedSubchannels.contains(subId)) return false;
        java.util.UUID[] arr = subscribedSubchannels.toArray(new java.util.UUID[0]);
        int idx = -1;
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(subId)) { idx = i; break; }
        if (idx < 0) return false;
        int target = idx + (direction < 0 ? -1 : 1);
        if (target < 0 || target >= arr.length) return false;
        java.util.UUID tmp = arr[idx]; arr[idx] = arr[target]; arr[target] = tmp;
        subscribedSubchannels.clear();
        for (java.util.UUID id : arr) subscribedSubchannels.add(id);
        bumpLocalEdit();
        return true;
    }

    /* ---- fluid subscription parallel ---- */

    public Set<java.util.UUID> getSubscribedFluidSubchannels() {
        return Collections.unmodifiableSet(subscribedFluidSubchannels);
    }

    public boolean isSubscribedToFluid(java.util.UUID subId) {
        return subId != null && subscribedFluidSubchannels.contains(subId);
    }

    public boolean addSubscribedFluidSubchannel(java.util.UUID subId) {
        if (subId == null) return false;
        if (!subscribedFluidSubchannels.contains(subId)
                && subscribedFluidSubchannels.size() >= com.quantumchanneling.ServerConfig.fluidsMaxSubsPerReceiver) {
            return false;
        }
        if (!subscribedFluidSubchannels.add(subId)) return false;
        bumpLocalEdit();
        return true;
    }

    public boolean removeSubscribedFluidSubchannel(java.util.UUID subId) {
        if (!subscribedFluidSubchannels.remove(subId)) return false;
        bumpLocalEdit();
        return true;
    }

    /* ---- gas subscription parallel ---- */

    public Set<java.util.UUID> getSubscribedGasSubchannels() {
        return Collections.unmodifiableSet(subscribedGasSubchannels);
    }
    public boolean isSubscribedToGas(java.util.UUID subId) {
        return subId != null && subscribedGasSubchannels.contains(subId);
    }
    public boolean addSubscribedGasSubchannel(java.util.UUID subId) {
        if (subId == null) return false;
        if (!subscribedGasSubchannels.contains(subId)
                && subscribedGasSubchannels.size() >= com.quantumchanneling.ServerConfig.gasesMaxSubsPerReceiver) {
            return false;
        }
        if (!subscribedGasSubchannels.add(subId)) return false;
        bumpLocalEdit();
        return true;
    }
    public boolean removeSubscribedGasSubchannel(java.util.UUID subId) {
        if (!subscribedGasSubchannels.remove(subId)) return false;
        bumpLocalEdit();
        return true;
    }
    public boolean moveSubscribedGasSubchannel(java.util.UUID subId, int direction) {
        if (subId == null || direction == 0 || !subscribedGasSubchannels.contains(subId)) return false;
        java.util.UUID[] arr = subscribedGasSubchannels.toArray(new java.util.UUID[0]);
        int idx = -1;
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(subId)) { idx = i; break; }
        if (idx < 0) return false;
        int target = idx + (direction < 0 ? -1 : 1);
        if (target < 0 || target >= arr.length) return false;
        java.util.UUID tmp = arr[idx]; arr[idx] = arr[target]; arr[target] = tmp;
        subscribedGasSubchannels.clear();
        for (java.util.UUID id : arr) subscribedGasSubchannels.add(id);
        bumpLocalEdit();
        return true;
    }

    public boolean moveSubscribedFluidSubchannel(java.util.UUID subId, int direction) {
        if (subId == null || direction == 0 || !subscribedFluidSubchannels.contains(subId)) return false;
        java.util.UUID[] arr = subscribedFluidSubchannels.toArray(new java.util.UUID[0]);
        int idx = -1;
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(subId)) { idx = i; break; }
        if (idx < 0) return false;
        int target = idx + (direction < 0 ? -1 : 1);
        if (target < 0 || target >= arr.length) return false;
        java.util.UUID tmp = arr[idx]; arr[idx] = arr[target]; arr[target] = tmp;
        subscribedFluidSubchannels.clear();
        for (java.util.UUID id : arr) subscribedFluidSubchannels.add(id);
        bumpLocalEdit();
        return true;
    }

    /** Monotonically increasing counter — emitters compare it against their cached value. */
    public int getLocalEditCount() { return localEditCount; }
    public void bumpLocalEdit() { localEditCount++; setChanged(); }

    /* ---- per-device resource enable flags ---- */
    public boolean isItemsEnabled()  { return itemsEnabled; }
    public boolean isFluidsEnabled() { return fluidsEnabled; }
    public boolean isGasEnabled()    { return gasEnabled; }
    public void setItemsEnabled(boolean v)  { if (v != itemsEnabled)  { itemsEnabled  = v; bumpLocalEdit(); } }
    public void setFluidsEnabled(boolean v) { if (v != fluidsEnabled) { fluidsEnabled = v; bumpLocalEdit(); } }
    public void setGasEnabled(boolean v)    { if (v != gasEnabled)    { gasEnabled    = v; bumpLocalEdit(); } }

    /* ---- dispatch strategy ---- */
    public DispatchStrategy getItemDispatch()  { return itemDispatch; }
    public DispatchStrategy getFluidDispatch() { return fluidDispatch; }
    public DispatchStrategy getGasDispatch()   { return gasDispatch; }

    public void setItemDispatch(DispatchStrategy s) {
        if (s == null || s == itemDispatch) return;
        itemDispatch = s; itemRoundRobinCursor = 0; bumpLocalEdit();
    }
    public void setFluidDispatch(DispatchStrategy s) {
        if (s == null || s == fluidDispatch) return;
        fluidDispatch = s; fluidRoundRobinCursor = 0; bumpLocalEdit();
    }
    public void setGasDispatch(DispatchStrategy s) {
        if (s == null || s == gasDispatch) return;
        gasDispatch = s; gasRoundRobinCursor = 0; bumpLocalEdit();
    }

    /** Round-robin position for a target count of {@code size}, then advance the cursor. */
    public int takeItemRoundRobinIndex(int size) {
        if (size <= 0) return 0;
        int i = Math.floorMod(itemRoundRobinCursor, size);
        itemRoundRobinCursor = (itemRoundRobinCursor + 1) & 0x7FFFFFFF;
        setChanged();
        return i;
    }
    public int takeFluidRoundRobinIndex(int size) {
        if (size <= 0) return 0;
        int i = Math.floorMod(fluidRoundRobinCursor, size);
        fluidRoundRobinCursor = (fluidRoundRobinCursor + 1) & 0x7FFFFFFF;
        setChanged();
        return i;
    }
    public int takeGasRoundRobinIndex(int size) {
        if (size <= 0) return 0;
        int i = Math.floorMod(gasRoundRobinCursor, size);
        gasRoundRobinCursor = (gasRoundRobinCursor + 1) & 0x7FFFFFFF;
        setChanged();
        return i;
    }

    /**
     * Resolves the actual per-tick FE budget. Surge overrides the per-device cap entirely. Otherwise:
     * the per-device cap takes precedence (when set), then the config global ceiling. A globalDefault
     * of {@code <= 0} is treated as unlimited (the default config value is 0 = unlimited).
     */
    public int effectiveBudget(int globalDefault) {
        if (surgeMode) return Integer.MAX_VALUE;
        if (throughputCap != CAP_UNLIMITED) return throughputCap;
        return globalDefault <= 0 ? Integer.MAX_VALUE : globalDefault;
    }

    private void applyForcedChunk() {
        if (!(level instanceof ServerLevel server)) return;
        int cx = getBlockPos().getX() >> 4;
        int cz = getBlockPos().getZ() >> 4;
        ForgeChunkManager.forceChunk(server, QuantumChanneling.MODID, getBlockPos(), cx, cz, forceChunkLoaded, true);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        applyForcedChunk();
        // If a remote-unbind happened while this BE was unloaded, the channel won't list us
        // anymore. Drop the stale channelId so the menu reads cleanly as "Not bound".
        if (channelId != null && level instanceof ServerLevel sl) {
            ChannelData data = ChannelData.get(sl.getServer());
            com.quantumchanneling.channel.QuantumChannel ch = data.getChannel(channelId);
            GlobalPos here = GlobalPos.of(sl.dimension(), getBlockPos());
            if (ch == null || !ch.members().contains(here)) {
                channelId = null;
                setChanged();
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (forceChunkLoaded && level instanceof ServerLevel server) {
            int cx = getBlockPos().getX() >> 4;
            int cz = getBlockPos().getZ() >> 4;
            ForgeChunkManager.forceChunk(server, QuantumChanneling.MODID, getBlockPos(), cx, cz, false, true);
        }
    }

    /**
     * Called from {@code Block.onRemove} when the block was actually broken or replaced (not just
     * unloaded). Removes this device from the channel's member set so the network stops trying to
     * route through a dead position.
     */
    public void unbindFromChannelOnBreak() {
        if (channelId != null && level instanceof ServerLevel sl) {
            ChannelData data = ChannelData.get(sl.getServer());
            com.quantumchanneling.channel.QuantumChannel ch = data.getChannel(channelId);
            if (ch != null) onLeavingChannel(ch);
            data.removeMember(channelId, GlobalPos.of(sl.dimension(), getBlockPos()));
            channelId = null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (channelId != null) tag.putUUID("ChannelId", channelId);
        if (forceChunkLoaded) tag.putBoolean("ForceChunkLoad", true);
        if (throughputCap != CAP_UNLIMITED) tag.putInt("ThroughputCap", throughputCap);
        if (priority != 0) tag.putInt("Priority", priority);
        if (surgeMode) tag.putBoolean("Surge", true);
        if (!customName.isEmpty()) tag.putString("CustomName", customName);
        if (!subscribedSubchannels.isEmpty()) {
            tag.put("SubscribedSubs", saveUuidList(subscribedSubchannels));
        }
        if (!subscribedFluidSubchannels.isEmpty()) {
            tag.put("SubscribedFluidSubs", saveUuidList(subscribedFluidSubchannels));
        }
        if (!subscribedGasSubchannels.isEmpty()) {
            tag.put("SubscribedGasSubs", saveUuidList(subscribedGasSubchannels));
        }
        // Default-off — only the enable bits are persisted; a missing tag reads as off.
        if (itemsEnabled)  tag.putBoolean("ItemsEnabled",  true);
        if (fluidsEnabled) tag.putBoolean("FluidsEnabled", true);
        if (gasEnabled)    tag.putBoolean("GasEnabled",    true);
        // Only save non-default strategies; SERVE_FIRST(=0) maps to a missing tag.
        if (itemDispatch  != DispatchStrategy.SERVE_FIRST) tag.putByte("ItemDispatch",  (byte) itemDispatch.ordinal());
        if (fluidDispatch != DispatchStrategy.SERVE_FIRST) tag.putByte("FluidDispatch", (byte) fluidDispatch.ordinal());
        if (gasDispatch   != DispatchStrategy.SERVE_FIRST) tag.putByte("GasDispatch",   (byte) gasDispatch.ordinal());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        channelId = tag.hasUUID("ChannelId") ? tag.getUUID("ChannelId") : null;
        forceChunkLoaded = tag.getBoolean("ForceChunkLoad");
        throughputCap = tag.contains("ThroughputCap") ? tag.getInt("ThroughputCap") : CAP_UNLIMITED;
        priority = tag.getInt("Priority");
        surgeMode = tag.getBoolean("Surge");
        customName = tag.contains("CustomName") ? tag.getString("CustomName") : "";
        subscribedSubchannels.clear();
        if (tag.contains("SubscribedSubs", Tag.TAG_LIST)) {
            loadUuidList(tag.getList("SubscribedSubs", Tag.TAG_COMPOUND), subscribedSubchannels);
        }
        subscribedFluidSubchannels.clear();
        if (tag.contains("SubscribedFluidSubs", Tag.TAG_LIST)) {
            loadUuidList(tag.getList("SubscribedFluidSubs", Tag.TAG_COMPOUND), subscribedFluidSubchannels);
        }
        subscribedGasSubchannels.clear();
        if (tag.contains("SubscribedGasSubs", Tag.TAG_LIST)) {
            loadUuidList(tag.getList("SubscribedGasSubs", Tag.TAG_COMPOUND), subscribedGasSubchannels);
        }
        itemsEnabled  = tag.getBoolean("ItemsEnabled");
        fluidsEnabled = tag.getBoolean("FluidsEnabled");
        gasEnabled    = tag.getBoolean("GasEnabled");
        itemDispatch  = DispatchStrategy.byOrdinal(tag.getByte("ItemDispatch"));
        fluidDispatch = DispatchStrategy.byOrdinal(tag.getByte("FluidDispatch"));
        gasDispatch   = DispatchStrategy.byOrdinal(tag.getByte("GasDispatch"));
    }

    private static ListTag saveUuidList(LinkedHashSet<java.util.UUID> set) {
        ListTag list = new ListTag();
        for (java.util.UUID id : set) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            list.add(e);
        }
        return list;
    }

    private static void loadUuidList(ListTag list, LinkedHashSet<java.util.UUID> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (e.hasUUID("Id")) into.add(e.getUUID("Id"));
        }
    }

    public GlobalPos globalPos() {
        Level l = Objects.requireNonNull(level, "BlockEntity not yet placed in a Level");
        return GlobalPos.of(l.dimension(), getBlockPos());
    }
}
