package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: remove one item ID from a filter target (main / void / sub-channel). */
public record RemoveItemFilterEntryPacket(UUID channelId, int target, ResourceLocation itemId) {
    public static void encode(RemoveItemFilterEntryPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeVarInt(p.target);
        b.writeResourceLocation(p.itemId);
    }
    public static RemoveItemFilterEntryPacket decode(FriendlyByteBuf b) {
        return new RemoveItemFilterEntryPacket(b.readUUID(), b.readVarInt(), b.readResourceLocation());
    }
    public static void handle(RemoveItemFilterEntryPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.removeItemFilterEntry(p.channelId, player.getUUID(), p.target, p.itemId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
