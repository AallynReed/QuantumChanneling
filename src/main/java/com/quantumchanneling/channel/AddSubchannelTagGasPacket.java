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

/** Client → server: add a gas tag id to one of {@code emitterPos}'s gas subchannel filters. */
public record AddSubchannelTagGasPacket(BlockPos emitterPos, UUID subId, ResourceLocation tagId) {
    public static void encode(AddSubchannelTagGasPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeResourceLocation(p.tagId);
    }
    public static AddSubchannelTagGasPacket decode(FriendlyByteBuf b) {
        return new AddSubchannelTagGasPacket(b.readBlockPos(), b.readUUID(), b.readResourceLocation());
    }
    public static void handle(AddSubchannelTagGasPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
            if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
            if (em.addGasSubchannelTagEntry(p.subId, p.tagId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
