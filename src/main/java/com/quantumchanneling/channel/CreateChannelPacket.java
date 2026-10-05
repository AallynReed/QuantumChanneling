package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: create a new channel and immediately apply the chosen accent / PIN / access. */
public record CreateChannelPacket(String name, int color, String pin, boolean isPublic) implements CustomPacketPayload {
    public static final Type<CreateChannelPacket> TYPE = new Type<>(QuantumChanneling.id("create_channel"));
    public static final StreamCodec<FriendlyByteBuf, CreateChannelPacket> STREAM_CODEC =
            StreamCodec.ofMember(CreateChannelPacket::encode, CreateChannelPacket::decode);

    @Override
    public Type<CreateChannelPacket> type() { return TYPE; }


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

    public static void handle(CreateChannelPacket pkt, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        String name = pkt.name.trim();
        if (name.isEmpty() || name.length() > 32) return;
        ChannelData data = ChannelData.get(player.level().getServer());
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
    }

    public static void sendListBackTo(ServerPlayer player) {
        var server = player.level().getServer();
        PacketDistributor.sendToPlayer(player, buildListFor(player, ChannelData.get(server)));
    }

    /** Re-sync every online player who can currently see the channel {@code channelId}. */
    public static void broadcastListTo(net.minecraft.server.MinecraftServer server, java.util.UUID channelId) {
        ChannelData data = ChannelData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean sees = data.visibleTo(player).stream().anyMatch(n -> n.id().equals(channelId));
            if (!sees) continue;
            PacketDistributor.sendToPlayer(player, buildListFor(player, data));
        }
    }

    private static ShowChannelsListPacket buildListFor(ServerPlayer player, ChannelData data) {
        var server = player.level().getServer();
        java.util.UUID sub = data.getChargingSubscription(player.getUUID());
        var list = data.visibleTo(player).stream()
                .map(n -> ChannelInfo.from(n, player.getUUID(), n.id().equals(sub), server))
                .toList();
        return new ShowChannelsListPacket(list);
    }
}
