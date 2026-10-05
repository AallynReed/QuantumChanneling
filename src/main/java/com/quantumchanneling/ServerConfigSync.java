package com.quantumchanneling;

import com.quantumchanneling.channel.SyncServerConfigPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Pushes the server→client config snapshot when a player logs in, so the UI has the
 * authoritative caps before they open any GUI. Reload pushes live in {@link ServerConfig}; the
 * client-side revert on logout lives in {@code client.QuantumChannelingClient}.
 */
@EventBusSubscriber(modid = QuantumChanneling.MODID)
public final class ServerConfigSync {
    private ServerConfigSync() {}

    @SubscribeEvent
    public static void onLogin(final PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            SyncServerConfigPacket.sendTo(sp);
        }
    }
}
