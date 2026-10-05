package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: flip the per-device items participation flag. */
public record SetItemEnabledPacket(BlockPos pos, boolean enabled) {
    public static void encode(SetItemEnabledPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos); b.writeBoolean(p.enabled);
    }
    public static SetItemEnabledPacket decode(FriendlyByteBuf b) {
        return new SetItemEnabledPacket(b.readBlockPos(), b.readBoolean());
    }
    public static void handle(SetItemEnabledPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity bound = PacketUtil.usableDevice(player, p.pos);
            if (bound != null) {
                bound.setItemsEnabled(p.enabled);
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
