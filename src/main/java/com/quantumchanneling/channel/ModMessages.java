package com.quantumchanneling.channel;

import com.quantumchanneling.QuantumChanneling;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModMessages {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(QuantumChanneling.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private ModMessages() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(ToggleChunkLoadPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleChunkLoadPacket::encode).decoder(ToggleChunkLoadPacket::decode)
                .consumerMainThread(ToggleChunkLoadPacket::handle).add();
        CHANNEL.messageBuilder(OpenChannelsRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenChannelsRequestPacket::encode).decoder(OpenChannelsRequestPacket::decode)
                .consumerMainThread(OpenChannelsRequestPacket::handle).add();
        CHANNEL.messageBuilder(CreateChannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CreateChannelPacket::encode).decoder(CreateChannelPacket::decode)
                .consumerMainThread(CreateChannelPacket::handle).add();
        CHANNEL.messageBuilder(DeleteChannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DeleteChannelPacket::encode).decoder(DeleteChannelPacket::decode)
                .consumerMainThread(DeleteChannelPacket::handle).add();
        CHANNEL.messageBuilder(SelectChannelOnTunerPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectChannelOnTunerPacket::encode).decoder(SelectChannelOnTunerPacket::decode)
                .consumerMainThread(SelectChannelOnTunerPacket::handle).add();
        CHANNEL.messageBuilder(RenameChannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RenameChannelPacket::encode).decoder(RenameChannelPacket::decode)
                .consumerMainThread(RenameChannelPacket::handle).add();
        CHANNEL.messageBuilder(SetChannelPublicPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetChannelPublicPacket::encode).decoder(SetChannelPublicPacket::decode)
                .consumerMainThread(SetChannelPublicPacket::handle).add();
        CHANNEL.messageBuilder(SetChannelChargingPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetChannelChargingPacket::encode).decoder(SetChannelChargingPacket::decode)
                .consumerMainThread(SetChannelChargingPacket::handle).add();
        CHANNEL.messageBuilder(SetChannelPermissionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetChannelPermissionPacket::encode).decoder(SetChannelPermissionPacket::decode)
                .consumerMainThread(SetChannelPermissionPacket::handle).add();
        CHANNEL.messageBuilder(SubscribeChargingPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SubscribeChargingPacket::encode).decoder(SubscribeChargingPacket::decode)
                .consumerMainThread(SubscribeChargingPacket::handle).add();
        CHANNEL.messageBuilder(SetDeviceThroughputPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetDeviceThroughputPacket::encode).decoder(SetDeviceThroughputPacket::decode)
                .consumerMainThread(SetDeviceThroughputPacket::handle).add();
        CHANNEL.messageBuilder(ShowChannelsListPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ShowChannelsListPacket::encode).decoder(ShowChannelsListPacket::decode)
                .consumerMainThread(ShowChannelsListPacket::handle).add();
    }

    public static void sendToServer(Object msg) { CHANNEL.sendToServer(msg); }

    public static PacketDistributor.PacketTarget targetServer() {
        return PacketDistributor.SERVER.noArg();
    }
}
