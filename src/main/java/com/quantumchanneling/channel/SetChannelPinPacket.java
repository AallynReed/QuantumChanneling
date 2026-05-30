package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: admin sets/clears the join PIN for a channel. Empty string clears it. */
public record SetChannelPinPacket(UUID channelId, String pin) {
    public static void encode(SetChannelPinPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUtf(p.pin, 32);
    }
    public static SetChannelPinPacket decode(FriendlyByteBuf b) {
        return new SetChannelPinPacket(b.readUUID(), b.readUtf(32));
    }
    public static void handle(SetChannelPinPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.setPin(p.channelId, player.getUUID(), p.pin)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
