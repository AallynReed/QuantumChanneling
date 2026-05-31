package com.quantumchanneling.client.render;

import com.quantumchanneling.block.PhotonEmitterBlock;
import com.quantumchanneling.block.PhotonManagerBlock;
import com.quantumchanneling.block.PhotonReceiverBlock;
import com.quantumchanneling.block.PhotonStorageBlock;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonManagerBlockEntity;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import com.quantumchanneling.blockentity.PhotonStorageBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Single source of truth for the accent color used by the photon shader on each device kind.
 *
 * <p>The black-hole + accretion shader is geometry-shared across emitter, receiver, manager, and
 * the five storage tiers — only the accent color changes. Centralising the lookup here means the
 * in-world {@link PhotonNodeRenderer} (BE → color) and the inventory {@link PhotonItemRenderer}
 * (Block → color) can't drift apart.
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

    /** Resolves the accent color for an item (no BE context — only the wrapped block). */
    public static int colorFor(Block block) {
        if (block instanceof PhotonEmitterBlock)  return EMITTER;
        if (block instanceof PhotonReceiverBlock) return RECEIVER;
        if (block instanceof PhotonManagerBlock)  return MANAGER;
        if (block instanceof PhotonStorageBlock s) return colorForStorageTier(s.getTier());
        return 0xFFFFFF;
    }

    /** True when this BE/block is one of the four photon device kinds that should render the shader. */
    public static boolean isPhotonDevice(BlockEntity be) {
        return be instanceof PhotonEmitterBlockEntity
                || be instanceof PhotonReceiverBlockEntity
                || be instanceof PhotonManagerBlockEntity
                || be instanceof PhotonStorageBlockEntity;
    }
    public static boolean isPhotonDevice(Block block) {
        return block instanceof PhotonEmitterBlock
                || block instanceof PhotonReceiverBlock
                || block instanceof PhotonManagerBlock
                || block instanceof PhotonStorageBlock;
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
