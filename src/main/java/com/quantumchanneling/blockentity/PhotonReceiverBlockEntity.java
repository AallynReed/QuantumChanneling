package com.quantumchanneling.blockentity;

import com.quantumchanneling.Config;
import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.menu.PhotonNodeMenu;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.channel.ItemFilter;
import com.quantumchanneling.channel.ItemSubchannel;
import com.quantumchanneling.channel.ItemTransitHelper;
import com.quantumchanneling.channel.QuantumChannel;
import com.quantumchanneling.channel.ResourceMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
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
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PhotonReceiverBlockEntity extends ChannelBoundBlockEntity implements MenuProvider {
    private int forwardedThisTick = 0;
    private int lastTickThroughput = 0;

    // Throughput history: one sample per second (sum over 20 ticks), 3600 samples = 60 minutes.
    private static final int HISTORY_SECONDS = 3600;
    private final int[] secondsHistory = new int[HISTORY_SECONDS];
    private int historyHead = 0;
    private int historyFilled = 0;
    private int secondAccumulator = 0;
    private int secondTickCounter = 0;

    private final IEnergyStorage energyIO = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) { return acceptAndForward(max, simulate); }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return effectiveBudget(Config.receiverOutputRate); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energyIO);

    /**
     * Virtual single-slot item handler. External pullers (AE2 exporters, RS exporters, hoppers
     * below a receiver) extract via this — items come from the channel buffer filtered by this
     * device's sub-channel binding.
     */
    private final IItemHandler itemIO = new IItemHandler() {
        @Override public int getSlots() { return 1; }
        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            QuantumChannel net = currentChannel();
            if (net == null) return ItemStack.EMPTY;
            return ItemTransitHelper.peekOrTake(net, resolveSubchannelFilter(net), true);
        }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return false; }
        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) { return stack; }
        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0 || getResourceMode() != ResourceMode.ITEMS) return ItemStack.EMPTY;
            QuantumChannel net = currentChannel();
            if (net == null) return ItemStack.EMPTY;
            ItemStack peek = ItemTransitHelper.peekOrTake(net, resolveSubchannelFilter(net), true);
            if (peek.isEmpty()) return ItemStack.EMPTY;
            int take = Math.min(amount, peek.getCount());
            if (simulate) {
                ItemStack copy = peek.copy();
                copy.setCount(take);
                return copy;
            }
            // Real take: drain the head, splice off only `take` items, push the remainder back.
            ItemStack head = ItemTransitHelper.peekOrTake(net, resolveSubchannelFilter(net), false);
            if (head.isEmpty()) return ItemStack.EMPTY;
            if (take >= head.getCount()) return head;
            ItemStack out = head.copy();
            out.setCount(take);
            head.shrink(take);
            net.itemBuffer().addFirst(head);                       // remainder rejoins the head of the queue
            return out;
        }
    };
    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> itemIO);

    /** Returns the channel this receiver is bound to, or null if unbound / channel gone. */
    private @Nullable QuantumChannel currentChannel() {
        if (!(level instanceof ServerLevel sl)) return null;
        UUID id = getChannelId();
        return id == null ? null : ChannelData.get(sl.getServer()).getChannel(id);
    }

    /** Returns null for "main trunk" binding, or the sub-channel's filter when bound to one. */
    private @Nullable ItemFilter resolveSubchannelFilter(QuantumChannel net) {
        int idx = getSubchannelIndex();
        if (idx < 0) return null;
        ItemSubchannel sub = net.itemConfig().subchannel(idx);
        return sub == null ? null : sub.filter();
    }

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

    public PhotonReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(QuantumChanneling.PHOTON_RECEIVER_BE.get(), pos, state);
    }

    public int getLastTickThroughput() { return lastTickThroughput; }

    /** See {@link PhotonEmitterBlockEntity#getGraphBucket}. */
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
        if (cap == ForgeCapabilities.ENERGY && getResourceMode() == ResourceMode.ENERGY) return energyCap.cast();
        if (cap == ForgeCapabilities.ITEM_HANDLER && getResourceMode() == ResourceMode.ITEMS) return itemCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        itemCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCap = LazyOptional.of(() -> energyIO);
        itemCap = LazyOptional.of(() -> itemIO);
    }

    public void serverTick(ServerLevel level) {
        lastTickThroughput = forwardedThisTick;
        recordSample(lastTickThroughput);
        forwardedThisTick = 0;

        // Re-scan adjacency every second — see PhotonEmitterBlockEntity.serverTick for the rationale.
        if ((level.getGameTime() % 20) == 0) {
            var pos = getBlockPos();
            var state = getBlockState();
            var updated = com.quantumchanneling.block.PhotonShape.refreshConnections(
                    level, pos, state, com.quantumchanneling.block.PhotonShape.ConnectionMode.SINKS);
            if (updated != state) level.setBlock(pos, updated, 2);
        }

        if (getResourceMode() == ResourceMode.ITEMS) {
            tickItemsMode(level);
            return;
        }

        // Top up adjacent machines from channel storage when emitter input alone isn't enough.
        // This makes Photon Storage actually act as a reservoir: when the receiver's neighbours need
        // FE and there's still budget headroom this tick, we drain storage on the channel and
        // forward it. Emitter-pushed energy still gets here first (acceptAndForward runs during the
        // emitter's tick), so this only covers the shortfall.
        pullFromChannelStorageAndForward(level);
    }

    /**
     * One step of the items-mode active push. Every other tick, take one buffered stack matching
     * this receiver's sub-channel binding and try to deposit it into a 6-neighbour IItemHandler.
     * Unaccepted remainder is returned to the front of the buffer.
     */
    private void tickItemsMode(ServerLevel level) {
        if ((level.getGameTime() & 1L) != 0L) return;            // every-other-tick gating
        QuantumChannel net = currentChannel();
        if (net == null) return;
        ItemStack head = ItemTransitHelper.peekOrTake(net, resolveSubchannelFilter(net), false);
        if (head.isEmpty()) return;
        int batch = net.itemConfig().batchSize();
        int take = Math.min(batch, head.getCount());
        ItemStack toPush = head.copy();
        toPush.setCount(take);
        if (take < head.getCount()) {
            head.shrink(take);
            net.itemBuffer().addFirst(head);
        }
        ItemStack rejected = ItemTransitHelper.pushToAdjacent(level, this, toPush);
        if (!rejected.isEmpty()) net.itemBuffer().addFirst(rejected);
        forwardedThisTick = take - rejected.getCount();          // tracks items moved for stats
    }

    /**
     * Computes what the adjacent FE sinks would still accept this tick (clamped by remaining
     * budget), then pulls that much from any {@link PhotonStorageBlockEntity} loaded on the
     * channel and runs it through {@link #acceptAndForward}.
     */
    private void pullFromChannelStorageAndForward(ServerLevel level) {
        UUID channelId = getChannelId();
        if (channelId == null) return;
        int budget = effectiveBudget(Config.receiverOutputRate);
        int room = Math.max(0, budget - forwardedThisTick);
        if (room <= 0) return;
        int demand = simulateAdjacentDemand(level, room);
        if (demand <= 0) return;
        int pulled = pullFromChannelStorage(level, demand);
        if (pulled > 0) acceptAndForward(pulled, false);
    }

    /** Sum of FE the 6 neighbours would accept this tick (capped at {@code maxAmount}). */
    private int simulateAdjacentDemand(ServerLevel level, int maxAmount) {
        int total = 0;
        for (Direction side : Direction.values()) {
            if (total >= maxAmount) break;
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(side));
            if (neighbor == null) continue;
            IEnergyStorage sink = neighbor.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).orElse(null);
            if (sink == null || !sink.canReceive()) continue;
            int can = sink.receiveEnergy(maxAmount - total, true);
            if (can > 0) total += can;
        }
        return total;
    }

    /** Drain up to {@code want} FE from any loaded Photon Storage on this channel. */
    private int pullFromChannelStorage(ServerLevel level, int want) {
        UUID channelId = getChannelId();
        if (channelId == null || want <= 0) return 0;
        MinecraftServer server = level.getServer();
        if (server == null) return 0;
        ChannelData data = ChannelData.get(server);
        int collected = 0;
        for (GlobalPos gp : data.getMembers(channelId)) {
            if (collected >= want) break;
            ServerLevel target = server.getLevel(gp.dimension());
            if (target == null || !target.isLoaded(gp.pos())) continue;
            BlockEntity be = target.getBlockEntity(gp.pos());
            if (be instanceof PhotonStorageBlockEntity storage) {
                collected += storage.pullForExternal(want - collected);
            }
        }
        return collected;
    }

    public int acceptAndForward(int amount, boolean simulate) {
        int budget = effectiveBudget(Config.receiverOutputRate);
        int cap = Math.max(0, budget - forwardedThisTick);
        int b = Math.min(amount, cap);
        if (b <= 0) return 0;
        if (!(level instanceof ServerLevel server)) return 0;

        int delivered = 0;
        for (Direction side : Direction.values()) {
            if (b <= 0) break;
            BlockEntity neighbor = server.getBlockEntity(worldPosition.relative(side));
            if (neighbor == null) continue;
            IEnergyStorage sink = neighbor.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).orElse(null);
            if (sink == null || !sink.canReceive()) continue;
            int simulated = sink.receiveEnergy(b, true);
            if (simulated <= 0) continue;
            if (simulate) { delivered += simulated; b -= simulated; }
            else {
                int actual = sink.receiveEnergy(simulated, false);
                if (actual > 0) { delivered += actual; b -= actual; forwardedThisTick += actual; }
            }
        }
        return delivered;
    }
}
