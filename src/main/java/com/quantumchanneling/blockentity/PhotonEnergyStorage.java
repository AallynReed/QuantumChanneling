package com.quantumchanneling.blockentity;

import net.minecraftforge.energy.EnergyStorage;

/**
 * EnergyStorage that notifies a callback whenever its stored amount changes, so the owning
 * BlockEntity can call {@code setChanged()} and have the new value saved to disk.
 */
public class PhotonEnergyStorage extends EnergyStorage {
    private final Runnable onChange;

    public PhotonEnergyStorage(int capacity, int maxReceive, int maxExtract, Runnable onChange) {
        super(capacity, maxReceive, maxExtract);
        this.onChange = onChange;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int received = super.receiveEnergy(maxReceive, simulate);
        if (!simulate && received > 0) onChange.run();
        return received;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        int extracted = super.extractEnergy(maxExtract, simulate);
        if (!simulate && extracted > 0) onChange.run();
        return extracted;
    }

    public void setEnergy(int amount) {
        int clamped = Math.max(0, Math.min(capacity, amount));
        if (clamped != energy) {
            energy = clamped;
            onChange.run();
        }
    }
}
