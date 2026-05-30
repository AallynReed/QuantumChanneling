package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Set a device's {@link ResourceMode} — ENERGY (legacy default) or ITEMS (for now). */
public record SetDeviceResourceModePacket(BlockPos pos, ResourceMode mode) {
    public static void encode(SetDeviceResourceModePacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        p.mode.write(b);
    }
    public static SetDeviceResourceModePacket decode(FriendlyByteBuf b) {
        return new SetDeviceResourceModePacket(b.readBlockPos(), ResourceMode.read(b));
    }
    public static void handle(SetDeviceResourceModePacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            double dx = p.pos.getX() + 0.5 - player.getX();
            double dy = p.pos.getY() + 0.5 - player.getY();
            double dz = p.pos.getZ() + 0.5 - player.getZ();
            if (dx * dx + dy * dy + dz * dz > 64.0) return;
            BlockEntity be = player.level().getBlockEntity(p.pos);
            if (be instanceof ChannelBoundBlockEntity bound) {
                bound.setResourceMode(p.mode);
                // The capability surface changes with resource mode — re-publish so anything
                // currently cached (hoppers, AE2 grids) drops their stale energy/item handler.
                bound.invalidateCaps();
                bound.reviveCaps();
            }
        });
        ctx.setPacketHandled(true);
    }
}
