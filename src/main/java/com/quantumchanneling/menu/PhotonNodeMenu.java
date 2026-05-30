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

/**
 * Shared menu for the Photon Emitter and Photon Receiver. Synchronised state is two-track:
 * <ul>
 *   <li>{@link ContainerData} ints carry per-tick state (throughput + chunk-load flag),
 *       automatically broadcast each server tick while the menu is open.</li>
 *   <li>The network name and owner name are written into the open-buf, so the client gets a
 *       one-shot snapshot when the screen opens. Renaming a bound network mid-view will not
 *       reflect until the user reopens — acceptable for now.</li>
 * </ul>
 */
public class PhotonNodeMenu extends AbstractContainerMenu {
    public static final int DATA_THROUGHPUT = 0;
    public static final int DATA_CHUNK_LOADED = 1;
    public static final int DATA_CHANNEL_BOUND = 2;
    public static final int DATA_THROUGHPUT_CAP = 3;
    public static final int DATA_SIZE = 4;

    private final BlockPos pos;
    private final ContainerData data;
    private final String channelName;
    private final String channelOwner;

    /** Server-side: backed by the BE's live {@link ContainerData} and the resolved network info. */
    public PhotonNodeMenu(int containerId, Inventory inv, BlockPos pos, ContainerData data,
                          String channelName, String channelOwner) {
        super(QuantumChanneling.PHOTON_NODE_MENU.get(), containerId);
        this.pos = pos;
        this.data = data;
        this.channelName = channelName == null ? "" : channelName;
        this.channelOwner = channelOwner == null ? "" : channelOwner;
        addDataSlots(data);
    }

    /** Client-side: deserialised from the packet sent by NetworkHooks.openScreen. */
    public PhotonNodeMenu(int containerId, Inventory inv, FriendlyByteBuf buf) {
        this(containerId, inv, buf.readBlockPos(), new SimpleContainerData(DATA_SIZE),
                buf.readUtf(64), buf.readUtf(64));
    }

    public BlockPos getBlockPos() { return pos; }
    public int getThroughput() { return data.get(DATA_THROUGHPUT); }
    public boolean isChunkLoaded() { return data.get(DATA_CHUNK_LOADED) != 0; }
    public boolean isChannelBound() { return data.get(DATA_CHANNEL_BOUND) != 0; }
    public int getThroughputCap() { return data.get(DATA_THROUGHPUT_CAP); }
    public String getChannelName() { return channelName; }
    public String getChannelOwner() { return channelOwner; }

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
