package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: add {@code itemId} to one emitter's void filter. */
public record AddEmitterVoidItemPacket(BlockPos pos, ResourceLocation itemId) {
    public static void encode(AddEmitterVoidItemPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeResourceLocation(p.itemId);
    }
    public static AddEmitterVoidItemPacket decode(FriendlyByteBuf b) {
        return new AddEmitterVoidItemPacket(b.readBlockPos(), b.readResourceLocation());
    }
    public static void handle(AddEmitterVoidItemPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            double dx = p.pos.getX() + 0.5 - player.getX();
            double dy = p.pos.getY() + 0.5 - player.getY();
            double dz = p.pos.getZ() + 0.5 - player.getZ();
            if (dx * dx + dy * dy + dz * dz > 64.0) return;
            BlockEntity be = player.level().getBlockEntity(p.pos);
            if (be instanceof PhotonEmitterBlockEntity emitter) {
                if (emitter.voidFilter().add(p.itemId)) {
                    emitter.bumpLocalEdit();
                    // Push the channel list back so the client's MemberPos.voidFilter snapshot
                    // refreshes — without this, the UI keeps showing the pre-edit filter.
                    CreateChannelPacket.sendListBackTo(player);
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
