package com.quantumchanneling.blockentity;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.ServerConfig;
import com.quantumchanneling.block.PhotonStorageBlock;
import com.quantumchanneling.channel.JournaledInt;
import com.quantumchanneling.channel.JournaledLong;
import com.quantumchanneling.menu.PhotonNodeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Channel-bound battery. Holds an FE buffer that the channel's emitters fill (via
 * {@link #acceptFromChannel}) and receivers / wireless charging drain (via {@link #pullForExternal}).
 *
 * <p>Storage is intentionally NOT exposed as an energy capability — energy only enters and leaves
 * through the channel. That frees the buffer from the int-bounded handler contract: capacity comes
 * from {@link ServerConfig#storageCapacities} and goes up to {@code Long.MAX_VALUE}. Individual
 * transfers are still int-sized, but the stored total keeps climbing.
 *
 * <p>The block-state {@link PhotonStorageBlock#LEVEL} (0..8) follows every committed change so the
 * model shows a fill bar.
 */
public class PhotonStorageBlockEntity extends ChannelBoundBlockEntity {
    /** Number of fill buckets exposed to the model — 0 empty, LEVEL_BUCKETS-1 full. */
    private static final int LEVEL_BUCKETS = 9;

    private final long capacity;
    private final JournaledLong stored = new JournaledLong(this::onStoredChanged);
    /**
     * FE extracted this tick — counted toward {@link #effectiveBudget(int)} so the device cap
     * throttles output. Intake is uncapped: the cap only limits how fast the buffer drains.
     */
    private final JournaledInt extracted = new JournaledInt();

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            long value = stored.get();
            return switch (index) {
                // The screen reads DATA_THROUGHPUT as an int; the real buffer can exceed that.
                case PhotonNodeMenu.DATA_THROUGHPUT -> (int) Math.min(value, Integer.MAX_VALUE);
                case PhotonNodeMenu.DATA_CHUNK_LOADED -> isChunkLoadForced() ? 1 : 0;
                case PhotonNodeMenu.DATA_CHANNEL_BOUND -> getChannelId() != null ? 1 : 0;
                case PhotonNodeMenu.DATA_THROUGHPUT_CAP -> getThroughputCap();
                case PhotonNodeMenu.DATA_PRIORITY -> getPriority();
                case PhotonNodeMenu.DATA_SURGE -> isSurgeMode() ? 1 : 0;
                // The long buffer travels as two ints.
                case PhotonNodeMenu.DATA_STORED_LOW -> (int) (value & 0xFFFFFFFFL);
                case PhotonNodeMenu.DATA_STORED_HIGH -> (int) (value >>> 32);
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
        this.capacity = tier - 1 < caps.length ? caps[tier - 1] : 1L << 16;
    }

    public long getStored() { return stored.get(); }
    public long getCapacity() { return capacity; }

    /** Channel-side push from an emitter. Returns the amount accepted inside {@code tx}. */
    public int acceptFromChannel(int amount, TransactionContext tx) {
        int accepted = (int) Math.min(amount, capacity - stored.get());
        if (accepted <= 0) return 0;
        stored.add(accepted, tx);
        return accepted;
    }

    /**
     * Receiver / charging-side draw of up to {@code want} FE, capped by the per-tick output budget
     * (which honours the throughput cap and Overdrive).
     */
    public int pullForExternal(int want, TransactionContext tx) {
        int room = effectiveBudget(0) - extracted.get();
        int taken = (int) Math.min(Math.min(want, room), stored.get());
        if (taken <= 0) return 0;
        stored.add(-taken, tx);
        extracted.add(taken, tx);
        return taken;
    }

    public void serverTick(ServerLevel level) {
        extracted.reset();
    }

    private void onStoredChanged() {
        setChanged();
        updateLevelState();
    }

    /** Writes the 0..LEVEL_BUCKETS-1 fill bucket into the block state when it changed. Cosmetic only. */
    private void updateLevelState() {
        if (level == null || level.isClientSide()) return;
        int bucket = capacity <= 0 ? 0 : (int) Math.min(LEVEL_BUCKETS - 1, stored.get() * LEVEL_BUCKETS / capacity);
        BlockState s = getBlockState();
        if (s.getBlock() instanceof PhotonStorageBlock && s.getValue(PhotonStorageBlock.LEVEL) != bucket) {
            level.setBlock(worldPosition, s.setValue(PhotonStorageBlock.LEVEL, bucket), 2);
        }
    }

    @Override
    protected ContainerData menuData() { return containerData; }

    @Override
    protected long menuStorageCapacity() { return capacity; }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("Energy", stored.get());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        stored.set(Math.clamp(input.getLongOr("Energy", 0L), 0L, capacity));
    }
}
