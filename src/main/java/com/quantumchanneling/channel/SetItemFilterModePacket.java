package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → server: toggle whitelist/blacklist mode on one filter target.
 * Targets: 0 = main, 1 = void, 2..6 = sub-channel 0..4 (see {@link ChannelData#filterTargetSub}).
 */
public record SetItemFilterModePacket(UUID channelId, int target, boolean whitelist) {
    public static void encode(SetItemFilterModePacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeVarInt(p.target);
        b.writeBoolean(p.whitelist);
    }
    public static SetItemFilterModePacket decode(FriendlyByteBuf b) {
        return new SetItemFilterModePacket(b.readUUID(), b.readVarInt(), b.readBoolean());
    }
    public static void handle(SetItemFilterModePacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            if (data.setItemFilterMode(p.channelId, player.getUUID(), p.target, p.whitelist)) {
                CreateChannelPacket.sendListBackTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
