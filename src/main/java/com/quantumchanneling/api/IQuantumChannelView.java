package com.quantumchanneling.api;

import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Read-only view of a Quantum Channel. Stable for third-party mods that want to introspect
 * channel state without coupling to internal classes. Returned by
 * {@link QuantumChannelingAPI#getChannel(net.minecraft.server.MinecraftServer, UUID)} and friends.
 *
 * <p>All methods are safe to call on the server thread. Most are cheap snapshots; {@link #members()}
 * returns a defensive copy.
 */
public interface IQuantumChannelView {
    UUID id();
    String name();
    @Nullable UUID ownerId();
    String ownerName();
    int memberCount();
    boolean isPublic();
    int color();

    /** Defensive snapshot of every bound device position on this channel. */
    List<GlobalPos> members();

    /** Live throughput summary for the last server tick. */
    int totalEmitterInputFE();
    int totalReceiverOutputFE();
}
