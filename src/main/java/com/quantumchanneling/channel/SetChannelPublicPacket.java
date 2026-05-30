package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record SetChannelPublicPacket(UUID id, boolean publicAccess) {
    public static void encode(SetChannelPublicPacket p, FriendlyByteBuf b) { b.writeUUID(p.id); b.writeBoolean(p.publicAccess); }
    public static SetChannelPublicPacket decode(FriendlyByteBuf b) { return new SetChannelPublicPacket(b.readUUID(), b.readBoolean()); }
    public static void handle(SetChannelPublicPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData.get(player.serverLevel().getServer()).setPublic(p.id, player.getUUID(), p.publicAccess);
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
