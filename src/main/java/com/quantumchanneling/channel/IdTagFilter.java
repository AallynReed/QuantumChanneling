package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Whitelist or blacklist of registry ids plus tag ids. Whitelist mode matches only the listed
 * entries; blacklist mode matches everything else. An empty whitelist matches nothing, an empty
 * blacklist everything.
 *
 * <p>Tag entries resolve at match time, so things other mods add to a tag later pick up the rule
 * without re-editing the filter. Sets are {@link LinkedHashSet}s so the UI shows entries in the
 * order they were added.
 */
public abstract class IdTagFilter implements ResourceFilter {
    /** Hard cap on entries (ids + tags together) to bound NBT and packet size. */
    public static final int MAX_ENTRIES = 256;

    private boolean whitelist;
    private final Set<Identifier> ids = new LinkedHashSet<>();
    private final Set<Identifier> tags = new LinkedHashSet<>();

    protected IdTagFilter(boolean whitelist) {
        this.whitelist = whitelist;
    }

    @Override public boolean isWhitelist() { return whitelist; }
    public void setWhitelist(boolean w) { this.whitelist = w; }
    @Override public Set<Identifier> ids() { return Collections.unmodifiableSet(ids); }
    @Override public Set<Identifier> tags() { return Collections.unmodifiableSet(tags); }
    public int size() { return ids.size() + tags.size(); }
    public boolean contains(Identifier id) { return ids.contains(id); }
    public boolean containsTag(Identifier id) { return tags.contains(id); }

    public boolean add(Identifier id) {
        if (id == null || size() >= MAX_ENTRIES) return false;
        return ids.add(id);
    }

    public boolean addTag(Identifier id) {
        if (id == null || size() >= MAX_ENTRIES) return false;
        return tags.add(id);
    }

    public boolean remove(Identifier id) { return ids.remove(id); }
    public boolean removeTag(Identifier id) { return tags.remove(id); }
    public void clear() { ids.clear(); tags.clear(); }

    /** True when the filter would change behaviour from a fresh blacklist (i.e. it's worth saving). */
    public boolean isConfigured() { return whitelist || !ids.isEmpty() || !tags.isEmpty(); }

    public void copyFrom(IdTagFilter other) {
        if (other == null) return;
        this.whitelist = other.whitelist;
        this.ids.clear();
        this.ids.addAll(other.ids);
        this.tags.clear();
        this.tags.addAll(other.tags);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Whitelist", whitelist);
        if (!ids.isEmpty()) tag.store("Ids", Identifier.CODEC.listOf(), List.copyOf(ids));
        if (!tags.isEmpty()) tag.store("Tags", Identifier.CODEC.listOf(), List.copyOf(tags));
        return tag;
    }

    protected static <F extends IdTagFilter> F load(CompoundTag tag, F into) {
        IdTagFilter f = into;
        f.whitelist = tag.getBooleanOr("Whitelist", false);
        tag.read("Ids", Identifier.CODEC.listOf()).ifPresent(f.ids::addAll);
        tag.read("Tags", Identifier.CODEC.listOf()).ifPresent(f.tags::addAll);
        return into;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(whitelist);
        buf.writeCollection(ids, FriendlyByteBuf::writeIdentifier);
        buf.writeCollection(tags, FriendlyByteBuf::writeIdentifier);
    }

    protected static <F extends IdTagFilter> F read(FriendlyByteBuf buf, F into) {
        IdTagFilter f = into;
        f.whitelist = buf.readBoolean();
        f.ids.addAll(buf.readList(FriendlyByteBuf::readIdentifier));
        f.tags.addAll(buf.readList(FriendlyByteBuf::readIdentifier));
        return into;
    }
}
