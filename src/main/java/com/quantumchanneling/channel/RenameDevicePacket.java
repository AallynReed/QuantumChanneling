package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: assign a custom display name to a device. */
public record RenameDevicePacket(BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<RenameDevicePacket> TYPE = new Type<>(QuantumChanneling.id("rename_device"));
    public static final StreamCodec<FriendlyByteBuf, RenameDevicePacket> STREAM_CODEC =
            StreamCodec.ofMember(RenameDevicePacket::encode, RenameDevicePacket::decode);

    @Override
    public Type<RenameDevicePacket> type() { return TYPE; }

    public static void encode(RenameDevicePacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeUtf(p.name, 64);
    }
    public static RenameDevicePacket decode(FriendlyByteBuf b) {
        return new RenameDevicePacket(b.readBlockPos(), b.readUtf(64));
    }
    public static void handle(RenameDevicePacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity bound = PacketUtil.manageableDevice(player, p.pos);
        if (bound != null) bound.setCustomName(p.name);
    }
}
