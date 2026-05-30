package com.quantumchanneling.compat.jei;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.client.PhotonNodeScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * JEI plugin entry. Only loaded by JEI's annotation scanner — the class is invisible to the rest
 * of the mod, so when JEI isn't installed it never gets touched and the absence of JEI types in
 * the classpath never matters.
 *
 * <p>Registers two things on the Photon Node screen:
 * <ol>
 *   <li>A {@link PhotonNodeGhostHandler} so dragged items from JEI's ingredient list land in the
 *       items-mode filter grid.</li>
 *   <li>An {@link IGuiContainerHandler} that exposes the screen's extra protruding areas (the
 *       side-tab strip on the right and the left channel-info panel). JEI uses these to shift its
 *       ingredient column clear of our UI so the column doesn't disappear under our side tabs.</li>
 * </ol>
 */
@JeiPlugin
public class QuantumChannelingJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            new ResourceLocation(QuantumChanneling.MODID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(PhotonNodeScreen.class, new PhotonNodeGhostHandler());
        registration.addGuiContainerHandler(PhotonNodeScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(PhotonNodeScreen screen) {
                return screen.getExtraGuiAreas();
            }
        });
    }
}
