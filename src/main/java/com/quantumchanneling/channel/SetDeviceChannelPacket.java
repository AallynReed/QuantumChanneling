package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Bind (or unbind with NONE) THIS device to a specific channel. */
public record SetDeviceChannelPacket(BlockPos pos, UUID channelId) implements CustomPacketPayload {
    public static final Type<SetDeviceChannelPacket> TYPE = new Type<>(QuantumChanneling.id("set_device_channel"));
    public static final StreamCodec<FriendlyByteBuf, SetDeviceChannelPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetDeviceChannelPacket::encode, SetDeviceChannelPacket::decode);

    @Override
    public Type<SetDeviceChannelPacket> type() { return TYPE; }

    public static final UUID NONE = new UUID(0L, 0L);

    public static void encode(SetDeviceChannelPacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); b.writeUUID(p.channelId); }
    public static SetDeviceChannelPacket decode(FriendlyByteBuf b) { return new SetDeviceChannelPacket(b.readBlockPos(), b.readUUID()); }

    public static void handle(SetDeviceChannelPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        // distance / sanity check
        double dx = p.pos.getX() + 0.5 - player.getX();
        double dy = p.pos.getY() + 0.5 - player.getY();
        double dz = p.pos.getZ() + 0.5 - player.getZ();
        if (dx * dx + dy * dy + dz * dz > 64.0) return;
        BlockEntity be = player.level().getBlockEntity(p.pos);
        if (!(be instanceof ChannelBoundBlockEntity bound)) return;
        if (NONE.equals(p.channelId)) {
            bound.setChannelId(null);
        } else {
            ChannelData data = ChannelData.get(player.level().getServer());
            QuantumChannel ch = data.getChannel(p.channelId);
            if (ch == null || !ch.canUse(player.getUUID())) return;
            bound.setChannelId(p.channelId);
        }
        // Always refresh the client so the UI reflects the new (or cleared) binding.
        CreateChannelPacket.sendListBackTo(player);
    }
}
