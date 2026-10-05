package com.quantumchanneling.client;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.ServerConfig;
import com.quantumchanneling.channel.LightBurstPacket;
import com.quantumchanneling.channel.ShowChannelsListPacket;
import com.quantumchanneling.channel.SyncServerConfigPacket;
import com.quantumchanneling.client.render.PhotonBurstRenderer;
import com.quantumchanneling.client.render.PhotonItemRenderer;
import com.quantumchanneling.client.render.PhotonNodeRenderer;
import com.quantumchanneling.client.render.PhotonShaders;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = QuantumChanneling.MODID, dist = Dist.CLIENT)
public final class QuantumChannelingClient {
    public QuantumChannelingClient(IEventBus modEventBus) {
        modEventBus.addListener(QuantumChannelingClient::registerScreens);
        modEventBus.addListener(QuantumChannelingClient::registerRenderers);
        modEventBus.addListener(QuantumChannelingClient::registerSpecialModels);
        modEventBus.addListener(QuantumChannelingClient::registerPayloadHandlers);
        modEventBus.addListener((RegisterRenderPipelinesEvent e) -> PhotonShaders.register(e));
        NeoForge.EVENT_BUS.addListener(QuantumChannelingClient::onLoggingOut);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(QuantumChanneling.PHOTON_NODE_MENU.get(), PhotonNodeScreen::new);
    }

    /** One renderer for every device: the orb, plus beams for emitter/receiver and the
     *  gyroscope cage for the manager. */
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(QuantumChanneling.PHOTON_EMITTER_BE.get(), PhotonNodeRenderer::new);
        event.registerBlockEntityRenderer(QuantumChanneling.PHOTON_RECEIVER_BE.get(), PhotonNodeRenderer::new);
        event.registerBlockEntityRenderer(QuantumChanneling.PHOTON_MANAGER_BE.get(), PhotonNodeRenderer::new);
        event.registerBlockEntityRenderer(QuantumChanneling.PHOTON_STORAGE_BE.get(), PhotonNodeRenderer::new);
    }

    private static void registerSpecialModels(RegisterSpecialModelRendererEvent event) {
        event.register(QuantumChanneling.id("photon_effect"), PhotonItemRenderer.Unbaked.MAP_CODEC);
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ShowChannelsListPacket.TYPE, (p, ctx) -> ClientChannelUI.onShowChannelsList(p.channels()));
        event.register(SyncServerConfigPacket.TYPE, (p, ctx) -> ClientServerConfig.apply(p));
        event.register(LightBurstPacket.TYPE, (p, ctx) ->
                PhotonBurstRenderer.addBurst(p.x(), p.y(), p.z(), p.radius(), p.colorRgb(), p.dimension()));
    }

    /**
     * Leaving any server (remote or integrated) re-applies the player's own toml to both the spec
     * mirror and the client mirror; otherwise the last server's overrides would linger until a restart.
     */
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ServerConfig.reapplyLocal();
        ClientServerConfig.applyFromLocal();
    }
}
