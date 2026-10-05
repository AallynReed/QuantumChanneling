package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: add an item id to one of {@code emitterPos}'s item subchannel filters. */
public record AddSubchannelItemPacket(BlockPos emitterPos, UUID subId, ResourceLocation itemId) {
    public static void encode(AddSubchannelItemPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeResourceLocation(p.itemId);
    }
    public static AddSubchannelItemPacket decode(FriendlyByteBuf b) {
        return new AddSubchannelItemPacket(b.readBlockPos(), b.readUUID(), b.readResourceLocation());
    }
    public static void handle(AddSubchannelItemPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
            if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
            if (em.addItemSubchannelEntry(p.subId, p.itemId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
