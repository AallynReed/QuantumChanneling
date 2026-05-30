package com.quantumchanneling.menu;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PhotonNodeMenu extends AbstractContainerMenu {
    public static final int DATA_THROUGHPUT = 0;
    public static final int DATA_CHUNK_LOADED = 1;
    public static final int DATA_CHANNEL_BOUND = 2;
    public static final int DATA_THROUGHPUT_CAP = 3;
    public static final int DATA_PRIORITY = 4;
    public static final int DATA_SURGE = 5;
    public static final int DATA_AVG_1MIN = 6;
    public static final int DATA_AVG_5MIN = 7;
    public static final int DATA_AVG_10MIN = 8;
    /** Storage stored FE, low 32 bits. Always 0 for non-storage devices. */
    public static final int DATA_STORED_LOW = 9;
    /** Storage stored FE, high 32 bits. Always 0 for non-storage devices. */
    public static final int DATA_STORED_HIGH = 10;

    /** 30 graph buckets per window (1-min / 5-min / 10-min). Bucket 0 = oldest, 29 = newest. */
    public static final int GRAPH_BUCKETS = 30;
    public static final int DATA_GRAPH_1M_BASE = 11;
    public static final int DATA_GRAPH_5M_BASE = DATA_GRAPH_1M_BASE + GRAPH_BUCKETS;
    public static final int DATA_GRAPH_10M_BASE = DATA_GRAPH_5M_BASE + GRAPH_BUCKETS;
    public static final int DATA_SIZE = DATA_GRAPH_10M_BASE + GRAPH_BUCKETS;

    private final BlockPos pos;
    private final ContainerData data;
    private final @Nullable UUID boundChannelId;
    private final String channelName;
    private final String channelOwner;
    private final String deviceName;
    /** Storage capacity in FE; 0 when this device isn't a storage. */
    private final long storageCapacity;

    /** Server-side. */
    public PhotonNodeMenu(int containerId, Inventory inv, BlockPos pos, ContainerData data,
                          @Nullable UUID boundChannelId, String channelName, String channelOwner,
                          String deviceName, long storageCapacity) {
        super(QuantumChanneling.PHOTON_NODE_MENU.get(), containerId);
        this.pos = pos;
        this.data = data;
        this.boundChannelId = boundChannelId;
        this.channelName = channelName == null ? "" : channelName;
        this.channelOwner = channelOwner == null ? "" : channelOwner;
        this.deviceName = deviceName == null ? "" : deviceName;
        this.storageCapacity = storageCapacity;
        addDataSlots(data);
    }

    /** Client-side. */
    public PhotonNodeMenu(int containerId, Inventory inv, FriendlyByteBuf buf) {
        this(containerId, inv,
                buf.readBlockPos(),
                new SimpleContainerData(DATA_SIZE),
                buf.readBoolean() ? buf.readUUID() : null,
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readLong());
    }

    public BlockPos getBlockPos() { return pos; }
    public int getThroughput() { return data.get(DATA_THROUGHPUT); }
    public boolean isChunkLoaded() { return data.get(DATA_CHUNK_LOADED) != 0; }
    public boolean isChannelBound() { return data.get(DATA_CHANNEL_BOUND) != 0; }
    public int getThroughputCap() { return data.get(DATA_THROUGHPUT_CAP); }
    public int getPriority() { return data.get(DATA_PRIORITY); }
    public boolean isSurge() { return data.get(DATA_SURGE) != 0; }
    public int getAvg1Min() { return data.get(DATA_AVG_1MIN); }
    public int getAvg5Min() { return data.get(DATA_AVG_5MIN); }
    public int getAvg10Min() { return data.get(DATA_AVG_10MIN); }
    /** Reads one of the 30 buckets for the chosen window. {@code windowMinutes} = 1, 5, or 10. */
    public int getGraphBucket(int windowMinutes, int bucketIdx) {
        if (bucketIdx < 0 || bucketIdx >= GRAPH_BUCKETS) return 0;
        int base = switch (windowMinutes) {
            case 1  -> DATA_GRAPH_1M_BASE;
            case 5  -> DATA_GRAPH_5M_BASE;
            case 10 -> DATA_GRAPH_10M_BASE;
            default -> DATA_GRAPH_10M_BASE;
        };
        return data.get(base + bucketIdx);
    }
    /** Returns the storage stored amount in FE (reassembled long). Zero for non-storage devices. */
    public long getStoredLong() {
        long low = data.get(DATA_STORED_LOW) & 0xFFFFFFFFL;
        long high = ((long) data.get(DATA_STORED_HIGH)) << 32;
        return high | low;
    }
    public long getStorageCapacity() { return storageCapacity; }
    public boolean isStorage() { return storageCapacity > 0; }
    public @Nullable UUID getBoundChannelId() { return boundChannelId; }
    public String getChannelName() { return channelName; }
    public String getChannelOwner() { return channelOwner; }
    public String getDeviceName() { return deviceName; }

    @Override
    public boolean stillValid(Player player) {
        double dx = pos.getX() + 0.5 - player.getX();
        double dy = pos.getY() + 0.5 - player.getY();
        double dz = pos.getZ() + 0.5 - player.getZ();
        return dx * dx + dy * dy + dz * dz <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }
}
