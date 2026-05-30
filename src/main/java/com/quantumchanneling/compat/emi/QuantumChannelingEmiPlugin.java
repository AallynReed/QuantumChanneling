package com.quantumchanneling.compat.emi;

import com.quantumchanneling.client.PhotonNodeScreen;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

/**
 * EMI plugin entry. Loaded only by EMI's annotation scanner — invisible to the rest of the mod, so
 * when EMI isn't installed it's never touched and the EMI types in its imports never need to
 * resolve at runtime.
 *
 * <p>Registers a drag-and-drop handler on the Photon Node screen so items dragged from EMI's
 * recipe browser into the items-mode filter grid behave identically to the JEI ghost-drag path:
 * dropping on an empty slot appends to the current filter target, dropping on a filled slot
 * swaps the entry.
 */
@EmiEntrypoint
public class QuantumChannelingEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(PhotonNodeScreen.class, new PhotonNodeEmiDragHandler());
    }
}
