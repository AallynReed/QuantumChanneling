package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record SetChannelChargingPacket(UUID id, int modeOrdinal) {
    public static void encode(SetChannelChargingPacket p, FriendlyByteBuf b) { b.writeUUID(p.id); b.writeVarInt(p.modeOrdinal); }
    public static SetChannelChargingPacket decode(FriendlyByteBuf b) { return new SetChannelChargingPacket(b.readUUID(), b.readVarInt()); }
    public static void handle(SetChannelChargingPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData.get(player.serverLevel().getServer())
                    .setChargingMode(p.id, player.getUUID(), ChargingMode.byOrdinal(p.modeOrdinal));
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
