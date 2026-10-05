package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: create a new gas subchannel on the emitter at {@code emitterPos}. */
public record CreateGasSubchannelPacket(BlockPos emitterPos, String name) {
    public static void encode(CreateGasSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUtf(p.name, GasSubchannel.NAME_MAX);
    }
    public static CreateGasSubchannelPacket decode(FriendlyByteBuf b) {
        return new CreateGasSubchannelPacket(b.readBlockPos(), b.readUtf(GasSubchannel.NAME_MAX));
    }
    public static void handle(CreateGasSubchannelPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
            if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
            if (em.createGasSubchannel(p.name) != null) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
