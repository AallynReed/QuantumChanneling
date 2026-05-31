package com.quantumchanneling.blockentity;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.ServerConfig;
import com.quantumchanneling.menu.PhotonNodeMenu;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.channel.FluidChannelConfig;
import com.quantumchanneling.channel.FluidFilter;
import com.quantumchanneling.channel.FluidSubchannel;
import com.quantumchanneling.channel.FluidTransitHelper;
import com.quantumchanneling.channel.GasChannelConfig;
import com.quantumchanneling.channel.GasFilter;
import com.quantumchanneling.channel.GasSubchannel;
import com.quantumchanneling.channel.ItemChannelConfig;
import com.quantumchanneling.channel.ItemFilter;
import com.quantumchanneling.channel.ItemSubchannel;
import com.quantumchanneling.channel.ItemTransitHelper;
import com.quantumchanneling.channel.QuantumChannel;
import com.quantumchanneling.channel.ResourceMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PhotonEmitterBlockEntity extends ChannelBoundBlockEntity implements MenuProvider {
    private int forwardedThisTick = 0;
    private int lastTickThroughput = 0;

    // Throughput history: one sample per second (sum over 20 ticks), 3600 samples = 60 minutes.
    // Transient — never saved to disk, so reopened worlds start fresh.
    private static final int HISTORY_SECONDS = 3600;
    private final int[] secondsHistory = new int[HISTORY_SECONDS];
    private int historyHead = 0;
    private int historyFilled = 0;
    private int secondAccumulator = 0;
    private int secondTickCounter = 0;

    /**
     * Energy capability — pure Forge Energy ({@link IEnergyStorage}). This is the universal
     * energy API in 1.20.1; every modern mod uses it directly or wraps it:
     * <ul>
     *   <li><b>Mekanism</b> joule-based machines expose FE via an auto-converter on every face.</li>
     *   <li><b>Applied Energistics 2</b> uses internal AE units (1 AE = 2 RF) but its
     *       <i>Energy Acceptor</i> block consumes FE and pushes it into the network. Place an
     *       Energy Acceptor adjacent to a receiver and the AE grid charges normally.</li>
     *   <li><b>Refined Storage</b>, <b>Create</b>, <b>Draconic Evolution</b>, <b>Solar Flux</b>,
     *       <b>Brandon's Core</b> — all native FE consumers, work out of the box.</li>
     *   <li><b>Tesla</b> is dead in 1.20.1. The library survives only as a thin FE wrapper; any
     *       remaining Tesla-using machine exposes FE alongside, so we don't need a separate cap.</li>
     * </ul>
     */
    private final IEnergyStorage energyIO = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int budget = effectiveBudget(ServerConfig.emitterPushRate);
            int cap = Math.max(0, budget - forwardedThisTick);
            int amount = Math.min(maxReceive, cap);
            if (amount <= 0) return 0;
            int forwarded = forwardToChannel(amount, simulate);
            if (!simulate && forwarded > 0) forwardedThisTick += forwarded;
            return forwarded;
        }
        @Override public int extractEnergy(int max, boolean s) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return effectiveBudget(ServerConfig.emitterPushRate); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energyIO);

    /**
     * Per-emitter void filter. Defaults to {@link ItemFilter#ItemFilter(boolean) blacklist mode
     * with no entries} so a fresh emitter voids nothing.
     */
    private final ItemFilter voidFilter = new ItemFilter(false);
    /** Per-emitter fluid void filter — same blacklist-only semantics as the item one. */
    private final FluidFilter fluidVoidFilter = new FluidFilter(false);
    /** Per-emitter gas void filter. */
    private final GasFilter gasVoidFilter = new GasFilter(false);

    /**
     * Subchannels owned by this emitter. Each one is a (name, filter) pair the emitter pushes
     * through; receivers on the same channel subscribe by UUID. Iteration order is the priority
     * "rule book" — the emitter walks the map in order and routes through the first subchannel
     * whose filter accepts the resource. Capped at {@link ItemChannelConfig#MAX_SUBCHANNELS}.
     */
    private final LinkedHashMap<UUID, ItemSubchannel> itemSubchannels = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, FluidSubchannel> fluidSubchannels = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, GasSubchannel> gasSubchannels = new LinkedHashMap<>();

    /**
     * Per-item decision cache. Bounded LRU keyed by Item registry id, wiped whenever the channel's
     * mask version or this BE's local-edit count changes.
     */
    private static final int DECISION_CACHE_MAX = 256;
    private final Map<ResourceLocation, ItemTransitHelper.Decision> decisionCache =
            new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<ResourceLocation, ItemTransitHelper.Decision> e) {
                    return size() > DECISION_CACHE_MAX;
                }
            };
    private int cachedChannelMaskVersion = -1;
    private int cachedLocalEditCount = -1;
    /** Same shape as {@link #decisionCache} but for fluid routing — keyed by Fluid registry id. */
    private final Map<ResourceLocation, FluidTransitHelper.Decision> fluidDecisionCache =
            new java.util.LinkedHashMap<>(32, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<ResourceLocation, FluidTransitHelper.Decision> e) {
                    return size() > DECISION_CACHE_MAX;
                }
            };
    private int cachedFluidChannelMaskVersion = -1;
    private int cachedFluidLocalEditCount = -1;

    public ItemFilter voidFilter() { return voidFilter; }
    public FluidFilter fluidVoidFilter() { return fluidVoidFilter; }
    public GasFilter gasVoidFilter() { return gasVoidFilter; }

    /* ---- per-emitter subchannel CRUD ---- */

    public Collection<ItemSubchannel>  itemSubchannels()  { return Collections.unmodifiableCollection(itemSubchannels.values()); }
    public Collection<FluidSubchannel> fluidSubchannels() { return Collections.unmodifiableCollection(fluidSubchannels.values()); }
    public Collection<GasSubchannel>   gasSubchannels()   { return Collections.unmodifiableCollection(gasSubchannels.values()); }

    public @Nullable ItemSubchannel  itemSubchannel(UUID id)  { return id == null ? null : itemSubchannels.get(id); }
    public @Nullable FluidSubchannel fluidSubchannel(UUID id) { return id == null ? null : fluidSubchannels.get(id); }
    public @Nullable GasSubchannel   gasSubchannel(UUID id)   { return id == null ? null : gasSubchannels.get(id); }

    public int itemSubchannelCount()  { return itemSubchannels.size(); }
    public int fluidSubchannelCount() { return fluidSubchannels.size(); }
    public int gasSubchannelCount()   { return gasSubchannels.size(); }

    /**
     * Returns the new subchannel's UUID, or {@code null} when validation failed: empty name,
     * duplicate name on this emitter, the per-emitter cap was hit, or the channel-wide cap
     * (counted across every loaded emitter on the same channel) was hit.
     */
    public @Nullable UUID createItemSubchannel(String name) {
        String n = trim(name);
        if (n.isEmpty()) return null;
        if (itemSubchannels.size() >= com.quantumchanneling.ServerConfig.itemsMaxSubsPerEmitter) return null;
        if (countChannelSubchannels(0) >= com.quantumchanneling.ServerConfig.itemsMaxSubsPerChannel) return null;
        for (ItemSubchannel s : itemSubchannels.values()) if (s.name().equalsIgnoreCase(n)) return null;
        UUID id = UUID.randomUUID();
        itemSubchannels.put(id, new ItemSubchannel(id, n));
        bumpLocalEdit();
        return id;
    }

    public @Nullable UUID createFluidSubchannel(String name) {
        String n = trim(name);
        if (n.isEmpty()) return null;
        if (fluidSubchannels.size() >= com.quantumchanneling.ServerConfig.fluidsMaxSubsPerEmitter) return null;
        if (countChannelSubchannels(1) >= com.quantumchanneling.ServerConfig.fluidsMaxSubsPerChannel) return null;
        for (FluidSubchannel s : fluidSubchannels.values()) if (s.name().equalsIgnoreCase(n)) return null;
        UUID id = UUID.randomUUID();
        fluidSubchannels.put(id, new FluidSubchannel(id, n));
        bumpLocalEdit();
        return id;
    }

    public @Nullable UUID createGasSubchannel(String name) {
        String n = trim(name);
        if (n.isEmpty()) return null;
        if (gasSubchannels.size() >= com.quantumchanneling.ServerConfig.gasesMaxSubsPerEmitter) return null;
        if (countChannelSubchannels(2) >= com.quantumchanneling.ServerConfig.gasesMaxSubsPerChannel) return null;
        for (GasSubchannel s : gasSubchannels.values()) if (s.name().equalsIgnoreCase(n)) return null;
        UUID id = UUID.randomUUID();
        gasSubchannels.put(id, new GasSubchannel(id, n));
        bumpLocalEdit();
        return id;
    }

    /**
     * Sum of subchannels of the given type ({@code 0 = items, 1 = fluids, 2 = gas}) across every
     * currently-loaded emitter on this channel, INCLUDING this one. Unloaded emitters are ignored
     * by necessity — their subchannel collections only live in chunk NBT and aren't readable
     * without paging them in.
     */
    private int countChannelSubchannels(int kind) {
        if (!(level instanceof ServerLevel sl)) return localSubCount(kind);
        UUID channelId = getChannelId();
        if (channelId == null) return localSubCount(kind);
        QuantumChannel ch = ChannelData.get(sl.getServer()).getChannel(channelId);
        if (ch == null) return localSubCount(kind);
        int total = 0;
        for (GlobalPos gp : ch.members()) {
            ServerLevel lvl = sl.getServer().getLevel(gp.dimension());
            if (lvl == null || !lvl.isLoaded(gp.pos())) continue;
            BlockEntity be = lvl.getBlockEntity(gp.pos());
            if (!(be instanceof PhotonEmitterBlockEntity em)) continue;
            total += switch (kind) {
                case 0 -> em.itemSubchannelCount();
                case 1 -> em.fluidSubchannelCount();
                case 2 -> em.gasSubchannelCount();
                default -> 0;
            };
        }
        return total;
    }

    private int localSubCount(int kind) {
        return switch (kind) {
            case 0 -> itemSubchannelCount();
            case 1 -> fluidSubchannelCount();
            case 2 -> gasSubchannelCount();
            default -> 0;
        };
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }

    /**
     * Removes a subchannel. The caller is responsible for telling subscribed receivers to drop
     * the dangling UUID — see {@link #sweepReceiverSubscriptionsForItemSubchannel(UUID)} and the
     * matching helpers below.
     */
    public boolean deleteItemSubchannel(UUID id) {
        if (id == null || itemSubchannels.remove(id) == null) return false;
        bumpLocalEdit();
        return true;
    }
    public boolean deleteFluidSubchannel(UUID id) {
        if (id == null || fluidSubchannels.remove(id) == null) return false;
        bumpLocalEdit();
        return true;
    }
    public boolean deleteGasSubchannel(UUID id) {
        if (id == null || gasSubchannels.remove(id) == null) return false;
        bumpLocalEdit();
        return true;
    }

    public boolean renameItemSubchannel(UUID id, String name) {
        ItemSubchannel s = itemSubchannels.get(id);
        if (s == null) return false;
        String n = trim(name);
        if (n.isEmpty()) return false;
        for (ItemSubchannel other : itemSubchannels.values()) {
            if (other != s && other.name().equalsIgnoreCase(n)) return false;
        }
        s.setName(n); bumpLocalEdit(); return true;
    }
    public boolean renameFluidSubchannel(UUID id, String name) {
        FluidSubchannel s = fluidSubchannels.get(id);
        if (s == null) return false;
        String n = trim(name);
        if (n.isEmpty()) return false;
        for (FluidSubchannel other : fluidSubchannels.values()) {
            if (other != s && other.name().equalsIgnoreCase(n)) return false;
        }
        s.setName(n); bumpLocalEdit(); return true;
    }
    public boolean renameGasSubchannel(UUID id, String name) {
        GasSubchannel s = gasSubchannels.get(id);
        if (s == null) return false;
        String n = trim(name);
        if (n.isEmpty()) return false;
        for (GasSubchannel other : gasSubchannels.values()) {
            if (other != s && other.name().equalsIgnoreCase(n)) return false;
        }
        s.setName(n); bumpLocalEdit(); return true;
    }

    public boolean setItemSubchannelFilterMode(UUID id, boolean whitelist) {
        ItemSubchannel s = itemSubchannels.get(id);
        if (s == null || s.filter().isWhitelist() == whitelist) return false;
        s.filter().setWhitelist(whitelist); bumpLocalEdit(); return true;
    }
    public boolean setFluidSubchannelFilterMode(UUID id, boolean whitelist) {
        FluidSubchannel s = fluidSubchannels.get(id);
        if (s == null || s.filter().isWhitelist() == whitelist) return false;
        s.filter().setWhitelist(whitelist); bumpLocalEdit(); return true;
    }
    public boolean setGasSubchannelFilterMode(UUID id, boolean whitelist) {
        GasSubchannel s = gasSubchannels.get(id);
        if (s == null || s.filter().isWhitelist() == whitelist) return false;
        s.filter().setWhitelist(whitelist); bumpLocalEdit(); return true;
    }

    public boolean addItemSubchannelEntry(UUID id, ResourceLocation entry) {
        ItemSubchannel s = itemSubchannels.get(id);
        if (s == null || !s.filter().add(entry)) return false;
        bumpLocalEdit(); return true;
    }
    public boolean removeItemSubchannelEntry(UUID id, ResourceLocation entry) {
        ItemSubchannel s = itemSubchannels.get(id);
        if (s == null || !s.filter().remove(entry)) return false;
        bumpLocalEdit(); return true;
    }
    public boolean addFluidSubchannelEntry(UUID id, ResourceLocation entry) {
        FluidSubchannel s = fluidSubchannels.get(id);
        if (s == null || !s.filter().add(entry)) return false;
        bumpLocalEdit(); return true;
    }
    public boolean removeFluidSubchannelEntry(UUID id, ResourceLocation entry) {
        FluidSubchannel s = fluidSubchannels.get(id);
        if (s == null || !s.filter().remove(entry)) return false;
        bumpLocalEdit(); return true;
    }
    public boolean addGasSubchannelEntry(UUID id, ResourceLocation entry) {
        GasSubchannel s = gasSubchannels.get(id);
        if (s == null || !s.filter().add(entry)) return false;
        bumpLocalEdit(); return true;
    }
    public boolean removeGasSubchannelEntry(UUID id, ResourceLocation entry) {
        GasSubchannel s = gasSubchannels.get(id);
        if (s == null || !s.filter().remove(entry)) return false;
        bumpLocalEdit(); return true;
    }

    /** Moves a subchannel up ({@code dir<0}) or down ({@code dir>0}) in iteration order. */
    public boolean moveItemSubchannel(UUID id, int dir)  { return reorder(itemSubchannels, id, dir); }
    public boolean moveFluidSubchannel(UUID id, int dir) { return reorder(fluidSubchannels, id, dir); }
    public boolean moveGasSubchannel(UUID id, int dir)   { return reorder(gasSubchannels, id, dir); }

    private <V> boolean reorder(LinkedHashMap<UUID, V> map, UUID id, int dir) {
        if (id == null || dir == 0 || !map.containsKey(id)) return false;
        List<UUID> keys = new ArrayList<>(map.keySet());
        int idx = keys.indexOf(id);
        int target = idx + (dir < 0 ? -1 : 1);
        if (target < 0 || target >= keys.size()) return false;
        UUID swap = keys.get(target);
        keys.set(target, id);
        keys.set(idx, swap);
        LinkedHashMap<UUID, V> rebuilt = new LinkedHashMap<>();
        for (UUID k : keys) rebuilt.put(k, map.get(k));
        map.clear();
        map.putAll(rebuilt);
        bumpLocalEdit();
        return true;
    }

    /** Cache-respecting wrapper around {@link ItemTransitHelper#evaluate}. */
    public ItemTransitHelper.Decision decide(QuantumChannel channel, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemTransitHelper.Decision.REJECT;
        int channelMV = channel == null ? -1 : channel.itemConfig().maskVersion();
        int localMV = getLocalEditCount();
        if (channelMV != cachedChannelMaskVersion || localMV != cachedLocalEditCount) {
            decisionCache.clear();
            cachedChannelMaskVersion = channelMV;
            cachedLocalEditCount = localMV;
        }
        ResourceLocation id = ItemTransitHelper.idOf(stack);
        if (id == null) return ItemTransitHelper.Decision.REJECT;
        MinecraftServer server = (level instanceof ServerLevel sl) ? sl.getServer() : null;
        return decisionCache.computeIfAbsent(id,
                k -> ItemTransitHelper.evaluate(server, this, channel, stack));
    }

    /** Fluid analog of {@link #decide(QuantumChannel, ItemStack)}. */
    public FluidTransitHelper.Decision decideFluid(QuantumChannel channel, FluidStack stack) {
        if (stack == null || stack.isEmpty()) return FluidTransitHelper.Decision.REJECT;
        int channelMV = channel == null ? -1 : channel.fluidConfig().maskVersion();
        int localMV = getLocalEditCount();
        if (channelMV != cachedFluidChannelMaskVersion || localMV != cachedFluidLocalEditCount) {
            fluidDecisionCache.clear();
            cachedFluidChannelMaskVersion = channelMV;
            cachedFluidLocalEditCount = localMV;
        }
        ResourceLocation id = FluidTransitHelper.idOf(stack);
        if (id == null) return FluidTransitHelper.Decision.REJECT;
        MinecraftServer server = (level instanceof ServerLevel sl) ? sl.getServer() : null;
        return fluidDecisionCache.computeIfAbsent(id,
                k -> FluidTransitHelper.evaluate(server, this, channel, stack));
    }

    /**
     * Virtual single-slot item handler. AE2 importers, RS exporters, hoppers, and anything else
     * pushing items via {@link IItemHandler#insertItem} hit this. Inserts run through the same
     * void + subscribed-subchannels + direct-push pipeline as the active tick.
     */
    private final IItemHandler itemIO = new IItemHandler() {
        @Override public int getSlots() { return 1; }
        @Override public @NotNull ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return true; }
        @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return stack;
            if (!com.quantumchanneling.ServerConfig.itemsRoutingEnabled) return stack;
            if (!isItemsEnabled()) return stack;
            if (!(level instanceof ServerLevel sl)) return stack;
            UUID channelId = getChannelId();
            if (channelId == null) return stack;
            QuantumChannel net = ChannelData.get(sl.getServer()).getChannel(channelId);
            if (net == null) return stack;
            return ItemTransitHelper.route(sl, PhotonEmitterBlockEntity.this, net, stack, simulate);
        }
    };
    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> itemIO);

    /**
     * Virtual fluid handler. Any neighbor that pushes fluids via {@link IFluidHandler#fill}
     * (Mekanism pipes, Create, vanilla buckets via dispensers, etc.) hits this. Pushes go through
     * the same void + subscribed-subchannels + direct-push pipeline as the active fluid tick.
     */
    private final IFluidHandler fluidIO = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public @NotNull FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int tank, @NotNull FluidStack stack) { return true; }
        @Override public @NotNull FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
        @Override public @NotNull FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource == null || resource.isEmpty()) return 0;
            if (!com.quantumchanneling.ServerConfig.fluidsRoutingEnabled) return 0;
            if (!isFluidsEnabled()) return 0;
            if (!(level instanceof ServerLevel sl)) return 0;
            UUID channelId = getChannelId();
            if (channelId == null) return 0;
            QuantumChannel net = ChannelData.get(sl.getServer()).getChannel(channelId);
            if (net == null) return 0;
            FluidStack leftover = FluidTransitHelper.route(sl, PhotonEmitterBlockEntity.this, net, resource, action);
            return resource.getAmount() - leftover.getAmount();
        }
    };
    private LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> fluidIO);

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case PhotonNodeMenu.DATA_THROUGHPUT -> lastTickThroughput;
                case PhotonNodeMenu.DATA_CHUNK_LOADED -> isChunkLoadForced() ? 1 : 0;
                case PhotonNodeMenu.DATA_CHANNEL_BOUND -> getChannelId() != null ? 1 : 0;
                case PhotonNodeMenu.DATA_THROUGHPUT_CAP -> getThroughputCap();
                case PhotonNodeMenu.DATA_PRIORITY -> getPriority();
                case PhotonNodeMenu.DATA_SURGE -> isSurgeMode() ? 1 : 0;
                case PhotonNodeMenu.DATA_AVG_1MIN -> getAverageFEPerSecond(60);
                case PhotonNodeMenu.DATA_AVG_5MIN -> getAverageFEPerSecond(300);
                case PhotonNodeMenu.DATA_AVG_10MIN -> getAverageFEPerSecond(600);
                default -> resolveGraphBucket(index);
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return PhotonNodeMenu.DATA_SIZE; }
    };

    public PhotonEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(QuantumChanneling.PHOTON_EMITTER_BE.get(), pos, state);
    }

    public int getLastTickThroughput() { return lastTickThroughput; }

    /** Stagger the periodic connection refresh by position so emitters don't all fire on the same tick. */
    private int connectionRefreshPhase() { return Math.floorMod(getBlockPos().hashCode(), 20); }

    /** Maps a ContainerData slot index in the graph range to the right window's bucket. */
    private int resolveGraphBucket(int slotIndex) {
        int n = PhotonNodeMenu.GRAPH_BUCKETS;
        if (slotIndex >= PhotonNodeMenu.DATA_GRAPH_1M_BASE && slotIndex < PhotonNodeMenu.DATA_GRAPH_1M_BASE + n) {
            return getGraphBucket(60, slotIndex - PhotonNodeMenu.DATA_GRAPH_1M_BASE, n);
        }
        if (slotIndex >= PhotonNodeMenu.DATA_GRAPH_5M_BASE && slotIndex < PhotonNodeMenu.DATA_GRAPH_5M_BASE + n) {
            return getGraphBucket(300, slotIndex - PhotonNodeMenu.DATA_GRAPH_5M_BASE, n);
        }
        if (slotIndex >= PhotonNodeMenu.DATA_GRAPH_10M_BASE && slotIndex < PhotonNodeMenu.DATA_GRAPH_10M_BASE + n) {
            return getGraphBucket(600, slotIndex - PhotonNodeMenu.DATA_GRAPH_10M_BASE, n);
        }
        return 0;
    }

    /** Accumulates one tick's throughput; commits a sample every 20 ticks (= 1 s). */
    private void recordSample(int tickAmount) {
        secondAccumulator += tickAmount;
        secondTickCounter++;
        if (secondTickCounter >= 20) {
            secondsHistory[historyHead] = secondAccumulator;
            historyHead = (historyHead + 1) % HISTORY_SECONDS;
            if (historyFilled < HISTORY_SECONDS) historyFilled++;
            secondAccumulator = 0;
            secondTickCounter = 0;
        }
    }

    /** Average FE/second over the last {@code seconds} (capped to what we've actually recorded). */
    public int getAverageFEPerSecond(int seconds) {
        if (historyFilled == 0 || seconds <= 0) return 0;
        int count = Math.min(seconds, historyFilled);
        long sum = 0;
        for (int i = 0; i < count; i++) {
            int idx = (historyHead - 1 - i + HISTORY_SECONDS) % HISTORY_SECONDS;
            sum += secondsHistory[idx];
        }
        return (int) (sum / count);
    }

    /**
     * One bucket out of {@code bucketCount} buckets that together cover {@code windowSecs} seconds
     * of history. Bucket 0 = oldest, bucketCount-1 = newest. Average FE/s within the bucket.
     */
    public int getGraphBucket(int windowSecs, int bucketIdx, int bucketCount) {
        if (historyFilled == 0 || windowSecs <= 0 || bucketCount <= 0) return 0;
        int samplesPerBucket = Math.max(1, windowSecs / bucketCount);
        int offset = (bucketCount - 1 - bucketIdx) * samplesPerBucket;
        long sum = 0;
        int count = 0;
        for (int i = 0; i < samplesPerBucket; i++) {
            int sampleOffset = offset + i;
            if (sampleOffset >= historyFilled) continue;
            int idx = (historyHead - 1 - sampleOffset + HISTORY_SECONDS) % HISTORY_SECONDS;
            sum += secondsHistory[idx];
            count++;
        }
        return count == 0 ? 0 : (int) (sum / count);
    }

    @Override
    public Component getDisplayName() { return getBlockState().getBlock().getName(); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
        return new PhotonNodeMenu(id, inv, getBlockPos(), containerData,
                getChannelId(), resolveChannelName(), resolveChannelOwner(), getCustomName(), 0L);
    }

    public String resolveChannelName() {
        UUID id = getChannelId();
        if (id == null || !(level instanceof ServerLevel sl)) return "";
        QuantumChannel net = ChannelData.get(sl.getServer()).getChannel(id);
        return net == null ? "" : net.name();
    }

    public String resolveChannelOwner() {
        UUID id = getChannelId();
        if (id == null || !(level instanceof ServerLevel sl)) return "";
        QuantumChannel net = ChannelData.get(sl.getServer()).getChannel(id);
        return net == null ? "" : net.ownerName();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        // All-in-one transport: every device exposes every resource cap simultaneously.
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        if (cap == ForgeCapabilities.FLUID_HANDLER) return fluidCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        itemCap.invalidate();
        fluidCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCap = LazyOptional.of(() -> energyIO);
        itemCap = LazyOptional.of(() -> itemIO);
        fluidCap = LazyOptional.of(() -> fluidIO);
    }

    /**
     * External consumer (e.g. the charging system) requests up to {@code want} FE. We pull from
     * adjacent canExtract sources within the same per-tick budget we use for forwarding.
     */
    public int pullForExternal(int want) {
        if (!(level instanceof ServerLevel) || want <= 0) return 0;
        int budget = effectiveBudget(ServerConfig.emitterPushRate) - forwardedThisTick;
        if (budget <= 0) return 0;
        int target = Math.min(want, budget);
        int collected = 0;
        for (Direction side : Direction.values()) {
            if (collected >= target) break;
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(side));
            if (neighbor == null) continue;
            IEnergyStorage source = neighbor.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).orElse(null);
            if (source == null || !source.canExtract()) continue;
            int taken = source.extractEnergy(target - collected, false);
            if (taken > 0) {
                collected += taken;
                forwardedThisTick += taken;
            }
        }
        return collected;
    }

    public void serverTick(ServerLevel level) {
        lastTickThroughput = forwardedThisTick;
        recordSample(lastTickThroughput);
        forwardedThisTick = 0;

        // Re-scan adjacency every second, but stagger across emitters by position hash so all of
        // them don't refresh on the same tick. neighborChanged only fires on block-state changes,
        // and a neighbor BE can acquire/lose its IEnergyStorage cap silently (first-tick init,
        // mod-side cap invalidation). Without this catch-up the arms go stale.
        if (((level.getGameTime() + connectionRefreshPhase()) % 20) == 0) {
            var pos = getBlockPos();
            var state = getBlockState();
            var updated = com.quantumchanneling.block.PhotonShape.refreshConnections(
                    level, pos, state, com.quantumchanneling.block.PhotonShape.ConnectionMode.SOURCES);
            if (updated != state) level.setBlock(pos, updated, 2);
        }

        if (getChannelId() == null) return;

        // Energy + items + fluids all run side-by-side. Items + fluids only act every other tick.
        tickItemsMode(level);
        tickFluidsMode(level);
        // Skip the gas/heat tick entirely when Mekanism isn't on the server — no stack frame,
        // no static-field reads inside the method body, nothing.
        if (com.quantumchanneling.client.Compat.mekanismLoaded()) tickGasAndHeat(level);

        int budget = effectiveBudget(ServerConfig.emitterPushRate);
        for (Direction side : Direction.values()) {
            int rem = budget - forwardedThisTick;
            if (rem <= 0) break;
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(side));
            if (neighbor == null) continue;
            IEnergyStorage source = neighbor.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).orElse(null);
            if (source == null || !source.canExtract()) continue;
            int simulated = source.extractEnergy(rem, true);
            if (simulated <= 0) continue;
            int forwarded = forwardToChannel(simulated, false);
            if (forwarded <= 0) continue;
            source.extractEnergy(forwarded, false);
            forwardedThisTick += forwarded;
        }
    }

    /**
     * One step of the items-mode tick. Rate-limited to roughly "1 batch / 2 ticks" by only doing
     * work on even ticks. Each cycle scans the 6 neighbours for an IItemHandler with an item the
     * emitter would route, extracts up to {@link com.quantumchanneling.channel.ItemChannelConfig#batchSize()}
     * items, and pushes them straight to a subscribed receiver's adjacent inventory.
     */
    private void tickItemsMode(ServerLevel level) {
        if ((level.getGameTime() & 1L) != 0L) return;
        // Cheap gates first — server-side master switch, then per-device opt-in, then
        // owns-anything-routable. Each is a static field read or a single int comparison.
        if (!com.quantumchanneling.ServerConfig.itemsRoutingEnabled) return;
        if (!isItemsEnabled() || itemSubchannelCount() == 0) return;
        UUID channelId = getChannelId();
        if (channelId == null) return;
        QuantumChannel net = ChannelData.get(level.getServer()).getChannel(channelId);
        if (net == null) return;
        // Clamp the channel-set batch to the server-enforced ceiling. Lower the cap mid-game and
        // every emitter picks it up on the next tick — no migration needed.
        int batch = Math.min(net.itemConfig().batchSize(), com.quantumchanneling.ServerConfig.itemsMaxBatch);
        int moved = ItemTransitHelper.pullFromAdjacentAndRoute(level, this, net, batch);
        if (moved > 0) forwardedThisTick = moved;
    }

    /**
     * Mekanism-gated tick: gas active-pull + heat thermal-wire averaging. Both routines are in
     * compat.mekanism so this BE never references their classes when Mekanism isn't loaded — the
     * static call is the JVM load gate, exactly like the cap attacher registration.
     */
    private void tickGasAndHeat(ServerLevel level) {
        // Caller already gates on Compat.mekanismLoaded() — this method should never run otherwise.
        if ((level.getGameTime() & 1L) != 0L) return;
        if (!com.quantumchanneling.ServerConfig.gasesRoutingEnabled) return;
        if (!isGasEnabled() || gasSubchannelCount() == 0) return;
        UUID channelId = getChannelId();
        if (channelId == null) return;
        QuantumChannel net = ChannelData.get(level.getServer()).getChannel(channelId);
        if (net == null) return;
        com.quantumchanneling.compat.mekanism.GasIntegration.pullAndRoute(level, this, net);
        // Heat routing is intentionally disabled — the HEAT side-tab is hidden in the UI and the
        // thermal-wire model needs more design work. HeatIntegration + HeatChannelConfig stay
        // intact so existing channels round-trip cleanly; we just never invoke the tick.
        // The matching ServerConfig.heatRoutingEnabled flag is there for when the pipeline ships.
    }

    /** Fluid analog of {@link #tickItemsMode}. mB instead of stacks; same every-other-tick cadence. */
    private void tickFluidsMode(ServerLevel level) {
        if ((level.getGameTime() & 1L) != 0L) return;
        if (!com.quantumchanneling.ServerConfig.fluidsRoutingEnabled) return;
        if (!isFluidsEnabled() || fluidSubchannelCount() == 0) return;
        UUID channelId = getChannelId();
        if (channelId == null) return;
        QuantumChannel net = ChannelData.get(level.getServer()).getChannel(channelId);
        if (net == null) return;
        int batch = Math.min(net.fluidConfig().batchSize(), com.quantumchanneling.ServerConfig.fluidsMaxBatch);
        FluidTransitHelper.pullFromAdjacentAndRoute(level, this, net, batch);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!voidFilter.items().isEmpty() || voidFilter.isWhitelist()) {
            tag.put("VoidFilter", voidFilter.save());
        }
        if (!fluidVoidFilter.fluids().isEmpty() || fluidVoidFilter.isWhitelist()) {
            tag.put("FluidVoidFilter", fluidVoidFilter.save());
        }
        if (!gasVoidFilter.gases().isEmpty() || gasVoidFilter.isWhitelist()) {
            tag.put("GasVoidFilter", gasVoidFilter.save());
        }
        if (!itemSubchannels.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (ItemSubchannel s : itemSubchannels.values()) list.add(s.save());
            tag.put("ItemSubchannels", list);
        }
        if (!fluidSubchannels.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (FluidSubchannel s : fluidSubchannels.values()) list.add(s.save());
            tag.put("FluidSubchannels", list);
        }
        if (!gasSubchannels.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (GasSubchannel s : gasSubchannels.values()) list.add(s.save());
            tag.put("GasSubchannels", list);
        }
    }

    /**
     * When this emitter leaves its channel — either because it was broken or because the player
     * re-bound it elsewhere — the subchannels it hosts cease to exist. Receivers that were
     * listening to them need their dangling subscriptions swept so the UI doesn't keep showing
     * ghost entries.
     */
    @Override
    protected void onLeavingChannel(QuantumChannel channel) {
        if (!(level instanceof ServerLevel sl) || channel == null) return;
        sweepReceiversOnChannel(channel, sl,
                itemSubchannels.keySet(), fluidSubchannels.keySet(), gasSubchannels.keySet());
    }

    private static void sweepReceiversOnChannel(QuantumChannel channel, ServerLevel sl,
                                                Set<UUID> itemIds, Set<UUID> fluidIds, Set<UUID> gasIds) {
        if (itemIds.isEmpty() && fluidIds.isEmpty() && gasIds.isEmpty()) return;
        for (GlobalPos gp : channel.members()) {
            ServerLevel lvl = sl.getServer().getLevel(gp.dimension());
            if (lvl == null || !lvl.isLoaded(gp.pos())) continue;
            BlockEntity be = lvl.getBlockEntity(gp.pos());
            if (!(be instanceof PhotonReceiverBlockEntity rcv)) continue;
            for (UUID id : itemIds)  rcv.removeSubscribedSubchannel(id);
            for (UUID id : fluidIds) rcv.removeSubscribedFluidSubchannel(id);
            for (UUID id : gasIds)   rcv.removeSubscribedGasSubchannel(id);
        }
    }

    /** Called by the per-resource subchannel-delete handlers to clean up dangling receivers. */
    public void sweepReceiverSubscriptionsForItemSubchannel(UUID subId)  { sweepOne(subId, true, false, false); }
    public void sweepReceiverSubscriptionsForFluidSubchannel(UUID subId) { sweepOne(subId, false, true, false); }
    public void sweepReceiverSubscriptionsForGasSubchannel(UUID subId)   { sweepOne(subId, false, false, true); }

    private void sweepOne(UUID subId, boolean items, boolean fluids, boolean gas) {
        if (subId == null) return;
        if (!(level instanceof ServerLevel sl)) return;
        UUID channelId = getChannelId();
        if (channelId == null) return;
        QuantumChannel ch = ChannelData.get(sl.getServer()).getChannel(channelId);
        if (ch == null) return;
        for (GlobalPos gp : ch.members()) {
            ServerLevel lvl = sl.getServer().getLevel(gp.dimension());
            if (lvl == null || !lvl.isLoaded(gp.pos())) continue;
            BlockEntity be = lvl.getBlockEntity(gp.pos());
            if (!(be instanceof PhotonReceiverBlockEntity rcv)) continue;
            if (items)  rcv.removeSubscribedSubchannel(subId);
            if (fluids) rcv.removeSubscribedFluidSubchannel(subId);
            if (gas)    rcv.removeSubscribedGasSubchannel(subId);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("FluidVoidFilter", Tag.TAG_COMPOUND)) {
            fluidVoidFilter.copyFrom(FluidFilter.load(tag.getCompound("FluidVoidFilter")));
            fluidVoidFilter.setWhitelist(false);   // hard-lock to blacklist
        }
        if (tag.contains("GasVoidFilter", Tag.TAG_COMPOUND)) {
            gasVoidFilter.copyFrom(GasFilter.load(tag.getCompound("GasVoidFilter")));
            gasVoidFilter.setWhitelist(false);
        }
        if (tag.contains("VoidFilter", Tag.TAG_COMPOUND)) {
            voidFilter.copyFrom(ItemFilter.load(tag.getCompound("VoidFilter")));
        }
        itemSubchannels.clear();
        if (tag.contains("ItemSubchannels", Tag.TAG_LIST)) {
            var list = tag.getList("ItemSubchannels", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ItemSubchannel s = ItemSubchannel.load(list.getCompound(i));
                itemSubchannels.put(s.id(), s);
            }
        }
        fluidSubchannels.clear();
        if (tag.contains("FluidSubchannels", Tag.TAG_LIST)) {
            var list = tag.getList("FluidSubchannels", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                FluidSubchannel s = FluidSubchannel.load(list.getCompound(i));
                fluidSubchannels.put(s.id(), s);
            }
        }
        gasSubchannels.clear();
        if (tag.contains("GasSubchannels", Tag.TAG_LIST)) {
            var list = tag.getList("GasSubchannels", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                GasSubchannel s = GasSubchannel.load(list.getCompound(i));
                gasSubchannels.put(s.id(), s);
            }
        }
    }

    private int forwardToChannel(int amount, boolean simulate) {
        UUID channel = getChannelId();
        if (channel == null || amount <= 0) return 0;
        if (!(level instanceof ServerLevel server)) return 0;
        MinecraftServer mcServer = server.getServer();
        ChannelData data = ChannelData.get(mcServer);
        Set<GlobalPos> members = data.getMembers(channel);
        if (members.isEmpty()) return 0;

        GlobalPos self = GlobalPos.of(server.dimension(), worldPosition);
        List<PhotonReceiverBlockEntity> receivers = new ArrayList<>();
        List<PhotonStorageBlockEntity> storages = new ArrayList<>();
        for (GlobalPos gp : members) {
            if (gp.equals(self)) continue;
            if (!com.quantumchanneling.ServerConfig.allowCrossDimension
                    && !gp.dimension().equals(server.dimension())) continue;
            ServerLevel target = mcServer.getLevel(gp.dimension());
            if (target == null || !target.isLoaded(gp.pos())) continue;
            BlockEntity be = target.getBlockEntity(gp.pos());
            if (be instanceof PhotonReceiverBlockEntity r) receivers.add(r);
            else if (be instanceof PhotonStorageBlockEntity s) storages.add(s);
        }
        if (receivers.isEmpty() && storages.isEmpty()) return 0;

        // Strict priority order within each group; ties broken by packed BlockPos for stability.
        var byPriority = (java.util.Comparator<ChannelBoundBlockEntity>) (a, b) -> {
            int cmp = Integer.compare(b.getPriority(), a.getPriority());
            if (cmp != 0) return cmp;
            return Long.compare(a.getBlockPos().asLong(), b.getBlockPos().asLong());
        };
        receivers.sort(byPriority);
        storages.sort(byPriority);

        int remaining = amount;
        int delivered = 0;
        // 1) Receivers — push energy through to live consumers.
        for (PhotonReceiverBlockEntity r : receivers) {
            if (remaining <= 0) break;
            int sent = r.acceptAndForward(remaining, simulate);
            if (sent > 0) { delivered += sent; remaining -= sent; }
        }
        // 2) Storage — soak up any leftover into buffers for later.
        for (PhotonStorageBlockEntity s : storages) {
            if (remaining <= 0) break;
            int sent = s.acceptFromChannel(remaining, simulate);
            if (sent > 0) { delivered += sent; remaining -= sent; }
        }
        return delivered;
    }
}
