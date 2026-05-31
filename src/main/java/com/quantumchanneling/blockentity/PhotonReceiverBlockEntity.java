package com.quantumchanneling.blockentity;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.ServerConfig;
import com.quantumchanneling.menu.PhotonNodeMenu;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.channel.QuantumChannel;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
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
        @Override public int getMaxEnergyStored() { return effectiveBudget(ServerConfig.receiverOutputRate); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energyIO);

    /**
     * Receivers are now passive: the emitter pushes items directly into the receiver's adjacent
     * inventory, no buffer. This cap is intentionally inert (zero slots) so external pullers
     * don't try to extract from a non-existent buffer; the receiver's role is purely "drop point".
     */
    private final IItemHandler itemIO = new IItemHandler() {
        @Override public int getSlots() { return 0; }
        @Override public @NotNull ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 0; }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return false; }
        @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) { return stack; }
        @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
    };
    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> itemIO);

    /** Same passive shape as {@link #itemIO} but for fluids — zero-tank, rejects all fills. */
    private final IFluidHandler fluidIO = new IFluidHandler() {
        @Override public int getTanks() { return 0; }
        @Override public @NotNull FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return 0; }
        @Override public boolean isFluidValid(int tank, @NotNull FluidStack stack) { return false; }
        @Override public int fill(FluidStack resource, FluidAction action) { return 0; }
        @Override public @NotNull FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public @NotNull FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
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

    public void serverTick(ServerLevel level) {
        lastTickThroughput = forwardedThisTick;
        recordSample(lastTickThroughput);
        forwardedThisTick = 0;

        // Re-scan adjacency every second, staggered by position hash so receivers don't all
        // refresh on the same tick. See PhotonEmitterBlockEntity for the rationale.
        if (((level.getGameTime() + Math.floorMod(getBlockPos().hashCode(), 20)) % 20) == 0) {
            var pos = getBlockPos();
            var state = getBlockState();
            var updated = com.quantumchanneling.block.PhotonShape.refreshConnections(
                    level, pos, state, com.quantumchanneling.block.PhotonShape.ConnectionMode.SINKS);
            if (updated != state) level.setBlock(pos, updated, 2);
        }

        // Items: receivers are passive — emitters push directly into the receiver's adjacent
        // inventory each tick, so there's no per-tick work to do on the items side.

        // Energy: top up adjacent machines from channel storage when emitter input alone isn't enough.
        // This makes Photon Storage actually act as a reservoir: when the receiver's neighbours need
        // FE and there's still budget headroom this tick, we drain storage on the channel and
        // forward it. Emitter-pushed energy still gets here first (acceptAndForward runs during the
        // emitter's tick), so this only covers the shortfall.
        pullFromChannelStorageAndForward(level);
    }

    /**
     * Computes what the adjacent FE sinks would still accept this tick (clamped by remaining
     * budget), then pulls that much from any {@link PhotonStorageBlockEntity} loaded on the
     * channel and runs it through {@link #acceptAndForward}.
     */
    private void pullFromChannelStorageAndForward(ServerLevel level) {
        UUID channelId = getChannelId();
        if (channelId == null) return;
        int budget = effectiveBudget(ServerConfig.receiverOutputRate);
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
        int budget = effectiveBudget(ServerConfig.receiverOutputRate);
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
