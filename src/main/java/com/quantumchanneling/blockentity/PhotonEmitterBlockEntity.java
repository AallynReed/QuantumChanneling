package com.quantumchanneling.blockentity;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.ServerConfig;
import com.quantumchanneling.api.IQuantumSubchannelView.Kind;
import com.quantumchanneling.api.event.SubchannelChangedEvent;
import com.quantumchanneling.block.PhotonShape;
import com.quantumchanneling.channel.FluidFilter;
import com.quantumchanneling.channel.FluidSubchannel;
import com.quantumchanneling.channel.GasFilter;
import com.quantumchanneling.channel.GasSubchannel;
import com.quantumchanneling.channel.IdTagFilter;
import com.quantumchanneling.channel.ItemFilter;
import com.quantumchanneling.channel.ItemSubchannel;
import com.quantumchanneling.channel.JournaledInt;
import com.quantumchanneling.channel.QuantumChannel;
import com.quantumchanneling.channel.ResourceMode;
import com.quantumchanneling.channel.ResourceRouter;
import com.quantumchanneling.channel.RouteDecision;
import com.quantumchanneling.channel.SubchannelBase;
import com.quantumchanneling.client.Compat;
import com.quantumchanneling.compat.mekanism.ChemicalCompat;
import com.quantumchanneling.menu.PhotonNodeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.RegisteredResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class PhotonEmitterBlockEntity extends ChannelBoundBlockEntity {
    // Energy forwarded this tick, in FE. Journaled: a forward that rolls back must not eat budget.
    private final JournaledInt feForwarded = new JournaledInt();
    private int lastTickFE = 0;

    // One sample per second per resource, 60 minutes deep. Transient — reopened worlds start fresh.
    private final ThroughputHistory history = new ThroughputHistory();

    // Loop detector. Set when the router refuses to push into the block this emitter pulled from;
    // the UI shows a warning while the flag is fresh, and it self-clears after a few seconds.
    private long lastLoopDetectedTick = -1L;
    private static final int LOOP_FLAG_LINGER_TICKS = 60;

    /** Per-emitter void filters. Blacklist with no entries by default, so a fresh emitter voids nothing. */
    private final ItemFilter voidFilter = new ItemFilter(false);
    private final FluidFilter fluidVoidFilter = new FluidFilter(false);
    private final GasFilter gasVoidFilter = new GasFilter(false);

    /**
     * Subchannels owned by this emitter. Each is a (name, filter) pair the emitter pushes through;
     * receivers on the same channel subscribe by UUID. Iteration order is the priority "rule book"
     * — the router uses the first subchannel whose filter accepts.
     */
    private final LinkedHashMap<UUID, ItemSubchannel> itemSubchannels = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, FluidSubchannel> fluidSubchannels = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, GasSubchannel> gasSubchannels = new LinkedHashMap<>();

    /** Runtime routing state per resource. */
    private final EnumMap<Kind, Pipeline> pipelines = new EnumMap<>(Kind.class);

    private static final int DECISION_CACHE_MAX = 256;
    /** Skip a receiver for this long after a push delivered nothing to it. */
    private static final int REJECT_COOLDOWN_TICKS = 20;
    private static final int REJECT_MAP_PRUNE_AT = 64;
    /** Adjacent scans run every 2 ticks while things move, every 20 after a dry scan. */
    private static final int TICK_INTERVAL_FAST = 2;
    private static final int TICK_INTERVAL_SLOW = 20;

    private static final class Pipeline {
        /** LRU keyed by registry id; wiped when the channel's routing version or our local edits change. */
        final Map<Identifier, RouteDecision> decisions = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Identifier, RouteDecision> eldest) {
                return size() > DECISION_CACHE_MAX;
            }
        };
        int cachedChannelVersion = -1;
        int cachedLocalEdit = -1;
        /** Packed receiver pos → game tick until which it's skipped. */
        final HashMap<Long, Long> rejectUntil = new HashMap<>();
        int tickPhase;
        boolean slowMode;
        int lastSeenChannelVersion = -1;
        int routedThisTick;
        int lastTick;
    }

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case PhotonNodeMenu.DATA_THROUGHPUT        -> lastTickFE;
                case PhotonNodeMenu.DATA_THROUGHPUT_ITEMS  -> getLastTickItems();
                case PhotonNodeMenu.DATA_THROUGHPUT_FLUIDS -> getLastTickFluids();
                case PhotonNodeMenu.DATA_THROUGHPUT_GAS    -> getLastTickGas();
                case PhotonNodeMenu.DATA_LOOP_WARNING      -> isLoopWarningActive() ? 1 : 0;
                case PhotonNodeMenu.DATA_CHUNK_LOADED      -> isChunkLoadForced() ? 1 : 0;
                case PhotonNodeMenu.DATA_CHANNEL_BOUND     -> getChannelId() != null ? 1 : 0;
                case PhotonNodeMenu.DATA_THROUGHPUT_CAP    -> getThroughputCap();
                case PhotonNodeMenu.DATA_PRIORITY          -> getPriority();
                case PhotonNodeMenu.DATA_SURGE             -> isSurgeMode() ? 1 : 0;
                case PhotonNodeMenu.DATA_AVG_1MIN          -> history.averagePerSecond(60, ResourceMode.ENERGY);
                case PhotonNodeMenu.DATA_AVG_5MIN          -> history.averagePerSecond(300, ResourceMode.ENERGY);
                case PhotonNodeMenu.DATA_AVG_10MIN         -> history.averagePerSecond(600, ResourceMode.ENERGY);
                default -> history.graphSlot(index);
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return PhotonNodeMenu.DATA_SIZE; }
    };

    /**
     * Energy input. Anything pushing energy into the emitter is forwarded straight through the
     * channel inside the caller's transaction, capped by this tick's remaining budget.
     */
    private final EnergyHandler energyHandler = new EnergyHandler() {
        @Override public long getAmountAsLong() { return 0; }
        @Override public long getCapacityAsLong() { return effectiveBudget(ServerConfig.emitterPushRate); }

        @Override
        public int insert(int amount, TransactionContext tx) {
            if (!passesRedstoneGate()) return 0;
            int room = Math.max(0, effectiveBudget(ServerConfig.emitterPushRate) - feForwarded.get());
            int offered = Math.min(amount, room);
            if (offered <= 0) return 0;
            int forwarded = forwardToChannel(offered, tx);
            if (forwarded > 0) feForwarded.add(forwarded, tx);
            return forwarded;
        }

        // Pure input — nothing is ever stored here to extract.
        @Override public int extract(int amount, TransactionContext tx) { return 0; }
    };

    private final ResourceHandler<ItemResource> itemHandler =
            new RoutingHandler<>(ResourceRouter.ITEMS, ItemResource.EMPTY);
    private final ResourceHandler<FluidResource> fluidHandler =
            new RoutingHandler<>(ResourceRouter.FLUIDS, FluidResource.EMPTY);
    private @Nullable ResourceHandler<RegisteredResource<?>> chemicalHandler;

    public PhotonEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(QuantumChanneling.PHOTON_EMITTER_BE.get(), pos, state);
        for (Kind k : Kind.values()) pipelines.put(k, new Pipeline());
    }

    public EnergyHandler energyHandler() { return energyHandler; }
    public ResourceHandler<ItemResource> itemHandler() { return itemHandler; }
    public ResourceHandler<FluidResource> fluidHandler() { return fluidHandler; }

    public @Nullable ResourceHandler<RegisteredResource<?>> chemicalHandler() {
        if (chemicalHandler == null) {
            RegisteredResource<?> empty = ChemicalCompat.emptyResource();
            if (empty == null) return null;
            chemicalHandler = new RoutingHandler<>(ResourceRouter.CHEMICALS, empty);
        }
        return chemicalHandler;
    }

    /**
     * A single always-empty slot: hoppers, pipes and import buses insert into it, and every insert
     * runs through the same void + subchannel pipeline as the active pull, inside the caller's
     * transaction.
     */
    private final class RoutingHandler<R extends RegisteredResource<?>> implements ResourceHandler<R> {
        private final ResourceRouter<R> router;
        private final R empty;

        RoutingHandler(ResourceRouter<R> router, R empty) {
            this.router = router;
            this.empty = empty;
        }

        @Override public int size() { return 1; }
        @Override public R getResource(int index) { return empty; }
        @Override public long getAmountAsLong(int index) { return 0; }
        @Override public long getCapacityAsLong(int index, R resource) { return Integer.MAX_VALUE; }
        @Override public boolean isValid(int index, R resource) { return true; }

        @Override
        public int insert(int index, R resource, int amount, TransactionContext tx) {
            Kind kind = router.kind();
            if (resource.isEmpty() || amount <= 0) return 0;
            if (!routingEnabled(kind) || !isActive(kind)) return 0;
            if (!(level instanceof ServerLevel sl)) return 0;
            QuantumChannel channel = boundChannel();
            if (channel == null) return 0;
            return router.insert(sl, PhotonEmitterBlockEntity.this, channel, resource, amount, tx);
        }

        @Override public int extract(int index, R resource, int amount, TransactionContext tx) { return 0; }
    }

    private static boolean routingEnabled(Kind kind) {
        return switch (kind) {
            case ITEM -> ServerConfig.itemsRoutingEnabled;
            case FLUID -> ServerConfig.fluidsRoutingEnabled;
            case GAS -> ServerConfig.gasesRoutingEnabled && ChemicalCompat.isAvailable();
        };
    }

    /* ---- filters + subchannels ---- */

    public ItemFilter voidFilter() { return voidFilter; }
    public FluidFilter fluidVoidFilter() { return fluidVoidFilter; }
    public GasFilter gasVoidFilter() { return gasVoidFilter; }

    public IdTagFilter voidFilter(Kind kind) {
        return switch (kind) {
            case ITEM -> voidFilter;
            case FLUID -> fluidVoidFilter;
            case GAS -> gasVoidFilter;
        };
    }

    public Collection<ItemSubchannel>  itemSubchannels()  { return Collections.unmodifiableCollection(itemSubchannels.values()); }
    public Collection<FluidSubchannel> fluidSubchannels() { return Collections.unmodifiableCollection(fluidSubchannels.values()); }
    public Collection<GasSubchannel>   gasSubchannels()   { return Collections.unmodifiableCollection(gasSubchannels.values()); }

    private LinkedHashMap<UUID, ? extends SubchannelBase<?>> map(Kind kind) {
        return switch (kind) {
            case ITEM -> itemSubchannels;
            case FLUID -> fluidSubchannels;
            case GAS -> gasSubchannels;
        };
    }

    /** Subchannels of {@code kind} in routing-priority order. */
    public Collection<? extends SubchannelBase<?>> subchannels(Kind kind) {
        return Collections.unmodifiableCollection(map(kind).values());
    }

    public @Nullable SubchannelBase<?> subchannel(Kind kind, @Nullable UUID id) {
        return id == null ? null : map(kind).get(id);
    }

    public int subchannelCount(Kind kind) { return map(kind).size(); }

    /**
     * Returns the new subchannel's UUID, or {@code null} when validation failed: empty name,
     * duplicate name on this emitter, the per-emitter cap was hit, or the channel-wide cap
     * (counted across every loaded emitter on the same channel) was hit.
     */
    public @Nullable UUID createSubchannel(Kind kind, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || nameTaken(kind, n, null)) return null;
        if (subchannelCount(kind) >= maxPerEmitter(kind)) return null;
        if (countChannelSubchannels(kind) >= maxPerChannel(kind)) return null;
        UUID id = UUID.randomUUID();
        switch (kind) {
            case ITEM -> itemSubchannels.put(id, new ItemSubchannel(id, n));
            case FLUID -> fluidSubchannels.put(id, new FluidSubchannel(id, n));
            case GAS -> gasSubchannels.put(id, new GasSubchannel(id, n));
        }
        bumpLocalEdit();
        fireSubchannelChanged(kind, id, SubchannelChangedEvent.Kind.CREATED);
        return id;
    }

    /** Removes a subchannel. Callers sweep the dangling subscriptions with {@link #sweepReceiverSubscriptions}. */
    public boolean deleteSubchannel(Kind kind, UUID id) {
        if (id == null || map(kind).remove(id) == null) return false;
        bumpLocalEdit();
        fireSubchannelChanged(kind, id, SubchannelChangedEvent.Kind.DELETED);
        return true;
    }

    public boolean renameSubchannel(Kind kind, UUID id, String name) {
        SubchannelBase<?> s = subchannel(kind, id);
        String n = name == null ? "" : name.trim();
        if (s == null || n.isEmpty() || nameTaken(kind, n, s)) return false;
        s.setName(n);
        bumpLocalEdit();
        fireSubchannelChanged(kind, id, SubchannelChangedEvent.Kind.RENAMED);
        return true;
    }

    public boolean setSubchannelFilterMode(Kind kind, UUID id, boolean whitelist) {
        SubchannelBase<?> s = subchannel(kind, id);
        if (s == null || s.filter().isWhitelist() == whitelist) return false;
        s.filter().setWhitelist(whitelist);
        bumpLocalEdit();
        return true;
    }

    public boolean addSubchannelEntry(Kind kind, UUID id, Identifier entry) {
        return editFilter(kind, id, f -> f.add(entry));
    }

    public boolean removeSubchannelEntry(Kind kind, UUID id, Identifier entry) {
        return editFilter(kind, id, f -> f.remove(entry));
    }

    public boolean addSubchannelTagEntry(Kind kind, UUID id, Identifier tagId) {
        return editFilter(kind, id, f -> f.addTag(tagId));
    }

    public boolean removeSubchannelTagEntry(Kind kind, UUID id, Identifier tagId) {
        return editFilter(kind, id, f -> f.removeTag(tagId));
    }

    private boolean editFilter(Kind kind, UUID id, Predicate<IdTagFilter> edit) {
        SubchannelBase<?> s = subchannel(kind, id);
        if (s == null || !edit.test(s.filter())) return false;
        bumpLocalEdit();
        return true;
    }

    public boolean setSubchannelColor(Kind kind, UUID id, int rgb) {
        SubchannelBase<?> s = subchannel(kind, id);
        if (s == null || s.color() == (rgb & 0xFFFFFF)) return false;
        s.setColor(rgb);
        bumpLocalEdit();
        fireSubchannelChanged(kind, id, SubchannelChangedEvent.Kind.RECOLORED);
        return true;
    }

    /** Moves a subchannel up ({@code dir<0}) or down ({@code dir>0}) in iteration order. */
    public boolean moveSubchannel(Kind kind, UUID id, int dir) {
        if (!reorder(map(kind), id, dir)) return false;
        bumpLocalEdit();
        fireSubchannelChanged(kind, id, SubchannelChangedEvent.Kind.REORDERED);
        return true;
    }

    private static <V> boolean reorder(LinkedHashMap<UUID, V> map, UUID id, int dir) {
        if (id == null || dir == 0 || !map.containsKey(id)) return false;
        List<UUID> keys = new ArrayList<>(map.keySet());
        int idx = keys.indexOf(id);
        int target = idx + (dir < 0 ? -1 : 1);
        if (target < 0 || target >= keys.size()) return false;
        Collections.swap(keys, idx, target);
        LinkedHashMap<UUID, V> rebuilt = new LinkedHashMap<>();
        for (UUID k : keys) rebuilt.put(k, map.get(k));
        map.clear();
        map.putAll(rebuilt);
        return true;
    }

    private boolean nameTaken(Kind kind, String name, @Nullable SubchannelBase<?> except) {
        for (SubchannelBase<?> s : map(kind).values()) {
            if (s != except && s.name().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static int maxPerEmitter(Kind kind) {
        return switch (kind) {
            case ITEM -> ServerConfig.itemsMaxSubsPerEmitter;
            case FLUID -> ServerConfig.fluidsMaxSubsPerEmitter;
            case GAS -> ServerConfig.gasesMaxSubsPerEmitter;
        };
    }

    private static int maxPerChannel(Kind kind) {
        return switch (kind) {
            case ITEM -> ServerConfig.itemsMaxSubsPerChannel;
            case FLUID -> ServerConfig.fluidsMaxSubsPerChannel;
            case GAS -> ServerConfig.gasesMaxSubsPerChannel;
        };
    }

    /**
     * Subchannels of {@code kind} across every currently-loaded emitter on this channel, including
     * this one. Unloaded emitters can't be counted — their subchannels only live in chunk data.
     */
    private int countChannelSubchannels(Kind kind) {
        QuantumChannel ch = boundChannel();
        if (ch == null || !(level instanceof ServerLevel sl)) return subchannelCount(kind);
        int total = 0;
        for (GlobalPos gp : ch.members()) {
            ServerLevel lvl = sl.getServer().getLevel(gp.dimension());
            if (lvl == null || !lvl.isLoaded(gp.pos())) continue;
            if (lvl.getBlockEntity(gp.pos()) instanceof PhotonEmitterBlockEntity em) total += em.subchannelCount(kind);
        }
        return total;
    }

    /** Posts a structural-edit event. Server-side only; a listener can never break the edit. */
    private void fireSubchannelChanged(Kind kind, UUID subchannelId, SubchannelChangedEvent.Kind change) {
        if (!(level instanceof ServerLevel)) return;
        try {
            NeoForge.EVENT_BUS.post(new SubchannelChangedEvent(getChannelId(), globalPos(), kind, subchannelId, change));
        } catch (Throwable ignored) {}
    }

    /**
     * Copies every subchannel hosted on {@code source} into this emitter, generating fresh UUIDs so
     * receivers subscribed to the source keep pointing at the source — the copy is a structural
     * template, not a join. Per-emitter caps still apply. Returns the number of subchannels created.
     */
    public int cloneSubchannelsFrom(PhotonEmitterBlockEntity source) {
        if (source == null) return 0;
        int n = 0;
        for (ItemSubchannel src : source.itemSubchannels.values()) {
            if (itemSubchannels.size() >= maxPerEmitter(Kind.ITEM)) break;
            String name = uniqueName(Kind.ITEM, src.name());
            if (name.isEmpty()) continue;
            ItemFilter f = new ItemFilter();
            f.copyFrom(src.filter());
            UUID id = UUID.randomUUID();
            itemSubchannels.put(id, new ItemSubchannel(id, name, f, src.color()));
            n++;
        }
        for (FluidSubchannel src : source.fluidSubchannels.values()) {
            if (fluidSubchannels.size() >= maxPerEmitter(Kind.FLUID)) break;
            String name = uniqueName(Kind.FLUID, src.name());
            if (name.isEmpty()) continue;
            FluidFilter f = new FluidFilter();
            f.copyFrom(src.filter());
            UUID id = UUID.randomUUID();
            fluidSubchannels.put(id, new FluidSubchannel(id, name, f, src.color()));
            n++;
        }
        for (GasSubchannel src : source.gasSubchannels.values()) {
            if (gasSubchannels.size() >= maxPerEmitter(Kind.GAS)) break;
            String name = uniqueName(Kind.GAS, src.name());
            if (name.isEmpty()) continue;
            GasFilter f = new GasFilter();
            f.copyFrom(src.filter());
            UUID id = UUID.randomUUID();
            gasSubchannels.put(id, new GasSubchannel(id, name, f, src.color()));
            n++;
        }
        if (n > 0) bumpLocalEdit();
        return n;
    }

    /** Disambiguates a copied name against existing entries by suffixing {@code (2)}, {@code (3)}, etc. */
    private String uniqueName(Kind kind, String base) {
        String b = base == null ? "" : base.trim();
        if (b.isEmpty() || !nameTaken(kind, b, null)) return b;
        for (int i = 2; i < 1000; i++) {
            String suffix = " (" + i + ")";
            String candidate = b.length() + suffix.length() > SubchannelBase.NAME_MAX
                    ? b.substring(0, Math.max(0, SubchannelBase.NAME_MAX - suffix.length())) + suffix
                    : b + suffix;
            if (!nameTaken(kind, candidate, null)) return candidate;
        }
        return "";
    }

    /* ---- routing state used by ResourceRouter ---- */

    /** Cached wrapper around {@link ResourceRouter#evaluate}. */
    public RouteDecision decide(Kind kind, QuantumChannel channel, Holder<?> type) {
        Pipeline p = pipelines.get(kind);
        int channelVersion = routingVersion(channel, kind);
        int localEdit = getLocalEditCount();
        if (channelVersion != p.cachedChannelVersion || localEdit != p.cachedLocalEdit) {
            p.decisions.clear();
            p.cachedChannelVersion = channelVersion;
            p.cachedLocalEdit = localEdit;
        }
        Identifier id = ResourceRouter.idOf(type);
        if (id == null) return RouteDecision.REJECT;
        MinecraftServer server = level instanceof ServerLevel sl ? sl.getServer() : null;
        return p.decisions.computeIfAbsent(id, k -> ResourceRouter.of(kind).evaluate(server, this, channel, type));
    }

    private static int routingVersion(QuantumChannel channel, Kind kind) {
        return switch (kind) {
            case ITEM -> channel.itemConfig().maskVersion();
            case FLUID -> channel.fluidConfig().maskVersion();
            case GAS -> channel.gasConfig().maskVersion();
        };
    }

    public boolean isReceiverCooledDown(Kind kind, long packedPos, long now) {
        HashMap<Long, Long> map = pipelines.get(kind).rejectUntil;
        Long until = map.get(packedPos);
        if (until == null) return false;
        if (until <= now) { map.remove(packedPos); return false; }
        return true;
    }

    public void markReceiverRejected(Kind kind, long packedPos, long now) {
        HashMap<Long, Long> map = pipelines.get(kind).rejectUntil;
        map.put(packedPos, now + REJECT_COOLDOWN_TICKS);
        // Prune expired stragglers so the map doesn't grow without bound on emitters routing
        // through many intermittent receivers.
        if (map.size() > REJECT_MAP_PRUNE_AT) map.values().removeIf(v -> v <= now);
    }

    public void clearReceiverCooldown(Kind kind, long packedPos) {
        pipelines.get(kind).rejectUntil.remove(packedPos);
    }

    /** Amount actually moved this tick: item count, or mB for fluids and chemicals. */
    public void recordRouted(Kind kind, int amount) {
        if (amount > 0) pipelines.get(kind).routedThisTick += amount;
    }

    public void markLoopDetected() {
        if (level != null) lastLoopDetectedTick = level.getGameTime();
    }

    public boolean isLoopWarningActive() {
        if (lastLoopDetectedTick < 0 || level == null) return false;
        return level.getGameTime() - lastLoopDetectedTick < LOOP_FLAG_LINGER_TICKS;
    }

    /* ---- telemetry ---- */

    public int getLastTickThroughput() { return lastTickFE; }
    public int getLastTickItems()      { return pipelines.get(Kind.ITEM).lastTick; }
    public int getLastTickFluids()     { return pipelines.get(Kind.FLUID).lastTick; }
    public int getLastTickGas()        { return pipelines.get(Kind.GAS).lastTick; }

    /** Average per second over the last {@code seconds}: FE/s, items/s, or mB/s depending on {@code mode}. */
    public int getAveragePerSecond(int seconds, ResourceMode mode) {
        return history.averagePerSecond(seconds, mode);
    }

    /** Bucket {@code bucketIdx} of {@code bucketCount} covering the last {@code windowSecs} seconds; 0 = oldest. */
    public int getGraphBucket(int windowSecs, int bucketIdx, int bucketCount, ResourceMode mode) {
        return history.bucket(windowSecs, bucketIdx, bucketCount, mode);
    }

    @Override
    protected ContainerData menuData() { return containerData; }

    /* ---- energy ---- */

    /**
     * External consumer (wireless charging) draws up to {@code want} FE from adjacent sources,
     * inside the caller's transaction and within this tick's forwarding budget.
     */
    public int pullForExternal(int want, TransactionContext tx) {
        if (!(level instanceof ServerLevel sl) || want <= 0) return 0;
        // A redstone-disabled emitter surrenders nothing — not to the channel, not to charging.
        if (!passesRedstoneGate()) return 0;
        int target = Math.min(want, effectiveBudget(ServerConfig.emitterPushRate) - feForwarded.get());
        int collected = 0;
        for (Direction side : Direction.values()) {
            if (collected >= target) break;
            EnergyHandler source = sl.getCapability(Capabilities.Energy.BLOCK, worldPosition.relative(side), side.getOpposite());
            if (source == null) continue;
            collected += source.extract(target - collected, tx);
        }
        if (collected > 0) feForwarded.add(collected, tx);
        return collected;
    }

    public void serverTick(ServerLevel level) {
        // Snapshot this tick's counters for the UI, then roll the per-second history.
        lastTickFE = feForwarded.get();
        feForwarded.reset();
        for (Pipeline p : pipelines.values()) {
            p.lastTick = p.routedThisTick;
            p.routedThisTick = 0;
        }
        history.record(lastTickFE, getLastTickItems(), getLastTickFluids(), getLastTickGas());

        // Roll the per-subchannel windows even when nothing routed, so idle subchannels roll over.
        for (Kind kind : Kind.values()) map(kind).values().forEach(SubchannelBase::tickRoutingWindow);

        // Re-scan adjacency every second, staggered by position hash so emitters don't all refresh
        // on the same tick. neighborChanged only fires on block-state changes, and a neighbour can
        // gain or lose its energy capability silently — without this catch-up the arms go stale.
        if ((level.getGameTime() + Math.floorMod(worldPosition.hashCode(), 20)) % 20 == 0) {
            BlockState state = getBlockState();
            BlockState updated = PhotonShape.refreshConnections(level, worldPosition, state);
            if (updated != state) level.setBlock(worldPosition, updated, 2);
        }

        QuantumChannel channel = boundChannel();
        if (channel == null) return;

        tickPipeline(level, channel, Kind.ITEM);
        tickPipeline(level, channel, Kind.FLUID);
        if (Compat.mekanismLoaded()) tickPipeline(level, channel, Kind.GAS);
        if (passesRedstoneGate()) pullEnergy(level);
    }

    /**
     * Pulls FE from adjacent sources and forwards it through the channel. Each side is one
     * transaction: forward first, then extract exactly what the channel took, so nothing ever has
     * to be handed back (most generators can't accept energy).
     */
    private void pullEnergy(ServerLevel level) {
        int budget = effectiveBudget(ServerConfig.emitterPushRate);
        for (Direction side : Direction.values()) {
            int remaining = budget - feForwarded.get();
            if (remaining <= 0) break;
            EnergyHandler source = level.getCapability(Capabilities.Energy.BLOCK, worldPosition.relative(side), side.getOpposite());
            if (source == null) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int available;
                try (Transaction probe = Transaction.open(tx)) {
                    available = source.extract(remaining, probe);
                }
                if (available <= 0) continue;
                int forwarded = forwardToChannel(available, tx);
                if (forwarded <= 0 || source.extract(forwarded, tx) != forwarded) continue;
                feForwarded.add(forwarded, tx);
                tx.commit();
            }
        }
    }

    /**
     * One step of an item / fluid / chemical pipeline, with an adaptive cadence: scans run every 2
     * ticks while things move and back off to every 20 after a dry scan. A channel routing-version
     * bump (receiver toggled, subscription changed, batch size edited) wakes the fast cadence.
     */
    private void tickPipeline(ServerLevel level, QuantumChannel channel, Kind kind) {
        if (!routingEnabled(kind) || !isActive(kind) || subchannelCount(kind) == 0) return;
        Pipeline p = pipelines.get(kind);
        int channelVersion = routingVersion(channel, kind);
        if (channelVersion != p.lastSeenChannelVersion) {
            p.lastSeenChannelVersion = channelVersion;
            p.slowMode = false;
            p.tickPhase = 0;
        }
        int interval = p.slowMode ? TICK_INTERVAL_SLOW : TICK_INTERVAL_FAST;
        if (++p.tickPhase < interval) return;
        p.tickPhase = 0;

        int moved = ResourceRouter.of(kind).pullFromAdjacent(level, this, channel, batchSize(channel, kind));
        p.slowMode = moved <= 0;
    }

    private static int batchSize(QuantumChannel channel, Kind kind) {
        return switch (kind) {
            case ITEM -> Math.min(channel.itemConfig().batchSize(), ServerConfig.itemsMaxBatch);
            case FLUID -> Math.min(channel.fluidConfig().batchSize(), ServerConfig.fluidsMaxBatch);
            case GAS -> Math.min(channel.gasConfig().batchSize(), ServerConfig.gasesMaxBatch);
        };
    }

    /** Delivers up to {@code amount} FE: receivers first (live consumers), then storage buffers. */
    private int forwardToChannel(int amount, TransactionContext tx) {
        if (amount <= 0 || !(level instanceof ServerLevel server)) return 0;
        QuantumChannel channel = boundChannel();
        if (channel == null) return 0;

        GlobalPos self = GlobalPos.of(server.dimension(), worldPosition);
        List<PhotonReceiverBlockEntity> receivers = new ArrayList<>();
        List<PhotonStorageBlockEntity> storages = new ArrayList<>();
        for (GlobalPos gp : channel.members()) {
            if (gp.equals(self)) continue;
            if (!ServerConfig.allowCrossDimension && !gp.dimension().equals(server.dimension())) continue;
            ServerLevel target = server.getServer().getLevel(gp.dimension());
            if (target == null || !target.isLoaded(gp.pos())) continue;
            BlockEntity be = target.getBlockEntity(gp.pos());
            if (be instanceof PhotonReceiverBlockEntity r) receivers.add(r);
            else if (be instanceof PhotonStorageBlockEntity s) storages.add(s);
        }
        if (receivers.isEmpty() && storages.isEmpty()) return 0;

        // Strict priority order within each group; ties broken by packed BlockPos for stability.
        Comparator<ChannelBoundBlockEntity> byPriority = Comparator
                .comparingInt((ChannelBoundBlockEntity d) -> -d.getPriority())
                .thenComparingLong(d -> d.getBlockPos().asLong());
        receivers.sort(byPriority);
        storages.sort(byPriority);

        int delivered = 0;
        for (PhotonReceiverBlockEntity r : receivers) {
            if (delivered >= amount) break;
            delivered += r.acceptAndForward(amount - delivered, tx);
        }
        for (PhotonStorageBlockEntity s : storages) {
            if (delivered >= amount) break;
            delivered += s.acceptFromChannel(amount - delivered, tx);
        }
        return delivered;
    }

    /* ---- channel membership ---- */

    /**
     * When this emitter leaves its channel — broken, or re-bound elsewhere — the subchannels it hosts
     * cease to exist. Receivers listening to them get their dangling subscriptions swept.
     */
    @Override
    protected void onLeavingChannel(QuantumChannel channel) {
        for (Kind kind : Kind.values()) {
            for (UUID id : List.copyOf(map(kind).keySet())) sweepReceivers(channel, kind, id);
        }
    }

    /** Drops {@code subId} from every loaded receiver on this emitter's channel. */
    public void sweepReceiverSubscriptions(Kind kind, UUID subId) {
        QuantumChannel channel = boundChannel();
        if (channel != null && subId != null) sweepReceivers(channel, kind, subId);
    }

    private void sweepReceivers(QuantumChannel channel, Kind kind, UUID subId) {
        if (!(level instanceof ServerLevel sl)) return;
        for (GlobalPos gp : channel.members()) {
            ServerLevel lvl = sl.getServer().getLevel(gp.dimension());
            if (lvl == null || !lvl.isLoaded(gp.pos())) continue;
            if (lvl.getBlockEntity(gp.pos()) instanceof PhotonReceiverBlockEntity rcv) rcv.removeSubscription(kind, subId);
        }
    }

    /* ---- persistence ---- */

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (voidFilter.isConfigured()) output.store("VoidFilter", CompoundTag.CODEC, voidFilter.save());
        if (fluidVoidFilter.isConfigured()) output.store("FluidVoidFilter", CompoundTag.CODEC, fluidVoidFilter.save());
        if (gasVoidFilter.isConfigured()) output.store("GasVoidFilter", CompoundTag.CODEC, gasVoidFilter.save());
        saveSubchannels(output, "ItemSubchannels", itemSubchannels);
        saveSubchannels(output, "FluidSubchannels", fluidSubchannels);
        saveSubchannels(output, "GasSubchannels", gasSubchannels);
    }

    private static void saveSubchannels(ValueOutput output, String key, Map<UUID, ? extends SubchannelBase<?>> subs) {
        if (subs.isEmpty()) return;
        ValueOutput.TypedOutputList<CompoundTag> list = output.list(key, CompoundTag.CODEC);
        for (SubchannelBase<?> s : subs.values()) list.add(s.save());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Void filters are hard-locked to blacklist mode.
        voidFilter.copyFrom(input.read("VoidFilter", CompoundTag.CODEC).map(ItemFilter::load).orElseGet(ItemFilter::new));
        voidFilter.setWhitelist(false);
        fluidVoidFilter.copyFrom(input.read("FluidVoidFilter", CompoundTag.CODEC).map(FluidFilter::load).orElseGet(FluidFilter::new));
        fluidVoidFilter.setWhitelist(false);
        gasVoidFilter.copyFrom(input.read("GasVoidFilter", CompoundTag.CODEC).map(GasFilter::load).orElseGet(GasFilter::new));
        gasVoidFilter.setWhitelist(false);

        itemSubchannels.clear();
        input.listOrEmpty("ItemSubchannels", CompoundTag.CODEC).forEach(t -> {
            ItemSubchannel s = ItemSubchannel.load(t);
            itemSubchannels.put(s.id(), s);
        });
        fluidSubchannels.clear();
        input.listOrEmpty("FluidSubchannels", CompoundTag.CODEC).forEach(t -> {
            FluidSubchannel s = FluidSubchannel.load(t);
            fluidSubchannels.put(s.id(), s);
        });
        gasSubchannels.clear();
        input.listOrEmpty("GasSubchannels", CompoundTag.CODEC).forEach(t -> {
            GasSubchannel s = GasSubchannel.load(t);
            gasSubchannels.put(s.id(), s);
        });
    }
}
