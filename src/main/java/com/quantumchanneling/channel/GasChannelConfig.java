package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Gas analog of {@link FluidChannelConfig}. Full subchannel model. */
public class GasChannelConfig {
    public static final int MAX_SUBCHANNELS = 32;
    public static final int DEFAULT_BATCH = 1000;
    public static final int MIN_BATCH = 1;
    public static final int MAX_BATCH = 64_000;

    /** Per-channel enable removed — participation is now per-device on the BE side. */
    private int batchSize = DEFAULT_BATCH;
    private final LinkedHashMap<UUID, GasSubchannel> subchannels = new LinkedHashMap<>();
    private int maskVersion = 0;

    public int batchSize() { return batchSize; }
    public void setBatchSize(int b) {
        int v = Math.max(MIN_BATCH, Math.min(MAX_BATCH, b));
        if (v != batchSize) { batchSize = v; bump(); }
    }

    public int maskVersion() { return maskVersion; }
    private void bump() { maskVersion++; }

    public Collection<GasSubchannel> subchannels() { return Collections.unmodifiableCollection(subchannels.values()); }
    public @Nullable GasSubchannel subchannel(UUID id) { return subchannels.get(id); }
    public int subchannelCount() { return subchannels.size(); }
    public boolean contains(UUID id) { return subchannels.containsKey(id); }

    public @Nullable UUID createSubchannel(String name) {
        if (subchannels.size() >= MAX_SUBCHANNELS) return null;
        UUID id = UUID.randomUUID();
        subchannels.put(id, new GasSubchannel(id, name));
        bump();
        return id;
    }
    public boolean deleteSubchannel(UUID id) {
        if (subchannels.remove(id) == null) return false;
        bump();
        return true;
    }
    public boolean setSubchannelFilterMode(UUID id, boolean whitelist) {
        GasSubchannel s = subchannels.get(id);
        if (s == null) return false;
        if (s.filter().isWhitelist() == whitelist) return false;
        s.filter().setWhitelist(whitelist);
        bump();
        return true;
    }
    public boolean addSubchannelGas(UUID id, ResourceLocation gasId) {
        GasSubchannel s = subchannels.get(id);
        if (s == null) return false;
        if (!s.filter().add(gasId)) return false;
        bump();
        return true;
    }
    public boolean removeSubchannelGas(UUID id, ResourceLocation gasId) {
        GasSubchannel s = subchannels.get(id);
        if (s == null) return false;
        if (!s.filter().remove(gasId)) return false;
        bump();
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("BatchSize", batchSize);
        tag.putInt("MaskVersion", maskVersion);
        ListTag list = new ListTag();
        for (GasSubchannel s : subchannels.values()) list.add(s.save());
        tag.put("Subs", list);
        return tag;
    }
    public static GasChannelConfig load(CompoundTag tag) {
        GasChannelConfig c = new GasChannelConfig();
        if (tag.contains("BatchSize")) c.batchSize = Math.max(MIN_BATCH, Math.min(MAX_BATCH, tag.getInt("BatchSize")));
        if (tag.contains("MaskVersion")) c.maskVersion = tag.getInt("MaskVersion");
        if (tag.contains("Subs", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Subs", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                GasSubchannel s = GasSubchannel.load(list.getCompound(i));
                c.subchannels.put(s.id(), s);
            }
        }
        return c;
    }
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(batchSize);
        buf.writeVarInt(maskVersion);
        buf.writeVarInt(subchannels.size());
        for (GasSubchannel s : subchannels.values()) s.write(buf);
    }
    public static GasChannelConfig read(FriendlyByteBuf buf) {
        GasChannelConfig c = new GasChannelConfig();
        c.batchSize = Math.max(MIN_BATCH, Math.min(MAX_BATCH, buf.readVarInt()));
        c.maskVersion = buf.readVarInt();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            GasSubchannel s = GasSubchannel.read(buf);
            c.subchannels.put(s.id(), s);
        }
        return c;
    }
    public void copyFrom(GasChannelConfig other) {
        if (other == null) return;
        this.batchSize = other.batchSize;
        this.maskVersion = other.maskVersion;
        this.subchannels.clear();
        this.subchannels.putAll(other.subchannels);
    }
    public Map<String, GasSubchannel> byName() {
        LinkedHashMap<String, GasSubchannel> out = new LinkedHashMap<>();
        for (GasSubchannel s : subchannels.values()) out.put(s.name(), s);
        return out;
    }
}
