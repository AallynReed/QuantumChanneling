package com.quantumchanneling.channel;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Fluid analog of {@link ItemFilter}. Matches by Fluid registry id. Whitelist mode matches only
 * listed fluids; blacklist mode matches everything except the listed fluids. Empty whitelist
 * matches nothing; empty blacklist matches everything.
 *
 * <p>Used by per-channel subchannel filters and per-emitter void filters (the void variant is
 * hard-locked to blacklist mode — items in the list get voided).
 */
public class FluidFilter {
    public static final int MAX_ENTRIES = 256;

    private boolean whitelist;
    private final Set<ResourceLocation> fluids = new LinkedHashSet<>();

    public FluidFilter() { this(true); }
    public FluidFilter(boolean whitelist) { this.whitelist = whitelist; }

    public boolean isWhitelist() { return whitelist; }
    public void setWhitelist(boolean w) { this.whitelist = w; }
    public Set<ResourceLocation> fluids() { return Collections.unmodifiableSet(fluids); }
    public int size() { return fluids.size(); }
    public boolean contains(ResourceLocation id) { return fluids.contains(id); }

    public boolean add(ResourceLocation id) {
        if (id == null || fluids.size() >= MAX_ENTRIES) return false;
        return fluids.add(id);
    }

    public boolean remove(ResourceLocation id) { return fluids.remove(id); }
    public void clear() { fluids.clear(); }

    public void copyFrom(FluidFilter other) {
        if (other == null) return;
        this.whitelist = other.whitelist;
        this.fluids.clear();
        this.fluids.addAll(other.fluids);
    }

    /** True when {@code stack}'s fluid is matched (passes the filter). */
    public boolean matches(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        boolean present = fluids.contains(id);
        return whitelist ? present : !present;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Whitelist", whitelist);
        ListTag list = new ListTag();
        for (ResourceLocation id : fluids) {
            CompoundTag e = new CompoundTag();
            e.putString("Id", id.toString());
            list.add(e);
        }
        tag.put("Fluids", list);
        return tag;
    }

    public static FluidFilter load(CompoundTag tag) {
        FluidFilter f = new FluidFilter(tag.getBoolean("Whitelist"));
        ListTag list = tag.getList("Fluids", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            String s = list.getCompound(i).getString("Id");
            try { f.fluids.add(new ResourceLocation(s)); } catch (Exception ignored) {}
        }
        return f;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(whitelist);
        buf.writeVarInt(fluids.size());
        for (ResourceLocation id : fluids) buf.writeResourceLocation(id);
    }

    public static FluidFilter read(FriendlyByteBuf buf) {
        FluidFilter f = new FluidFilter(buf.readBoolean());
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) f.fluids.add(buf.readResourceLocation());
        return f;
    }
}
