package com.quantumchanneling.client;

import com.quantumchanneling.channel.ChannelInfo;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class ClientChannelUI {
    private ClientChannelUI() {}

    public static void onShowChannelsList(List<ChannelInfo> channels) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ChannelsScreen open) {
            open.refresh(channels);
        } else {
            mc.setScreen(new ChannelsScreen(channels));
        }
    }
}
