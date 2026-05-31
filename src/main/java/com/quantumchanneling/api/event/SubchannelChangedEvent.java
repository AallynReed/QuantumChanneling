package com.quantumchanneling.api.event;

import com.quantumchanneling.api.IQuantumSubchannelView;
import net.minecraft.core.GlobalPos;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * Fired when a subchannel is created, deleted, renamed, recolored, or reordered. Server thread.
 * For routing-level filter edits (add/remove entries) the existing routing mask-version system is
 * a better fit — this event is for the structural changes that the UI cares about.
 */
public class SubchannelChangedEvent extends Event {
    public enum Kind { CREATED, DELETED, RENAMED, RECOLORED, REORDERED, FILTER_EDITED }

    private final UUID channelId;
    private final GlobalPos emitterPos;
    private final IQuantumSubchannelView.Kind resource;
    private final UUID subchannelId;
    private final Kind change;

    public SubchannelChangedEvent(UUID channelId, GlobalPos emitterPos,
                                  IQuantumSubchannelView.Kind resource, UUID subchannelId, Kind change) {
        this.channelId = channelId;
        this.emitterPos = emitterPos;
        this.resource = resource;
        this.subchannelId = subchannelId;
        this.change = change;
    }

    public UUID channelId() { return channelId; }
    public GlobalPos emitterPos() { return emitterPos; }
    public IQuantumSubchannelView.Kind resource() { return resource; }
    public UUID subchannelId() { return subchannelId; }
    public Kind change() { return change; }
}
