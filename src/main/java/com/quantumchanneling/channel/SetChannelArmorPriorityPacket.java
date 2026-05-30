package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → server: admin sets the priority of a single armor piece on the channel.
 * {@code armorIdx} = 0 head, 1 chest, 2 legs, 3 feet.
 */
public record SetChannelArmorPriorityPacket(UUID channelId, int armorIdx, int priority) {
    public static void encode(SetChannelArmorPriorityPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeVarInt(p.armorIdx);
        b.writeVarInt(p.priority);
    }
    public static SetChannelArmorPriorityPacket decode(FriendlyByteBuf b) {
        return new SetChannelArmorPriorityPacket(b.readUUID(), b.readVarInt(), b.readVarInt());
    }
    public static void handle(SetChannelArmorPriorityPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.setArmorPiecePriority(p.channelId, player.getUUID(), p.armorIdx, p.priority)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
