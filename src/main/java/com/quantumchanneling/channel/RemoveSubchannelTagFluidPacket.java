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

/** Client → server: drop a fluid tag id from one of {@code emitterPos}'s fluid subchannel filters. */
public record RemoveSubchannelTagFluidPacket(BlockPos emitterPos, UUID subId, ResourceLocation tagId) {
    public static void encode(RemoveSubchannelTagFluidPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeResourceLocation(p.tagId);
    }
    public static RemoveSubchannelTagFluidPacket decode(FriendlyByteBuf b) {
        return new RemoveSubchannelTagFluidPacket(b.readBlockPos(), b.readUUID(), b.readResourceLocation());
    }
    public static void handle(RemoveSubchannelTagFluidPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
            if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
            if (em.removeFluidSubchannelTagEntry(p.subId, p.tagId)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
