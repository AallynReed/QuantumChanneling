package com.quantumchanneling.blockentity;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.ServerConfig;
import com.quantumchanneling.api.IQuantumSubchannelView.Kind;
import com.quantumchanneling.api.event.ChannelMembershipEvent;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.channel.DispatchStrategy;
import com.quantumchanneling.channel.QuantumChannel;
import com.quantumchanneling.menu.PhotonNodeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public abstract class ChannelBoundBlockEntity extends BlockEntity implements MenuProvider {
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

    /** Routing settings for each subchannel-routed resource. Each kind owns its own namespace. */
    private final EnumMap<Kind, ResourceSettings> resources = new EnumMap<>(Kind.class);

    /** Bumped by subclasses whenever a local-only setting changes that an emitter's mask must see. */
    private int localEditCount = 0;

    /**
     * Redstone gating. {@link RedstoneMode#IGNORE} (default) is the historical behaviour. The other
     * two consult the BE's neighbours and disable the device when the condition matches. Checked
     * once per tick by subclasses' {@code serverTick} entry point.
     */
    private RedstoneMode redstoneMode = RedstoneMode.IGNORE;

    /** Three-state redstone gate. Names match the screen labels. */
    public enum RedstoneMode {
        IGNORE,
        OFF_WHEN_POWERED,
        OFF_WHEN_UNPOWERED;

        public static RedstoneMode byOrdinal(int o) {
            RedstoneMode[] vs = values();
            return (o >= 0 && o < vs.length) ? vs[o] : IGNORE;
        }
    }

    private static final class ResourceSettings {
        /**
         * Subchannels this device subscribes to. For emitters the iteration order is the priority
         * "rule book" — earlier subchannels are tried first; for receivers it's display-only.
         */
        final LinkedHashSet<UUID> subscriptions = new LinkedHashSet<>();
        /** Default off so a fresh device routes nothing until the user opts in — keeps
         *  inventories from being silently drained by a freshly placed emitter. */
        boolean enabled;
        /** On an emitter: picks among subscribed receivers. On a receiver: among adjacent targets. */
        DispatchStrategy dispatch = DispatchStrategy.SERVE_FIRST;
        int roundRobinCursor;
        /** Bit N ({@link Direction#get3DDataValue()}) set = side N may be scanned / pushed to. */
        int sideMask = 0x3F;
    }

    protected ChannelBoundBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        for (Kind k : Kind.values()) resources.put(k, new ResourceSettings());
    }

    public @Nullable UUID getChannelId() { return channelId; }

    public boolean setChannelId(@Nullable UUID id) {
        if (Objects.equals(channelId, id)) return false;
        UUID old = channelId;
        channelId = id;
        if (level instanceof ServerLevel sl) {
            ChannelData data = ChannelData.get(sl.getServer());
            GlobalPos here = GlobalPos.of(level.dimension(), getBlockPos());
            if (old != null) {
                QuantumChannel oldCh = data.getChannel(old);
                if (oldCh != null) onLeavingChannel(oldCh);
                data.removeMember(old, here);
                fireMembership(old, here, ChannelMembershipEvent.Kind.LEFT);
            }
            if (id != null) {
                data.addMember(id, here);
                QuantumChannel newCh = data.getChannel(id);
                if (newCh != null) onJoiningChannel(newCh);
                fireMembership(id, here, ChannelMembershipEvent.Kind.JOINED);
            }
        }
        setChanged();
        return true;
    }

    private static void fireMembership(UUID channelId, GlobalPos pos, ChannelMembershipEvent.Kind kind) {
        try {
            NeoForge.EVENT_BUS.post(new ChannelMembershipEvent(channelId, pos, kind));
        } catch (Throwable ignored) {}
    }

    /** Subclasses override to clean up channel-side state (e.g. emitter reverse index entries). */
    protected void onLeavingChannel(QuantumChannel channel) {}

    /** Subclasses override to seed channel-side state (e.g. register emitter subscriptions). */
    protected void onJoiningChannel(QuantumChannel channel) {}

    public boolean isChunkLoadForced() { return forceChunkLoaded; }

    public void setChunkLoadForced(boolean value) {
        if (forceChunkLoaded == value) return;
        forceChunkLoaded = value;
        applyForcedChunk(value);
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

    /* ---- subscriptions ---- */

    /** Insertion-ordered and unmodifiable; mutate through the add/remove helpers so the
     *  local-edit bookkeeping can't be bypassed. */
    public Set<UUID> getSubscriptions(Kind kind) {
        return Collections.unmodifiableSet(resources.get(kind).subscriptions);
    }

    public boolean isSubscribed(Kind kind, @Nullable UUID subId) {
        return subId != null && resources.get(kind).subscriptions.contains(subId);
    }

    public boolean addSubscription(Kind kind, @Nullable UUID subId) {
        if (subId == null) return false;
        Set<UUID> subs = resources.get(kind).subscriptions;
        if (subs.contains(subId) || subs.size() >= maxSubscriptions(kind)) return false;
        subs.add(subId);
        bumpLocalEdit();
        bumpChannelVersion(kind);
        return true;
    }

    public boolean removeSubscription(Kind kind, @Nullable UUID subId) {
        if (!resources.get(kind).subscriptions.remove(subId)) return false;
        bumpLocalEdit();
        bumpChannelVersion(kind);
        return true;
    }

    private static int maxSubscriptions(Kind kind) {
        return switch (kind) {
            case ITEM -> ServerConfig.itemsMaxSubsPerReceiver;
            case FLUID -> ServerConfig.fluidsMaxSubsPerReceiver;
            case GAS -> ServerConfig.gasesMaxSubsPerReceiver;
        };
    }

    /** Monotonically increasing counter — emitters compare it against their cached value. */
    public int getLocalEditCount() { return localEditCount; }
    public void bumpLocalEdit() { localEditCount++; setChanged(); }

    /* ---- per-side enable masks ---- */

    public int getSideMask(Kind kind) { return resources.get(kind).sideMask; }

    public void setSideMask(Kind kind, int mask) {
        ResourceSettings s = resources.get(kind);
        int v = mask & 0x3F;
        if (v == s.sideMask) return;
        s.sideMask = v;
        bumpChannelVersion(kind);
        bumpLocalEdit();
    }

    public boolean isSideArmed(Kind kind, Direction dir) {
        return (resources.get(kind).sideMask & (1 << dir.get3DDataValue())) != 0;
    }

    /* ---- redstone gate ---- */

    public RedstoneMode getRedstoneMode() { return redstoneMode; }
    public void setRedstoneMode(RedstoneMode m) {
        if (m == null || m == redstoneMode) return;
        redstoneMode = m;
        bumpLocalEdit();
        // Toggling this can flip the device on/off this tick, so wake the routing decision caches.
        for (Kind k : Kind.values()) bumpChannelVersion(k);
    }

    /**
     * Checks the current redstone neighbour state against {@link #redstoneMode}. Returns true when
     * the device is effectively on (mode is IGNORE, or the neighbour signal matches the rule).
     */
    public boolean passesRedstoneGate() {
        if (redstoneMode == RedstoneMode.IGNORE || level == null) return true;
        boolean powered = level.hasNeighborSignal(worldPosition);
        return switch (redstoneMode) {
            case OFF_WHEN_POWERED   -> !powered;
            case OFF_WHEN_UNPOWERED -> powered;
            default -> true;
        };
    }

    /* ---- per-device resource enable flags ---- */

    public boolean isEnabled(Kind kind) { return resources.get(kind).enabled; }

    /** Enabled for {@code kind} and not switched off by redstone. */
    public boolean isActive(Kind kind) { return isEnabled(kind) && passesRedstoneGate(); }

    public void setEnabled(Kind kind, boolean v) {
        ResourceSettings s = resources.get(kind);
        if (v == s.enabled) return;
        s.enabled = v;
        bumpLocalEdit();
        // Toggling participation on a receiver changes whether emitters have a valid target for
        // its subscriptions — bump the channel version so every emitter drops its decision cache
        // instead of serving a stale "no loaded receiver" REJECT cached while this device was off.
        bumpChannelVersion(kind);
    }

    private void bumpChannelVersion(Kind kind) {
        if (!(level instanceof ServerLevel sl) || channelId == null) return;
        ChannelData data = ChannelData.get(sl.getServer());
        QuantumChannel ch = data.getChannel(channelId);
        if (ch == null) return;
        switch (kind) {
            case ITEM -> ch.itemConfig().bumpRoutingVersion();
            case FLUID -> ch.fluidConfig().bumpRoutingVersion();
            case GAS -> ch.gasConfig().bumpRoutingVersion();
        }
        data.setDirty();
    }

    /* ---- dispatch strategy ---- */

    public DispatchStrategy getDispatch(Kind kind) { return resources.get(kind).dispatch; }

    public void setDispatch(Kind kind, DispatchStrategy strategy) {
        ResourceSettings s = resources.get(kind);
        if (strategy == null || strategy == s.dispatch) return;
        s.dispatch = strategy;
        s.roundRobinCursor = 0;
        bumpLocalEdit();
    }

    /** Round-robin start position for {@code size} targets, without advancing the cursor. */
    public int peekRoundRobin(Kind kind, int size) {
        return size <= 0 ? 0 : Math.floorMod(resources.get(kind).roundRobinCursor, size);
    }

    /** Advances the round-robin cursor by one. Call once per committed delivery so every routed
     *  batch moves the cursor exactly once. */
    public void advanceRoundRobin(Kind kind) {
        ResourceSettings s = resources.get(kind);
        s.roundRobinCursor = (s.roundRobinCursor + 1) & 0x7FFFFFFF;
        setChanged();
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

    private void applyForcedChunk(boolean forced) {
        if (!(level instanceof ServerLevel server)) return;
        BlockPos pos = getBlockPos();
        QuantumChanneling.CHUNK_TICKETS.forceChunk(server, pos, pos.getX() >> 4, pos.getZ() >> 4, forced, true);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (forceChunkLoaded) applyForcedChunk(true);
        // If a remote-unbind happened while this BE was unloaded, the channel won't list us
        // anymore. Drop the stale channelId so the menu reads cleanly as "Not bound".
        if (channelId != null && level instanceof ServerLevel sl) {
            QuantumChannel ch = ChannelData.get(sl.getServer()).getChannel(channelId);
            if (ch == null || !ch.members().contains(GlobalPos.of(sl.dimension(), getBlockPos()))) {
                channelId = null;
                setChanged();
            }
        }
    }

    /** The block was broken or replaced (not just unloaded): leave the channel and drop the ticket. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (forceChunkLoaded) applyForcedChunk(false);
        if (channelId != null && level instanceof ServerLevel sl) {
            ChannelData data = ChannelData.get(sl.getServer());
            QuantumChannel ch = data.getChannel(channelId);
            if (ch != null) onLeavingChannel(ch);
            data.removeMember(channelId, GlobalPos.of(sl.dimension(), getBlockPos()));
            channelId = null;
        }
    }

    /* ---- menu ---- */

    /** Telemetry the device menu reads through {@link PhotonNodeMenu}'s data slots. */
    protected abstract ContainerData menuData();

    /** Storage capacity shown in the menu; 0 for every device that isn't a storage. */
    protected long menuStorageCapacity() { return 0L; }

    @Override
    public Component getDisplayName() { return getBlockState().getBlock().getName(); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new PhotonNodeMenu(id, inv, getBlockPos(), menuData(),
                channelId, resolveChannelName(), resolveChannelOwner(), customName, menuStorageCapacity());
    }

    public void openMenu(ServerPlayer player) {
        player.openMenu(this, this::writeMenuHeader);
    }

    /** Mirrors {@link PhotonNodeMenu}'s client constructor. */
    private void writeMenuHeader(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(getBlockPos());
        buf.writeBoolean(channelId != null);
        if (channelId != null) buf.writeUUID(channelId);
        buf.writeUtf(resolveChannelName(), 64);
        buf.writeUtf(resolveChannelOwner(), 64);
        buf.writeUtf(customName, 64);
        buf.writeLong(menuStorageCapacity());
    }

    public String resolveChannelName() {
        QuantumChannel ch = boundChannel();
        return ch == null ? "" : ch.name();
    }

    public String resolveChannelOwner() {
        QuantumChannel ch = boundChannel();
        return ch == null ? "" : ch.ownerName();
    }

    /** The channel this device is bound to, or null when unbound or not on the logical server. */
    protected @Nullable QuantumChannel boundChannel() {
        if (channelId == null || !(level instanceof ServerLevel sl)) return null;
        return ChannelData.get(sl.getServer()).getChannel(channelId);
    }

    /* ---- persistence ---- */

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("ChannelId", UUIDUtil.CODEC, channelId);
        if (forceChunkLoaded) output.putBoolean("ForceChunkLoad", true);
        if (throughputCap != CAP_UNLIMITED) output.putInt("ThroughputCap", throughputCap);
        if (priority != 0) output.putInt("Priority", priority);
        if (surgeMode) output.putBoolean("Surge", true);
        if (!customName.isEmpty()) output.putString("CustomName", customName);
        if (redstoneMode != RedstoneMode.IGNORE) output.putByte("RedstoneMode", (byte) redstoneMode.ordinal());
        for (Kind kind : Kind.values()) {
            ResourceSettings s = resources.get(kind);
            ValueOutput out = output.child(nbtKey(kind));
            if (s.enabled) out.putBoolean("Enabled", true);
            if (s.dispatch != DispatchStrategy.SERVE_FIRST) out.putByte("Dispatch", (byte) s.dispatch.ordinal());
            if (s.sideMask != 0x3F) out.putByte("SideMask", (byte) s.sideMask);
            if (!s.subscriptions.isEmpty()) {
                ValueOutput.TypedOutputList<UUID> list = out.list("Subscriptions", UUIDUtil.CODEC);
                s.subscriptions.forEach(list::add);
            }
            if (out.isEmpty()) output.discard(nbtKey(kind));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        channelId = input.read("ChannelId", UUIDUtil.CODEC).orElse(null);
        forceChunkLoaded = input.getBooleanOr("ForceChunkLoad", false);
        throughputCap = input.getIntOr("ThroughputCap", CAP_UNLIMITED);
        priority = input.getIntOr("Priority", 0);
        surgeMode = input.getBooleanOr("Surge", false);
        customName = input.getStringOr("CustomName", "");
        redstoneMode = RedstoneMode.byOrdinal(input.getByteOr("RedstoneMode", (byte) 0));
        for (Kind kind : Kind.values()) {
            ResourceSettings s = resources.get(kind);
            ValueInput in = input.childOrEmpty(nbtKey(kind));
            s.enabled = in.getBooleanOr("Enabled", false);
            s.dispatch = DispatchStrategy.byOrdinal(in.getByteOr("Dispatch", (byte) 0));
            s.sideMask = in.getByteOr("SideMask", (byte) 0x3F) & 0x3F;
            s.subscriptions.clear();
            in.listOrEmpty("Subscriptions", UUIDUtil.CODEC).forEach(s.subscriptions::add);
        }
    }

    private static String nbtKey(Kind kind) {
        return switch (kind) {
            case ITEM -> "Items";
            case FLUID -> "Fluids";
            case GAS -> "Gases";
        };
    }

    public GlobalPos globalPos() {
        Level l = Objects.requireNonNull(level, "BlockEntity not yet placed in a Level");
        return GlobalPos.of(l.dimension(), getBlockPos());
    }
}
