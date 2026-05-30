package com.quantumchanneling.channel;

import com.quantumchanneling.client.ClientChannelUI;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Server→client. Replaces the client's cached networks list and refreshes any open NetworksScreen. */
public record ShowChannelsListPacket(List<ChannelInfo> networks) {

    public static void encode(ShowChannelsListPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.networks.size());
        for (ChannelInfo info : pkt.networks) info.write(buf);
    }

    public static ShowChannelsListPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ChannelInfo> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(ChannelInfo.read(buf));
        return new ShowChannelsListPacket(Collections.unmodifiableList(list));
    }

    public static void handle(ShowChannelsListPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientChannelUI.onShowChannelsList(pkt.networks)));
        ctx.setPacketHandled(true);
    }
}
