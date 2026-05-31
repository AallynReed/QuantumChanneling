package com.quantumchanneling;

import com.quantumchanneling.channel.SyncServerConfigPacket;
import com.quantumchanneling.client.ClientServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives the server→client config snapshot: pushes once when a player logs in (so the UI has the
 * authoritative caps before they open any GUI) and again whenever an admin reloads the server
 * config (so the change applies live).
 */
@Mod.EventBusSubscriber(modid = QuantumChanneling.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerConfigSync {
    private ServerConfigSync() {}

    @SubscribeEvent
    public static void onLogin(final PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            SyncServerConfigPacket.sendTo(sp);
        }
    }

    /**
     * When the local player disconnects from any server (remote or integrated), re-apply the
     * client's own toml values to both the authoritative spec mirror and the ClientServerConfig
     * mirror. Without this the client would keep using the last-joined server's overrides until
     * it edited its own config file or restarted.
     */
    @SubscribeEvent
    public static void onClientLogout(final ClientPlayerNetworkEvent.LoggingOut e) {
        ServerConfig.reapplyLocal();
        ClientServerConfig.applyFromLocal();
    }
}
