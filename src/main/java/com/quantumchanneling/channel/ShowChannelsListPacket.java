package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server→client. Replaces the client's cached channel list and refreshes any open channel screen. */
public record ShowChannelsListPacket(List<ChannelInfo> channels) implements CustomPacketPayload {
    public static final Type<ShowChannelsListPacket> TYPE = new Type<>(QuantumChanneling.id("show_channels_list"));
    public static final StreamCodec<FriendlyByteBuf, ShowChannelsListPacket> STREAM_CODEC =
            StreamCodec.ofMember(ShowChannelsListPacket::encode, ShowChannelsListPacket::decode);

    @Override
    public Type<ShowChannelsListPacket> type() { return TYPE; }

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
}
