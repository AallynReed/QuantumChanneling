package com.quantumchanneling.client.render;

import com.quantumchanneling.block.PhotonStorageBlock;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonManagerBlockEntity;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import com.quantumchanneling.blockentity.PhotonStorageBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Single source of truth for the accent color used by the photon shader on each device kind.
 *
 * <p>The black-hole + accretion shader is geometry-shared across emitter, receiver, manager, and
 * the five storage tiers — only the accent color changes. The in-world {@link PhotonNodeRenderer}
 * and the item effects in {@link PhotonItemRenderer} both read these constants, so they can't
 * drift apart.
 */
public final class PhotonAccent {
    private PhotonAccent() {}

    public static final int EMITTER  = 0x4FA0FF;   // blue   — push/source
    public static final int RECEIVER = 0xFF5560;   // red    — sink/destination
    public static final int MANAGER  = 0xB07BFF;   // violet — control/authority

    /** Storage palette, indexed by tier-1 (Copper, Iron, Gold, Diamond, Emerald). */
    public static final int[] STORAGE = {
            0xC87533, // tier 1 — copper-bronze
            0xC0D0DC, // tier 2 — silver-cyan iron
            0xFFD060, // tier 3 — warm gold
            0x80E0F0, // tier 4 — cyan-white diamond
            0x50C880  // tier 5 — emerald green
    };

    /** Resolves the accent color for a placed BE in the world. */
    public static int colorFor(BlockEntity be) {
        if (be instanceof PhotonEmitterBlockEntity)  return EMITTER;
        if (be instanceof PhotonReceiverBlockEntity) return RECEIVER;
        if (be instanceof PhotonManagerBlockEntity)  return MANAGER;
        if (be instanceof PhotonStorageBlockEntity)  {
            int tier = (be.getBlockState().getBlock() instanceof PhotonStorageBlock s) ? s.getTier() : 1;
            return colorForStorageTier(tier);
        }
        return 0xFFFFFF;
    }

    /** Emitters and receivers actively route through ports — they render directional beams.
     *  Manager and storage don't, so their renderer skips the beam pass. */
    public static boolean rendersBeams(BlockEntity be) {
        return be instanceof PhotonEmitterBlockEntity || be instanceof PhotonReceiverBlockEntity;
    }

    private static int colorForStorageTier(int tier) {
        int idx = Math.max(0, Math.min(STORAGE.length - 1, tier - 1));
        return STORAGE[idx];
    }
}
