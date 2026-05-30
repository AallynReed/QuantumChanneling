package com.quantumchanneling.client;

import com.quantumchanneling.channel.ChannelInfo;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.List;

/** Client-side dispatcher + cache for channel data the server has sent us. */
public final class ClientChannelUI {
    private static List<ChannelInfo> cached = Collections.emptyList();

    private ClientChannelUI() {}

    public static List<ChannelInfo> cachedChannels() {
        return cached;
    }

    public static void onShowChannelsList(List<ChannelInfo> channels) {
        cached = channels;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PhotonNodeScreen open) {
            open.onChannelsRefreshed(channels);
        }
    }
}
