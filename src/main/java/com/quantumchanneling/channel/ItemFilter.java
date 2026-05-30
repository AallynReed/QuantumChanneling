package com.quantumchanneling.channel;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * An item filter operating in either whitelist or blacklist mode. Whitelist mode matches only the
 * listed items; blacklist mode matches everything except the listed items. Empty whitelist matches
 * nothing; empty blacklist matches everything.
 *
 * <p>Used by both the per-channel main filter, the void list (interpreted as a whitelist), and
 * each sub-channel's routing filter.
 */
public class ItemFilter {
    /** Hard cap on entries to bound NBT and packet size. */
    public static final int MAX_ENTRIES = 256;

    private boolean whitelist;
    /** {@link LinkedHashSet} so add-order is preserved for stable UI display. */
    private final Set<ResourceLocation> items = new LinkedHashSet<>();

    public ItemFilter() { this(true); }
    public ItemFilter(boolean whitelist) { this.whitelist = whitelist; }

    public boolean isWhitelist() { return whitelist; }
    public void setWhitelist(boolean w) { this.whitelist = w; }
    public Set<ResourceLocation> items() { return Collections.unmodifiableSet(items); }
    public int size() { return items.size(); }
    public boolean contains(ResourceLocation id) { return items.contains(id); }

    public boolean add(ResourceLocation id) {
        if (id == null || items.size() >= MAX_ENTRIES) return false;
        return items.add(id);
    }

    public boolean remove(ResourceLocation id) { return items.remove(id); }
    public void clear() { items.clear(); }

    /** Replaces this filter's state with {@code other}'s state (in place — used during NBT load). */
    public void copyFrom(ItemFilter other) {
        if (other == null) return;
        this.whitelist = other.whitelist;
        this.items.clear();
        this.items.addAll(other.items);
    }

    /** True when {@code stack}'s item is matched (passes the filter). */
    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        boolean present = items.contains(id);
        return whitelist ? present : !present;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Whitelist", whitelist);
        ListTag list = new ListTag();
        for (ResourceLocation id : items) {
            CompoundTag e = new CompoundTag();
            e.putString("Id", id.toString());
            list.add(e);
        }
        tag.put("Items", list);
        return tag;
    }

    public static ItemFilter load(CompoundTag tag) {
        ItemFilter f = new ItemFilter(tag.getBoolean("Whitelist"));
        ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            String s = list.getCompound(i).getString("Id");
            try { f.items.add(new ResourceLocation(s)); } catch (Exception ignored) {}
        }
        return f;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(whitelist);
        buf.writeVarInt(items.size());
        for (ResourceLocation id : items) buf.writeResourceLocation(id);
    }

    public static ItemFilter read(FriendlyByteBuf buf) {
        ItemFilter f = new ItemFilter(buf.readBoolean());
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) f.items.add(buf.readResourceLocation());
        return f;
    }
}
