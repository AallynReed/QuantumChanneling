package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: set the device's redstone gating mode (ordinal of {@code RedstoneMode}). */
public record SetRedstoneModePacket(BlockPos devicePos, byte modeOrdinal) {
    public static void encode(SetRedstoneModePacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.devicePos);
        b.writeByte(p.modeOrdinal);
    }
    public static SetRedstoneModePacket decode(FriendlyByteBuf b) {
        return new SetRedstoneModePacket(b.readBlockPos(), b.readByte());
    }
    public static void handle(SetRedstoneModePacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !PacketUtil.withinReach(player, p.devicePos)) return;
            BlockEntity be = player.level().getBlockEntity(p.devicePos);
            if (!(be instanceof ChannelBoundBlockEntity bound)) return;
            bound.setRedstoneMode(ChannelBoundBlockEntity.RedstoneMode.byOrdinal(p.modeOrdinal));
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
