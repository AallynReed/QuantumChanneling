package com.quantumchanneling.api.event;

import com.quantumchanneling.api.IQuantumSubchannelView;
import net.minecraft.core.GlobalPos;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * Fired on the {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS Forge event bus}, on the
 * server thread, once per successful subchannel route. Items pushed: {@link #amount()} is the
 * stack-count. Fluids / gas pushed: {@code amount} is mB.
 *
 * <p>Not cancellable — by the time this fires, the move has already executed on the source
 * inventory. Listeners that just want to observe (counters, dashboards, logs) are the intended
 * audience; gating the route should be done with subchannel filters on the emitter instead.
 */
public class ChannelRoutedEvent extends Event {
    private final UUID channelId;
    private final GlobalPos emitterPos;
    private final IQuantumSubchannelView subchannel;
    private final IQuantumSubchannelView.Kind kind;
    private final int amount;

    public ChannelRoutedEvent(UUID channelId, GlobalPos emitterPos, IQuantumSubchannelView subchannel,
                              IQuantumSubchannelView.Kind kind, int amount) {
        this.channelId = channelId;
        this.emitterPos = emitterPos;
        this.subchannel = subchannel;
        this.kind = kind;
        this.amount = amount;
    }

    public UUID channelId() { return channelId; }
    public GlobalPos emitterPos() { return emitterPos; }
    public IQuantumSubchannelView subchannel() { return subchannel; }
    public IQuantumSubchannelView.Kind kind() { return kind; }
    public int amount() { return amount; }
}
