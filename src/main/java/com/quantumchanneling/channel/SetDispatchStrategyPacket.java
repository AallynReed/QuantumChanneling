package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.api.IQuantumSubchannelView.Kind;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: set this device's dispatch strategy for one resource.
 * {@code resource}: 0 = items, 1 = fluids, 2 = gas. {@code strategy}: see {@link DispatchStrategy} ordinal.
 */
public record SetDispatchStrategyPacket(BlockPos pos, byte resource, byte strategy) implements CustomPacketPayload {
    public static final Type<SetDispatchStrategyPacket> TYPE = new Type<>(QuantumChanneling.id("set_dispatch_strategy"));
    public static final StreamCodec<FriendlyByteBuf, SetDispatchStrategyPacket> STREAM_CODEC =
            StreamCodec.ofMember(SetDispatchStrategyPacket::encode, SetDispatchStrategyPacket::decode);

    @Override
    public Type<SetDispatchStrategyPacket> type() { return TYPE; }

    public static void encode(SetDispatchStrategyPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeByte(p.resource);
        b.writeByte(p.strategy);
    }
    public static SetDispatchStrategyPacket decode(FriendlyByteBuf b) {
        return new SetDispatchStrategyPacket(b.readBlockPos(), b.readByte(), b.readByte());
    }
    public static void handle(SetDispatchStrategyPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ChannelBoundBlockEntity bound = PacketUtil.manageableDevice(player, p.pos);
        if (bound == null) return;
        DispatchStrategy s = DispatchStrategy.byOrdinal(p.strategy);
        switch (p.resource) {
            case 0 -> bound.setDispatch(Kind.ITEM, s);
            case 1 -> bound.setDispatch(Kind.FLUID, s);
            case 2 -> bound.setDispatch(Kind.GAS, s);
            default -> { return; }
        }
        CreateChannelPacket.sendListBackTo(player);
    }
}
