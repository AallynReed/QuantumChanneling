package com.quantumchanneling.block;

import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class PhotonReceiverBlock extends Block implements EntityBlock {
    public PhotonReceiverBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhotonReceiverBlockEntity(pos, state);
    }
}
