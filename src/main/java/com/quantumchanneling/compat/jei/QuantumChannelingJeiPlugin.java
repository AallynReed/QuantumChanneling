package com.quantumchanneling.compat.jei;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.client.PhotonNodeScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI plugin entry. Only loaded by JEI's annotation scanner — the class is invisible to the rest
 * of the mod, so when JEI isn't installed it never gets touched and the absence of JEI types in
 * the classpath never matters.
 *
 * <p>Currently registers one capability: a ghost-ingredient handler on the Photon Node screen, so
 * the user can drag items from JEI's ingredient list straight into the items-mode filter editor
 * without owning a single copy of the item.
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
    }
}
