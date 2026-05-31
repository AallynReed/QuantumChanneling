package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: update the 6-bit per-side mask for one resource kind on a channel-bound device.
 * {@code kind} = 0 (items), 1 (fluids), 2 (gas). {@code mask} bits 0..5 follow
 * {@link net.minecraft.core.Direction#get3DDataValue()}.
 */
public record SetSideMaskPacket(BlockPos devicePos, byte kind, byte mask) {
    public static void encode(SetSideMaskPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.devicePos);
        b.writeByte(p.kind);
        b.writeByte(p.mask);
    }
    public static SetSideMaskPacket decode(FriendlyByteBuf b) {
        return new SetSideMaskPacket(b.readBlockPos(), b.readByte(), b.readByte());
    }
    public static void handle(SetSideMaskPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !PacketUtil.withinReach(player, p.devicePos)) return;
            BlockEntity be = player.level().getBlockEntity(p.devicePos);
            if (!(be instanceof ChannelBoundBlockEntity bound)) return;
            int v = p.mask & 0x3F;
            switch (p.kind) {
                case 0 -> bound.setItemSideMask(v);
                case 1 -> bound.setFluidSideMask(v);
                case 2 -> bound.setGasSideMask(v);
                default -> { return; }
            }
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
