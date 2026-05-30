package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: flip the per-channel items-mode master enable flag. */
public record SetItemEnabledPacket(UUID channelId, boolean enabled) {
    public static void encode(SetItemEnabledPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeBoolean(p.enabled);
    }
    public static SetItemEnabledPacket decode(FriendlyByteBuf b) {
        return new SetItemEnabledPacket(b.readUUID(), b.readBoolean());
    }
    public static void handle(SetItemEnabledPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.setItemEnabled(p.channelId, player.getUUID(), p.enabled)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
