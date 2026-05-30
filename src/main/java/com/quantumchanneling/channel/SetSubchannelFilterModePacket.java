package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: toggle whitelist/blacklist on one subchannel's filter. */
public record SetSubchannelFilterModePacket(UUID channelId, UUID subId, boolean whitelist) {
    public static void encode(SetSubchannelFilterModePacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUUID(p.subId);
        b.writeBoolean(p.whitelist);
    }
    public static SetSubchannelFilterModePacket decode(FriendlyByteBuf b) {
        return new SetSubchannelFilterModePacket(b.readUUID(), b.readUUID(), b.readBoolean());
    }
    public static void handle(SetSubchannelFilterModePacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.setSubchannelFilterMode(p.channelId, player.getUUID(), p.subId, p.whitelist)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
