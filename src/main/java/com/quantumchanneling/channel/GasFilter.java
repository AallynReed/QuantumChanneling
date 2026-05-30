package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gas analog of {@link FluidFilter}. Stores {@link ResourceLocation}s of Mekanism gas registry
 * entries — no Mekanism types imported, so this class is safe to load without Mekanism. The actual
 * gas-stack matching happens in {@code compat.mekanism.GasIntegration} where the registry lookup
 * is done with the runtime Mekanism API.
 */
public class GasFilter {
    public static final int MAX_ENTRIES = 256;

    private boolean whitelist;
    private final Set<ResourceLocation> gases = new LinkedHashSet<>();

    public GasFilter() { this(true); }
    public GasFilter(boolean whitelist) { this.whitelist = whitelist; }

    public boolean isWhitelist() { return whitelist; }
    public void setWhitelist(boolean w) { this.whitelist = w; }
    public Set<ResourceLocation> gases() { return Collections.unmodifiableSet(gases); }
    public int size() { return gases.size(); }
    public boolean contains(ResourceLocation id) { return gases.contains(id); }

    public boolean add(ResourceLocation id) {
        if (id == null || gases.size() >= MAX_ENTRIES) return false;
        return gases.add(id);
    }
    public boolean remove(ResourceLocation id) { return gases.remove(id); }
    public void clear() { gases.clear(); }

    public void copyFrom(GasFilter other) {
        if (other == null) return;
        this.whitelist = other.whitelist;
        this.gases.clear();
        this.gases.addAll(other.gases);
    }

    /** Registry-id-based match. Returns true when {@code gasId} is "accepted" by the filter mode. */
    public boolean matchesId(ResourceLocation gasId) {
        if (gasId == null) return false;
        boolean present = gases.contains(gasId);
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
        return tag;
    }

    public static GasFilter load(CompoundTag tag) {
        GasFilter f = new GasFilter(tag.getBoolean("Whitelist"));
        ListTag list = tag.getList("Gases", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            String s = list.getCompound(i).getString("Id");
            try { f.gases.add(new ResourceLocation(s)); } catch (Exception ignored) {}
        }
        return f;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(whitelist);
        buf.writeVarInt(gases.size());
        for (ResourceLocation id : gases) buf.writeResourceLocation(id);
    }

    public static GasFilter read(FriendlyByteBuf buf) {
        GasFilter f = new GasFilter(buf.readBoolean());
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) f.gases.add(buf.readResourceLocation());
        return f;
    }
}
