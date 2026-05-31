package com.quantumchanneling.channel;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.fluids.FluidStack;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Fluid analog of {@link ItemFilter}. Two parallel sets — one of concrete fluid ids, one of fluid
 * tag ids — let users mix specific fluids with broader rules ({@code #forge:milk}) on the same
 * subchannel. Tag entries resolve at match-time via the fluid registry.
 */
public class FluidFilter {
    public static final int MAX_ENTRIES = 256;

    private boolean whitelist;
    private final Set<ResourceLocation> fluids = new LinkedHashSet<>();
    private final Set<ResourceLocation> tags = new LinkedHashSet<>();

    public FluidFilter() { this(true); }
    public FluidFilter(boolean whitelist) { this.whitelist = whitelist; }

    public boolean isWhitelist() { return whitelist; }
    public void setWhitelist(boolean w) { this.whitelist = w; }
    public Set<ResourceLocation> fluids() { return Collections.unmodifiableSet(fluids); }
    public Set<ResourceLocation> tags() { return Collections.unmodifiableSet(tags); }
    public int size() { return fluids.size() + tags.size(); }
    public boolean contains(ResourceLocation id) { return fluids.contains(id); }
    public boolean containsTag(ResourceLocation id) { return tags.contains(id); }

    public boolean add(ResourceLocation id) {
        if (id == null || size() >= MAX_ENTRIES) return false;
        return fluids.add(id);
    }

    public boolean addTag(ResourceLocation id) {
        if (id == null || size() >= MAX_ENTRIES) return false;
        return tags.add(id);
    }

    public boolean remove(ResourceLocation id) { return fluids.remove(id); }
    public boolean removeTag(ResourceLocation id) { return tags.remove(id); }
    public void clear() { fluids.clear(); tags.clear(); }

    public void copyFrom(FluidFilter other) {
        if (other == null) return;
        this.whitelist = other.whitelist;
        this.fluids.clear();
        this.fluids.addAll(other.fluids);
        this.tags.clear();
        this.tags.addAll(other.tags);
    }

    /** True when {@code stack}'s fluid is matched (passes the filter). */
    public boolean matches(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        boolean present = matchesAny(stack);
        return whitelist ? present : !present;
    }

    private boolean matchesAny(FluidStack stack) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        if (fluids.contains(id)) return true;
        if (tags.isEmpty()) return false;
        Holder<net.minecraft.world.level.material.Fluid> holder =
                BuiltInRegistries.FLUID.wrapAsHolder(stack.getFluid());
        for (ResourceLocation tagId : tags) {
            TagKey<net.minecraft.world.level.material.Fluid> key = TagKey.create(Registries.FLUID, tagId);
            if (holder.is(key)) return true;
        }
        return false;
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

    public static FluidFilter load(CompoundTag tag) {
        FluidFilter f = new FluidFilter(tag.getBoolean("Whitelist"));
        ListTag list = tag.getList("Fluids", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            String s = list.getCompound(i).getString("Id");
            try { f.fluids.add(new ResourceLocation(s)); } catch (Exception ignored) {}
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
        buf.writeVarInt(fluids.size());
        for (ResourceLocation id : fluids) buf.writeResourceLocation(id);
        buf.writeVarInt(tags.size());
        for (ResourceLocation id : tags) buf.writeResourceLocation(id);
    }

    public static FluidFilter read(FriendlyByteBuf buf) {
        FluidFilter f = new FluidFilter(buf.readBoolean());
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) f.fluids.add(buf.readResourceLocation());
        int t = buf.readVarInt();
        for (int i = 0; i < t; i++) f.tags.add(buf.readResourceLocation());
        return f;
    }
}
