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

        // ---- items mode v2: dynamic subchannels + per-emitter void + per-device subscriptions ----
        CHANNEL.messageBuilder(SetItemEnabledPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetItemEnabledPacket::encode).decoder(SetItemEnabledPacket::decode)
                .consumerMainThread(SetItemEnabledPacket::handle).add();
        CHANNEL.messageBuilder(SetItemBatchSizePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetItemBatchSizePacket::encode).decoder(SetItemBatchSizePacket::decode)
                .consumerMainThread(SetItemBatchSizePacket::handle).add();
        // Channel-level subchannel CRUD
        CHANNEL.messageBuilder(CreateSubchannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CreateSubchannelPacket::encode).decoder(CreateSubchannelPacket::decode)
                .consumerMainThread(CreateSubchannelPacket::handle).add();
        CHANNEL.messageBuilder(DeleteSubchannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DeleteSubchannelPacket::encode).decoder(DeleteSubchannelPacket::decode)
                .consumerMainThread(DeleteSubchannelPacket::handle).add();
        CHANNEL.messageBuilder(RenameSubchannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RenameSubchannelPacket::encode).decoder(RenameSubchannelPacket::decode)
                .consumerMainThread(RenameSubchannelPacket::handle).add();
        // Per-subchannel filter edits
        CHANNEL.messageBuilder(SetSubchannelFilterModePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetSubchannelFilterModePacket::encode).decoder(SetSubchannelFilterModePacket::decode)
                .consumerMainThread(SetSubchannelFilterModePacket::handle).add();
        CHANNEL.messageBuilder(AddSubchannelItemPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AddSubchannelItemPacket::encode).decoder(AddSubchannelItemPacket::decode)
                .consumerMainThread(AddSubchannelItemPacket::handle).add();
        CHANNEL.messageBuilder(RemoveSubchannelItemPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoveSubchannelItemPacket::encode).decoder(RemoveSubchannelItemPacket::decode)
                .consumerMainThread(RemoveSubchannelItemPacket::handle).add();
        // Per-emitter void filter edits. No WL/BL toggle packet — void is hard-locked to
        // "items in the list are voided" semantics.
        CHANNEL.messageBuilder(AddEmitterVoidItemPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AddEmitterVoidItemPacket::encode).decoder(AddEmitterVoidItemPacket::decode)
                .consumerMainThread(AddEmitterVoidItemPacket::handle).add();
        CHANNEL.messageBuilder(RemoveEmitterVoidItemPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoveEmitterVoidItemPacket::encode).decoder(RemoveEmitterVoidItemPacket::decode)
                .consumerMainThread(RemoveEmitterVoidItemPacket::handle).add();
        // Per-device subscription mgmt
        CHANNEL.messageBuilder(SubscribeDevicePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SubscribeDevicePacket::encode).decoder(SubscribeDevicePacket::decode)
                .consumerMainThread(SubscribeDevicePacket::handle).add();
        CHANNEL.messageBuilder(MoveDeviceSubchannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MoveDeviceSubchannelPacket::encode).decoder(MoveDeviceSubchannelPacket::decode)
                .consumerMainThread(MoveDeviceSubchannelPacket::handle).add();

        // --- Fluid-mode packets: full parallel to the item-mode set above. ---
        CHANNEL.messageBuilder(SetFluidEnabledPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetFluidEnabledPacket::encode).decoder(SetFluidEnabledPacket::decode)
                .consumerMainThread(SetFluidEnabledPacket::handle).add();
        CHANNEL.messageBuilder(CreateFluidSubchannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CreateFluidSubchannelPacket::encode).decoder(CreateFluidSubchannelPacket::decode)
                .consumerMainThread(CreateFluidSubchannelPacket::handle).add();
        CHANNEL.messageBuilder(DeleteFluidSubchannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DeleteFluidSubchannelPacket::encode).decoder(DeleteFluidSubchannelPacket::decode)
                .consumerMainThread(DeleteFluidSubchannelPacket::handle).add();
        CHANNEL.messageBuilder(SetFluidSubchannelFilterModePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetFluidSubchannelFilterModePacket::encode).decoder(SetFluidSubchannelFilterModePacket::decode)
                .consumerMainThread(SetFluidSubchannelFilterModePacket::handle).add();
        CHANNEL.messageBuilder(AddSubchannelFluidPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AddSubchannelFluidPacket::encode).decoder(AddSubchannelFluidPacket::decode)
                .consumerMainThread(AddSubchannelFluidPacket::handle).add();
        CHANNEL.messageBuilder(RemoveSubchannelFluidPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoveSubchannelFluidPacket::encode).decoder(RemoveSubchannelFluidPacket::decode)
                .consumerMainThread(RemoveSubchannelFluidPacket::handle).add();
        CHANNEL.messageBuilder(AddEmitterFluidVoidPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AddEmitterFluidVoidPacket::encode).decoder(AddEmitterFluidVoidPacket::decode)
                .consumerMainThread(AddEmitterFluidVoidPacket::handle).add();
        CHANNEL.messageBuilder(RemoveEmitterFluidVoidPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoveEmitterFluidVoidPacket::encode).decoder(RemoveEmitterFluidVoidPacket::decode)
                .consumerMainThread(RemoveEmitterFluidVoidPacket::handle).add();
        CHANNEL.messageBuilder(SubscribeDeviceFluidPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SubscribeDeviceFluidPacket::encode).decoder(SubscribeDeviceFluidPacket::decode)
                .consumerMainThread(SubscribeDeviceFluidPacket::handle).add();
        CHANNEL.messageBuilder(MoveDeviceFluidSubchannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MoveDeviceFluidSubchannelPacket::encode).decoder(MoveDeviceFluidSubchannelPacket::decode)
                .consumerMainThread(MoveDeviceFluidSubchannelPacket::handle).add();

        // Gas + heat master enable (Mekanism-gated runtime).
        CHANNEL.messageBuilder(SetGasEnabledPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetGasEnabledPacket::encode).decoder(SetGasEnabledPacket::decode)
                .consumerMainThread(SetGasEnabledPacket::handle).add();
        CHANNEL.messageBuilder(SetHeatEnabledPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetHeatEnabledPacket::encode).decoder(SetHeatEnabledPacket::decode)
                .consumerMainThread(SetHeatEnabledPacket::handle).add();

        // --- Gas subchannel packets (Mekanism-gated runtime). ---
        CHANNEL.messageBuilder(CreateGasSubchannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CreateGasSubchannelPacket::encode).decoder(CreateGasSubchannelPacket::decode)
                .consumerMainThread(CreateGasSubchannelPacket::handle).add();
        CHANNEL.messageBuilder(DeleteGasSubchannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DeleteGasSubchannelPacket::encode).decoder(DeleteGasSubchannelPacket::decode)
                .consumerMainThread(DeleteGasSubchannelPacket::handle).add();
        CHANNEL.messageBuilder(SetGasSubchannelFilterModePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetGasSubchannelFilterModePacket::encode).decoder(SetGasSubchannelFilterModePacket::decode)
                .consumerMainThread(SetGasSubchannelFilterModePacket::handle).add();
        CHANNEL.messageBuilder(AddSubchannelGasPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AddSubchannelGasPacket::encode).decoder(AddSubchannelGasPacket::decode)
                .consumerMainThread(AddSubchannelGasPacket::handle).add();
        CHANNEL.messageBuilder(RemoveSubchannelGasPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoveSubchannelGasPacket::encode).decoder(RemoveSubchannelGasPacket::decode)
                .consumerMainThread(RemoveSubchannelGasPacket::handle).add();
        CHANNEL.messageBuilder(AddEmitterGasVoidPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AddEmitterGasVoidPacket::encode).decoder(AddEmitterGasVoidPacket::decode)
                .consumerMainThread(AddEmitterGasVoidPacket::handle).add();
        CHANNEL.messageBuilder(RemoveEmitterGasVoidPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoveEmitterGasVoidPacket::encode).decoder(RemoveEmitterGasVoidPacket::decode)
                .consumerMainThread(RemoveEmitterGasVoidPacket::handle).add();
        CHANNEL.messageBuilder(SubscribeDeviceGasPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SubscribeDeviceGasPacket::encode).decoder(SubscribeDeviceGasPacket::decode)
                .consumerMainThread(SubscribeDeviceGasPacket::handle).add();
        CHANNEL.messageBuilder(MoveDeviceGasSubchannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MoveDeviceGasSubchannelPacket::encode).decoder(MoveDeviceGasSubchannelPacket::decode)
                .consumerMainThread(MoveDeviceGasSubchannelPacket::handle).add();

        // Per-device dispatch strategy (round-robin vs serve-first) for items / fluids / gas.
        CHANNEL.messageBuilder(SetDispatchStrategyPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetDispatchStrategyPacket::encode).decoder(SetDispatchStrategyPacket::decode)
                .consumerMainThread(SetDispatchStrategyPacket::handle).add();

        // Server config snapshot sent on login + on config reload.
        CHANNEL.messageBuilder(SyncServerConfigPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncServerConfigPacket::encode).decoder(SyncServerConfigPacket::decode)
                .consumerMainThread(SyncServerConfigPacket::handle).add();
    }

    public static void sendToServer(Object msg) { CHANNEL.sendToServer(msg); }

    public static PacketDistributor.PacketTarget targetServer() {
        return PacketDistributor.SERVER.noArg();
    }
}
