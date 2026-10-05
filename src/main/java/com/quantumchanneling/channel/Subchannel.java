package com.quantumchanneling.channel;

import java.util.UUID;

/** A named route hosted on an emitter, independent of the resource it carries. */
public interface Subchannel {
    UUID id();

    String name();

    /** 0 = uncolored; otherwise 0xRRGGBB. */
    int color();

    ResourceFilter filter();

    /** Called when resource actually moves through this subchannel. */
    void recordRouted(int amount);

    /** Lifetime counter since world load. */
    long routedTotal();

    /** Amount routed during the last completed rolling window. */
    int routedLastWindow();
}
