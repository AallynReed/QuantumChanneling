package com.quantumchanneling.channel;

import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.blockentity.PhotonManagerBlockEntity;
import com.quantumchanneling.blockentity.PhotonEmitterBlockEntity;
import com.quantumchanneling.blockentity.PhotonReceiverBlockEntity;
import com.quantumchanneling.blockentity.PhotonStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client-bound projection of a {@link QuantumChannel}. */
public record ChannelInfo(
        UUID id,
        String name,
        @Nullable UUID ownerId,
        String ownerName,
        int memberCount,
        boolean canManage,
        boolean isPublic,
        int chargingSlots,
        boolean subscribed,
        int emitterCount,
        int receiverCount,
        /** Energy totals — sum of every loaded emitter / receiver's FE/t last tick. */
        int totalInputRate,
        int totalOutputRate,
        /** Per-resource totals. Items in stack count/t; fluids and gas in mB/t. */
        int totalInputItems,
        int totalOutputItems,
        int totalInputFluids,
        int totalOutputFluids,
        int totalInputGas,
        int totalOutputGas,
        int color,
        boolean hasPin,
        /** Only populated when the viewer is an admin — empty otherwise. */
        String pin,
        boolean canUse,
        int slotPriorityHand,
        int slotPriorityHotbar,
        int slotPriorityInventory,
        int slotPriorityArmor,
        int slotPriorityCurios,
        /** Per-armor-piece priorities: [head, chest, legs, feet]. */
        int[] armorPiecePriorities,
        List<PlayerEntry> players,
        List<MemberPos> memberPositions,
        /**
         * Players actively receiving wireless charging this tick, with a per-slot breakdown.
         * Empty when no one is actively charging (subscribed-but-full players don't appear).
         */
        List<ChargingNowEntry> chargingNow,
        /** Per-channel item-mode settings (dynamic subchannels). Always present. */
        ItemChannelConfig itemConfig,
        /** Per-channel fluid-mode settings (dynamic subchannels). Always present. */
        FluidChannelConfig fluidConfig,
        /** Per-channel gas master-enable (Mekanism). Always present (even when Mekanism is absent). */
        GasChannelConfig gasConfig,
        /** Per-channel heat thermal-wire master-enable (Mekanism). Always present. */
        HeatChannelConfig heatConfig
) {

    public int slotPriority(int slotBit) {
        if (slotBit == ChargingSlots.HAND)      return slotPriorityHand;
        if (slotBit == ChargingSlots.HOTBAR)    return slotPriorityHotbar;
        if (slotBit == ChargingSlots.INVENTORY) return slotPriorityInventory;
        if (slotBit == ChargingSlots.ARMOR)     return slotPriorityArmor;
        if (slotBit == ChargingSlots.CURIOS)    return slotPriorityCurios;
        return 0;
    }

    /** Convenience: per-piece armor priority. 0 = head, 1 = chest, 2 = legs, 3 = feet. */
    public int armorPiecePriority(int armorIdx) {
        if (armorPiecePriorities == null || armorIdx < 0 || armorIdx >= armorPiecePriorities.length) return 0;
        return armorPiecePriorities[armorIdx];
    }

    /**
     * One actively-charging player + per-slot FE/t delivered last tick. {@code slotKey} on the
     * inner entries is a translation key from {@link ChargingSlots} (e.g. SLOT_ARMOR_HEAD).
     */
    public record ChargingNowEntry(UUID playerId, String playerName, List<ChargingSlotEntry> slots) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(playerId);
            buf.writeUtf(playerName);
            buf.writeVarInt(slots.size());
            for (ChargingSlotEntry s : slots) s.write(buf);
        }
        public static ChargingNowEntry read(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            String name = buf.readUtf();
            int n = buf.readVarInt();
            List<ChargingSlotEntry> slots = new ArrayList<>(n);
            for (int i = 0; i < n; i++) slots.add(ChargingSlotEntry.read(buf));
            return new ChargingNowEntry(id, name, slots);
        }
    }

    public record ChargingSlotEntry(String slotKey, int feLastTick) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(slotKey, 128);
            buf.writeVarInt(feLastTick);
        }
        public static ChargingSlotEntry read(FriendlyByteBuf buf) {
            String key = buf.readUtf(128);
            int fe = buf.readVarInt();
            return new ChargingSlotEntry(key, fe);
        }
    }

    public record PlayerEntry(UUID id, String name, Permission permission,
                              boolean chargingSubscribed, boolean chargingBlocked) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name);
            buf.writeVarInt(permission.ordinal());
            buf.writeBoolean(chargingSubscribed);
            buf.writeBoolean(chargingBlocked);
        }
        public static PlayerEntry read(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            String name = buf.readUtf();
            int o = buf.readVarInt();
            Permission p = (o >= 0 && o < Permission.values().length) ? Permission.values()[o] : Permission.USER;
            boolean sub = buf.readBoolean();
            boolean blocked = buf.readBoolean();
            return new PlayerEntry(id, name, p, sub, blocked);
        }
    }

    public static final byte TYPE_UNKNOWN    = 0;
    public static final byte TYPE_EMITTER    = 1;
    public static final byte TYPE_RECEIVER   = 2;
    public static final byte TYPE_STORAGE    = 3;
    public static final byte TYPE_MANAGER    = 4;

    /** A bound device's location + a snapshot of its per-device state (or default if unloaded). */
    public record MemberPos(
            String dim, long packedPos,
            byte type, int priority, boolean surge, boolean chunkLoaded, int cap, int lastTickRate,
            String customName,
            /** Subchannel UUIDs this RECEIVER subscribes to. Empty for non-receivers. */
            List<UUID> subscribedSubchannels,
            /** Per-emitter void filter. Always serialized; only meaningful for emitters. */
            ItemFilter voidFilter,
            /** Receiver-only fluid subscription set. */
            List<UUID> subscribedFluidSubchannels,
            /** Per-emitter fluid void filter. Only meaningful for emitters. */
            FluidFilter fluidVoidFilter,
            /** Receiver-only gas subscription set. */
            List<UUID> subscribedGasSubchannels,
            /** Per-emitter gas void filter. Only meaningful for emitters. */
            GasFilter gasVoidFilter,
            /** Per-device resource participation. False = device opts out of that resource. */
            boolean itemsEnabled,
            boolean fluidsEnabled,
            boolean gasEnabled,
            /** Subchannels HOSTED by this emitter, in routing-priority order. Empty for non-emitters. */
            List<ItemSubchannel> itemSubchannels,
            List<FluidSubchannel> fluidSubchannels,
            List<GasSubchannel> gasSubchannels,
            /** Per-device dispatch strategies — apply to receivers (emitter) or adjacent invs (receiver). */
            DispatchStrategy itemDispatch,
            DispatchStrategy fluidDispatch,
            DispatchStrategy gasDispatch,
            /** 6-bit side-armed masks (bit N = side with {@code Direction.get3DDataValue() == N}). */
            byte itemSideMask,
            byte fluidSideMask,
            byte gasSideMask,
            /** Redstone gate ordinal — 0 ignore, 1 off-when-powered, 2 off-when-unpowered. */
            byte redstoneMode
    ) {
        public void write(FriendlyByteBuf b) {
            b.writeUtf(dim, 80);
            b.writeLong(packedPos);
            b.writeByte(type);
            b.writeVarInt(priority);
            b.writeBoolean(surge);
            b.writeBoolean(chunkLoaded);
            b.writeVarInt(cap);
            b.writeVarInt(lastTickRate);
            b.writeUtf(customName, 64);
            b.writeVarInt(subscribedSubchannels.size());
            for (UUID id : subscribedSubchannels) b.writeUUID(id);
            voidFilter.write(b);
            b.writeVarInt(subscribedFluidSubchannels.size());
            for (UUID id : subscribedFluidSubchannels) b.writeUUID(id);
            fluidVoidFilter.write(b);
            b.writeVarInt(subscribedGasSubchannels.size());
            for (UUID id : subscribedGasSubchannels) b.writeUUID(id);
            gasVoidFilter.write(b);
            b.writeBoolean(itemsEnabled);
            b.writeBoolean(fluidsEnabled);
            b.writeBoolean(gasEnabled);
            b.writeVarInt(itemSubchannels.size());
            for (ItemSubchannel s : itemSubchannels) s.write(b);
            b.writeVarInt(fluidSubchannels.size());
            for (FluidSubchannel s : fluidSubchannels) s.write(b);
            b.writeVarInt(gasSubchannels.size());
            for (GasSubchannel s : gasSubchannels) s.write(b);
            b.writeByte(itemDispatch.ordinal());
            b.writeByte(fluidDispatch.ordinal());
            b.writeByte(gasDispatch.ordinal());
            b.writeByte(itemSideMask);
            b.writeByte(fluidSideMask);
            b.writeByte(gasSideMask);
            b.writeByte(redstoneMode);
        }
        public static MemberPos read(FriendlyByteBuf b) {
            String dim = b.readUtf(80);
            long pos = b.readLong();
            byte type = b.readByte();
            int pri = b.readVarInt();
            boolean surge = b.readBoolean();
            boolean cl = b.readBoolean();
            int cap = b.readVarInt();
            int rate = b.readVarInt();
            String name = b.readUtf(64);
            int n = b.readVarInt();
            List<UUID> subs = new ArrayList<>(n);
            for (int i = 0; i < n; i++) subs.add(b.readUUID());
            ItemFilter voidF = ItemFilter.read(b);
            int fn = b.readVarInt();
            List<UUID> fluidSubs = new ArrayList<>(fn);
            for (int i = 0; i < fn; i++) fluidSubs.add(b.readUUID());
            FluidFilter fluidVoidF = FluidFilter.read(b);
            int gn = b.readVarInt();
            List<UUID> gasSubs = new ArrayList<>(gn);
            for (int i = 0; i < gn; i++) gasSubs.add(b.readUUID());
            GasFilter gasVoidF = GasFilter.read(b);
            boolean itEn = b.readBoolean();
            boolean flEn = b.readBoolean();
            boolean gaEn = b.readBoolean();
            int isn = b.readVarInt();
            List<ItemSubchannel> itemSubs = new ArrayList<>(isn);
            for (int i = 0; i < isn; i++) itemSubs.add(ItemSubchannel.read(b));
            int fsn = b.readVarInt();
            List<FluidSubchannel> fluidSubchans = new ArrayList<>(fsn);
            for (int i = 0; i < fsn; i++) fluidSubchans.add(FluidSubchannel.read(b));
            int gsn = b.readVarInt();
            List<GasSubchannel> gasSubchans = new ArrayList<>(gsn);
            for (int i = 0; i < gsn; i++) gasSubchans.add(GasSubchannel.read(b));
            DispatchStrategy itemD  = DispatchStrategy.byOrdinal(b.readByte());
            DispatchStrategy fluidD = DispatchStrategy.byOrdinal(b.readByte());
            DispatchStrategy gasD   = DispatchStrategy.byOrdinal(b.readByte());
            byte itemMask  = b.readByte();
            byte fluidMask = b.readByte();
            byte gasMask   = b.readByte();
            byte redMode   = b.readByte();
            return new MemberPos(dim, pos, type, pri, surge, cl, cap, rate, name, subs, voidF,
                    fluidSubs, fluidVoidF, gasSubs, gasVoidF, itEn, flEn, gaEn,
                    itemSubs, fluidSubchans, gasSubchans, itemD, fluidD, gasD,
                    itemMask, fluidMask, gasMask, redMode);
        }
        public BlockPos pos() { return BlockPos.of(packedPos); }
        public String typeName() {
            return switch (type) {
                case TYPE_EMITTER    -> "Emitter";
                case TYPE_RECEIVER   -> "Receiver";
                case TYPE_STORAGE    -> "Storage";
                case TYPE_MANAGER    -> "Manager";
                default              -> "Unknown";
            };
        }
    }

    public boolean charges(int slotBit) { return ChargingSlots.has(chargingSlots, slotBit); }
    public boolean chargesAnything()    { return ChargingSlots.any(chargingSlots); }

    /** Channel-wide input total for the given resource mode. ENERGY → FE/t, ITEMS → stacks/t,
     *  FLUIDS / GASES → mB/t, HEAT → 0 (pipeline not shipped). */
    public int totalInputFor(com.quantumchanneling.channel.ResourceMode mode) {
        return switch (mode) {
            case ITEMS  -> totalInputItems;
            case FLUIDS -> totalInputFluids;
            case GASES  -> totalInputGas;
            case HEAT   -> 0;
            default     -> totalInputRate;
        };
    }
    public int totalOutputFor(com.quantumchanneling.channel.ResourceMode mode) {
        return switch (mode) {
            case ITEMS  -> totalOutputItems;
            case FLUIDS -> totalOutputFluids;
            case GASES  -> totalOutputGas;
            case HEAT   -> 0;
            default     -> totalOutputRate;
        };
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name);
        buf.writeBoolean(ownerId != null);
        if (ownerId != null) buf.writeUUID(ownerId);
        buf.writeUtf(ownerName);
        buf.writeVarInt(memberCount);
        buf.writeBoolean(canManage);
        buf.writeBoolean(isPublic);
        buf.writeVarInt(chargingSlots);
        buf.writeBoolean(subscribed);
        buf.writeVarInt(emitterCount);
        buf.writeVarInt(receiverCount);
        buf.writeVarInt(totalInputRate);
        buf.writeVarInt(totalOutputRate);
        buf.writeVarInt(totalInputItems);
        buf.writeVarInt(totalOutputItems);
        buf.writeVarInt(totalInputFluids);
        buf.writeVarInt(totalOutputFluids);
        buf.writeVarInt(totalInputGas);
        buf.writeVarInt(totalOutputGas);
        buf.writeInt(color);
        buf.writeBoolean(hasPin);
        buf.writeUtf(pin, 32);
        buf.writeBoolean(canUse);
        buf.writeVarInt(slotPriorityHand);
        buf.writeVarInt(slotPriorityHotbar);
        buf.writeVarInt(slotPriorityInventory);
        buf.writeVarInt(slotPriorityArmor);
        buf.writeVarInt(slotPriorityCurios);
        for (int i = 0; i < 4; i++) {
            buf.writeVarInt(armorPiecePriorities != null && i < armorPiecePriorities.length
                    ? armorPiecePriorities[i] : 0);
        }
        buf.writeVarInt(players.size());
        for (PlayerEntry e : players) e.write(buf);
        buf.writeVarInt(memberPositions.size());
        for (MemberPos m : memberPositions) m.write(buf);
        buf.writeVarInt(chargingNow.size());
        for (ChargingNowEntry c : chargingNow) c.write(buf);
        itemConfig.write(buf);
        fluidConfig.write(buf);
        gasConfig.write(buf);
        heatConfig.write(buf);
    }

    public static ChannelInfo read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf();
        UUID owner = buf.readBoolean() ? buf.readUUID() : null;
        String ownerName = buf.readUtf();
        int members = buf.readVarInt();
        boolean canManage = buf.readBoolean();
        boolean isPublic = buf.readBoolean();
        int slots = buf.readVarInt();
        boolean subscribed = buf.readBoolean();
        int emitterCount = buf.readVarInt();
        int receiverCount = buf.readVarInt();
        int totalIn = buf.readVarInt();
        int totalOut = buf.readVarInt();
        int totalInItems  = buf.readVarInt();
        int totalOutItems = buf.readVarInt();
        int totalInFluids  = buf.readVarInt();
        int totalOutFluids = buf.readVarInt();
        int totalInGas  = buf.readVarInt();
        int totalOutGas = buf.readVarInt();
        int color = buf.readInt();
        boolean hasPin = buf.readBoolean();
        String pin = buf.readUtf(32);
        boolean canUse = buf.readBoolean();
        int sh = buf.readVarInt();
        int sho = buf.readVarInt();
        int si = buf.readVarInt();
        int sa = buf.readVarInt();
        int sc = buf.readVarInt();
        int[] armorPr = new int[4];
        for (int i = 0; i < 4; i++) armorPr[i] = buf.readVarInt();
        int pn = buf.readVarInt();
        List<PlayerEntry> ps = new ArrayList<>(pn);
        for (int i = 0; i < pn; i++) ps.add(PlayerEntry.read(buf));
        int mn = buf.readVarInt();
        List<MemberPos> ms = new ArrayList<>(mn);
        for (int i = 0; i < mn; i++) ms.add(MemberPos.read(buf));
        int cn = buf.readVarInt();
        List<ChargingNowEntry> cnow = new ArrayList<>(cn);
        for (int i = 0; i < cn; i++) cnow.add(ChargingNowEntry.read(buf));
        ItemChannelConfig icfg = ItemChannelConfig.read(buf);
        FluidChannelConfig fcfg = FluidChannelConfig.read(buf);
        GasChannelConfig gcfg = GasChannelConfig.read(buf);
        HeatChannelConfig hcfg = HeatChannelConfig.read(buf);
        return new ChannelInfo(id, name, owner, ownerName, members, canManage, isPublic, slots, subscribed,
                emitterCount, receiverCount, totalIn, totalOut,
                totalInItems, totalOutItems, totalInFluids, totalOutFluids, totalInGas, totalOutGas,
                color, hasPin, pin, canUse,
                sh, sho, si, sa, sc, armorPr, ps, ms, cnow, icfg, fcfg, gcfg, hcfg);
    }

    public static ChannelInfo from(QuantumChannel net, @Nullable UUID viewerId, boolean subscribed, MinecraftServer server) {
        ChannelData data = ChannelData.get(server);
        List<PlayerEntry> ps = new ArrayList<>();
        // Owner appears at the top of the list as an implicit ADMIN — they're not stored in
        // playerPermissions but the user expects to see themselves on the access list.
        if (net.ownerId() != null) {
            ps.add(new PlayerEntry(net.ownerId(), net.ownerName(), Permission.ADMIN,
                    net.id().equals(data.getChargingSubscription(net.ownerId())),
                    net.isChargingBlocked(net.ownerId())));
        }
        net.playerPermissions().forEach((pid, perm) ->
                ps.add(new PlayerEntry(pid, net.playerNames().getOrDefault(pid, ""), perm,
                        net.id().equals(data.getChargingSubscription(pid)),
                        net.isChargingBlocked(pid))));
        List<MemberPos> ms = new ArrayList<>();
        int emitterCount = 0, receiverCount = 0;
        int totalIn = 0, totalOut = 0;
        int totalInItems = 0, totalOutItems = 0;
        int totalInFluids = 0, totalOutFluids = 0;
        int totalInGas = 0, totalOutGas = 0;
        for (GlobalPos gp : net.members()) {
            String dim = gp.dimension().location().toString();
            long packed = gp.pos().asLong();
            byte type = TYPE_UNKNOWN;
            int priority = 0, cap = ChannelBoundBlockEntity.DEFAULT_CAP, rate = 0;
            boolean surge = false, chunkLoaded = false;
            String customName = "";
            List<UUID> subs = new ArrayList<>();
            ItemFilter voidFilterCopy = new ItemFilter(false);   // default (blacklist empty) for non-emitters
            List<UUID> fluidSubs = new ArrayList<>();
            FluidFilter fluidVoidFilterCopy = new FluidFilter(false);
            List<UUID> gasSubs = new ArrayList<>();
            GasFilter gasVoidFilterCopy = new GasFilter(false);
            boolean itEn = false, flEn = false, gaEn = false;
            List<ItemSubchannel> itemHosted = new ArrayList<>();
            List<FluidSubchannel> fluidHosted = new ArrayList<>();
            List<GasSubchannel> gasHosted = new ArrayList<>();
            DispatchStrategy itemD = DispatchStrategy.SERVE_FIRST;
            DispatchStrategy fluidD = DispatchStrategy.SERVE_FIRST;
            DispatchStrategy gasD = DispatchStrategy.SERVE_FIRST;
            byte itemMask = 0x3F, fluidMask = 0x3F, gasMask = 0x3F;
            byte redMode = 0;
            ServerLevel level = server.getLevel(gp.dimension());
            if (level != null && level.isLoaded(gp.pos())) {
                BlockEntity be = level.getBlockEntity(gp.pos());
                if (be instanceof ChannelBoundBlockEntity bound) {
                    priority = bound.getPriority();
                    surge = bound.isSurgeMode();
                    chunkLoaded = bound.isChunkLoadForced();
                    cap = bound.getThroughputCap();
                    customName = bound.getCustomName();
                    subs.addAll(bound.getSubscribedSubchannels());
                    fluidSubs.addAll(bound.getSubscribedFluidSubchannels());
                    gasSubs.addAll(bound.getSubscribedGasSubchannels());
                    itEn = bound.isItemsEnabled();
                    flEn = bound.isFluidsEnabled();
                    gaEn = bound.isGasEnabled();
                    itemD  = bound.getItemDispatch();
                    fluidD = bound.getFluidDispatch();
                    gasD   = bound.getGasDispatch();
                    itemMask  = (byte) bound.getItemSideMask();
                    fluidMask = (byte) bound.getFluidSideMask();
                    gasMask   = (byte) bound.getGasSideMask();
                    redMode   = (byte) bound.getRedstoneMode().ordinal();
                }
                if (be instanceof PhotonEmitterBlockEntity em) {
                    type = TYPE_EMITTER; emitterCount++; rate = em.getLastTickThroughput(); totalIn += rate;
                    totalInItems  += em.getLastTickItems();
                    totalInFluids += em.getLastTickFluids();
                    totalInGas    += em.getLastTickGas();
                    voidFilterCopy.copyFrom(em.voidFilter());
                    fluidVoidFilterCopy.copyFrom(em.fluidVoidFilter());
                    gasVoidFilterCopy.copyFrom(em.gasVoidFilter());
                    itemHosted.addAll(em.itemSubchannels());
                    fluidHosted.addAll(em.fluidSubchannels());
                    gasHosted.addAll(em.gasSubchannels());
                } else if (be instanceof PhotonReceiverBlockEntity rc) {
                    type = TYPE_RECEIVER; receiverCount++; rate = rc.getLastTickThroughput(); totalOut += rate;
                    totalOutItems  += rc.getLastTickItems();
                    totalOutFluids += rc.getLastTickFluids();
                    totalOutGas    += rc.getLastTickGas();
                } else if (be instanceof PhotonStorageBlockEntity st) {
                    type = TYPE_STORAGE;
                    rate = (int) Math.min(st.getStored(), (long) Integer.MAX_VALUE);
                } else if (be instanceof PhotonManagerBlockEntity) {
                    type = TYPE_MANAGER;
                }
            }
            ms.add(new MemberPos(dim, packed, type, priority, surge, chunkLoaded, cap, rate,
                    customName, subs, voidFilterCopy, fluidSubs, fluidVoidFilterCopy, gasSubs, gasVoidFilterCopy,
                    itEn, flEn, gaEn, itemHosted, fluidHosted, gasHosted, itemD, fluidD, gasD,
                    itemMask, fluidMask, gasMask, redMode));
        }
        boolean canManage = net.canManage(viewerId);
        boolean canUse = net.canUse(viewerId);
        String pinForViewer = canManage ? net.pin() : "";

        // Build the "actively charging right now" list from last tick's per-slot breakdown.
        List<ChargingNowEntry> chargingNow = new ArrayList<>();
        var breakdown = data.getLastTickPlayerSlotBreakdown(net.id());
        for (var pe : breakdown.entrySet()) {
            UUID pid = pe.getKey();
            Map<String, Integer> bySlot = pe.getValue();
            if (bySlot.isEmpty()) continue;
            // Name lookup: owner first, then the permission map, then a live online lookup.
            String pname;
            if (pid.equals(net.ownerId())) {
                pname = net.ownerName();
            } else {
                pname = net.playerNames().getOrDefault(pid, "");
                if (pname.isEmpty()) {
                    var live = server.getPlayerList().getPlayer(pid);
                    if (live != null) pname = live.getGameProfile().getName();
                }
            }
            // Stable order: highest FE/t at the top so the biggest sinks are obvious.
            List<ChargingSlotEntry> slots = new ArrayList<>(bySlot.size());
            for (var se : bySlot.entrySet()) {
                slots.add(new ChargingSlotEntry(se.getKey(), se.getValue()));
            }
            slots.sort((a, b) -> Integer.compare(b.feLastTick(), a.feLastTick()));
            chargingNow.add(new ChargingNowEntry(pid, pname, slots));
        }
        // Players with the largest total intake float to the top.
        chargingNow.sort((a, b) -> {
            int sa = 0, sb = 0;
            for (var s : a.slots()) sa += s.feLastTick();
            for (var s : b.slots()) sb += s.feLastTick();
            return Integer.compare(sb, sa);
        });

        return new ChannelInfo(
                net.id(), net.name(), net.ownerId(), net.ownerName(),
                net.memberCount(), canManage,
                net.isPublic(), net.chargingSlots(), subscribed,
                emitterCount, receiverCount, totalIn, totalOut,
                totalInItems, totalOutItems, totalInFluids, totalOutFluids, totalInGas, totalOutGas,
                net.color(),
                net.hasPin(), pinForViewer, canUse,
                net.slotPriority(ChargingSlots.HAND),
                net.slotPriority(ChargingSlots.HOTBAR),
                net.slotPriority(ChargingSlots.INVENTORY),
                net.slotPriority(ChargingSlots.ARMOR),
                net.slotPriority(ChargingSlots.CURIOS),
                new int[]{ net.armorPiecePriority(0), net.armorPiecePriority(1),
                           net.armorPiecePriority(2), net.armorPiecePriority(3) },
                ps, ms, chargingNow,
                net.itemConfig(),
                net.fluidConfig(),
                net.gasConfig(),
                net.heatConfig());
    }
}
