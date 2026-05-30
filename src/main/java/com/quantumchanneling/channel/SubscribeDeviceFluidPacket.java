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

/** Client → server: subscribe / unsubscribe a device to a fluid subchannel. */
public record SubscribeDeviceFluidPacket(BlockPos pos, UUID subId, boolean subscribe) {
    public static void encode(SubscribeDeviceFluidPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeUUID(p.subId);
        b.writeBoolean(p.subscribe);
    }
    public static SubscribeDeviceFluidPacket decode(FriendlyByteBuf b) {
        return new SubscribeDeviceFluidPacket(b.readBlockPos(), b.readUUID(), b.readBoolean());
    }
    public static void handle(SubscribeDeviceFluidPacket p, Supplier<NetworkEvent.Context> sup) {
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
                    ? bound.addSubscribedFluidSubchannel(p.subId)
                    : bound.removeSubscribedFluidSubchannel(p.subId);
            if (!changed) return;
            if (be instanceof PhotonEmitterBlockEntity && bound.getChannelId() != null) {
                ChannelData data = ChannelData.get(player.serverLevel().getServer());
                QuantumChannel ch = data.getChannel(bound.getChannelId());
                if (ch != null) {
                    GlobalPos here = GlobalPos.of(player.serverLevel().dimension(), p.pos);
                    if (p.subscribe) ch.registerEmitterFluidSubscription(here, p.subId);
                    else ch.unregisterEmitterFluidSubscription(here, p.subId);
                    data.setDirty();
                }
            }
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
