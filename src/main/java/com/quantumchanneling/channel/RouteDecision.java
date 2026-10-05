package com.quantumchanneling.channel;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** What an emitter does with one resource type: void it, route it through a subchannel, or leave it. */
public record RouteDecision(Kind kind, @Nullable UUID subchannelId) {
    public enum Kind { VOID, ROUTE, REJECT }

    public static final RouteDecision VOID = new RouteDecision(Kind.VOID, null);
    public static final RouteDecision REJECT = new RouteDecision(Kind.REJECT, null);

    public static RouteDecision route(UUID subchannelId) {
        return new RouteDecision(Kind.ROUTE, subchannelId);
    }
}
