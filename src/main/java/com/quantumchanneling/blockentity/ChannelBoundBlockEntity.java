package com.quantumchanneling.blockentity;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.channel.ChannelData;
import com.quantumchanneling.channel.ItemChannelConfig;
import com.quantumchanneling.channel.ResourceMode;
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
    /** Initial per-device throughput cap (FE/t). Reset button in the UI restores this value. */
    public static final int DEFAULT_CAP = 1_000_000;

    private @Nullable UUID channelId;
    private boolean forceChunkLoaded = false;
    /** Initial value = {@link #DEFAULT_CAP}. The UI shows the value and lets the user edit / reset / clear it. */
    private int throughputCap = DEFAULT_CAP;
    /** Higher = receives energy first when an emitter distributes across receivers. */
    private int priority = 0;
    /** When true, ignores throughputCap and runs unbounded (effectiveBudget = MAX_INT). */
    private boolean surgeMode = false;
    /** User-defined label shown in the device's title bar and Nodes list. Empty = use block name. */
    private String customName = "";

    /** Which resource this device handles. Default ENERGY preserves legacy behaviour. */
    private ResourceMode resourceMode = ResourceMode.ENERGY;
    /**
     * Sub-channel binding for items-mode routing. -1 = main trunk (any item passing the channel's
     * main filter); 0..{@link ItemChannelConfig#SUBCHANNEL_COUNT}-1 = bound to that sub-channel's filter.
     */
    private int subchannelIndex = -1;

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

    public int getPriority() { return priority; }
    public void setPriority(int p) { if (p != priority) { priority = p; setChanged(); } }

    public boolean isSurgeMode() { return surgeMode; }
    public void setSurgeMode(boolean v) { if (v != surgeMode) { surgeMode = v; setChanged(); } }

    public String getCustomName() { return customName; }
    public void setCustomName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() > 32) trimmed = trimmed.substring(0, 32);
        if (!trimmed.equals(customName)) { customName = trimmed; setChanged(); }
    }

    public ResourceMode getResourceMode() { return resourceMode; }
    public void setResourceMode(ResourceMode m) {
        if (m == null || m == resourceMode) return;
        resourceMode = m;
        setChanged();
    }

    /** -1 = main trunk; 0..{@link ItemChannelConfig#SUBCHANNEL_COUNT}-1 = bound sub-channel. */
    public int getSubchannelIndex() { return subchannelIndex; }
    public void setSubchannelIndex(int idx) {
        int v = Math.max(-1, Math.min(ItemChannelConfig.SUBCHANNEL_COUNT - 1, idx));
        if (v != subchannelIndex) { subchannelIndex = v; setChanged(); }
    }

    /**
     * Resolves the actual per-tick FE budget. Surge overrides the per-device cap entirely. Otherwise:
     * the per-device cap takes precedence (when set), then the config global ceiling. A globalDefault
     * of {@code <= 0} is treated as unlimited (the default config value is 0 = unlimited).
     */
    public int effectiveBudget(int globalDefault) {
        if (surgeMode) return Integer.MAX_VALUE;
        if (throughputCap != CAP_UNLIMITED) return throughputCap;
        return globalDefault <= 0 ? Integer.MAX_VALUE : globalDefault;
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
        // If a remote-unbind happened while this BE was unloaded, the channel won't list us
        // anymore. Drop the stale channelId so the menu reads cleanly as "Not bound".
        if (channelId != null && level instanceof ServerLevel sl) {
            ChannelData data = ChannelData.get(sl.getServer());
            com.quantumchanneling.channel.QuantumChannel ch = data.getChannel(channelId);
            GlobalPos here = GlobalPos.of(sl.dimension(), getBlockPos());
            if (ch == null || !ch.members().contains(here)) {
                channelId = null;
                setChanged();
            }
        }
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

    /**
     * Called from {@code Block.onRemove} when the block was actually broken or replaced (not just
     * unloaded). Removes this device from the channel's member set so the network stops trying to
     * route through a dead position.
     */
    public void unbindFromChannelOnBreak() {
        if (channelId != null && level instanceof ServerLevel sl) {
            ChannelData data = ChannelData.get(sl.getServer());
            data.removeMember(channelId, GlobalPos.of(sl.dimension(), getBlockPos()));
            channelId = null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (channelId != null) tag.putUUID("ChannelId", channelId);
        if (forceChunkLoaded) tag.putBoolean("ForceChunkLoad", true);
        if (throughputCap != CAP_UNLIMITED) tag.putInt("ThroughputCap", throughputCap);
        if (priority != 0) tag.putInt("Priority", priority);
        if (surgeMode) tag.putBoolean("Surge", true);
        if (!customName.isEmpty()) tag.putString("CustomName", customName);
        if (resourceMode != ResourceMode.ENERGY) tag.putString("ResourceMode", resourceMode.name());
        if (subchannelIndex != -1) tag.putInt("SubchannelIndex", subchannelIndex);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        channelId = tag.hasUUID("ChannelId") ? tag.getUUID("ChannelId") : null;
        forceChunkLoaded = tag.getBoolean("ForceChunkLoad");
        throughputCap = tag.contains("ThroughputCap") ? tag.getInt("ThroughputCap") : CAP_UNLIMITED;
        priority = tag.getInt("Priority");
        surgeMode = tag.getBoolean("Surge");
        customName = tag.contains("CustomName") ? tag.getString("CustomName") : "";
        resourceMode = ResourceMode.fromName(tag.getString("ResourceMode"));
        subchannelIndex = tag.contains("SubchannelIndex") ? tag.getInt("SubchannelIndex") : -1;
        if (subchannelIndex < -1 || subchannelIndex >= ItemChannelConfig.SUBCHANNEL_COUNT) subchannelIndex = -1;
    }

    public GlobalPos globalPos() {
        Level l = Objects.requireNonNull(level, "BlockEntity not yet placed in a Level");
        return GlobalPos.of(l.dimension(), getBlockPos());
    }
}
