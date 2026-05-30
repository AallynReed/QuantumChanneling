package com.quantumchanneling;

/**
 * Photon Energy (PE) is the unit consumed by a quantum channel each time it moves a packet
 * of items, fluid, or gas. 1 PE = 8 FE (Forge Energy); the network is FE-compatible at this
 * fixed ratio, so any FE source or sink may be wired in.
 */
public final class PhotonEnergy {
    public static final int FE_PER_PE = 8;

    private PhotonEnergy() {}

    public static int fromFE(int fe) {
        return fe / FE_PER_PE;
    }

    public static int toFE(int pe) {
        return pe * FE_PER_PE;
    }
}
