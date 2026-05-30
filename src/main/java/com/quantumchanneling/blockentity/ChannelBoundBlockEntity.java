package com.quantumchanneling.blockentity;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.channel.ChannelData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.world.ForgeChunkManager;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public abstract class ChannelBoundBlockEntity extends BlockEntity {
    public static final int CAP_UNLIMITED = -1;

    private @Nullable UUID channelId;
    private boolean forceChunkLoaded = false;
    /** Per-device per-tick throughput cap. -1 = use the global Config value. */
    private int throughputCap = CAP_UNLIMITED;

    protected ChannelBoundBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public @Nullable UUID getChannelId() { return channelId; }

    public boolean setChannelId(@Nullable UUID id) {
        if (Objects.equals(channelId, id)) return false;
        UUID old = channelId;
        channelId = id;
        if (level != null && !level.isClientSide && level.getServer() != null) {
            ChannelData data = ChannelData.get(level.getServer());
            GlobalPos here = GlobalPos.of(level.dimension(), getBlockPos());
            if (old != null) data.removeMember(old, here);
            if (id != null) data.addMember(id, here);
        }
        setChanged();
        return true;
    }

    public boolean isChunkLoadForced() { return forceChunkLoaded; }

    public void setChunkLoadForced(boolean value) {
        if (forceChunkLoaded == value) return;
        forceChunkLoaded = value;
        applyForcedChunk();
        setChanged();
    }

    public int getThroughputCap() { return throughputCap; }

    public void setThroughputCap(int cap) {
        int v = cap < 0 ? CAP_UNLIMITED : cap;
        if (v == throughputCap) return;
        throughputCap = v;
        setChanged();
    }

    /** Resolve the actual per-tick budget given a global default. */
    public int effectiveBudget(int globalDefault) {
        return throughputCap == CAP_UNLIMITED ? globalDefault : throughputCap;
    }

    private void applyForcedChunk() {
        if (!(level instanceof ServerLevel server)) return;
        int cx = getBlockPos().getX() >> 4;
        int cz = getBlockPos().getZ() >> 4;
        ForgeChunkManager.forceChunk(server, QuantumChanneling.MODID, getBlockPos(), cx, cz, forceChunkLoaded, true);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        applyForcedChunk();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (forceChunkLoaded && level instanceof ServerLevel server) {
            int cx = getBlockPos().getX() >> 4;
            int cz = getBlockPos().getZ() >> 4;
            ForgeChunkManager.forceChunk(server, QuantumChanneling.MODID, getBlockPos(), cx, cz, false, true);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (channelId != null) tag.putUUID("ChannelId", channelId);
        if (forceChunkLoaded) tag.putBoolean("ForceChunkLoad", true);
        if (throughputCap != CAP_UNLIMITED) tag.putInt("ThroughputCap", throughputCap);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        channelId = tag.hasUUID("ChannelId") ? tag.getUUID("ChannelId") : null;
        forceChunkLoaded = tag.getBoolean("ForceChunkLoad");
        throughputCap = tag.contains("ThroughputCap") ? tag.getInt("ThroughputCap") : CAP_UNLIMITED;
    }

    public GlobalPos globalPos() {
        Level l = Objects.requireNonNull(level, "BlockEntity not yet placed in a Level");
        return GlobalPos.of(l.dimension(), getBlockPos());
    }
}
