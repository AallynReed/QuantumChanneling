package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: flip the per-channel heat thermal-wire master enable. */
public record SetHeatEnabledPacket(UUID channelId, boolean enabled) {
    public static void encode(SetHeatEnabledPacket p, FriendlyByteBuf b) { b.writeUUID(p.channelId); b.writeBoolean(p.enabled); }
    public static SetHeatEnabledPacket decode(FriendlyByteBuf b) { return new SetHeatEnabledPacket(b.readUUID(), b.readBoolean()); }
    public static void handle(SetHeatEnabledPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            var server = player.serverLevel().getServer();
            ChannelData data = ChannelData.get(server);
            if (data.setHeatEnabled(p.channelId, player.getUUID(), p.enabled)) {
                CreateChannelPacket.broadcastListTo(server, p.channelId);
            }
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
