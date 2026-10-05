package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: remove {@code itemId} from one emitter's void filter. */
public record RemoveEmitterVoidItemPacket(BlockPos pos, ResourceLocation itemId) {
    public static void encode(RemoveEmitterVoidItemPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeResourceLocation(p.itemId);
    }
    public static RemoveEmitterVoidItemPacket decode(FriendlyByteBuf b) {
        return new RemoveEmitterVoidItemPacket(b.readBlockPos(), b.readResourceLocation());
    }
    public static void handle(RemoveEmitterVoidItemPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.pos);
            if (!(dev instanceof PhotonEmitterBlockEntity emitter)) return;
            if (emitter.voidFilter().remove(p.itemId)) {
                emitter.bumpLocalEdit();
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
