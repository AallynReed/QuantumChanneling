package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: add one item ID to a filter target (main / void / sub-channel). */
public record AddItemFilterEntryPacket(UUID channelId, int target, ResourceLocation itemId) {
    public static void encode(AddItemFilterEntryPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeVarInt(p.target);
        b.writeResourceLocation(p.itemId);
    }
    public static AddItemFilterEntryPacket decode(FriendlyByteBuf b) {
        return new AddItemFilterEntryPacket(b.readUUID(), b.readVarInt(), b.readResourceLocation());
    }
    public static void handle(AddItemFilterEntryPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.addItemFilterEntry(p.channelId, player.getUUID(), p.target, p.itemId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
