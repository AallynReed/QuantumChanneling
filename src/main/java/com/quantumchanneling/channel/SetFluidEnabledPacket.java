package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: flip the per-device fluids participation flag. */
public record SetFluidEnabledPacket(BlockPos pos, boolean enabled) {
    public static void encode(SetFluidEnabledPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos); b.writeBoolean(p.enabled);
    }
    public static SetFluidEnabledPacket decode(FriendlyByteBuf b) {
        return new SetFluidEnabledPacket(b.readBlockPos(), b.readBoolean());
    }
    public static void handle(SetFluidEnabledPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity bound = PacketUtil.usableDevice(player, p.pos);
            if (bound != null) {
                bound.setFluidsEnabled(p.enabled);
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
