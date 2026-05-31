package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Gas analog of {@link FluidFilter}. Stores plain {@link ResourceLocation}s for both concrete gas
 * ids and gas tag ids — no Mekanism types imported, so this class is safe to load without
 * Mekanism. Tag resolution lives in {@code compat.mekanism.GasIntegration} where the runtime
 * Mekanism tag registry is accessible.
 */
public class GasFilter {
    public static final int MAX_ENTRIES = 256;

    private boolean whitelist;
    private final Set<ResourceLocation> gases = new LinkedHashSet<>();
    private final Set<ResourceLocation> tags = new LinkedHashSet<>();

    public GasFilter() { this(true); }
    public GasFilter(boolean whitelist) { this.whitelist = whitelist; }

    public boolean isWhitelist() { return whitelist; }
    public void setWhitelist(boolean w) { this.whitelist = w; }
    public Set<ResourceLocation> gases() { return Collections.unmodifiableSet(gases); }
    public Set<ResourceLocation> tags() { return Collections.unmodifiableSet(tags); }
    public int size() { return gases.size() + tags.size(); }
    public boolean contains(ResourceLocation id) { return gases.contains(id); }
    public boolean containsTag(ResourceLocation id) { return tags.contains(id); }

    public boolean add(ResourceLocation id) {
        if (id == null || size() >= MAX_ENTRIES) return false;
        return gases.add(id);
    }

    public boolean addTag(ResourceLocation id) {
        if (id == null || size() >= MAX_ENTRIES) return false;
        return tags.add(id);
    }

    public boolean remove(ResourceLocation id) { return gases.remove(id); }
    public boolean removeTag(ResourceLocation id) { return tags.remove(id); }
    public void clear() { gases.clear(); tags.clear(); }

    public void copyFrom(GasFilter other) {
        if (other == null) return;
        this.whitelist = other.whitelist;
        this.gases.clear();
        this.gases.addAll(other.gases);
        this.tags.clear();
        this.tags.addAll(other.tags);
    }

    /** Id-only match — used where tag awareness isn't needed (sentinel checks, void filter). */
    public boolean matchesId(ResourceLocation gasId) {
        if (gasId == null) return false;
        boolean present = gases.contains(gasId);
        return whitelist ? present : !present;
    }

    /**
     * Full match including tags. {@code isInTag} is supplied by the caller (GasIntegration) — it
     * checks whether the gas belongs to the given tag id in Mekanism's registry. Done this way so
     * GasFilter stays Mekanism-free at load time.
     */
    public boolean matchesIdWithTags(ResourceLocation gasId, Predicate<ResourceLocation> isInTag) {
        if (gasId == null) return false;
        boolean present = gases.contains(gasId);
        if (!present && !tags.isEmpty() && isInTag != null) {
            for (ResourceLocation t : tags) {
                if (isInTag.test(t)) { present = true; break; }
            }
        }
        return whitelist ? present : !present;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Whitelist", whitelist);
        ListTag list = new ListTag();
        for (ResourceLocation id : gases) {
            CompoundTag e = new CompoundTag();
            e.putString("Id", id.toString());
            list.add(e);
        }
        tag.put("Gases", list);
        if (!tags.isEmpty()) {
            ListTag tagList = new ListTag();
            for (ResourceLocation id : tags) {
                CompoundTag e = new CompoundTag();
                e.putString("Id", id.toString());
                tagList.add(e);
            }
            tag.put("Tags", tagList);
        }
        return tag;
    }

    public static GasFilter load(CompoundTag tag) {
        GasFilter f = new GasFilter(tag.getBoolean("Whitelist"));
        ListTag list = tag.getList("Gases", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            String s = list.getCompound(i).getString("Id");
            try { f.gases.add(new ResourceLocation(s)); } catch (Exception ignored) {}
        }
        if (tag.contains("Tags", Tag.TAG_LIST)) {
            ListTag tagList = tag.getList("Tags", Tag.TAG_COMPOUND);
            for (int i = 0; i < tagList.size(); i++) {
                String s = tagList.getCompound(i).getString("Id");
                try { f.tags.add(new ResourceLocation(s)); } catch (Exception ignored) {}
            }
        }
        return f;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(whitelist);
        buf.writeVarInt(gases.size());
        for (ResourceLocation id : gases) buf.writeResourceLocation(id);
        buf.writeVarInt(tags.size());
        for (ResourceLocation id : tags) buf.writeResourceLocation(id);
    }

    public static GasFilter read(FriendlyByteBuf buf) {
        GasFilter f = new GasFilter(buf.readBoolean());
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) f.gases.add(buf.readResourceLocation());
        int t = buf.readVarInt();
        for (int i = 0; i < t; i++) f.tags.add(buf.readResourceLocation());
        return f;
    }
}
