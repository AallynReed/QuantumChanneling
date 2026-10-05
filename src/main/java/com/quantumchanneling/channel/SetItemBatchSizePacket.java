package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client → server: set the per-channel items-mode batch size (items per active tick). */
public record SetItemBatchSizePacket(UUID channelId, int batchSize) implements CustomPacketPayload {
    public static final Type<SetItemBatchSizePacket> TYPE = new Type<>(QuantumChanneling.id("set_item_batch_size"));
    public static final StreamCodec<FriendlyByteBuf, SetItemBatchSizePacket> STREAM_CODEC =
            StreamCodec.ofMember(SetItemBatchSizePacket::encode, SetItemBatchSizePacket::decode);

    @Override
    public Type<SetItemBatchSizePacket> type() { return TYPE; }

    public static void encode(SetItemBatchSizePacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeVarInt(p.batchSize);
    }
    public static SetItemBatchSizePacket decode(FriendlyByteBuf b) {
        return new SetItemBatchSizePacket(b.readUUID(), b.readVarInt());
    }
    public static void handle(SetItemBatchSizePacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        var server = player.level().getServer();
        ChannelData data = ChannelData.get(server);
        if (data.setItemBatchSize(p.channelId, player.getUUID(), p.batchSize)) {
            CreateChannelPacket.broadcastListTo(server, p.channelId);
        }
        CreateChannelPacket.sendListBackTo(player);
    }
}
