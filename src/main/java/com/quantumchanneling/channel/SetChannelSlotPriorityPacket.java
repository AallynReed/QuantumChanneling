package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: admin sets the priority of one charging slot group. */
public record SetChannelSlotPriorityPacket(UUID channelId, int slotBit, int priority) {
    public static void encode(SetChannelSlotPriorityPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeVarInt(p.slotBit);
        b.writeVarInt(p.priority);
    }
    public static SetChannelSlotPriorityPacket decode(FriendlyByteBuf b) {
        return new SetChannelSlotPriorityPacket(b.readUUID(), b.readVarInt(), b.readVarInt());
    }
    public static void handle(SetChannelSlotPriorityPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.setSlotPriority(p.channelId, player.getUUID(), p.slotBit, p.priority)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
