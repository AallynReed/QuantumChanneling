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
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * An item filter operating in either whitelist or blacklist mode. Whitelist mode matches only the
 * listed items / tags; blacklist mode matches everything except them. Empty whitelist matches
 * nothing; empty blacklist matches everything.
 *
 * <p>Two parallel sets — one of concrete item ids, one of tag ids — let users mix specific items
 * (drag from JEI/EMI) with broader rules ({@code #forge:ores/copper}) on the same subchannel.
 * Tag entries resolve at match-time against the registry's tag list, so new items added by other
 * mods that belong to the tag pick up the rule without re-editing the filter.
 *
 * <p>Used by per-subchannel routing filters and per-emitter void filters.
 */
public class ItemFilter {
    /** Hard cap on entries (items + tags counted together) to bound NBT and packet size. */
    public static final int MAX_ENTRIES = 256;

    private boolean whitelist;
    /** {@link LinkedHashSet} so add-order is preserved for stable UI display. */
    private final Set<ResourceLocation> items = new LinkedHashSet<>();
    /** Tag ids as raw ResourceLocations (no `#` prefix in the set itself). */
    private final Set<ResourceLocation> tags = new LinkedHashSet<>();

    public ItemFilter() { this(true); }
    public ItemFilter(boolean whitelist) { this.whitelist = whitelist; }

    public boolean isWhitelist() { return whitelist; }
    public void setWhitelist(boolean w) { this.whitelist = w; }
    public Set<ResourceLocation> items() { return Collections.unmodifiableSet(items); }
    public Set<ResourceLocation> tags() { return Collections.unmodifiableSet(tags); }
    public int size() { return items.size() + tags.size(); }
    public boolean contains(ResourceLocation id) { return items.contains(id); }
    public boolean containsTag(ResourceLocation id) { return tags.contains(id); }

    public boolean add(ResourceLocation id) {
        if (id == null || size() >= MAX_ENTRIES) return false;
        return items.add(id);
    }

    public boolean addTag(ResourceLocation id) {
        if (id == null || size() >= MAX_ENTRIES) return false;
        return tags.add(id);
    }

    public boolean remove(ResourceLocation id) { return items.remove(id); }
    public boolean removeTag(ResourceLocation id) { return tags.remove(id); }
    public void clear() { items.clear(); tags.clear(); }

    /** Replaces this filter's state with {@code other}'s state (in place — used during NBT load). */
    public void copyFrom(ItemFilter other) {
        if (other == null) return;
        this.whitelist = other.whitelist;
        this.items.clear();
        this.items.addAll(other.items);
        this.tags.clear();
        this.tags.addAll(other.tags);
    }

    /** True when {@code stack}'s item is matched (passes the filter). */
    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        boolean present = matchesAny(stack);
        return whitelist ? present : !present;
    }

    /** Membership check that ignores whitelist/blacklist polarity. */
    private boolean matchesAny(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (items.contains(id)) return true;
        if (tags.isEmpty()) return false;
        Holder<net.minecraft.world.item.Item> holder = stack.getItemHolder();
        for (ResourceLocation tagId : tags) {
            TagKey<net.minecraft.world.item.Item> key = TagKey.create(Registries.ITEM, tagId);
            if (holder.is(key)) return true;
        }
        return false;
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

    public static ItemFilter load(CompoundTag tag) {
        ItemFilter f = new ItemFilter(tag.getBoolean("Whitelist"));
        ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            String s = list.getCompound(i).getString("Id");
            try { f.items.add(new ResourceLocation(s)); } catch (Exception ignored) {}
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
        buf.writeVarInt(items.size());
        for (ResourceLocation id : items) buf.writeResourceLocation(id);
        buf.writeVarInt(tags.size());
        for (ResourceLocation id : tags) buf.writeResourceLocation(id);
    }

    public static ItemFilter read(FriendlyByteBuf buf) {
        ItemFilter f = new ItemFilter(buf.readBoolean());
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) f.items.add(buf.readResourceLocation());
        int t = buf.readVarInt();
        for (int i = 0; i < t; i++) f.tags.add(buf.readResourceLocation());
        return f;
    }
}
