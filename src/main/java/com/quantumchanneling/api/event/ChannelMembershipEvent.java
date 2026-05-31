package com.quantumchanneling.api.event;

import net.minecraft.core.GlobalPos;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * Fired when a device joins or leaves a channel — including remote unbinds. Server thread.
 * Listeners can use this to refresh peripheral attachments, dashboards, etc.
 */
public class ChannelMembershipEvent extends Event {
    public enum Kind { JOINED, LEFT }

    private final UUID channelId;
    private final GlobalPos devicePos;
    private final Kind kind;

    public ChannelMembershipEvent(UUID channelId, GlobalPos devicePos, Kind kind) {
        this.channelId = channelId;
        this.devicePos = devicePos;
        this.kind = kind;
    }

    public UUID channelId() { return channelId; }
    public GlobalPos devicePos() { return devicePos; }
    public Kind kind() { return kind; }
}
