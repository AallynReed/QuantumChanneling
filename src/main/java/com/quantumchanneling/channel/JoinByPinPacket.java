package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: try to gain USER access to {@code channelId} by submitting a PIN. */
public record JoinByPinPacket(UUID channelId, String pin) {
    public static void encode(JoinByPinPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUtf(p.pin, 32);
    }
    public static JoinByPinPacket decode(FriendlyByteBuf b) {
        return new JoinByPinPacket(b.readUUID(), b.readUtf(32));
    }
    public static void handle(JoinByPinPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            // Always push the latest list back — joined or not, so the UI clears/refreshes.
            data.joinByPin(player, p.channelId, p.pin);
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
