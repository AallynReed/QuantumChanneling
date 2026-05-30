package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Admin-only: unbind a device from the channel without standing next to it. Operates on a
 * GlobalPos so it works on loaded or unloaded members across any dimension.
 */
public record RemoteUnbindDevicePacket(UUID channelId, String dim, long packedPos) {

    public static void encode(RemoteUnbindDevicePacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUtf(p.dim, 80);
        b.writeLong(p.packedPos);
    }

    public static RemoteUnbindDevicePacket decode(FriendlyByteBuf b) {
        return new RemoteUnbindDevicePacket(b.readUUID(), b.readUtf(80), b.readLong());
    }

    public static void handle(RemoteUnbindDevicePacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            MinecraftServer server = player.serverLevel().getServer();
            ChannelData data = ChannelData.get(server);
            QuantumChannel net = data.getChannel(p.channelId);
            if (net == null || !net.canManage(player.getUUID())) return;

            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(p.dim));
            GlobalPos gp = GlobalPos.of(dimKey, BlockPos.of(p.packedPos));

            // If the BE is loaded, clear its channelId so it stops trying to participate.
            ServerLevel level = server.getLevel(dimKey);
            if (level != null && level.isLoaded(gp.pos())) {
                BlockEntity be = level.getBlockEntity(gp.pos());
                if (be instanceof ChannelBoundBlockEntity bound) {
                    bound.setChannelId(null);
                }
            } else {
                // Unloaded: just yank it from the channel's member set; on next load the BE
                // will validate its NBT channelId against ChannelData and clear itself.
                data.removeMember(p.channelId, gp);
            }
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
