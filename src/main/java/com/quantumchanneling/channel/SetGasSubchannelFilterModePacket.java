package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: flip whitelist/blacklist on a gas subchannel owned by {@code emitterPos}. */
public record SetGasSubchannelFilterModePacket(BlockPos emitterPos, UUID subId, boolean whitelist) {
    public static void encode(SetGasSubchannelFilterModePacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeUUID(p.subId);
        b.writeBoolean(p.whitelist);
    }
    public static SetGasSubchannelFilterModePacket decode(FriendlyByteBuf b) {
        return new SetGasSubchannelFilterModePacket(b.readBlockPos(), b.readUUID(), b.readBoolean());
    }
    public static void handle(SetGasSubchannelFilterModePacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelBoundBlockEntity dev = PacketUtil.manageableDevice(player, p.emitterPos);
            if (!(dev instanceof PhotonEmitterBlockEntity em)) return;
            if (em.setGasSubchannelFilterMode(p.subId, p.whitelist)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
