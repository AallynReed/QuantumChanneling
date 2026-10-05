package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: add {@code fluidId} to one emitter's fluid void filter. */
public record AddEmitterFluidVoidPacket(BlockPos pos, ResourceLocation fluidId) {
    public static void encode(AddEmitterFluidVoidPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeResourceLocation(p.fluidId);
    }
    public static AddEmitterFluidVoidPacket decode(FriendlyByteBuf b) {
        return new AddEmitterFluidVoidPacket(b.readBlockPos(), b.readResourceLocation());
    }
    public static void handle(AddEmitterFluidVoidPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.pos);
            if (!(dev instanceof PhotonEmitterBlockEntity emitter)) return;
            if (emitter.fluidVoidFilter().add(p.fluidId)) {
                emitter.bumpLocalEdit();
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
