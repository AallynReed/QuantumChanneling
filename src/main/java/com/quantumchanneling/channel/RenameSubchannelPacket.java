package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: rename an item subchannel owned by {@code emitterPos}. */
public record RenameSubchannelPacket(BlockPos emitterPos, UUID subId, String name) {
    public static void encode(RenameSubchannelPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeUtf(p.name, ItemSubchannel.NAME_MAX);
    }
    public static RenameSubchannelPacket decode(FriendlyByteBuf b) {
        return new RenameSubchannelPacket(b.readBlockPos(), b.readUUID(), b.readUtf(ItemSubchannel.NAME_MAX));
    }
    public static void handle(RenameSubchannelPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !PacketUtil.withinReach(player, p.emitterPos)) return;
            BlockEntity be = player.level().getBlockEntity(p.emitterPos);
            if (!(be instanceof PhotonEmitterBlockEntity em)) return;
            if (em.renameItemSubchannel(p.subId, p.name)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
