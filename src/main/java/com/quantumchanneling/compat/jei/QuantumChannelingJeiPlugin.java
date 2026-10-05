package com.quantumchanneling.compat.jei;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.client.PhotonNodeScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * JEI plugin entry. Only loaded by JEI's annotation scanner — the class is invisible to the rest
 * of the mod, so when JEI isn't installed it never gets touched and the absence of JEI types in
 * the classpath never matters.
 *
 * <p>Registers two things on the Photon Node screen:
 * <ol>
 *   <li>A {@link PhotonNodeGhostHandler} so ingredients dragged from JEI's list land in the item,
 *       fluid and gas filter grids.</li>
 *   <li>An {@link IGuiContainerHandler} that exposes the screen's extra protruding areas (the
 *       side-tab strip on the right and the left channel-info panel). JEI uses these to shift its
 *       ingredient column clear of our UI so the column doesn't disappear under our side tabs.</li>
 * </ol>
 */
@JeiPlugin
public class QuantumChannelingJeiPlugin implements IModPlugin {

    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(QuantumChanneling.MODID, "jei");

    private final PhotonNodeGhostHandler ghostHandler = new PhotonNodeGhostHandler();

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(PhotonNodeScreen.class, ghostHandler);
        registration.addGuiContainerHandler(PhotonNodeScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(PhotonNodeScreen screen) {
                return screen.getExtraGuiAreas();
            }
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        ghostHandler.setIngredientManager(runtime.getIngredientManager());
    }

    @Override
    public void onRuntimeUnavailable() {
        ghostHandler.setIngredientManager(null);
    }
}
