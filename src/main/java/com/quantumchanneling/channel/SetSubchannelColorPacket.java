package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → server: set the color swatch on one of an emitter's subchannels.
 * {@code kind = 0} = item, {@code 1} = fluid, {@code 2} = gas. {@code rgb} is a packed 0xRRGGBB
 * value; 0 means "no color".
 */
public record SetSubchannelColorPacket(BlockPos emitterPos, byte kind, UUID subId, int rgb) {
    public static void encode(SetSubchannelColorPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.emitterPos);
        b.writeByte(p.kind);
        b.writeUUID(p.subId);
        b.writeInt(p.rgb);
    }
    public static SetSubchannelColorPacket decode(FriendlyByteBuf b) {
        return new SetSubchannelColorPacket(b.readBlockPos(), b.readByte(), b.readUUID(), b.readInt());
    }
    public static void handle(SetSubchannelColorPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !PacketUtil.withinReach(player, p.emitterPos)) return;
            BlockEntity be = player.level().getBlockEntity(p.emitterPos);
            if (!(be instanceof PhotonEmitterBlockEntity em)) return;
            boolean ok = switch (p.kind) {
                case 0 -> em.setItemSubchannelColor(p.subId, p.rgb);
                case 1 -> em.setFluidSubchannelColor(p.subId, p.rgb);
                case 2 -> em.setGasSubchannelColor(p.subId, p.rgb);
                default -> false;
            };
            if (ok) CreateChannelPacket.sendListBackTo(player);
        });
        ctx.setPacketHandled(true);
    }
}
