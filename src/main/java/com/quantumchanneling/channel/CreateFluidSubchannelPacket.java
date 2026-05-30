package com.quantumchanneling.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: create a new fluid subchannel on {@code channelId}. */
public record CreateFluidSubchannelPacket(UUID channelId, String name) {
    public static void encode(CreateFluidSubchannelPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.channelId);
        b.writeUtf(p.name, FluidSubchannel.NAME_MAX);
    }
    public static CreateFluidSubchannelPacket decode(FriendlyByteBuf b) {
        return new CreateFluidSubchannelPacket(b.readUUID(), b.readUtf(FluidSubchannel.NAME_MAX));
    }
    public static void handle(CreateFluidSubchannelPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChannelData data = ChannelData.get(player.serverLevel().getServer());
            UUID newId = data.createFluidSubchannel(p.channelId, player.getUUID(), p.name);
            if (newId != null) CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
