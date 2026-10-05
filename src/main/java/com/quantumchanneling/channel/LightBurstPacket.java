package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client one-shot trigger for the shader-driven light burst (currently fired by the
 * Star Shaper's Hammer when it crushes one or more White Dwarfs). The server sends this to every
 * player tracking the chunk; each client adds a {@code LightBurst} to
 * {@link com.quantumchanneling.client.render.PhotonBurstRenderer} which expands + fades the
 * effect via the existing photon_halo shader.
 *
 * <p>Pure visual + audio — no game state. Damage is applied server-side in the hammer block; this
 * packet is only the player-facing show.
 */
public record LightBurstPacket(double x, double y, double z, float radius, int colorRgb,
                               String dimension) implements CustomPacketPayload {
    public static final Type<LightBurstPacket> TYPE = new Type<>(QuantumChanneling.id("light_burst"));
    public static final StreamCodec<FriendlyByteBuf, LightBurstPacket> STREAM_CODEC =
            StreamCodec.ofMember(LightBurstPacket::encode, LightBurstPacket::decode);

    @Override
    public Type<LightBurstPacket> type() { return TYPE; }

    public static void encode(LightBurstPacket p, FriendlyByteBuf b) {
        b.writeDouble(p.x);
        b.writeDouble(p.y);
        b.writeDouble(p.z);
        b.writeFloat(p.radius);
        b.writeInt(p.colorRgb);
        b.writeUtf(p.dimension);
    }

    public static LightBurstPacket decode(FriendlyByteBuf b) {
        return new LightBurstPacket(b.readDouble(), b.readDouble(), b.readDouble(),
                b.readFloat(), b.readInt(), b.readUtf());
    }
}
