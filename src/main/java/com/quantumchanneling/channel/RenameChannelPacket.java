package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record RenameChannelPacket(UUID id, String name) implements CustomPacketPayload {
    public static final Type<RenameChannelPacket> TYPE = new Type<>(QuantumChanneling.id("rename_channel"));
    public static final StreamCodec<FriendlyByteBuf, RenameChannelPacket> STREAM_CODEC =
            StreamCodec.ofMember(RenameChannelPacket::encode, RenameChannelPacket::decode);

    @Override
    public Type<RenameChannelPacket> type() { return TYPE; }

    public static void encode(RenameChannelPacket p, FriendlyByteBuf b) { b.writeUUID(p.id); b.writeUtf(p.name); }
    public static RenameChannelPacket decode(FriendlyByteBuf b) { return new RenameChannelPacket(b.readUUID(), b.readUtf(64)); }
    public static void handle(RenameChannelPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        String name = p.name.trim();
        if (name.isEmpty() || name.length() > 32) return;
        var server = player.level().getServer();
        if (ChannelData.get(server).renameChannel(p.id, player.getUUID(), name)) {
            CreateChannelPacket.broadcastListTo(server, p.id);
        }
        CreateChannelPacket.sendListBackTo(player);
    }
}
