package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record SetChannelColorPacket(UUID id, int color) {
    public static void encode(SetChannelColorPacket p, FriendlyByteBuf b) { b.writeUUID(p.id); b.writeInt(p.color); }
    public static SetChannelColorPacket decode(FriendlyByteBuf b) { return new SetChannelColorPacket(b.readUUID(), b.readInt()); }
    public static void handle(SetChannelColorPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData.get(player.serverLevel().getServer())
                    .setColor(p.id, player.getUUID(), p.color);
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
