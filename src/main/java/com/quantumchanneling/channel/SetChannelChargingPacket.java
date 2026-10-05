package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Set a channel's charging-slot bitmask (HAND / HOTBAR / INVENTORY / ARMOR). */
public record SetChannelChargingPacket(UUID id, int slotMask) {
    public static void encode(SetChannelChargingPacket p, FriendlyByteBuf b) { b.writeUUID(p.id); b.writeVarInt(p.slotMask); }
    public static SetChannelChargingPacket decode(FriendlyByteBuf b) { return new SetChannelChargingPacket(b.readUUID(), b.readVarInt()); }
    public static void handle(SetChannelChargingPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            var server = player.serverLevel().getServer();
            if (ChannelData.get(server).setChargingSlots(p.id, player.getUUID(), p.slotMask)) {
                CreateChannelPacket.broadcastListTo(server, p.id);
            }
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
