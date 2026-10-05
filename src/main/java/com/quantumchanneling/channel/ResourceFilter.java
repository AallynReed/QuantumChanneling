package com.quantumchanneling.channel;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import java.util.Set;

/** Id + tag filter over one registry (items, fluids, or Mekanism chemicals). */
public interface ResourceFilter {
    boolean isWhitelist();

    /** Concrete registry ids, in insertion order. */
    Set<Identifier> ids();

    /** Tag ids (no leading {@code #}), in insertion order. */
    Set<Identifier> tags();

    /** True when {@code type} passes the filter, honouring whitelist/blacklist polarity. */
    default boolean matches(Holder<?> type) {
        boolean present = contains(ids(), tags(), type);
        return isWhitelist() == present;
    }

    private static <T> boolean contains(Set<Identifier> ids, Set<Identifier> tags, Holder<T> type) {
        ResourceKey<T> key = type.unwrapKey().orElse(null);
        if (key == null) return false;
        if (ids.contains(key.identifier())) return true;
        for (Identifier tag : tags) {
            if (type.is(TagKey.create(key.registryKey(), tag))) return true;
        }
        return false;
    }
}
