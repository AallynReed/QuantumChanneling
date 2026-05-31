package com.quantumchanneling.blockentity;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.ServerConfig;
import com.quantumchanneling.block.PhotonStorageBlock;
import com.quantumchanneling.menu.PhotonNodeMenu;
import com.quantumchanneling.channel.QuantumChannel;
import com.quantumchanneling.channel.ChannelData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.UUID;

/**
 * Channel-bound battery. Holds an FE buffer that the network's emitters fill (via
 * {@link #acceptFromChannel}) and the wireless-charging system drains (via {@link #pullForExternal}).
 *
 * <p>Storage is intentionally NOT exposed as an {@link IEnergyStorage} capability to adjacent
 * blocks — energy only enters/exits via the channel. Because of that, the buffer can be a
 * {@code long} rather than the int-bounded {@code IEnergyStorage} contract: capacity is taken
 * from {@link Config#storageCapacities} and goes up to {@code Long.MAX_VALUE} (~9.2 quintillion FE).
 * Per-transaction amounts still arrive as {@code int} from the channel layer (push/pull is
 * bounded by Integer.MAX_VALUE per call), but cumulative {@code stored} can keep climbing.</p>
 *
 * <p>The block-state {@link PhotonStorageBlock#LEVEL} (0..4) is recomputed after every mutation so
 * the model can paint a visible fill bar that grows as the buffer fills.</p>
 */
public class PhotonStorageBlockEntity extends ChannelBoundBlockEntity implements MenuProvider {
    /** Number of fill buckets exposed to the model — 0 empty, LEVEL_BUCKETS-1 full. */
    private static final int LEVEL_BUCKETS = 9;

    private final long capacity;
    private long stored = 0;
    /**
     * FE extracted this server tick — counted toward {@link #effectiveBudget(int)} so the device
     * cap throttles output. Intake (acceptFromChannel) is intentionally uncounted: incoming energy
     * is always uncapped, the cap only limits how fast the buffer drains.
     */
    private int extractedThisTick = 0;
    private int lastTickExtracted = 0;

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                // The screen reads DATA_THROUGHPUT as a 32-bit signed int. Storage may exceed that —
                // clamp for display purposes (the actual buffer keeps the real value).
                case PhotonNodeMenu.DATA_THROUGHPUT -> (int) Math.min(stored, Integer.MAX_VALUE);
                case PhotonNodeMenu.DATA_CHUNK_LOADED -> isChunkLoadForced() ? 1 : 0;
                case PhotonNodeMenu.DATA_CHANNEL_BOUND -> getChannelId() != null ? 1 : 0;
                case PhotonNodeMenu.DATA_THROUGHPUT_CAP -> getThroughputCap();
                case PhotonNodeMenu.DATA_PRIORITY -> getPriority();
                case PhotonNodeMenu.DATA_SURGE -> isSurgeMode() ? 1 : 0;
                // Split the long buffer into two ints for the client menu (which is int-based).
                case PhotonNodeMenu.DATA_STORED_LOW -> (int) (stored & 0xFFFFFFFFL);
                case PhotonNodeMenu.DATA_STORED_HIGH -> (int) (stored >>> 32);
                default -> 0;
            };
        }
        @Override public void set(int i, int v) {}
        @Override public int getCount() { return PhotonNodeMenu.DATA_SIZE; }
    };

    public PhotonStorageBlockEntity(BlockPos pos, BlockState state) {
        super(QuantumChanneling.PHOTON_STORAGE_BE.get(), pos, state);
        int tier = state.getBlock() instanceof PhotonStorageBlock psb ? psb.getTier() : 1;
        long[] caps = ServerConfig.storageCapacities;
        this.capacity = (caps != null && tier - 1 < caps.length) ? caps[tier - 1] : (1L << 16);
    }

    public long getStored() { return stored; }
    public long getCapacity() { return capacity; }

    /** Channel-side push from an emitter. Returns the amount actually accepted (int-bounded). */
    public int acceptFromChannel(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        long room = capacity - stored;
        if (room <= 0) return 0;
        int actual = (int) Math.min((long) amount, room);
        if (actual <= 0) return 0;
        if (!simulate) {
            stored += actual;
            setChanged();
            updateLevelState();
        }
        return actual;
    }

    /**
     * Charging-side / receiver-side pull. Drains up to {@code want} FE from the buffer, capped by
     * the device's per-tick output budget (which honors {@code throughputCap} and Overdrive).
     */
    public int pullForExternal(int want) {
        if (want <= 0) return 0;
        // effectiveBudget(0) ⇒ if no per-device cap and no global default, returns MAX_VALUE.
        // Overdrive (surgeMode) also returns MAX_VALUE — uncaps the drain rate.
        int budget = effectiveBudget(0);
        int room = Math.max(0, budget - extractedThisTick);
        if (room <= 0) return 0;
        int allowed = Math.min(want, room);
        int actual = (int) Math.min((long) allowed, stored);
        if (actual <= 0) return 0;
        stored -= actual;
        extractedThisTick += actual;
        setChanged();
        updateLevelState();
        return actual;
    }

    /** FE drained from this storage during the previous server tick (for UI display). */
    public int getLastTickExtracted() { return lastTickExtracted; }

    /** Server-side tick — resets the per-tick extraction counter. Called by the block's ticker. */
    public void serverTick(ServerLevel level) {
        lastTickExtracted = extractedThisTick;
        extractedThisTick = 0;
    }

    /**
     * Writes the bucket index 0..LEVEL_BUCKETS-1 into the block state if it changed. Server-side
     * only. Uses flag 2 so the block-state update is purely cosmetic — no neighbor wake.
     */
    private void updateLevelState() {
        if (level == null || level.isClientSide) return;
        int newLevel = capacity <= 0 ? 0
                : (int) Math.min(LEVEL_BUCKETS - 1, stored * LEVEL_BUCKETS / capacity);
        BlockState s = getBlockState();
        if (!(s.getBlock() instanceof PhotonStorageBlock)) return;
        if (s.getValue(PhotonStorageBlock.LEVEL) != newLevel) {
            level.setBlock(worldPosition, s.setValue(PhotonStorageBlock.LEVEL, newLevel), 2);
        }
    }

    @Override
    public Component getDisplayName() { return getBlockState().getBlock().getName(); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
        return new PhotonNodeMenu(id, inv, getBlockPos(), containerData,
                getChannelId(), resolveChannelName(), resolveChannelOwner(), getCustomName(), capacity);
    }

    public String resolveChannelName() {
        UUID id = getChannelId();
        if (id == null || !(level instanceof ServerLevel sl)) return "";
        QuantumChannel net = ChannelData.get(sl.getServer()).getChannel(id);
        return net == null ? "" : net.name();
    }

    public String resolveChannelOwner() {
        UUID id = getChannelId();
        if (id == null || !(level instanceof ServerLevel sl)) return "";
        QuantumChannel net = ChannelData.get(sl.getServer()).getChannel(id);
        return net == null ? "" : net.ownerName();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Energy", stored);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stored = tag.getLong("Energy");
        if (stored < 0) stored = 0;
        // Capacity isn't known at load() time before the constructor runs, so clamp lazily in
        // accept/pull. (The constructor already ran by the time load() is invoked.)
        if (stored > capacity) stored = capacity;
    }
}
