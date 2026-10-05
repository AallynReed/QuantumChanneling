package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: remove {@code fluidId} from one emitter's fluid void filter. */
public record RemoveEmitterFluidVoidPacket(BlockPos pos, ResourceLocation fluidId) {
    public static void encode(RemoveEmitterFluidVoidPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeResourceLocation(p.fluidId);
    }
    public static RemoveEmitterFluidVoidPacket decode(FriendlyByteBuf b) {
        return new RemoveEmitterFluidVoidPacket(b.readBlockPos(), b.readResourceLocation());
    }
    public static void handle(RemoveEmitterFluidVoidPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.pos);
            if (!(dev instanceof PhotonEmitterBlockEntity emitter)) return;
            if (emitter.fluidVoidFilter().remove(p.fluidId)) {
                emitter.bumpLocalEdit();
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
