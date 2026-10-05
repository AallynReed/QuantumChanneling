package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: copy every item / fluid / gas subchannel from {@code sourcePos} (within the
 * player's current dimension) into {@code targetPos} (any dimension the server has loaded). New
 * UUIDs are generated for the copied subchannels so any receivers subscribed to the source remain
 * pointed at the original — the clone is a structural template, not a join. The player must be
 * within reach of the source and have manage rights on the target's channel.
 */
public record CloneEmitterSubchannelsPacket(BlockPos sourcePos, String targetDim, BlockPos targetPos) implements CustomPacketPayload {
    public static final Type<CloneEmitterSubchannelsPacket> TYPE = new Type<>(QuantumChanneling.id("clone_emitter_subchannels"));
    public static final StreamCodec<FriendlyByteBuf, CloneEmitterSubchannelsPacket> STREAM_CODEC =
            StreamCodec.ofMember(CloneEmitterSubchannelsPacket::encode, CloneEmitterSubchannelsPacket::decode);

    @Override
    public Type<CloneEmitterSubchannelsPacket> type() { return TYPE; }

    public static void encode(CloneEmitterSubchannelsPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.sourcePos);
        b.writeUtf(p.targetDim, 80);
        b.writeBlockPos(p.targetPos);
    }
    public static CloneEmitterSubchannelsPacket decode(FriendlyByteBuf b) {
        return new CloneEmitterSubchannelsPacket(b.readBlockPos(), b.readUtf(80), b.readBlockPos());
    }
    public static void handle(CloneEmitterSubchannelsPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !PacketUtil.withinReach(player, p.sourcePos)) return;
        BlockEntity sourceBe = player.level().getBlockEntity(p.sourcePos);
        if (!(sourceBe instanceof PhotonEmitterBlockEntity source)) return;

        ResourceKey<Level> dimKey;
        try {
            dimKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    Identifier.parse(p.targetDim));
        } catch (Throwable ignored) { return; }
        ServerLevel targetLevel = player.level().getServer().getLevel(dimKey);
        if (targetLevel == null || !targetLevel.isLoaded(p.targetPos)) return;
        BlockEntity targetBe = targetLevel.getBlockEntity(p.targetPos);
        if (!(targetBe instanceof PhotonEmitterBlockEntity target)) return;
        if (target.getChannelId() == null) return;
        QuantumChannel ch = ChannelData.get(player.level().getServer()).getChannel(target.getChannelId());
        if (ch == null || !ch.canManage(player.getUUID())) return;

        // The actual clone happens on the target — keeps the "new UUIDs come from target" rule
        // local to the BE that owns them.
        int copied = target.cloneSubchannelsFrom(source);
        if (copied > 0) {
            ChannelData.get(player.level().getServer()).setDirty();
            CreateChannelPacket.sendListBackTo(player);
        }
    }
}
