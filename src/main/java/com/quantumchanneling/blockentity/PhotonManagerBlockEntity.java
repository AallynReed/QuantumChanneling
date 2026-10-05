package com.quantumchanneling.blockentity;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.menu.PhotonNodeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Channel manager. Its mere presence (loaded) on a channel enables wireless charging; without
 * one, the channel refuses to dispense FE to subscribed players. No energy capability — it's a
 * pure gate.
 */
public class PhotonManagerBlockEntity extends ChannelBoundBlockEntity {

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                // For the Manager, "throughput" = FE dispensed to wireless-charging subscribers last tick.
                case PhotonNodeMenu.DATA_THROUGHPUT -> lastTickChargeRate();
                case PhotonNodeMenu.DATA_CHUNK_LOADED -> isChunkLoadForced() ? 1 : 0;
                case PhotonNodeMenu.DATA_CHANNEL_BOUND -> getChannelId() != null ? 1 : 0;
                case PhotonNodeMenu.DATA_THROUGHPUT_CAP -> getThroughputCap();
                case PhotonNodeMenu.DATA_PRIORITY -> getPriority();
                case PhotonNodeMenu.DATA_SURGE -> isSurgeMode() ? 1 : 0;
                default -> 0;
            };
        }
        @Override public void set(int i, int v) {}
        @Override public int getCount() { return PhotonNodeMenu.DATA_SIZE; }
    };

    private int lastTickChargeRate() {
        if (!(level instanceof ServerLevel sl) || getChannelId() == null) return 0;
        return ChannelData.get(sl.getServer()).getLastTickChargeRate(getChannelId());
    }

    public PhotonManagerBlockEntity(BlockPos pos, BlockState state) {
        super(QuantumChanneling.PHOTON_MANAGER_BE.get(), pos, state);
    }

    @Override
    protected ContainerData menuData() { return containerData; }
}
