package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Bind (or unbind with NONE) THIS device to a specific channel. */
public record SetDeviceChannelPacket(BlockPos pos, UUID channelId) {
    public static final UUID NONE = new UUID(0L, 0L);

    public static void encode(SetDeviceChannelPacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); b.writeUUID(p.channelId); }
    public static SetDeviceChannelPacket decode(FriendlyByteBuf b) { return new SetDeviceChannelPacket(b.readBlockPos(), b.readUUID()); }

    public static void handle(SetDeviceChannelPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
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
                ChannelData data = ChannelData.get(player.serverLevel().getServer());
                QuantumChannel ch = data.getChannel(p.channelId);
                if (ch == null || !ch.canUse(player.getUUID())) return;
                bound.setChannelId(p.channelId);
            }
            // Always refresh the client so the UI reflects the new (or cleared) binding.
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
