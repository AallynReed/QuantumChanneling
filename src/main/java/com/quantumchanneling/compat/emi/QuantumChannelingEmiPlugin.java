package com.quantumchanneling.compat.emi;

import com.quantumchanneling.client.PhotonNodeScreen;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.renderer.Rect2i;

/**
 * EMI plugin entry. Loaded only by EMI's annotation scanner — invisible to the rest of the mod, so
 * when EMI isn't installed it's never touched and the EMI types in its imports never need to
 * resolve at runtime.
 *
 * <p>Registers two things on the Photon Node screen:
 * <ol>
 *   <li>A {@link PhotonNodeEmiDragHandler} so EMI ingredient drags land in the items-mode filter
 *       grid (parity with JEI's ghost-ingredient handler).</li>
 *   <li>An exclusion area for the right-side mode strip and left channel-info panel, so EMI's
 *       ingredient column shifts clear of our UI instead of being covered by it.</li>
 * </ol>
 */
@EmiEntrypoint
public class QuantumChannelingEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(PhotonNodeScreen.class, new PhotonNodeEmiDragHandler());
        registry.addExclusionArea(PhotonNodeScreen.class, (screen, consumer) -> {
            for (Rect2i r : screen.getExtraGuiAreas()) {
                consumer.accept(new Bounds(r.getX(), r.getY(), r.getWidth(), r.getHeight()));
            }
        });
    }
}
