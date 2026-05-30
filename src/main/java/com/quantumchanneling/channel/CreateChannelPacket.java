package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Client → server: create a new channel and immediately apply the chosen accent / PIN / access. */
public record CreateChannelPacket(String name, int color, String pin, boolean isPublic) {

    public static void encode(CreateChannelPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.name, 64);
        buf.writeInt(pkt.color);
        buf.writeUtf(pkt.pin, 32);
        buf.writeBoolean(pkt.isPublic);
    }

    public static CreateChannelPacket decode(FriendlyByteBuf buf) {
        return new CreateChannelPacket(
                buf.readUtf(64),
                buf.readInt(),
                buf.readUtf(32),
                buf.readBoolean());
    }

    public static void handle(CreateChannelPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            String name = pkt.name.trim();
            if (name.isEmpty() || name.length() > 32) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            QuantumChannel created = data.createChannel(player, name);
            // Apply initial settings authored on the Forge tab. setColor() forces alpha to 0xFF.
            if (pkt.color != 0) created.setColor(pkt.color);
            if (pkt.pin != null && !pkt.pin.isEmpty()) created.setPin(pkt.pin);
            if (pkt.isPublic) created.setPublic(true);
            data.setDirty();
            // Auto-bind the device whose menu the player currently has open, so creating a
            // channel from inside a device's Forge tab leaves you connected to it immediately.
            if (player.containerMenu instanceof com.quantumchanneling.menu.PhotonNodeMenu pm) {
                var be = player.level().getBlockEntity(pm.getBlockPos());
                if (be instanceof com.quantumchanneling.blockentity.ChannelBoundBlockEntity bound) {
                    bound.setChannelId(created.id());
                }
            }
            sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }

    public static void sendListBackTo(ServerPlayer player) {
        var server = player.serverLevel().getServer();
        ChannelData data = ChannelData.get(server);
        java.util.UUID sub = data.getChargingSubscription(player.getUUID());
        var list = data.visibleTo(player).stream()
                .map(n -> ChannelInfo.from(n, player.getUUID(), n.id().equals(sub), server))
                .toList();
        ModMessages.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ShowChannelsListPacket(list));
    }
}
