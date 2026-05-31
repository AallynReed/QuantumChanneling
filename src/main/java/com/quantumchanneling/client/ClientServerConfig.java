package com.quantumchanneling.client;

/**
 * Client-side mirror of {@link com.quantumchanneling.ServerConfig}. Populated by the sync packet
 * the server sends on login and again whenever an admin reloads the config. All fields hold the
 * default at mod load so single-player and "haven't received the packet yet" both render sensibly.
 *
 * <p>The UI uses these values to gate the Create / Subscribe buttons before sending packets — the
 * server enforces the same caps, but client-side feedback keeps the user from getting silent
 * no-ops when they're at a limit.
 */
public final class ClientServerConfig {
    private ClientServerConfig() {}

    public static boolean allowCrossDimension = true;

    /** Storage tier capacities (index = tier - 1). Defaults match ServerConfig's defaults. */
    public static long[] storageCapacities = new long[]{ 1L << 16, 1L << 20, 1L << 24, 1L << 28, 1L << 32 };

    public static boolean itemsRoutingEnabled    = true;
    public static int     itemsMaxBatch          = 500;
    public static int     itemsMaxSubsPerEmitter = 3;
    public static int     itemsMaxSubsPerReceiver = 3;
    public static int     itemsMaxSubsPerChannel = 9;

    public static boolean fluidsRoutingEnabled    = true;
    public static int     fluidsMaxBatch          = 64_000;
    public static int     fluidsMaxSubsPerEmitter = 3;
    public static int     fluidsMaxSubsPerReceiver = 3;
    public static int     fluidsMaxSubsPerChannel = 9;

    public static boolean gasesRoutingEnabled    = true;
    public static int     gasesMaxBatch          = 64_000;
    public static int     gasesMaxSubsPerEmitter = 3;
    public static int     gasesMaxSubsPerReceiver = 3;
    public static int     gasesMaxSubsPerChannel = 9;

    public static boolean heatRoutingEnabled   = false;

    public static boolean wirelessEnabled      = true;
    public static boolean slotHandEnabled      = true;
    public static boolean slotHotbarEnabled    = true;
    public static boolean slotInventoryEnabled = true;
    public static boolean slotArmorEnabled     = true;
    public static boolean slotCuriosEnabled    = true;

    /**
     * Copy the current local {@link com.quantumchanneling.ServerConfig} values into this mirror.
     * Used after the client logs out of a server so the UI stops showing the (now-irrelevant)
     * remote server's caps and reverts to whatever the player has in their own toml.
     */
    public static void applyFromLocal() {
        allowCrossDimension     = com.quantumchanneling.ServerConfig.allowCrossDimension;
        storageCapacities       = com.quantumchanneling.ServerConfig.storageCapacities;
        itemsRoutingEnabled     = com.quantumchanneling.ServerConfig.itemsRoutingEnabled;
        itemsMaxBatch           = com.quantumchanneling.ServerConfig.itemsMaxBatch;
        itemsMaxSubsPerEmitter  = com.quantumchanneling.ServerConfig.itemsMaxSubsPerEmitter;
        itemsMaxSubsPerReceiver = com.quantumchanneling.ServerConfig.itemsMaxSubsPerReceiver;
        itemsMaxSubsPerChannel  = com.quantumchanneling.ServerConfig.itemsMaxSubsPerChannel;
        fluidsRoutingEnabled    = com.quantumchanneling.ServerConfig.fluidsRoutingEnabled;
        fluidsMaxBatch          = com.quantumchanneling.ServerConfig.fluidsMaxBatch;
        fluidsMaxSubsPerEmitter = com.quantumchanneling.ServerConfig.fluidsMaxSubsPerEmitter;
        fluidsMaxSubsPerReceiver = com.quantumchanneling.ServerConfig.fluidsMaxSubsPerReceiver;
        fluidsMaxSubsPerChannel = com.quantumchanneling.ServerConfig.fluidsMaxSubsPerChannel;
        gasesRoutingEnabled     = com.quantumchanneling.ServerConfig.gasesRoutingEnabled;
        gasesMaxBatch           = com.quantumchanneling.ServerConfig.gasesMaxBatch;
        gasesMaxSubsPerEmitter  = com.quantumchanneling.ServerConfig.gasesMaxSubsPerEmitter;
        gasesMaxSubsPerReceiver = com.quantumchanneling.ServerConfig.gasesMaxSubsPerReceiver;
        gasesMaxSubsPerChannel  = com.quantumchanneling.ServerConfig.gasesMaxSubsPerChannel;
        heatRoutingEnabled      = com.quantumchanneling.ServerConfig.heatRoutingEnabled;
        wirelessEnabled         = com.quantumchanneling.ServerConfig.wirelessEnabled;
        slotHandEnabled         = com.quantumchanneling.ServerConfig.slotHandEnabled;
        slotHotbarEnabled       = com.quantumchanneling.ServerConfig.slotHotbarEnabled;
        slotInventoryEnabled    = com.quantumchanneling.ServerConfig.slotInventoryEnabled;
        slotArmorEnabled        = com.quantumchanneling.ServerConfig.slotArmorEnabled;
        slotCuriosEnabled       = com.quantumchanneling.ServerConfig.slotCuriosEnabled;
    }
}
