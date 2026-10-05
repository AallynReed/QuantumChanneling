package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: reorder a fluid subchannel on {@code emitterPos} in routing-priority order. */
public record MoveDeviceFluidSubchannelPacket(BlockPos emitterPos, UUID subId, int direction) {
    public static void encode(MoveDeviceFluidSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeVarInt(p.direction);
    }
    public static MoveDeviceFluidSubchannelPacket decode(FriendlyByteBuf b) {
        return new MoveDeviceFluidSubchannelPacket(b.readBlockPos(), b.readUUID(), b.readVarInt());
    }
    public static void handle(MoveDeviceFluidSubchannelPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
            if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
            if (em.moveFluidSubchannel(p.subId, p.direction)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
