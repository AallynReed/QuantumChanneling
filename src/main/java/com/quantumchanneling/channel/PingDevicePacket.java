package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client → server: "ping" a known device. The server resolves the position, verifies the player
 * can see it (manage rights on its channel, or simple reach), and spawns a short burst of
 * particles + a click sound at that block. Useful in megabase networks to spot a member you can
 * see in the channel info but can't immediately find in the world.
 */
public record PingDevicePacket(String dim, BlockPos pos) implements CustomPacketPayload {
    public static final Type<PingDevicePacket> TYPE = new Type<>(QuantumChanneling.id("ping_device"));
    public static final StreamCodec<FriendlyByteBuf, PingDevicePacket> STREAM_CODEC =
            StreamCodec.ofMember(PingDevicePacket::encode, PingDevicePacket::decode);

    @Override
    public Type<PingDevicePacket> type() { return TYPE; }

    public static void encode(PingDevicePacket p, FriendlyByteBuf b) {
        b.writeUtf(p.dim, 80);
        b.writeBlockPos(p.pos);
    }
    public static PingDevicePacket decode(FriendlyByteBuf b) {
        return new PingDevicePacket(b.readUtf(80), b.readBlockPos());
    }
    public static void handle(PingDevicePacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ResourceKey<Level> dimKey;
        try {
            dimKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    Identifier.parse(p.dim));
        } catch (Throwable ignored) { return; }
        ServerLevel level = player.level().getServer().getLevel(dimKey);
        if (level == null || !level.isLoaded(p.pos)) return;
        BlockEntity be = level.getBlockEntity(p.pos);
        if (!(be instanceof ChannelBoundBlockEntity bound)) return;

        // Authorize: must share at least one open channel with the device — i.e. canUse or
        // canManage on the device's channel. Public channels pass on canUse.
        UUID channelId = bound.getChannelId();
        if (channelId != null) {
            QuantumChannel ch = ChannelData.get(player.level().getServer()).getChannel(channelId);
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
    }
}
