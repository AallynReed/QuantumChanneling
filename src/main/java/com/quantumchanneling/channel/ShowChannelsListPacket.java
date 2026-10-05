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

/** Server→client. Replaces the client's cached channel list and refreshes any open channel screen. */
public record ShowChannelsListPacket(List<ChannelInfo> channels) {

    public static void encode(ShowChannelsListPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.channels.size());
        for (ChannelInfo info : pkt.channels) info.write(buf);
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
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientChannelUI.onShowChannelsList(pkt.channels)));
        ctx.setPacketHandled(true);
    }
}
