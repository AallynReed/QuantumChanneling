package com.quantumchanneling.blockentity;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PhotonEmitterBlockEntity extends ChannelBoundBlockEntity {
    public static final int CAPACITY = 100_000;
    public static final int MAX_TRANSFER = 10_000;

    private final PhotonEnergyStorage energy =
            new PhotonEnergyStorage(CAPACITY, MAX_TRANSFER, MAX_TRANSFER, this::setChanged);
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    public PhotonEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(QuantumChanneling.PHOTON_EMITTER_BE.get(), pos, state);
    }

    public int getEnergyStored() {
        return energy.getEnergyStored();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCap = LazyOptional.of(() -> energy);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energy.getEnergyStored());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.setEnergy(tag.getInt("Energy"));
    }
}
