package com.quantumchanneling.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * Per-channel item-transfer settings: master enable, batch size, main filter, void filter, and
 * {@link #SUBCHANNEL_COUNT} virtual sub-channels for routing.
 *
 * <p>Default behaviour is a no-op: disabled, blacklist with no entries (allows everything), void
 * list empty (nothing voided), batch size 64 (one full stack per cycle).
 */
public class ItemChannelConfig {
    public static final int SUBCHANNEL_COUNT = 5;
    public static final int DEFAULT_BATCH = 64;
    public static final int MIN_BATCH = 1;
    /** A vanilla stack is at most 64; cap a single-tick movement at 9 stacks (one hotbar row). */
    public static final int MAX_BATCH = 64 * 9;

    private boolean enabled = false;
    private int batchSize = DEFAULT_BATCH;
    /** Whether the channel performs an active transfer cycle this tick. Toggles every other tick. */
    private final ItemFilter mainFilter = new ItemFilter(false);   // default: blacklist (everything passes)
    private final ItemFilter voidFilter = new ItemFilter(true);    // default: whitelist with no entries (nothing voided)
    private final ItemSubchannel[] subchannels = new ItemSubchannel[SUBCHANNEL_COUNT];

    public ItemChannelConfig() {
        for (int i = 0; i < SUBCHANNEL_COUNT; i++) subchannels[i] = new ItemSubchannel("");
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }

    public int batchSize() { return batchSize; }
    public void setBatchSize(int b) {
        this.batchSize = Math.max(MIN_BATCH, Math.min(MAX_BATCH, b));
    }

    public ItemFilter mainFilter() { return mainFilter; }
    public ItemFilter voidFilter() { return voidFilter; }
    public ItemSubchannel subchannel(int idx) {
        if (idx < 0 || idx >= SUBCHANNEL_COUNT) return null;
        return subchannels[idx];
    }
    public ItemSubchannel[] subchannels() { return subchannels; }

    /**
     * Returns true when {@code stack} should be voided (silently consumed during transit).
     * Void list is interpreted as a whitelist of items-to-discard.
     */
    public boolean isVoided(ItemStack stack) {
        // voidFilter is always whitelist semantically — items in the list are voided.
        // We override semantics here so the UI can show it cleanly as a "void list".
        if (stack == null || stack.isEmpty()) return false;
        return voidFilter.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Enabled", enabled);
        tag.putInt("BatchSize", batchSize);
        tag.put("Main", mainFilter.save());
        tag.put("Void", voidFilter.save());
        ListTag subs = new ListTag();
        for (ItemSubchannel s : subchannels) subs.add(s.save());
        tag.put("Subs", subs);
        return tag;
    }

    public static ItemChannelConfig load(CompoundTag tag) {
        ItemChannelConfig c = new ItemChannelConfig();
        c.enabled = tag.getBoolean("Enabled");
        if (tag.contains("BatchSize")) c.setBatchSize(tag.getInt("BatchSize"));
        if (tag.contains("Main", Tag.TAG_COMPOUND)) c.mainFilter.copyFrom(ItemFilter.load(tag.getCompound("Main")));
        if (tag.contains("Void", Tag.TAG_COMPOUND)) c.voidFilter.copyFrom(ItemFilter.load(tag.getCompound("Void")));
        if (tag.contains("Subs", Tag.TAG_LIST)) {
            ListTag subs = tag.getList("Subs", Tag.TAG_COMPOUND);
            int n = Math.min(subs.size(), SUBCHANNEL_COUNT);
            for (int i = 0; i < n; i++) c.subchannels[i] = ItemSubchannel.load(subs.getCompound(i));
        }
        return c;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeVarInt(batchSize);
        mainFilter.write(buf);
        voidFilter.write(buf);
        for (ItemSubchannel s : subchannels) s.write(buf);
    }

    public static ItemChannelConfig read(FriendlyByteBuf buf) {
        ItemChannelConfig c = new ItemChannelConfig();
        c.enabled = buf.readBoolean();
        c.setBatchSize(buf.readVarInt());
        c.mainFilter.copyFrom(ItemFilter.read(buf));
        c.voidFilter.copyFrom(ItemFilter.read(buf));
        for (int i = 0; i < SUBCHANNEL_COUNT; i++) c.subchannels[i] = ItemSubchannel.read(buf);
        return c;
    }
}
