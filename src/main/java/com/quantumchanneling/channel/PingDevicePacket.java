package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → server: "ping" a known device. The server resolves the position, verifies the player
 * can see it (manage rights on its channel, or simple reach), and spawns a short burst of
 * particles + a click sound at that block. Useful in megabase networks to spot a member you can
 * see in the channel info but can't immediately find in the world.
 */
public record PingDevicePacket(String dim, BlockPos pos) {
    public static void encode(PingDevicePacket p, FriendlyByteBuf b) {
        b.writeUtf(p.dim, 80);
        b.writeBlockPos(p.pos);
    }
    public static PingDevicePacket decode(FriendlyByteBuf b) {
        return new PingDevicePacket(b.readUtf(80), b.readBlockPos());
    }
    public static void handle(PingDevicePacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ResourceKey<Level> dimKey;
            try {
                dimKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        new ResourceLocation(p.dim));
            } catch (Throwable ignored) { return; }
            ServerLevel level = player.server.getLevel(dimKey);
            if (level == null || !level.isLoaded(p.pos)) return;
            BlockEntity be = level.getBlockEntity(p.pos);
            if (!(be instanceof ChannelBoundBlockEntity bound)) return;

            // Authorize: must share at least one open channel with the device — i.e. canUse or
            // canManage on the device's channel. Public channels pass on canUse.
            UUID channelId = bound.getChannelId();
            if (channelId != null) {
                QuantumChannel ch = ChannelData.get(player.server).getChannel(channelId);
                if (ch != null && !ch.canUse(player.getUUID()) && !ch.canManage(player.getUUID())) return;
            }

            double cx = p.pos.getX() + 0.5;
            double cy = p.pos.getY() + 0.5;
            double cz = p.pos.getZ() + 0.5;
            // Visible to everyone in the target dimension — small, brief, cheap. Tweak the count
            // up if it doesn't read well in a busy base.
            level.sendParticles(ParticleTypes.END_ROD, cx, cy + 0.8, cz, 24, 0.18, 0.18, 0.18, 0.02);
            level.sendParticles(ParticleTypes.GLOW, cx, cy + 0.5, cz, 12, 0.25, 0.25, 0.25, 0.0);
            level.playSound(null, p.pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5f, 1.4f);
        });
        ctx.setPacketHandled(true);
    }
}
