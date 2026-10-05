package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: create a new fluid subchannel on the emitter at {@code emitterPos}. */
public record CreateFluidSubchannelPacket(BlockPos emitterPos, String name) {
    public static void encode(CreateFluidSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUtf(p.name, FluidSubchannel.NAME_MAX);
    }
    public static CreateFluidSubchannelPacket decode(FriendlyByteBuf b) {
        return new CreateFluidSubchannelPacket(b.readBlockPos(), b.readUtf(FluidSubchannel.NAME_MAX));
    }
    public static void handle(CreateFluidSubchannelPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
            if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
            if (em.createFluidSubchannel(p.name) != null) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
