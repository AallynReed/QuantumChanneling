package com.quantumchanneling.channel;

/**
 * How a device spreads load across its downstream targets — the same enum is consulted on the
 * emitter side (picking among subscribed receivers) and on the receiver side (picking among
 * adjacent inventories / tanks).
 */
public enum DispatchStrategy {
    /** Highest-priority target first; only spill to the next once it can't accept any more. */
    SERVE_FIRST,
    /** Rotate which target sees the batch first so they share work over many ticks. */
    ROUND_ROBIN;

    public DispatchStrategy next() {
        DispatchStrategy[] vs = values();
        return vs[(ordinal() + 1) % vs.length];
    }

    public static DispatchStrategy byOrdinal(int ord) {
        DispatchStrategy[] vs = values();
        if (ord < 0 || ord >= vs.length) return SERVE_FIRST;
        return vs[ord];
    }

    public String labelKey() {
        return switch (this) {
            case SERVE_FIRST -> "gui.quantumchanneling.dispatch.serve_first";
            case ROUND_ROBIN -> "gui.quantumchanneling.dispatch.round_robin";
        };
    }
}
