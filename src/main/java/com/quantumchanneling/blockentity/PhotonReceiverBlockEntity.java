package com.quantumchanneling.blockentity;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.ServerConfig;
import com.quantumchanneling.api.IQuantumSubchannelView.Kind;
import com.quantumchanneling.block.PhotonShape;
import com.quantumchanneling.channel.JournaledInt;
import com.quantumchanneling.channel.QuantumChannel;
import com.quantumchanneling.channel.ResourceMode;
import com.quantumchanneling.menu.PhotonNodeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.EnumMap;

public class PhotonReceiverBlockEntity extends ChannelBoundBlockEntity {
    // FE is moved actively by the receiver (it pushes into adjacent sinks); items, fluids and gas
    // arrive via direct pushes from emitters and are recorded through recordRouted.
    private final JournaledInt feForwarded = new JournaledInt();
    private int lastTickFE = 0;
    private final EnumMap<Kind, int[]> routed = new EnumMap<>(Kind.class);

    private final ThroughputHistory history = new ThroughputHistory();

    /** Energy pushed into a receiver is forwarded to its neighbours, like energy arriving from the channel. */
    private final EnergyHandler energyHandler = new EnergyHandler() {
        @Override public long getAmountAsLong() { return 0; }
        @Override public long getCapacityAsLong() { return effectiveBudget(ServerConfig.receiverOutputRate); }
        @Override public int insert(int amount, TransactionContext tx) { return acceptAndForward(amount, tx); }
        // Receivers don't buffer, so there's nothing to extract.
        @Override public int extract(int amount, TransactionContext tx) { return 0; }
    };

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case PhotonNodeMenu.DATA_THROUGHPUT        -> lastTickFE;
                case PhotonNodeMenu.DATA_THROUGHPUT_ITEMS  -> getLastTickItems();
                case PhotonNodeMenu.DATA_THROUGHPUT_FLUIDS -> getLastTickFluids();
                case PhotonNodeMenu.DATA_THROUGHPUT_GAS    -> getLastTickGas();
                // Receivers don't initiate routing, so they never raise the loop flag.
                case PhotonNodeMenu.DATA_LOOP_WARNING      -> 0;
                case PhotonNodeMenu.DATA_CHUNK_LOADED      -> isChunkLoadForced() ? 1 : 0;
                case PhotonNodeMenu.DATA_CHANNEL_BOUND     -> getChannelId() != null ? 1 : 0;
                case PhotonNodeMenu.DATA_THROUGHPUT_CAP    -> getThroughputCap();
                case PhotonNodeMenu.DATA_PRIORITY          -> getPriority();
                case PhotonNodeMenu.DATA_SURGE             -> isSurgeMode() ? 1 : 0;
                case PhotonNodeMenu.DATA_AVG_1MIN          -> history.averagePerSecond(60, ResourceMode.ENERGY);
                case PhotonNodeMenu.DATA_AVG_5MIN          -> history.averagePerSecond(300, ResourceMode.ENERGY);
                case PhotonNodeMenu.DATA_AVG_10MIN         -> history.averagePerSecond(600, ResourceMode.ENERGY);
                default -> history.graphSlot(index);
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return PhotonNodeMenu.DATA_SIZE; }
    };

    public PhotonReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(QuantumChanneling.PHOTON_RECEIVER_BE.get(), pos, state);
        // [0] = this tick, [1] = last tick
        for (Kind k : Kind.values()) routed.put(k, new int[2]);
    }

    public EnergyHandler energyHandler() { return energyHandler; }

    public int getLastTickThroughput() { return lastTickFE; }
    public int getLastTickItems()      { return routed.get(Kind.ITEM)[1]; }
    public int getLastTickFluids()     { return routed.get(Kind.FLUID)[1]; }
    public int getLastTickGas()        { return routed.get(Kind.GAS)[1]; }

    /** Called by the router once a push into this receiver's neighbours has committed. */
    public void recordRouted(Kind kind, int amount) {
        if (amount > 0) routed.get(kind)[0] += amount;
    }

    public int getAveragePerSecond(int seconds, ResourceMode mode) {
        return history.averagePerSecond(seconds, mode);
    }

    public int getGraphBucket(int windowSecs, int bucketIdx, int bucketCount, ResourceMode mode) {
        return history.bucket(windowSecs, bucketIdx, bucketCount, mode);
    }

    @Override
    protected ContainerData menuData() { return containerData; }

    public void serverTick(ServerLevel level) {
        lastTickFE = feForwarded.get();
        feForwarded.reset();
        for (int[] r : routed.values()) {
            r[1] = r[0];
            r[0] = 0;
        }
        history.record(lastTickFE, getLastTickItems(), getLastTickFluids(), getLastTickGas());

        // Re-scan adjacency every second, staggered by position hash. See the emitter for why.
        if ((level.getGameTime() + Math.floorMod(worldPosition.hashCode(), 20)) % 20 == 0) {
            BlockState state = getBlockState();
            BlockState updated = PhotonShape.refreshConnections(level, worldPosition, state);
            if (updated != state) level.setBlock(worldPosition, updated, 2);
        }

        // Items/fluids/gas: emitters push straight into our neighbours — nothing to do here.
        pullFromChannelStorageAndForward(level);
    }

    /**
     * Tops up adjacent machines from channel storage when emitter input alone wasn't enough this
     * tick. Emitter-pushed energy arrives first (during the emitters' ticks), so this only covers
     * the shortfall. Storage is drawn for exactly what the neighbours accept, inside one transaction.
     */
    private void pullFromChannelStorageAndForward(ServerLevel level) {
        if (!passesRedstoneGate()) return;
        QuantumChannel channel = boundChannel();
        if (channel == null) return;
        int room = effectiveBudget(ServerConfig.receiverOutputRate) - feForwarded.get();
        if (room <= 0) return;
        try (Transaction tx = Transaction.openRoot()) {
            int available;
            try (Transaction probe = Transaction.open(tx)) {
                available = pullFromChannelStorage(level, channel, room, probe);
            }
            if (available <= 0) return;
            int delivered = acceptAndForward(available, tx);
            if (delivered <= 0 || pullFromChannelStorage(level, channel, delivered, tx) != delivered) return;
            tx.commit();
        }
    }

    /** Drains up to {@code want} FE from loaded Photon Storage on this channel. */
    private static int pullFromChannelStorage(ServerLevel level, QuantumChannel channel, int want, TransactionContext tx) {
        int collected = 0;
        for (GlobalPos gp : channel.members()) {
            if (collected >= want) break;
            ServerLevel target = level.getServer().getLevel(gp.dimension());
            if (target == null || !target.isLoaded(gp.pos())) continue;
            if (target.getBlockEntity(gp.pos()) instanceof PhotonStorageBlockEntity storage) {
                collected += storage.pullForExternal(want - collected, tx);
            }
        }
        return collected;
    }

    /** Pushes up to {@code amount} FE into adjacent energy handlers inside {@code tx}. */
    public int acceptAndForward(int amount, TransactionContext tx) {
        if (!passesRedstoneGate() || !(level instanceof ServerLevel server)) return 0;
        int budget = Math.min(amount, effectiveBudget(ServerConfig.receiverOutputRate) - feForwarded.get());
        if (budget <= 0) return 0;

        int delivered = 0;
        for (Direction side : Direction.values()) {
            if (delivered >= budget) break;
            BlockPos neighborPos = worldPosition.relative(side);
            // Skip sibling photon devices — energy routes through the channel, never device-to-device.
            if (server.getBlockEntity(neighborPos) instanceof ChannelBoundBlockEntity) continue;
            EnergyHandler sink = server.getCapability(Capabilities.Energy.BLOCK, neighborPos, side.getOpposite());
            if (sink == null) continue;
            delivered += sink.insert(budget - delivered, tx);
        }
        if (delivered > 0) feForwarded.add(delivered, tx);
        return delivered;
    }
}
