package com.quantumchanneling.blockentity;

import com.quantumchanneling.Config;
import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.menu.PhotonNodeMenu;
import com.quantumchanneling.channel.QuantumChannel;
import com.quantumchanneling.channel.ChannelData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PhotonReceiverBlockEntity extends ChannelBoundBlockEntity implements MenuProvider {
    private int forwardedThisTick = 0;
    private int lastTickThroughput = 0;

    private final IEnergyStorage energyIO = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) { return acceptAndForward(max, simulate); }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return effectiveBudget(Config.receiverOutputRate); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energyIO);

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case PhotonNodeMenu.DATA_THROUGHPUT -> lastTickThroughput;
                case PhotonNodeMenu.DATA_CHUNK_LOADED -> isChunkLoadForced() ? 1 : 0;
                case PhotonNodeMenu.DATA_CHANNEL_BOUND -> getChannelId() != null ? 1 : 0;
                case PhotonNodeMenu.DATA_THROUGHPUT_CAP -> getThroughputCap();
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return PhotonNodeMenu.DATA_SIZE; }
    };

    public PhotonReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(QuantumChanneling.PHOTON_RECEIVER_BE.get(), pos, state);
    }

    public int getLastTickThroughput() { return lastTickThroughput; }

    @Override
    public Component getDisplayName() { return getBlockState().getBlock().getName(); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
        return new PhotonNodeMenu(id, inv, getBlockPos(), containerData,
                resolveChannelName(), resolveChannelOwner());
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
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() { super.invalidateCaps(); energyCap.invalidate(); }

    @Override
    public void reviveCaps() { super.reviveCaps(); energyCap = LazyOptional.of(() -> energyIO); }

    public void serverTick(ServerLevel level) {
        lastTickThroughput = forwardedThisTick;
        forwardedThisTick = 0;
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
