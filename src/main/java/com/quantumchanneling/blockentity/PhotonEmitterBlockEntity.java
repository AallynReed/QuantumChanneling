package com.quantumchanneling.blockentity;

import com.quantumchanneling.Config;
import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.menu.PhotonNodeMenu;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.channel.FluidFilter;
import com.quantumchanneling.channel.FluidTransitHelper;
import com.quantumchanneling.channel.GasFilter;
import com.quantumchanneling.channel.ItemFilter;
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
import java.util.HashMap;
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
            int budget = effectiveBudget(Config.emitterPushRate);
            int cap = Math.max(0, budget - forwardedThisTick);
            int amount = Math.min(maxReceive, cap);
            if (amount <= 0) return 0;
            int forwarded = forwardToChannel(amount, simulate);
            if (!simulate && forwarded > 0) forwardedThisTick += forwarded;
            return forwarded;
        }
        @Override public int extractEnergy(int max, boolean s) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return effectiveBudget(Config.emitterPushRate); }
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
     * Per-item decision cache. Wiped whenever {@link QuantumChannel#itemConfig()#maskVersion()} or
     * this BE's {@link #getLocalEditCount() local edit count} changes.
     */
    private final Map<ResourceLocation, ItemTransitHelper.Decision> decisionCache = new HashMap<>();
    private int cachedChannelMaskVersion = -1;
    private int cachedLocalEditCount = -1;
    /** Same shape as {@link #decisionCache} but for fluid routing — keyed by Fluid registry id. */
    private final Map<ResourceLocation, FluidTransitHelper.Decision> fluidDecisionCache = new HashMap<>();
    private int cachedFluidChannelMaskVersion = -1;
    private int cachedFluidLocalEditCount = -1;

    public ItemFilter voidFilter() { return voidFilter; }
    public FluidFilter fluidVoidFilter() { return fluidVoidFilter; }
    public GasFilter gasVoidFilter() { return gasVoidFilter; }

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
        int budget = effectiveBudget(Config.emitterPushRate) - forwardedThisTick;
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

        // Re-scan adjacency every second. neighborChanged only fires on block-state changes, but a
        // neighbor BE can acquire/lose its IEnergyStorage capability at any time (e.g. first-tick
        // initialization, mod-side cap invalidation). Without this catch-up the connector arms stay
        // stale and never appear toward genuinely-connected FE/RF devices from other mods.
        if ((level.getGameTime() % 20) == 0) {
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
        tickGasAndHeat(level);

        int budget = effectiveBudget(Config.emitterPushRate);
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
        UUID channelId = getChannelId();
        if (channelId == null) return;
        QuantumChannel net = ChannelData.get(level.getServer()).getChannel(channelId);
        if (net == null) return;
        int batch = net.itemConfig().batchSize();
        int moved = ItemTransitHelper.pullFromAdjacentAndRoute(level, this, net, batch);
        if (moved > 0) forwardedThisTick = moved;
    }

    /**
     * Mekanism-gated tick: gas active-pull + heat thermal-wire averaging. Both routines are in
     * compat.mekanism so this BE never references their classes when Mekanism isn't loaded — the
     * static call is the JVM load gate, exactly like the cap attacher registration.
     */
    private void tickGasAndHeat(ServerLevel level) {
        if ((level.getGameTime() & 1L) != 0L) return;
        if (!com.quantumchanneling.client.Compat.mekanismLoaded()) return;
        UUID channelId = getChannelId();
        if (channelId == null) return;
        QuantumChannel net = ChannelData.get(level.getServer()).getChannel(channelId);
        if (net == null) return;
        com.quantumchanneling.compat.mekanism.GasIntegration.pullAndRoute(level, this, net);
        // Heat routing is intentionally disabled — the HEAT side-tab is hidden in the UI and the
        // thermal-wire model needs more design work. HeatIntegration + HeatChannelConfig stay
        // intact so existing channels round-trip cleanly; we just never invoke the tick.
    }

    /** Fluid analog of {@link #tickItemsMode}. mB instead of stacks; same every-other-tick cadence. */
    private void tickFluidsMode(ServerLevel level) {
        if ((level.getGameTime() & 1L) != 0L) return;
        UUID channelId = getChannelId();
        if (channelId == null) return;
        QuantumChannel net = ChannelData.get(level.getServer()).getChannel(channelId);
        if (net == null) return;
        int batch = net.fluidConfig().batchSize();
        FluidTransitHelper.pullFromAdjacentAndRoute(level, this, net, batch);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // Only save the void filter when non-default to keep NBT lean.
        if (!voidFilter.items().isEmpty() || voidFilter.isWhitelist()) {
            tag.put("VoidFilter", voidFilter.save());
        }
        if (!fluidVoidFilter.fluids().isEmpty() || fluidVoidFilter.isWhitelist()) {
            tag.put("FluidVoidFilter", fluidVoidFilter.save());
        }
        if (!gasVoidFilter.gases().isEmpty() || gasVoidFilter.isWhitelist()) {
            tag.put("GasVoidFilter", gasVoidFilter.save());
        }
    }

    @Override
    protected void onLeavingChannel(QuantumChannel channel) {
        if (level instanceof ServerLevel sl) {
            GlobalPos here = GlobalPos.of(sl.dimension(), getBlockPos());
            channel.unregisterAllForEmitter(here);
            channel.unregisterAllFluidForEmitter(here);
            channel.unregisterAllGasForEmitter(here);
        }
    }

    @Override
    protected void onJoiningChannel(QuantumChannel channel) {
        if (level instanceof ServerLevel sl) {
            GlobalPos gp = GlobalPos.of(sl.dimension(), getBlockPos());
            for (UUID subId : getSubscribedSubchannels()) {
                channel.registerEmitterSubscription(gp, subId);
            }
            for (UUID subId : getSubscribedFluidSubchannels()) {
                channel.registerEmitterFluidSubscription(gp, subId);
            }
            for (UUID subId : getSubscribedGasSubchannels()) {
                channel.registerEmitterGasSubscription(gp, subId);
            }
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
            if (!Config.allowCrossDimension && !gp.dimension().equals(server.dimension())) continue;
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
