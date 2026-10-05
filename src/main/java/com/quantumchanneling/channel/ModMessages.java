package com.quantumchanneling.channel;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payload registration. Client-bound handlers live in {@code client.ClientPayloadHandlers}. */
public final class ModMessages {
    private static final String PROTOCOL_VERSION = "1";

    private ModMessages() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(ToggleChunkLoadPacket.TYPE, ToggleChunkLoadPacket.STREAM_CODEC, ToggleChunkLoadPacket::handle);
        registrar.playToServer(OpenChannelsRequestPacket.TYPE, OpenChannelsRequestPacket.STREAM_CODEC, OpenChannelsRequestPacket::handle);
        registrar.playToServer(CreateChannelPacket.TYPE, CreateChannelPacket.STREAM_CODEC, CreateChannelPacket::handle);
        registrar.playToServer(DeleteChannelPacket.TYPE, DeleteChannelPacket.STREAM_CODEC, DeleteChannelPacket::handle);
        registrar.playToServer(SetDeviceChannelPacket.TYPE, SetDeviceChannelPacket.STREAM_CODEC, SetDeviceChannelPacket::handle);
        registrar.playToServer(RenameChannelPacket.TYPE, RenameChannelPacket.STREAM_CODEC, RenameChannelPacket::handle);
        registrar.playToServer(SetChannelPublicPacket.TYPE, SetChannelPublicPacket.STREAM_CODEC, SetChannelPublicPacket::handle);
        registrar.playToServer(SetChannelChargingPacket.TYPE, SetChannelChargingPacket.STREAM_CODEC, SetChannelChargingPacket::handle);
        registrar.playToServer(SetChannelPermissionPacket.TYPE, SetChannelPermissionPacket.STREAM_CODEC, SetChannelPermissionPacket::handle);
        registrar.playToServer(SubscribeChargingPacket.TYPE, SubscribeChargingPacket.STREAM_CODEC, SubscribeChargingPacket::handle);
        registrar.playToServer(SetDeviceThroughputPacket.TYPE, SetDeviceThroughputPacket.STREAM_CODEC, SetDeviceThroughputPacket::handle);
        registrar.playToServer(SetDevicePriorityPacket.TYPE, SetDevicePriorityPacket.STREAM_CODEC, SetDevicePriorityPacket::handle);
        registrar.playToServer(SetDeviceSurgePacket.TYPE, SetDeviceSurgePacket.STREAM_CODEC, SetDeviceSurgePacket::handle);
        registrar.playToServer(SetChannelColorPacket.TYPE, SetChannelColorPacket.STREAM_CODEC, SetChannelColorPacket::handle);
        registrar.playToServer(RemoteUnbindDevicePacket.TYPE, RemoteUnbindDevicePacket.STREAM_CODEC, RemoteUnbindDevicePacket::handle);
        registrar.playToClient(ShowChannelsListPacket.TYPE, ShowChannelsListPacket.STREAM_CODEC);
        registrar.playToServer(RenameDevicePacket.TYPE, RenameDevicePacket.STREAM_CODEC, RenameDevicePacket::handle);
        registrar.playToServer(SetChannelPinPacket.TYPE, SetChannelPinPacket.STREAM_CODEC, SetChannelPinPacket::handle);
        registrar.playToServer(JoinByPinPacket.TYPE, JoinByPinPacket.STREAM_CODEC, JoinByPinPacket::handle);
        registrar.playToServer(SetChannelSlotPriorityPacket.TYPE, SetChannelSlotPriorityPacket.STREAM_CODEC, SetChannelSlotPriorityPacket::handle);
        registrar.playToServer(TransferChannelOwnerPacket.TYPE, TransferChannelOwnerPacket.STREAM_CODEC, TransferChannelOwnerPacket::handle);
        registrar.playToServer(SetChannelArmorPriorityPacket.TYPE, SetChannelArmorPriorityPacket.STREAM_CODEC, SetChannelArmorPriorityPacket::handle);
        registrar.playToServer(SetChannelChargeBlockedPacket.TYPE, SetChannelChargeBlockedPacket.STREAM_CODEC, SetChannelChargeBlockedPacket::handle);
        // ---- items mode v2: dynamic subchannels + per-emitter void + per-device subscriptions ----
        registrar.playToServer(SetItemEnabledPacket.TYPE, SetItemEnabledPacket.STREAM_CODEC, SetItemEnabledPacket::handle);
        registrar.playToServer(SetItemBatchSizePacket.TYPE, SetItemBatchSizePacket.STREAM_CODEC, SetItemBatchSizePacket::handle);
        // Channel-level subchannel CRUD
        registrar.playToServer(CreateSubchannelPacket.TYPE, CreateSubchannelPacket.STREAM_CODEC, CreateSubchannelPacket::handle);
        registrar.playToServer(DeleteSubchannelPacket.TYPE, DeleteSubchannelPacket.STREAM_CODEC, DeleteSubchannelPacket::handle);
        registrar.playToServer(RenameSubchannelPacket.TYPE, RenameSubchannelPacket.STREAM_CODEC, RenameSubchannelPacket::handle);
        // Per-subchannel filter edits
        registrar.playToServer(SetSubchannelFilterModePacket.TYPE, SetSubchannelFilterModePacket.STREAM_CODEC, SetSubchannelFilterModePacket::handle);
        registrar.playToServer(AddSubchannelItemPacket.TYPE, AddSubchannelItemPacket.STREAM_CODEC, AddSubchannelItemPacket::handle);
        registrar.playToServer(RemoveSubchannelItemPacket.TYPE, RemoveSubchannelItemPacket.STREAM_CODEC, RemoveSubchannelItemPacket::handle);
        // Per-emitter void filter edits. No WL/BL toggle packet — void is hard-locked to
        // "items in the list are voided" semantics.
        registrar.playToServer(AddEmitterVoidItemPacket.TYPE, AddEmitterVoidItemPacket.STREAM_CODEC, AddEmitterVoidItemPacket::handle);
        registrar.playToServer(RemoveEmitterVoidItemPacket.TYPE, RemoveEmitterVoidItemPacket.STREAM_CODEC, RemoveEmitterVoidItemPacket::handle);
        // Per-device subscription mgmt
        registrar.playToServer(SubscribeDevicePacket.TYPE, SubscribeDevicePacket.STREAM_CODEC, SubscribeDevicePacket::handle);
        registrar.playToServer(MoveDeviceSubchannelPacket.TYPE, MoveDeviceSubchannelPacket.STREAM_CODEC, MoveDeviceSubchannelPacket::handle);
        // --- Fluid-mode packets: full parallel to the item-mode set above. ---
        registrar.playToServer(SetFluidEnabledPacket.TYPE, SetFluidEnabledPacket.STREAM_CODEC, SetFluidEnabledPacket::handle);
        registrar.playToServer(CreateFluidSubchannelPacket.TYPE, CreateFluidSubchannelPacket.STREAM_CODEC, CreateFluidSubchannelPacket::handle);
        registrar.playToServer(DeleteFluidSubchannelPacket.TYPE, DeleteFluidSubchannelPacket.STREAM_CODEC, DeleteFluidSubchannelPacket::handle);
        registrar.playToServer(SetFluidSubchannelFilterModePacket.TYPE, SetFluidSubchannelFilterModePacket.STREAM_CODEC, SetFluidSubchannelFilterModePacket::handle);
        registrar.playToServer(AddSubchannelFluidPacket.TYPE, AddSubchannelFluidPacket.STREAM_CODEC, AddSubchannelFluidPacket::handle);
        registrar.playToServer(RemoveSubchannelFluidPacket.TYPE, RemoveSubchannelFluidPacket.STREAM_CODEC, RemoveSubchannelFluidPacket::handle);
        registrar.playToServer(AddEmitterFluidVoidPacket.TYPE, AddEmitterFluidVoidPacket.STREAM_CODEC, AddEmitterFluidVoidPacket::handle);
        registrar.playToServer(RemoveEmitterFluidVoidPacket.TYPE, RemoveEmitterFluidVoidPacket.STREAM_CODEC, RemoveEmitterFluidVoidPacket::handle);
        registrar.playToServer(SubscribeDeviceFluidPacket.TYPE, SubscribeDeviceFluidPacket.STREAM_CODEC, SubscribeDeviceFluidPacket::handle);
        registrar.playToServer(MoveDeviceFluidSubchannelPacket.TYPE, MoveDeviceFluidSubchannelPacket.STREAM_CODEC, MoveDeviceFluidSubchannelPacket::handle);
        // Gas + heat master enable (Mekanism-gated runtime).
        registrar.playToServer(SetGasEnabledPacket.TYPE, SetGasEnabledPacket.STREAM_CODEC, SetGasEnabledPacket::handle);
        registrar.playToServer(SetHeatEnabledPacket.TYPE, SetHeatEnabledPacket.STREAM_CODEC, SetHeatEnabledPacket::handle);
        // --- Gas subchannel packets (Mekanism-gated runtime). ---
        registrar.playToServer(CreateGasSubchannelPacket.TYPE, CreateGasSubchannelPacket.STREAM_CODEC, CreateGasSubchannelPacket::handle);
        registrar.playToServer(DeleteGasSubchannelPacket.TYPE, DeleteGasSubchannelPacket.STREAM_CODEC, DeleteGasSubchannelPacket::handle);
        registrar.playToServer(SetGasSubchannelFilterModePacket.TYPE, SetGasSubchannelFilterModePacket.STREAM_CODEC, SetGasSubchannelFilterModePacket::handle);
        registrar.playToServer(AddSubchannelGasPacket.TYPE, AddSubchannelGasPacket.STREAM_CODEC, AddSubchannelGasPacket::handle);
        registrar.playToServer(RemoveSubchannelGasPacket.TYPE, RemoveSubchannelGasPacket.STREAM_CODEC, RemoveSubchannelGasPacket::handle);
        registrar.playToServer(AddEmitterGasVoidPacket.TYPE, AddEmitterGasVoidPacket.STREAM_CODEC, AddEmitterGasVoidPacket::handle);
        registrar.playToServer(RemoveEmitterGasVoidPacket.TYPE, RemoveEmitterGasVoidPacket.STREAM_CODEC, RemoveEmitterGasVoidPacket::handle);
        registrar.playToServer(SubscribeDeviceGasPacket.TYPE, SubscribeDeviceGasPacket.STREAM_CODEC, SubscribeDeviceGasPacket::handle);
        registrar.playToServer(MoveDeviceGasSubchannelPacket.TYPE, MoveDeviceGasSubchannelPacket.STREAM_CODEC, MoveDeviceGasSubchannelPacket::handle);
        // Per-device dispatch strategy (round-robin vs serve-first) for items / fluids / gas.
        registrar.playToServer(SetDispatchStrategyPacket.TYPE, SetDispatchStrategyPacket.STREAM_CODEC, SetDispatchStrategyPacket::handle);
        // Server config snapshot sent on login + on config reload.
        registrar.playToClient(SyncServerConfigPacket.TYPE, SyncServerConfigPacket.STREAM_CODEC);
        // Tag filter entries — add/remove a tag id alongside the existing item-id entries.
        registrar.playToServer(AddSubchannelTagItemPacket.TYPE, AddSubchannelTagItemPacket.STREAM_CODEC, AddSubchannelTagItemPacket::handle);
        registrar.playToServer(RemoveSubchannelTagItemPacket.TYPE, RemoveSubchannelTagItemPacket.STREAM_CODEC, RemoveSubchannelTagItemPacket::handle);
        registrar.playToServer(AddSubchannelTagFluidPacket.TYPE, AddSubchannelTagFluidPacket.STREAM_CODEC, AddSubchannelTagFluidPacket::handle);
        registrar.playToServer(RemoveSubchannelTagFluidPacket.TYPE, RemoveSubchannelTagFluidPacket.STREAM_CODEC, RemoveSubchannelTagFluidPacket::handle);
        registrar.playToServer(AddSubchannelTagGasPacket.TYPE, AddSubchannelTagGasPacket.STREAM_CODEC, AddSubchannelTagGasPacket::handle);
        registrar.playToServer(RemoveSubchannelTagGasPacket.TYPE, RemoveSubchannelTagGasPacket.STREAM_CODEC, RemoveSubchannelTagGasPacket::handle);
        // Cosmetic color swatch on a subchannel — one packet, kind byte picks item/fluid/gas.
        registrar.playToServer(SetSubchannelColorPacket.TYPE, SetSubchannelColorPacket.STREAM_CODEC, SetSubchannelColorPacket::handle);
        // Per-side enable masks + redstone gate.
        registrar.playToServer(SetSideMaskPacket.TYPE, SetSideMaskPacket.STREAM_CODEC, SetSideMaskPacket::handle);
        registrar.playToServer(SetRedstoneModePacket.TYPE, SetRedstoneModePacket.STREAM_CODEC, SetRedstoneModePacket::handle);
        // Channel clone — copy subchannels from one emitter to another.
        registrar.playToServer(CloneEmitterSubchannelsPacket.TYPE, CloneEmitterSubchannelsPacket.STREAM_CODEC, CloneEmitterSubchannelsPacket::handle);
        // Ping device — short particle/glow effect on the device.
        registrar.playToServer(PingDevicePacket.TYPE, PingDevicePacket.STREAM_CODEC, PingDevicePacket::handle);
        // Star Shaper's Hammer crushing a White Dwarf — server tells nearby clients to play the
        // shader-driven expanding light burst.
        registrar.playToClient(LightBurstPacket.TYPE, LightBurstPacket.STREAM_CODEC);
    }
}
