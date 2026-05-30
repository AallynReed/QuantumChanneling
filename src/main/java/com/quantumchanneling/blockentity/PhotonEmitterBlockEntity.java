package com.quantumchanneling.blockentity;

import com.quantumchanneling.Config;
import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.menu.PhotonNodeMenu;
import com.quantumchanneling.channel.QuantumChannel;
import com.quantumchanneling.channel.ChannelData;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PhotonEmitterBlockEntity extends ChannelBoundBlockEntity implements MenuProvider {
    private int forwardedThisTick = 0;
    private int lastTickThroughput = 0;

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

    public PhotonEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(QuantumChanneling.PHOTON_EMITTER_BE.get(), pos, state);
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
        forwardedThisTick = 0;

        if (getChannelId() == null) return;

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
        for (GlobalPos gp : members) {
            if (gp.equals(self)) continue;
            if (!Config.allowCrossDimension && !gp.dimension().equals(server.dimension())) continue;
            ServerLevel target = mcServer.getLevel(gp.dimension());
            if (target == null || !target.isLoaded(gp.pos())) continue;
            BlockEntity be = target.getBlockEntity(gp.pos());
            if (be instanceof PhotonReceiverBlockEntity r) receivers.add(r);
        }
        if (receivers.isEmpty()) return 0;

        int remaining = amount;
        int share = Math.max(1, remaining / receivers.size());
        int delivered = 0;
        for (PhotonReceiverBlockEntity r : receivers) {
            if (remaining <= 0) break;
            int give = Math.min(share, remaining);
            int sent = r.acceptAndForward(give, simulate);
            if (sent > 0) {
                delivered += sent;
                remaining -= sent;
            }
        }
        return delivered;
    }
}
