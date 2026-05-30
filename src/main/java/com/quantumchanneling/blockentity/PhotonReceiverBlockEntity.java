package com.quantumchanneling.blockentity;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class PhotonReceiverBlockEntity extends ChannelBoundBlockEntity {
    public PhotonReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(QuantumChanneling.PHOTON_RECEIVER_BE.get(), pos, state);
    }
}
