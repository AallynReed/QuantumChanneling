package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: set this device's dispatch strategy for one resource.
 * {@code resource}: 0 = items, 1 = fluids, 2 = gas. {@code strategy}: see {@link DispatchStrategy} ordinal.
 */
public record SetDispatchStrategyPacket(BlockPos pos, byte resource, byte strategy) {
    public static void encode(SetDispatchStrategyPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeByte(p.resource);
        b.writeByte(p.strategy);
    }
    public static SetDispatchStrategyPacket decode(FriendlyByteBuf b) {
        return new SetDispatchStrategyPacket(b.readBlockPos(), b.readByte(), b.readByte());
    }
    public static void handle(SetDispatchStrategyPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !PacketUtil.withinReach(player, p.pos)) return;
            BlockEntity be = player.level().getBlockEntity(p.pos);
            if (!(be instanceof ChannelBoundBlockEntity bound)) return;
            DispatchStrategy s = DispatchStrategy.byOrdinal(p.strategy);
            switch (p.resource) {
                case 0 -> bound.setItemDispatch(s);
                case 1 -> bound.setFluidDispatch(s);
                case 2 -> bound.setGasDispatch(s);
                default -> { return; }
            }
            CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
