package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Subscribe (or unsubscribe with the all-zero UUID) the sending player to the given channel for charging. */
public record SubscribeChargingPacket(UUID channelId) {
    public static final UUID NONE = new UUID(0L, 0L);

    public static void encode(SubscribeChargingPacket p, FriendlyByteBuf b) { b.writeUUID(p.channelId); }
    public static SubscribeChargingPacket decode(FriendlyByteBuf b) { return new SubscribeChargingPacket(b.readUUID()); }
    public static void handle(SubscribeChargingPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (NONE.equals(p.channelId)) {
                data.setChargingSubscription(player.getUUID(), null);
            } else {
                QuantumChannel net = data.getChannel(p.channelId);
                if (net == null || !net.canUse(player.getUUID())) return;
                data.setChargingSubscription(player.getUUID(), p.channelId);
            }
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
