package com.quantumchanneling.blockentity;

import com.quantumchanneling.channel.ResourceMode;
import com.quantumchanneling.menu.PhotonNodeMenu;

/**
 * Per-second throughput samples (sum over 20 ticks) for energy, items, fluids and gas, kept for 60
 * minutes. Transient — never saved, so reopened worlds start with an empty graph.
 */
final class ThroughputHistory {
    private static final int SECONDS = 3600;
    private static final int BANKS = 4;

    private final int[][] samples = new int[BANKS][SECONDS];
    private final int[] accumulators = new int[BANKS];
    private int head;
    private int filled;
    private int tickCounter;

    /** One tick's amounts; commits a sample every 20 ticks. */
    void record(int fe, int items, int fluids, int gas) {
        accumulators[0] += fe;
        accumulators[1] += items;
        accumulators[2] += fluids;
        accumulators[3] += gas;
        if (++tickCounter < 20) return;
        for (int b = 0; b < BANKS; b++) {
            samples[b][head] = accumulators[b];
            accumulators[b] = 0;
        }
        head = (head + 1) % SECONDS;
        if (filled < SECONDS) filled++;
        tickCounter = 0;
    }

    private int[] bank(ResourceMode mode) {
        return switch (mode) {
            case ITEMS -> samples[1];
            case FLUIDS -> samples[2];
            case GASES -> samples[3];
            default -> samples[0];
        };
    }

    /** Average per second over the last {@code seconds}, in the unit of {@code mode}. */
    int averagePerSecond(int seconds, ResourceMode mode) {
        if (filled == 0 || seconds <= 0) return 0;
        int[] hist = bank(mode);
        int count = Math.min(seconds, filled);
        long sum = 0;
        for (int i = 0; i < count; i++) sum += hist[(head - 1 - i + SECONDS) % SECONDS];
        return (int) (sum / count);
    }

    /** Average of bucket {@code bucketIdx} of {@code bucketCount} spanning the last {@code windowSecs}; 0 = oldest. */
    int bucket(int windowSecs, int bucketIdx, int bucketCount, ResourceMode mode) {
        if (filled == 0 || windowSecs <= 0 || bucketCount <= 0) return 0;
        int[] hist = bank(mode);
        int perBucket = Math.max(1, windowSecs / bucketCount);
        int offset = (bucketCount - 1 - bucketIdx) * perBucket;
        long sum = 0;
        int count = 0;
        for (int i = 0; i < perBucket; i++) {
            int age = offset + i;
            if (age >= filled) continue;
            sum += hist[(head - 1 - age + SECONDS) % SECONDS];
            count++;
        }
        return count == 0 ? 0 : (int) (sum / count);
    }

    /** Resolves a menu data slot in one of the energy graph ranges to its bucket value. */
    int graphSlot(int slot) {
        int n = PhotonNodeMenu.GRAPH_BUCKETS;
        if (slot >= PhotonNodeMenu.DATA_GRAPH_1M_BASE && slot < PhotonNodeMenu.DATA_GRAPH_1M_BASE + n) {
            return bucket(60, slot - PhotonNodeMenu.DATA_GRAPH_1M_BASE, n, ResourceMode.ENERGY);
        }
        if (slot >= PhotonNodeMenu.DATA_GRAPH_5M_BASE && slot < PhotonNodeMenu.DATA_GRAPH_5M_BASE + n) {
            return bucket(300, slot - PhotonNodeMenu.DATA_GRAPH_5M_BASE, n, ResourceMode.ENERGY);
        }
        if (slot >= PhotonNodeMenu.DATA_GRAPH_10M_BASE && slot < PhotonNodeMenu.DATA_GRAPH_10M_BASE + n) {
            return bucket(600, slot - PhotonNodeMenu.DATA_GRAPH_10M_BASE, n, ResourceMode.ENERGY);
        }
        return 0;
    }
}
