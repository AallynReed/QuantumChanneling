package com.quantumchanneling.channel;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Deque;
import java.util.Iterator;

/**
 * Stateless helpers shared by the emitter and receiver item-mode tick paths. Keeps the filter +
 * void + buffer routing logic in one place so the two BEs can't drift out of sync.
 */
public final class ItemTransitHelper {
    private ItemTransitHelper() {}

    /**
     * Tries to route {@code stack} from "incoming" to the channel buffer. Applies the channel's
     * main filter and void list in order:
     * <ol>
     *   <li>If {@code stack} fails the main filter → returned unchanged (caller leaves it where it is).</li>
     *   <li>If {@code stack} is on the void list → returned empty (silently consumed).</li>
     *   <li>Otherwise → enqueued into the channel buffer (as much as fits) and the unaccepted
     *       remainder is returned.</li>
     * </ol>
     */
    public static ItemStack routeToBuffer(QuantumChannel net, ItemStack stack, boolean simulate) {
        if (net == null || stack == null || stack.isEmpty()) return stack;
        ItemChannelConfig cfg = net.itemConfig();
        if (!cfg.isEnabled()) return stack;
        if (!cfg.mainFilter().matches(stack)) return stack;
        if (cfg.isVoided(stack)) return ItemStack.EMPTY;     // voided = silently consumed
        if (net.isItemBufferFull()) return stack;            // buffer full → reject
        if (simulate) return ItemStack.EMPTY;                // would be fully accepted
        net.enqueueItem(stack);
        return ItemStack.EMPTY;
    }

    /**
     * Removes and returns the first buffered stack whose item passes both the channel's main
     * filter AND {@code subchannelFilter}. Returns {@link ItemStack#EMPTY} when nothing matches.
     * When {@code simulate} is true, the buffer is read but not mutated.
     */
    public static ItemStack peekOrTake(QuantumChannel net, @Nullable ItemFilter subchannelFilter, boolean simulate) {
        if (net == null) return ItemStack.EMPTY;
        Deque<ItemStack> buf = net.itemBuffer();
        if (buf.isEmpty()) return ItemStack.EMPTY;

        Iterator<ItemStack> it = buf.iterator();
        while (it.hasNext()) {
            ItemStack s = it.next();
            if (s.isEmpty()) { if (!simulate) it.remove(); continue; }
            // Sub-channel binding: if a filter is provided AND it has whitelist entries (or is
            // blacklist), check it. A "main trunk" receiver (subchannelFilter == null) takes
            // anything the channel's main filter let through.
            if (subchannelFilter != null && !subchannelFilter.matches(s)) continue;
            if (!simulate) it.remove();
            return s.copy();
        }
        return ItemStack.EMPTY;
    }

    /** Pushes {@code stack} into the first adjacent IItemHandler that will accept it. Returns the unaccepted remainder. */
    public static ItemStack pushToAdjacent(ServerLevel level, BlockEntity origin, ItemStack stack) {
        if (stack.isEmpty()) return stack;
        ItemStack remaining = stack;
        for (Direction side : Direction.values()) {
            if (remaining.isEmpty()) break;
            BlockEntity neighbor = level.getBlockEntity(origin.getBlockPos().relative(side));
            if (neighbor == null) continue;
            IItemHandler dest = neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).orElse(null);
            if (dest == null) continue;
            remaining = insertAllSlots(dest, remaining);
        }
        return remaining;
    }

    /** Standard "try each slot" insertion. Returns whatever didn't fit. */
    public static ItemStack insertAllSlots(IItemHandler dest, ItemStack stack) {
        ItemStack remaining = stack;
        for (int slot = 0; slot < dest.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = dest.insertItem(slot, remaining, false);
        }
        return remaining;
    }

    /**
     * Pulls a stack from an adjacent IItemHandler, applies the main filter + void list, and routes
     * the survivor into the channel buffer. Returns the FE-style "amount moved" (item count) for
     * the caller's per-tick budgeting.
     */
    public static int pullFromAdjacentInto(ServerLevel level, BlockEntity origin, QuantumChannel net, int batchSize) {
        if (net == null || batchSize <= 0) return 0;
        ItemChannelConfig cfg = net.itemConfig();
        if (!cfg.isEnabled() || net.isItemBufferFull()) return 0;

        for (Direction side : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(origin.getBlockPos().relative(side));
            if (neighbor == null) continue;
            IItemHandler src = neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).orElse(null);
            if (src == null) continue;
            int moved = scanAndPull(src, net, batchSize);
            if (moved > 0) return moved;
        }
        return 0;
    }

    /** First-fit scan of {@code src}'s slots — extracts up to {@code budget} items that pass routing. */
    private static int scanAndPull(IItemHandler src, QuantumChannel net, int budget) {
        for (int slot = 0; slot < src.getSlots(); slot++) {
            ItemStack peek = src.extractItem(slot, budget, true);
            if (peek.isEmpty()) continue;
            // Decide what we'd do with it.
            ItemStack routed = routeToBuffer(net, peek, true);
            if (routed.getCount() == peek.getCount()) continue;  // wouldn't accept any
            // Compute the count we will actually take out of this slot.
            int take = peek.getCount() - routed.getCount();
            if (take <= 0) continue;
            ItemStack real = src.extractItem(slot, take, false);
            if (real.isEmpty()) continue;
            // Route for real now — anything not accepted is dropped on the floor (rare; the
            // simulation just told us it would all be accepted, so this is a belt-and-braces).
            ItemStack leftover = routeToBuffer(net, real, false);
            if (!leftover.isEmpty()) {
                // Try to put it back. If that fails too, void it — better than duplication.
                src.insertItem(slot, leftover, false);
            }
            return real.getCount() - leftover.getCount();
        }
        return 0;
    }
}
