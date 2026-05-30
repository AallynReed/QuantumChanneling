package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → server: subscribe one device to a subchannel. Emitters append the subchannel to the end
 * of their priority list; receivers add it to their listen set. When the device is an emitter,
 * also keeps the channel's subscriber reverse index in sync — unsubscribing from the last emitter
 * triggers an orphan-subchannel auto-delete.
 */
public record SubscribeDevicePacket(BlockPos pos, UUID subId, boolean subscribe) {
    public static void encode(SubscribeDevicePacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeUUID(p.subId);
        b.writeBoolean(p.subscribe);
    }
    public static SubscribeDevicePacket decode(FriendlyByteBuf b) {
        return new SubscribeDevicePacket(b.readBlockPos(), b.readUUID(), b.readBoolean());
    }
    public static void handle(SubscribeDevicePacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            double dx = p.pos.getX() + 0.5 - player.getX();
            double dy = p.pos.getY() + 0.5 - player.getY();
            double dz = p.pos.getZ() + 0.5 - player.getZ();
            if (dx * dx + dy * dy + dz * dz > 64.0) return;
            BlockEntity be = player.level().getBlockEntity(p.pos);
            if (!(be instanceof ChannelBoundBlockEntity bound)) return;
            boolean changed = p.subscribe
                    ? bound.addSubscribedSubchannel(p.subId)
                    : bound.removeSubscribedSubchannel(p.subId);
            if (!changed) return;
            // Reverse-index update for emitters only — receivers don't keep subchannels alive.
            if (be instanceof PhotonEmitterBlockEntity && bound.getChannelId() != null) {
                ChannelData data = ChannelData.get(player.serverLevel().getServer());
                QuantumChannel ch = data.getChannel(bound.getChannelId());
                if (ch != null) {
                    GlobalPos here = GlobalPos.of(player.serverLevel().dimension(), p.pos);
                    if (p.subscribe) ch.registerEmitterSubscription(here, p.subId);
                    else ch.unregisterEmitterSubscription(here, p.subId);
                    data.setDirty();
                }
            }
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
