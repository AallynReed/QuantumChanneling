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

/**
 * Channel-level item-transfer settings. Holds the master enable, batch size, and a dynamic
 * collection of subchannels keyed by stable {@link UUID}. The void filter has moved off the
 * channel and onto each emitter (so different emitters can void different things).
 *
 * <p>The {@link #maskVersion} counter is bumped on every mutation that could change routing
 * outcomes; emitters compare it against their last-seen version to know when to wipe their
 * per-item decision cache. Renames bump it too — cheap, and it keeps callers from having to
 * distinguish "structural" edits from cosmetic ones.
 */
public class ItemChannelConfig {
    public static final int MAX_SUBCHANNELS = 32;
    public static final int DEFAULT_BATCH = 64;
    public static final int MIN_BATCH = 1;
    /** Up to 9 stacks per cycle (one hotbar row). */
    public static final int MAX_BATCH = 64 * 9;

    private boolean enabled = false;
    private int batchSize = DEFAULT_BATCH;
    /** LinkedHashMap so {@code subchannels()} returns insertion-ordered for stable UI listing. */
    private final LinkedHashMap<UUID, ItemSubchannel> subchannels = new LinkedHashMap<>();
    private int maskVersion = 0;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) {
        if (v != enabled) { enabled = v; bump(); }
    }

    public int batchSize() { return batchSize; }
    public void setBatchSize(int b) {
        int v = Math.max(MIN_BATCH, Math.min(MAX_BATCH, b));
        if (v != batchSize) { batchSize = v; bump(); }
    }

    public int maskVersion() { return maskVersion; }
    private void bump() { maskVersion++; }

    public Collection<ItemSubchannel> subchannels() {
        return Collections.unmodifiableCollection(subchannels.values());
    }
    public @Nullable ItemSubchannel subchannel(UUID id) { return subchannels.get(id); }
    public int subchannelCount() { return subchannels.size(); }
    public boolean contains(UUID id) { return subchannels.containsKey(id); }

    /** Creates a new subchannel; returns its {@link UUID} or null when the cap is reached. */
    public @Nullable UUID createSubchannel(String name) {
        if (subchannels.size() >= MAX_SUBCHANNELS) return null;
        UUID id = UUID.randomUUID();
        subchannels.put(id, new ItemSubchannel(id, name));
        bump();
        return id;
    }

    public boolean deleteSubchannel(UUID id) {
        if (subchannels.remove(id) == null) return false;
        bump();
        return true;
    }

    public boolean renameSubchannel(UUID id, String newName) {
        ItemSubchannel s = subchannels.get(id);
        if (s == null) return false;
        s.setName(newName);
        bump();
        return true;
    }

    public boolean setSubchannelFilterMode(UUID id, boolean whitelist) {
        ItemSubchannel s = subchannels.get(id);
        if (s == null) return false;
        if (s.filter().isWhitelist() == whitelist) return false;
        s.filter().setWhitelist(whitelist);
        bump();
        return true;
    }

    public boolean addSubchannelItem(UUID id, ResourceLocation itemId) {
        ItemSubchannel s = subchannels.get(id);
        if (s == null) return false;
        if (!s.filter().add(itemId)) return false;
        bump();
        return true;
    }

    public boolean removeSubchannelItem(UUID id, ResourceLocation itemId) {
        ItemSubchannel s = subchannels.get(id);
        if (s == null) return false;
        if (!s.filter().remove(itemId)) return false;
        bump();
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Enabled", enabled);
        tag.putInt("BatchSize", batchSize);
        tag.putInt("MaskVersion", maskVersion);
        ListTag list = new ListTag();
        for (ItemSubchannel s : subchannels.values()) list.add(s.save());
        tag.put("Subs", list);
        return tag;
    }

    public static ItemChannelConfig load(CompoundTag tag) {
        ItemChannelConfig c = new ItemChannelConfig();
        c.enabled = tag.getBoolean("Enabled");
        if (tag.contains("BatchSize")) c.batchSize =
                Math.max(MIN_BATCH, Math.min(MAX_BATCH, tag.getInt("BatchSize")));
        if (tag.contains("MaskVersion")) c.maskVersion = tag.getInt("MaskVersion");
        if (tag.contains("Subs", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Subs", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ItemSubchannel s = ItemSubchannel.load(list.getCompound(i));
                c.subchannels.put(s.id(), s);
            }
        }
        return c;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeVarInt(batchSize);
        buf.writeVarInt(maskVersion);
        buf.writeVarInt(subchannels.size());
        for (ItemSubchannel s : subchannels.values()) s.write(buf);
    }

    public static ItemChannelConfig read(FriendlyByteBuf buf) {
        ItemChannelConfig c = new ItemChannelConfig();
        c.enabled = buf.readBoolean();
        c.batchSize = Math.max(MIN_BATCH, Math.min(MAX_BATCH, buf.readVarInt()));
        c.maskVersion = buf.readVarInt();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            ItemSubchannel s = ItemSubchannel.read(buf);
            c.subchannels.put(s.id(), s);
        }
        return c;
    }

    /** Restores this instance from another. Used by NBT load on the channel. */
    public void copyFrom(ItemChannelConfig other) {
        if (other == null) return;
        this.enabled = other.enabled;
        this.batchSize = other.batchSize;
        this.maskVersion = other.maskVersion;
        this.subchannels.clear();
        this.subchannels.putAll(other.subchannels);
    }

    /**
     * Returns a {@link Map} keyed by subchannel name for clients that need name-based lookup
     * (e.g. UI showing user-typed names). The returned map is not live.
     */
    public Map<String, ItemSubchannel> byName() {
        LinkedHashMap<String, ItemSubchannel> out = new LinkedHashMap<>();
        for (ItemSubchannel s : subchannels.values()) out.put(s.name(), s);
        return out;
    }
}
