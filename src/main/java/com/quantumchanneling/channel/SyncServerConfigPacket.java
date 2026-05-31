package com.quantumchanneling.channel;

import com.quantumchanneling.ServerConfig;
import com.quantumchanneling.client.ClientServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Server → client snapshot of {@link ServerConfig}. Sent once on login and again whenever an admin
 * reloads the server config. The client mirrors the values into {@link ClientServerConfig} for the
 * UI gates — server stays authoritative for actual enforcement.
 */
public record SyncServerConfigPacket(
        boolean allowCrossDimension,
        boolean itemsEnabled,  int itemsMaxBatch,  int itemsPerEmitter,  int itemsPerReceiver,  int itemsPerChannel,
        boolean fluidsEnabled, int fluidsMaxBatch, int fluidsPerEmitter, int fluidsPerReceiver, int fluidsPerChannel,
        boolean gasesEnabled,  int gasesMaxBatch,  int gasesPerEmitter,  int gasesPerReceiver,  int gasesPerChannel,
        boolean heatEnabled,
        boolean wirelessEnabled,
        boolean slotHand, boolean slotHotbar, boolean slotInventory, boolean slotArmor, boolean slotCurios
) {
    public static SyncServerConfigPacket snapshot() {
        return new SyncServerConfigPacket(
                ServerConfig.allowCrossDimension,
                ServerConfig.itemsRoutingEnabled,  ServerConfig.itemsMaxBatch,
                ServerConfig.itemsMaxSubsPerEmitter, ServerConfig.itemsMaxSubsPerReceiver, ServerConfig.itemsMaxSubsPerChannel,
                ServerConfig.fluidsRoutingEnabled, ServerConfig.fluidsMaxBatch,
                ServerConfig.fluidsMaxSubsPerEmitter, ServerConfig.fluidsMaxSubsPerReceiver, ServerConfig.fluidsMaxSubsPerChannel,
                ServerConfig.gasesRoutingEnabled,  ServerConfig.gasesMaxBatch,
                ServerConfig.gasesMaxSubsPerEmitter, ServerConfig.gasesMaxSubsPerReceiver, ServerConfig.gasesMaxSubsPerChannel,
                ServerConfig.heatRoutingEnabled,
                ServerConfig.wirelessEnabled,
                ServerConfig.slotHandEnabled, ServerConfig.slotHotbarEnabled, ServerConfig.slotInventoryEnabled,
                ServerConfig.slotArmorEnabled, ServerConfig.slotCuriosEnabled);
    }

    public static void encode(SyncServerConfigPacket p, FriendlyByteBuf b) {
        b.writeBoolean(p.allowCrossDimension);
        b.writeBoolean(p.itemsEnabled);  b.writeVarInt(p.itemsMaxBatch);
        b.writeVarInt(p.itemsPerEmitter); b.writeVarInt(p.itemsPerReceiver); b.writeVarInt(p.itemsPerChannel);
        b.writeBoolean(p.fluidsEnabled); b.writeVarInt(p.fluidsMaxBatch);
        b.writeVarInt(p.fluidsPerEmitter); b.writeVarInt(p.fluidsPerReceiver); b.writeVarInt(p.fluidsPerChannel);
        b.writeBoolean(p.gasesEnabled);  b.writeVarInt(p.gasesMaxBatch);
        b.writeVarInt(p.gasesPerEmitter); b.writeVarInt(p.gasesPerReceiver); b.writeVarInt(p.gasesPerChannel);
        b.writeBoolean(p.heatEnabled);
        b.writeBoolean(p.wirelessEnabled);
        b.writeBoolean(p.slotHand); b.writeBoolean(p.slotHotbar); b.writeBoolean(p.slotInventory);
        b.writeBoolean(p.slotArmor); b.writeBoolean(p.slotCurios);
    }

    public static SyncServerConfigPacket decode(FriendlyByteBuf b) {
        return new SyncServerConfigPacket(
                b.readBoolean(),
                b.readBoolean(), b.readVarInt(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readBoolean(), b.readVarInt(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readBoolean(), b.readVarInt(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readBoolean(),
                b.readBoolean(),
                b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean());
    }

    public static void handle(SyncServerConfigPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> applyClient(p)));
        ctx.setPacketHandled(true);
    }

    private static void applyClient(SyncServerConfigPacket p) {
        ClientServerConfig.allowCrossDimension     = p.allowCrossDimension;
        ClientServerConfig.itemsRoutingEnabled     = p.itemsEnabled;
        ClientServerConfig.itemsMaxBatch           = p.itemsMaxBatch;
        ClientServerConfig.itemsMaxSubsPerEmitter  = p.itemsPerEmitter;
        ClientServerConfig.itemsMaxSubsPerReceiver = p.itemsPerReceiver;
        ClientServerConfig.itemsMaxSubsPerChannel  = p.itemsPerChannel;
        ClientServerConfig.fluidsRoutingEnabled    = p.fluidsEnabled;
        ClientServerConfig.fluidsMaxBatch          = p.fluidsMaxBatch;
        ClientServerConfig.fluidsMaxSubsPerEmitter = p.fluidsPerEmitter;
        ClientServerConfig.fluidsMaxSubsPerReceiver = p.fluidsPerReceiver;
        ClientServerConfig.fluidsMaxSubsPerChannel = p.fluidsPerChannel;
        ClientServerConfig.gasesRoutingEnabled     = p.gasesEnabled;
        ClientServerConfig.gasesMaxBatch           = p.gasesMaxBatch;
        ClientServerConfig.gasesMaxSubsPerEmitter  = p.gasesPerEmitter;
        ClientServerConfig.gasesMaxSubsPerReceiver = p.gasesPerReceiver;
        ClientServerConfig.gasesMaxSubsPerChannel  = p.gasesPerChannel;
        ClientServerConfig.heatRoutingEnabled      = p.heatEnabled;
        ClientServerConfig.wirelessEnabled         = p.wirelessEnabled;
        ClientServerConfig.slotHandEnabled         = p.slotHand;
        ClientServerConfig.slotHotbarEnabled       = p.slotHotbar;
        ClientServerConfig.slotInventoryEnabled    = p.slotInventory;
        ClientServerConfig.slotArmorEnabled        = p.slotArmor;
        ClientServerConfig.slotCuriosEnabled       = p.slotCurios;
    }

    public static void sendTo(ServerPlayer player) {
        ModMessages.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot());
    }

    public static void sendToAll(MinecraftServer server) {
        SyncServerConfigPacket snap = snapshot();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ModMessages.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), snap);
        }
    }
}
