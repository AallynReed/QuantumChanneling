package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record AddEmitterGasVoidPacket(BlockPos pos, ResourceLocation gasId) {
    public static void encode(AddEmitterGasVoidPacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); b.writeResourceLocation(p.gasId); }
    public static AddEmitterGasVoidPacket decode(FriendlyByteBuf b) { return new AddEmitterGasVoidPacket(b.readBlockPos(), b.readResourceLocation()); }
    public static void handle(AddEmitterGasVoidPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.pos);
            if (!(dev instanceof PhotonEmitterBlockEntity emitter)) return;
            if (emitter.gasVoidFilter().add(p.gasId)) {
                emitter.bumpLocalEdit();
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
