package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server → client snapshot of {@link ServerConfig}. Sent once on login and again whenever an admin
 * reloads the server config. The client mirrors the values into {@link com.quantumchanneling.client.ClientServerConfig} for the
 * UI gates — server stays authoritative for actual enforcement.
 */
public record SyncServerConfigPacket(
        boolean allowCrossDimension,
        long storageT1, long storageT2, long storageT3, long storageT4, long storageT5,
        boolean itemsEnabled,  int itemsMaxBatch,  int itemsPerEmitter,  int itemsPerReceiver,  int itemsPerChannel,
        boolean fluidsEnabled, int fluidsMaxBatch, int fluidsPerEmitter, int fluidsPerReceiver, int fluidsPerChannel,
        boolean gasesEnabled,  int gasesMaxBatch,  int gasesPerEmitter,  int gasesPerReceiver,  int gasesPerChannel,
        boolean heatEnabled,
        boolean wirelessEnabled,
        boolean slotHand, boolean slotHotbar, boolean slotInventory, boolean slotArmor, boolean slotCurios
) implements CustomPacketPayload {
    public static final Type<SyncServerConfigPacket> TYPE = new Type<>(QuantumChanneling.id("sync_server_config"));
    public static final StreamCodec<FriendlyByteBuf, SyncServerConfigPacket> STREAM_CODEC =
            StreamCodec.ofMember(SyncServerConfigPacket::encode, SyncServerConfigPacket::decode);

    @Override
    public Type<SyncServerConfigPacket> type() { return TYPE; }

    public static SyncServerConfigPacket snapshot() {
        long[] caps = ServerConfig.storageCapacities;
        return new SyncServerConfigPacket(
                ServerConfig.allowCrossDimension,
                caps[0], caps[1], caps[2], caps[3], caps[4],
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
        b.writeLong(p.storageT1); b.writeLong(p.storageT2); b.writeLong(p.storageT3);
        b.writeLong(p.storageT4); b.writeLong(p.storageT5);
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
                b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readLong(),
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

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, snapshot());
    }

    public static void sendToAll(MinecraftServer server) {
        SyncServerConfigPacket snap = snapshot();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, snap);
        }
    }
}
