package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: rename one of the 5 sub-channels. */
public record SetSubchannelNamePacket(UUID channelId, int subIdx, String name) {
    public static void encode(SetSubchannelNamePacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeVarInt(p.subIdx);
        b.writeUtf(p.name, ItemSubchannel.NAME_MAX);
    }
    public static SetSubchannelNamePacket decode(FriendlyByteBuf b) {
        return new SetSubchannelNamePacket(b.readUUID(), b.readVarInt(), b.readUtf(ItemSubchannel.NAME_MAX));
    }
    public static void handle(SetSubchannelNamePacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.setSubchannelName(p.channelId, player.getUUID(), p.subIdx, p.name)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
