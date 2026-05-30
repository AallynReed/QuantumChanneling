package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record RenameChannelPacket(UUID id, String name) {
    public static void encode(RenameChannelPacket p, FriendlyByteBuf b) { b.writeUUID(p.id); b.writeUtf(p.name); }
    public static RenameChannelPacket decode(FriendlyByteBuf b) { return new RenameChannelPacket(b.readUUID(), b.readUtf(64)); }
    public static void handle(RenameChannelPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            String name = p.name.trim();
            if (name.isEmpty() || name.length() > 32) return;
            ChannelData.get(player.serverLevel().getServer()).renameChannel(p.id, player.getUUID(), name);
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
