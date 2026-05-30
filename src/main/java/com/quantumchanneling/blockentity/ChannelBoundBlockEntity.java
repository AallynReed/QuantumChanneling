package com.quantumchanneling.blockentity;

import com.quantumchanneling.network.QuantumNetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Common base for blocks that belong to a quantum channel. Stores the channel id and keeps the
 * world-saved network membership in sync on bind/unbind and on chunk unload.
 */
public abstract class ChannelBoundBlockEntity extends BlockEntity {
    private @Nullable UUID channelId;

    protected ChannelBoundBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public @Nullable UUID getChannelId() {
        return channelId;
    }

    /** Returns true if the binding actually changed. Updates {@link QuantumNetworkData} server-side. */
    public boolean setChannelId(@Nullable UUID id) {
        if (Objects.equals(channelId, id)) return false;
        UUID old = channelId;
        channelId = id;

        if (level != null && !level.isClientSide && level.getServer() != null) {
            QuantumNetworkData data = QuantumNetworkData.get(level.getServer());
            GlobalPos here = GlobalPos.of(level.dimension(), getBlockPos());
            if (old != null) data.removeMember(old, here);
            if (id != null) data.addMember(id, here);
        }

        setChanged();
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (channelId != null) tag.putUUID("ChannelId", channelId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        channelId = tag.hasUUID("ChannelId") ? tag.getUUID("ChannelId") : null;
    }

    public GlobalPos globalPos() {
        Level l = Objects.requireNonNull(level, "BlockEntity not yet placed in a Level");
        return GlobalPos.of(l.dimension(), getBlockPos());
    }
}
