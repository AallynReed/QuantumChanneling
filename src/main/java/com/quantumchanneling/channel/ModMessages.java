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
        CHANNEL.messageBuilder(SetDeviceChannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetDeviceChannelPacket::encode).decoder(SetDeviceChannelPacket::decode)
                .consumerMainThread(SetDeviceChannelPacket::handle).add();
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
        CHANNEL.messageBuilder(SetDevicePriorityPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetDevicePriorityPacket::encode).decoder(SetDevicePriorityPacket::decode)
                .consumerMainThread(SetDevicePriorityPacket::handle).add();
        CHANNEL.messageBuilder(SetDeviceSurgePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetDeviceSurgePacket::encode).decoder(SetDeviceSurgePacket::decode)
                .consumerMainThread(SetDeviceSurgePacket::handle).add();
        CHANNEL.messageBuilder(SetChannelColorPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetChannelColorPacket::encode).decoder(SetChannelColorPacket::decode)
                .consumerMainThread(SetChannelColorPacket::handle).add();
        CHANNEL.messageBuilder(RemoteUnbindDevicePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoteUnbindDevicePacket::encode).decoder(RemoteUnbindDevicePacket::decode)
                .consumerMainThread(RemoteUnbindDevicePacket::handle).add();
        CHANNEL.messageBuilder(ShowChannelsListPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ShowChannelsListPacket::encode).decoder(ShowChannelsListPacket::decode)
                .consumerMainThread(ShowChannelsListPacket::handle).add();
        CHANNEL.messageBuilder(RenameDevicePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RenameDevicePacket::encode).decoder(RenameDevicePacket::decode)
                .consumerMainThread(RenameDevicePacket::handle).add();
        CHANNEL.messageBuilder(SetChannelPinPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetChannelPinPacket::encode).decoder(SetChannelPinPacket::decode)
                .consumerMainThread(SetChannelPinPacket::handle).add();
        CHANNEL.messageBuilder(JoinByPinPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(JoinByPinPacket::encode).decoder(JoinByPinPacket::decode)
                .consumerMainThread(JoinByPinPacket::handle).add();
        CHANNEL.messageBuilder(SetChannelSlotPriorityPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetChannelSlotPriorityPacket::encode).decoder(SetChannelSlotPriorityPacket::decode)
                .consumerMainThread(SetChannelSlotPriorityPacket::handle).add();
        CHANNEL.messageBuilder(TransferChannelOwnerPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TransferChannelOwnerPacket::encode).decoder(TransferChannelOwnerPacket::decode)
                .consumerMainThread(TransferChannelOwnerPacket::handle).add();
        CHANNEL.messageBuilder(SetChannelArmorPriorityPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetChannelArmorPriorityPacket::encode).decoder(SetChannelArmorPriorityPacket::decode)
                .consumerMainThread(SetChannelArmorPriorityPacket::handle).add();
        CHANNEL.messageBuilder(SetChannelChargeBlockedPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetChannelChargeBlockedPacket::encode).decoder(SetChannelChargeBlockedPacket::decode)
                .consumerMainThread(SetChannelChargeBlockedPacket::handle).add();

        // ---- items mode ----
        CHANNEL.messageBuilder(SetItemEnabledPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetItemEnabledPacket::encode).decoder(SetItemEnabledPacket::decode)
                .consumerMainThread(SetItemEnabledPacket::handle).add();
        CHANNEL.messageBuilder(SetItemBatchSizePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetItemBatchSizePacket::encode).decoder(SetItemBatchSizePacket::decode)
                .consumerMainThread(SetItemBatchSizePacket::handle).add();
        CHANNEL.messageBuilder(SetItemFilterModePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetItemFilterModePacket::encode).decoder(SetItemFilterModePacket::decode)
                .consumerMainThread(SetItemFilterModePacket::handle).add();
        CHANNEL.messageBuilder(AddItemFilterEntryPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AddItemFilterEntryPacket::encode).decoder(AddItemFilterEntryPacket::decode)
                .consumerMainThread(AddItemFilterEntryPacket::handle).add();
        CHANNEL.messageBuilder(RemoveItemFilterEntryPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoveItemFilterEntryPacket::encode).decoder(RemoveItemFilterEntryPacket::decode)
                .consumerMainThread(RemoveItemFilterEntryPacket::handle).add();
        CHANNEL.messageBuilder(SetSubchannelNamePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetSubchannelNamePacket::encode).decoder(SetSubchannelNamePacket::decode)
                .consumerMainThread(SetSubchannelNamePacket::handle).add();
        CHANNEL.messageBuilder(SetDeviceResourceModePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetDeviceResourceModePacket::encode).decoder(SetDeviceResourceModePacket::decode)
                .consumerMainThread(SetDeviceResourceModePacket::handle).add();
        CHANNEL.messageBuilder(SetDeviceSubchannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetDeviceSubchannelPacket::encode).decoder(SetDeviceSubchannelPacket::decode)
                .consumerMainThread(SetDeviceSubchannelPacket::handle).add();
    }

    public static void sendToServer(Object msg) { CHANNEL.sendToServer(msg); }

    public static PacketDistributor.PacketTarget targetServer() {
        return PacketDistributor.SERVER.noArg();
    }
}
