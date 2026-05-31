package com.quantumchanneling.client;

import com.quantumchanneling.channel.AddEmitterFluidVoidPacket;
import com.quantumchanneling.channel.AddEmitterVoidItemPacket;
import com.quantumchanneling.channel.AddSubchannelFluidPacket;
import com.quantumchanneling.channel.AddSubchannelItemPacket;
import com.quantumchanneling.channel.ChannelInfo;
import com.quantumchanneling.channel.ChargingSlots;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.channel.CreateChannelPacket;
import com.quantumchanneling.channel.CreateFluidSubchannelPacket;
import com.quantumchanneling.channel.CreateSubchannelPacket;
import com.quantumchanneling.channel.DeleteFluidSubchannelPacket;
import com.quantumchanneling.channel.DeleteChannelPacket;
import com.quantumchanneling.channel.DeleteSubchannelPacket;
import com.quantumchanneling.channel.ItemChannelConfig;
import com.quantumchanneling.channel.ItemFilter;
import com.quantumchanneling.channel.ItemSubchannel;
import com.quantumchanneling.channel.JoinByPinPacket;
import com.quantumchanneling.channel.ModMessages;
import com.quantumchanneling.channel.MoveDeviceFluidSubchannelPacket;
import com.quantumchanneling.channel.MoveDeviceSubchannelPacket;
import com.quantumchanneling.channel.OpenChannelsRequestPacket;
import com.quantumchanneling.channel.Permission;
import com.quantumchanneling.channel.RemoteUnbindDevicePacket;
import com.quantumchanneling.channel.RemoveEmitterFluidVoidPacket;
import com.quantumchanneling.channel.RemoveEmitterVoidItemPacket;
import com.quantumchanneling.channel.RemoveSubchannelFluidPacket;
import com.quantumchanneling.channel.RemoveSubchannelItemPacket;
import com.quantumchanneling.channel.RenameChannelPacket;
import com.quantumchanneling.channel.RenameDevicePacket;
import com.quantumchanneling.channel.SetChannelArmorPriorityPacket;
import com.quantumchanneling.channel.SetChannelChargeBlockedPacket;
import com.quantumchanneling.channel.SetChannelChargingPacket;
import com.quantumchanneling.channel.SetChannelColorPacket;
import com.quantumchanneling.channel.SetChannelPermissionPacket;
import com.quantumchanneling.channel.SetChannelPinPacket;
import com.quantumchanneling.channel.SetChannelPublicPacket;
import com.quantumchanneling.channel.SetChannelSlotPriorityPacket;
import com.quantumchanneling.channel.SetDeviceChannelPacket;
import com.quantumchanneling.channel.SetDevicePriorityPacket;
import com.quantumchanneling.channel.SetDeviceSurgePacket;
import com.quantumchanneling.channel.SetDeviceThroughputPacket;
import com.quantumchanneling.channel.SetFluidEnabledPacket;
import com.quantumchanneling.channel.SetGasEnabledPacket;
import com.quantumchanneling.channel.SetHeatEnabledPacket;
import com.quantumchanneling.channel.SetFluidSubchannelFilterModePacket;
import com.quantumchanneling.channel.SetItemEnabledPacket;
import com.quantumchanneling.channel.SetSubchannelFilterModePacket;
import com.quantumchanneling.channel.SubscribeChargingPacket;
import com.quantumchanneling.channel.SubscribeDeviceFluidPacket;
import com.quantumchanneling.channel.SubscribeDevicePacket;
import com.quantumchanneling.channel.FluidFilter;
import com.quantumchanneling.channel.FluidSubchannel;
import net.minecraftforge.fluids.FluidStack;
import com.quantumchanneling.channel.ToggleChunkLoadPacket;
import com.quantumchanneling.channel.TransferChannelOwnerPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import com.quantumchanneling.menu.PhotonNodeMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PhotonNodeScreen extends AbstractContainerScreen<PhotonNodeMenu> {
    private static final int BG_W = 320;
    private static final int BG_H = 240;
    private static final int TAB_BAR_Y = 16;
    private static final int TAB_BAR_H = 20;
    private static final int CONTENT_TOP = 40;
    private static final int CHAN_ROW_H = 14;
    private static final int PERM_ROW_H = 14;
    /** Text-labelled action buttons — narrowed to 38 to fit 4 actions on a single row. */
    private static final int PERM_ACTION_W = 38;
    /** Number of action buttons rendered per Access row (Transfer / Toggle role / Block charging / Remove). */
    private static final int PERM_ACTION_COUNT = 4;
    /** Indexes into the per-row action strip — used by both renderAccess and mouseClicked. */
    private static final int ACT_TRANSFER = 0, ACT_TOGGLE_ROLE = 1, ACT_BLOCK_CHARGE = 2, ACT_REMOVE = 3;
    private static final int NODE_ROW_H = 12;

    /** Height of the hue spectrum strip on the Setup tab. */
    private static final int COLOR_STRIP_H = 18;

    /** 16 hand-picked accent swatches used as defaults when forging a channel. */
    private static final int[] PALETTE = {
            0xFFFF5050, 0xFFFF8050, 0xFFFFD050, 0xFFC8FF50,
            0xFF50FF50, 0xFF50FFC8, 0xFF50DCF0, 0xFF5080FF,
            0xFF8050FF, 0xFFC850FF, 0xFFFF50C8, 0xFFFF5080,
            0xFFFFFFFF, 0xFFC0C0C0, 0xFF808080, 0xFF303030,
    };

    public enum Tab {
        STATUS("gui.quantumchanneling.tab.status"),
        TUNE("gui.quantumchanneling.tab.tune"),
        CHARGE("gui.quantumchanneling.tab.charge"),
        NODES("gui.quantumchanneling.tab.nodes"),
        STATS("gui.quantumchanneling.tab.stats"),
        ACCESS("gui.quantumchanneling.tab.access"),
        SETUP("gui.quantumchanneling.tab.setup"),
        FORGE("gui.quantumchanneling.tab.forge");
        public final String labelKey;
        Tab(String labelKey) { this.labelKey = labelKey; }
    }

    public enum ChannelSort {
        NAME("gui.quantumchanneling.sort.name"),
        MEMBERS("gui.quantumchanneling.sort.members"),
        ACTIVITY("gui.quantumchanneling.sort.activity");
        public final String labelKey;
        ChannelSort(String labelKey) { this.labelKey = labelKey; }
        public ChannelSort next() { ChannelSort[] v = values(); return v[(ordinal() + 1) % v.length]; }
    }

    public enum NodeSort {
        POSITION("gui.quantumchanneling.sort.position"),
        TYPE("gui.quantumchanneling.sort.type"),
        PRIORITY("gui.quantumchanneling.sort.priority");
        public final String labelKey;
        NodeSort(String labelKey) { this.labelKey = labelKey; }
        public NodeSort next() { NodeSort[] v = values(); return v[(ordinal() + 1) % v.length]; }
    }

    /**
     * Side-tab strip on the right edge of the GUI. Each tab picks which configuration panel is
     * shown — devices always handle every resource simultaneously (all-in-one), the strip only
     * switches the user's view. ENERGY and ITEMS are fully implemented; FLUIDS is implemented as a
     * clone of ITEMS using vanilla {@link net.minecraftforge.fluids.capability.IFluidHandler};
     * GASES + HEAT are Mekanism-dependent and currently render explainer panels.
     */
    public enum ResourceMode {
        // HEAT is currently hidden — see #visible. The enum value is kept so existing channels
        // with a HeatConfig in NBT continue to round-trip without a save migration; the routing
        // engine and tick are both no-ops.
        ENERGY("⚡", 0xFFFFD080, "gui.quantumchanneling.resource.energy", true),
        ITEMS ("◆", 0xFFA0C0A0, "gui.quantumchanneling.resource.items",  true),
        FLUIDS("≈", 0xFF80C0FF, "gui.quantumchanneling.resource.fluids", true),
        GASES ("☁", 0xFFC080FF, "gui.quantumchanneling.resource.gases",  true),
        HEAT  ("✺", 0xFFFF8050, "gui.quantumchanneling.resource.heat",   false);
        public final String label;
        public final int color;
        public final String labelKey;
        /** Whether this mode renders in the side-tab strip and accepts clicks. */
        public final boolean visible;
        ResourceMode(String label, int color, String labelKey, boolean visible) {
            this.label = label; this.color = color; this.labelKey = labelKey; this.visible = visible;
        }
        /** Bridge to the server-side enum. Names match across both, so this is a pure relabel. */
        public com.quantumchanneling.channel.ResourceMode toChannelMode() {
            return com.quantumchanneling.channel.ResourceMode.valueOf(name());
        }
    }

    private Tab activeTab = Tab.STATUS;
    private ChannelSort channelSort = ChannelSort.NAME;
    private NodeSort nodeSort = NodeSort.POSITION;
    /**
     * The active side-mode panel. Always a {@link ResourceMode#visible} entry — if a hidden mode
     * is ever assigned (e.g. via {@link #switchTab} legacy state), the side-tab click handler
     * snaps it back to ENERGY before the next frame renders.
     */
    private ResourceMode activeMode = ResourceMode.ENERGY;
    /** Stats tab window: 1, 5, or 10 minutes. Drives which graph slot range the bars read from. */
    private int statsWindow = 1;

    private List<ChannelInfo> channels = new ArrayList<>(ClientChannelUI.cachedChannels());
    private @Nullable ChannelInfo currentChannel;
    private @Nullable ChannelInfo selectedInList;
    private @Nullable Long expandedNodePos; // packedPos of the inline-expanded Nodes row

    private @Nullable EditBox forgeNameInput;
    private @Nullable EditBox renameInput;
    private @Nullable EditBox capInput;
    private @Nullable EditBox priorityInput;
    private @Nullable EditBox addPlayerInput;
    private @Nullable EditBox deviceNameInput;
    private @Nullable EditBox pinInput;
    private @Nullable EditBox joinPinInput;
    private Permission addPlayerRole = Permission.USER;

    private @Nullable Button chunkloadButton;
    private @Nullable Button surgeButton;
    private @Nullable Button deleteButton;
    private @Nullable Button roleButton;
    private @Nullable Button resetCapButton;

    private int permScroll = 0;
    private int channelsScroll = 0;
    private int nodesScroll = 0;

    // Forge-tab authoring state — persists across rebuilds within the same screen session.
    private int forgeColor = PALETTE[6];
    private boolean forgePublic = false;

    /** Modal state: when non-null, blocks input on the rest of the screen until Confirm/Cancel. */
    private @Nullable PendingModal pendingModal;

    /**
     * Last-frame focused EditBox. We diff this against the current focus each tick to fire
     * the apply hook of whatever just lost focus (Tab away, click elsewhere, etc.).
     */
    private @Nullable EditBox lastFocusedEditBox = null;

    // ---- Items panel v2 state ----
    /**
     * Currently-edited target on the items panel. When {@code editingVoid == true} and the device
     * is an emitter, we're editing the per-emitter void filter; otherwise this is the subchannel
     * UUID (null = no subchannels yet, panel shows the empty-state).
     */
    private @Nullable UUID selectedSubchannelId;
    private boolean editingVoid = false;
    /** "New subchannel name" text input. */
    private @Nullable EditBox itemsNewSubInput;

    // ---- Fluids panel state (parallel to items) ----
    private @Nullable UUID selectedFluidSubchannelId;
    private boolean editingFluidVoid = false;
    private @Nullable EditBox fluidsNewSubInput;

    // ---- Gas panel state (parallel to fluids) ----
    private @Nullable UUID selectedGasSubchannelId;
    private boolean editingGasVoid = false;
    private @Nullable EditBox gasesNewSubInput;

    /** When true, the channel-info side panel (anchored to the left edge) is showing. */
    private boolean channelInfoOpen = false;
    /** Vertical scroll offset (in pixels) for the channel-info side panel. */
    private int channelInfoScroll = 0;
    /** Last-frame measured content height; used to clamp the scroll. */
    private int channelInfoContentHeight = 0;

    /** Description of a "Are you sure?" modal currently being shown. */
    private record PendingModal(Component title, Component body, Runnable onConfirm) {}

    public PhotonNodeScreen(PhotonNodeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = BG_W;
        // The 240-px content panel + 94-px player-inventory dock. AbstractContainerScreen renders
        // the dock's slot items automatically because the menu added 36 Slot entries; we draw the
        // dock background + slot squares in renderBg below.
        this.imageHeight = BG_H + PhotonNodeMenu.INV_DOCK_H;
        // "Inventory" label inside the dock — same horizontal alignment as the slot grid.
        this.inventoryLabelX = INV_DOCK_X;
        this.inventoryLabelY = BG_H + 4;
        // Hide the screen title text drawn by super — we paint the device name ourselves in renderLabels.
        this.titleLabelY = 5;
    }

    /** X-offset of the inventory dock slot grid (centered in BG_W). */
    private static final int INV_DOCK_X = 79;

    @Override
    protected void init() {
        super.init();
        ModMessages.sendToServer(new OpenChannelsRequestPacket());
        resolveCurrentChannel();
        rebuildAll();
    }

    public void onChannelsRefreshed(List<ChannelInfo> latest) {
        // Only rebuild widgets on STRUCTURAL change. Volatile fields (throughput, lastTickRate,
        // chargingNow) update via direct reads from currentChannel during render — they should
        // never force a rebuild because doing so would clobber active EditBox state (cursor
        // position, focus, typed-but-not-yet-submitted text).
        ChannelInfo prevCurrent = currentChannel;
        this.channels = new ArrayList<>(latest);
        resolveCurrentChannel();
        if (selectedInList != null) {
            this.selectedInList = channels.stream()
                    .filter(c -> c.id().equals(selectedInList.id())).findFirst().orElse(null);
        }
        if (structurallyDiffers(prevCurrent, currentChannel)) rebuildAll();
    }

    /**
     * Compares the parts of ChannelInfo that change widget layout or visible state, excluding
     * per-tick stats that should flow through the next render without rebuilding. Returning false
     * here means the open screen keeps its widgets intact (EditBoxes preserve text and focus).
     */
    private static boolean structurallyDiffers(@Nullable ChannelInfo a, @Nullable ChannelInfo b) {
        if (a == b) return false;
        if (a == null || b == null) return true;
        if (!a.id().equals(b.id())) return true;
        if (!a.name().equals(b.name())) return true;
        if (a.color() != b.color()) return true;
        if (a.chargingSlots() != b.chargingSlots()) return true;
        if (a.memberCount() != b.memberCount()) return true;
        if (a.canManage() != b.canManage()) return true;
        if (a.canUse() != b.canUse()) return true;
        if (a.isPublic() != b.isPublic()) return true;
        if (a.hasPin() != b.hasPin()) return true;
        // Member set — positions, types, per-device settings, and subchannel topology drive
        // which widgets appear. Don't compare lastTickRate (tick-volatile).
        var ams = a.memberPositions();
        var bms = b.memberPositions();
        if (ams.size() != bms.size()) return true;
        for (int i = 0; i < ams.size(); i++) {
            var ma = ams.get(i);
            var mb = bms.get(i);
            if (ma.packedPos() != mb.packedPos()) return true;
            if (ma.type() != mb.type()) return true;
            if (ma.priority() != mb.priority()) return true;
            if (ma.surge() != mb.surge()) return true;
            if (ma.chunkLoaded() != mb.chunkLoaded()) return true;
            if (ma.cap() != mb.cap()) return true;
            if (!ma.customName().equals(mb.customName())) return true;
            if (ma.itemsEnabled()  != mb.itemsEnabled())  return true;
            if (ma.fluidsEnabled() != mb.fluidsEnabled()) return true;
            if (ma.gasEnabled()    != mb.gasEnabled())    return true;
            if (ma.itemDispatch()  != mb.itemDispatch())  return true;
            if (ma.fluidDispatch() != mb.fluidDispatch()) return true;
            if (ma.gasDispatch()   != mb.gasDispatch())   return true;
            if (!ma.subscribedSubchannels().equals(mb.subscribedSubchannels())) return true;
            if (!ma.subscribedFluidSubchannels().equals(mb.subscribedFluidSubchannels())) return true;
            if (!ma.subscribedGasSubchannels().equals(mb.subscribedGasSubchannels())) return true;
            if (ma.itemSubchannels().size()  != mb.itemSubchannels().size())  return true;
            if (ma.fluidSubchannels().size() != mb.fluidSubchannels().size()) return true;
            if (ma.gasSubchannels().size()   != mb.gasSubchannels().size())   return true;
        }
        // Permission table — role and self-block flags affect Access tab widgets.
        var aps = a.players();
        var bps = b.players();
        if (aps.size() != bps.size()) return true;
        for (int i = 0; i < aps.size(); i++) {
            var pa = aps.get(i);
            var pb = bps.get(i);
            if (!pa.id().equals(pb.id())) return true;
            if (pa.permission() != pb.permission()) return true;
            if (pa.chargingSubscribed() != pb.chargingSubscribed()) return true;
            if (pa.chargingBlocked() != pb.chargingBlocked()) return true;
        }
        return false;
    }

    private void resolveCurrentChannel() {
        if (!menu.isChannelBound()) { currentChannel = null; return; }
        var myPos = menu.getBlockPos();
        for (ChannelInfo c : channels) {
            for (ChannelInfo.MemberPos m : c.memberPositions()) {
                if (m.pos().equals(myPos)) { currentChannel = c; return; }
            }
        }
        UUID id = menu.getBoundChannelId();
        currentChannel = id == null ? null
                : channels.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }

    private void rebuildAll() {
        if (minecraft == null) return;
        clearWidgets();
        chunkloadButton = null;
        surgeButton = null;
        deleteButton = null;
        buildTabContent();
    }

    private void switchTab(Tab t) {
        activeTab = t;
        permScroll = 0;
        nodesScroll = 0;
        expandedNodePos = null;
        rebuildAll();
    }

    /* -------- builders -------- */

    private void buildTabContent() {
        // Every tab is usable in every side-mode now. The CHARGE slot is the only one that swaps
        // content by mode — it becomes the items "Filters" panel when activeMode == ITEMS, and
        // shows a coming-soon placeholder for FLUIDS/GASES.
        switch (activeTab) {
            case STATUS -> buildStatusTab();
            case TUNE   -> buildTuneTab();
            case CHARGE -> buildChargeOrFiltersTab();
            case NODES  -> buildNodesTab();
            case STATS  -> {}
            case ACCESS -> buildAccessTab();
            case SETUP  -> buildSetupTab();
            case FORGE  -> buildForgeTab();
        }
    }

    /**
     * Dispatches the CHARGE tab slot by active side-mode: energy → charging UI, items → filters
     * (void + subchannels) UI, fluids/gases → no widgets (coming-soon overlay handles the visual).
     */
    private void buildChargeOrFiltersTab() {
        switch (activeMode) {
            case ENERGY -> buildChargeTab();
            case ITEMS  -> buildItemsPanel();
            case FLUIDS -> buildFluidsPanel();
            case GASES  -> buildGasPanel();
            case HEAT   -> buildHeatPanel();
        }
    }

    private void buildStatusTab() {
        int cx = leftPos + 8;
        int rw = BG_W - 16;

        // Device-name input with an inline "Device:" prefix label. The label is rendered in
        // renderStatus at the same Y; here we just leave room for it.
        int nameY = topPos + CONTENT_TOP + 4;
        int labelW = font.width(Component.translatable("gui.quantumchanneling.status.device_label")) + 4;
        deviceNameInput = new EditBox(font, cx + labelW, nameY, rw - labelW, 16, Component.literal(""));
        deviceNameInput.setHint(Component.translatable("gui.quantumchanneling.device.name_hint"));
        deviceNameInput.setMaxLength(32);
        deviceNameInput.setValue(menu.getDeviceName());
        addRenderableWidget(deviceNameInput);

        // Cap row: input only. Enter/unfocus commits the value; the Reset button (visible only when
        // the cap isn't already the default) snaps back to DEFAULT_CAP.
        int capY = nameY + 52;
        capInput = new EditBox(font, cx, capY, 188, 16, Component.literal(""));
        capInput.setHint(Component.translatable("gui.quantumchanneling.cap.hint"));
        capInput.setMaxLength(10);
        capInput.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        capInput.setValue(Integer.toString(Math.max(1, menu.getThroughputCap())));
        addRenderableWidget(capInput);
        resetCapButton = PhotonButton.of(cx + 192, capY - 1, 60, 18,
                Component.translatable("gui.quantumchanneling.cap.reset"),
                b -> {
                    int def = ChannelBoundBlockEntity.DEFAULT_CAP;
                    if (capInput != null) capInput.setValue(Integer.toString(def));
                    ModMessages.sendToServer(new SetDeviceThroughputPacket(menu.getBlockPos(), def));
                },
                this::accentColor);
        resetCapButton.visible = menu.getThroughputCap() != ChannelBoundBlockEntity.DEFAULT_CAP;
        addRenderableWidget(resetCapButton);

        int prY = capY + 38;
        priorityInput = new EditBox(font, cx, prY, 188, 16, Component.literal(""));
        priorityInput.setHint(Component.translatable("gui.quantumchanneling.priority.hint"));
        priorityInput.setMaxLength(6);
        priorityInput.setFilter(s -> s.isEmpty() || s.matches("-?[0-9]*"));
        priorityInput.setValue(Integer.toString(menu.getPriority()));
        addRenderableWidget(priorityInput);

        // Overdrive + Chunkload share a row — each takes half the width, with a 4-px gap between.
        int toggleY = prY + 32;
        int halfW = (rw - 4) / 2;
        surgeButton = PhotonButton.of(cx, toggleY, halfW, 18, surgeLabel(),
                b -> ModMessages.sendToServer(new SetDeviceSurgePacket(menu.getBlockPos(), !menu.isSurge())),
                this::accentColor);
        addRenderableWidget(surgeButton);

        chunkloadButton = PhotonButton.of(cx + halfW + 4, toggleY, halfW, 18, chunkloadLabel(),
                b -> ModMessages.sendToServer(new ToggleChunkLoadPacket(menu.getBlockPos(), !menu.isChunkLoaded())),
                this::accentColor);
        addRenderableWidget(chunkloadButton);

        // Disconnect is the Tune tab's job — the Status tab focuses on this device's settings.
    }

    private void applyCap() {
        if (capInput == null) return;
        String s = capInput.getValue().trim();
        int cap;
        // Cap is always a positive integer — empty or invalid input falls back to the default.
        if (s.isEmpty()) cap = ChannelBoundBlockEntity.DEFAULT_CAP;
        else {
            try { cap = Integer.parseInt(s); }
            catch (NumberFormatException ex) { cap = ChannelBoundBlockEntity.DEFAULT_CAP; }
            if (cap < 1) cap = ChannelBoundBlockEntity.DEFAULT_CAP;
        }
        capInput.setValue(Integer.toString(cap));
        ModMessages.sendToServer(new SetDeviceThroughputPacket(menu.getBlockPos(), cap));
    }

    private void applyPriority() {
        if (priorityInput == null) return;
        String s = priorityInput.getValue().trim();
        int pr;
        if (s.isEmpty() || s.equals("-")) pr = 0;
        else { try { pr = Integer.parseInt(s); } catch (NumberFormatException ex) { pr = 0; } }
        ModMessages.sendToServer(new SetDevicePriorityPacket(menu.getBlockPos(), pr));
    }

    private Component chunkloadLabel() {
        return Component.translatable(menu.isChunkLoaded()
                ? "gui.quantumchanneling.chunkload.on"
                : "gui.quantumchanneling.chunkload.off");
    }
    private Component surgeLabel() {
        return Component.translatable(menu.isSurge()
                ? "gui.quantumchanneling.surge.on"
                : "gui.quantumchanneling.surge.off");
    }

    private void buildTuneTab() {
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        int bottomY = topPos + BG_H - 26;
        // Sort button — inside the content area, top-right.
        addRenderableWidget(PhotonButton.of(cx + rw - 110, topPos + CONTENT_TOP + 2, 110, 16, sortLabel("gui.quantumchanneling.sort.by", channelSort.labelKey), b -> { channelSort = channelSort.next(); rebuildAll(); }, this::accentColor));

        // Clicking a row in the list connects (or selects-for-PIN). The bottom row is reserved for:
        //   - Disconnect, when this device is already bound to a channel
        //   - PIN entry + Join, when the selection is PIN-gated and we lack access
        //   - Nothing, otherwise (rows do the connecting)
        boolean needsPin = selectedInList != null && selectedInList.hasPin() && !selectedInList.canUse();
        if (needsPin) {
            int pinRowY = bottomY;
            joinPinInput = new EditBox(font, cx, pinRowY, rw - 80, 16, Component.literal(""));
            joinPinInput.setHint(Component.translatable("gui.quantumchanneling.channels.join_pin_hint"));
            joinPinInput.setMaxLength(16);
            addRenderableWidget(joinPinInput);
            addRenderableWidget(PhotonButton.of(cx + rw - 76, pinRowY - 1, 76, 18,
                    Component.translatable("gui.quantumchanneling.channels.join_pin"),
                    b -> {
                        if (selectedInList == null || joinPinInput == null) return;
                        ModMessages.sendToServer(new JoinByPinPacket(selectedInList.id(),
                                joinPinInput.getValue().trim()));
                    },
                    this::accentColor));
        } else if (currentChannel != null) {
            addRenderableWidget(PhotonButton.danger(cx, bottomY, rw, 20,
                    Component.translatable("gui.quantumchanneling.channel.disconnect"),
                    b -> ModMessages.sendToServer(new SetDeviceChannelPacket(menu.getBlockPos(), SetDeviceChannelPacket.NONE))));
        }
    }

    private void buildChargeTab() {
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        if (currentChannel == null) return;
        // Only the Subscribe button is a real widget. The 4 slot "cards" are rendered + hit-tested
        // manually in renderCharge / mouseClicked / mouseScrolled so they can hold more visual flair
        // than a vanilla Button supports.
        int y = topPos + CONTENT_TOP + 32;
        addRenderableWidget(PhotonButton.primary(cx, y, rw, 20,
                Component.translatable(currentChannel.subscribed()
                        ? "gui.quantumchanneling.channel.subscribed_on"
                        : "gui.quantumchanneling.channel.subscribed_off"),
                b -> ModMessages.sendToServer(new SubscribeChargingPacket(
                        currentChannel.subscribed() ? SubscribeChargingPacket.NONE : currentChannel.id())),
                this::accentColor));
    }

    private void buildNodesTab() {
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        // Sort button — same shift as Tune so it stays inside the content area.
        addRenderableWidget(PhotonButton.of(cx + rw - 110, topPos + CONTENT_TOP + 2, 110, 16, sortLabel("gui.quantumchanneling.sort.by", nodeSort.labelKey), b -> { nodeSort = nodeSort.next(); expandedNodePos = null; rebuildAll(); }, this::accentColor));

        // If a row is expanded, show its detail buttons (Unbind) at the bottom of the panel.
        if (expandedNodePos != null && currentChannel != null && currentChannel.canManage()) {
            ChannelInfo.MemberPos sel = findExpandedMember();
            if (sel != null) {
                addRenderableWidget(PhotonButton.danger(cx, topPos + BG_H - 24, rw, 20,
                        Component.translatable("gui.quantumchanneling.node.unbind"),
                        b -> {
                            ModMessages.sendToServer(new RemoteUnbindDevicePacket(currentChannel.id(), sel.dim(), sel.packedPos()));
                            expandedNodePos = null;
                        }));
            }
        }
    }

    private void buildAccessTab() {
        if (currentChannel == null || !currentChannel.canManage()) return;
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        int bottomY = topPos + BG_H - 24;

        // Three controls on the bottom row: name input, role toggle (USER ⇄ ADMIN), explicit Add
        // button. The role toggle only flips the role for the next add — it never sends a packet
        // itself. Add is the only path that actually creates the permission entry.
        int roleW = 52;
        int addW = 44;
        addPlayerInput = new EditBox(font, cx, bottomY, rw - roleW - addW - 4, 16, Component.literal(""));
        addPlayerInput.setHint(Component.translatable("gui.quantumchanneling.channels.player_hint"));
        addPlayerInput.setMaxLength(32);
        addRenderableWidget(addPlayerInput);

        roleButton = PhotonButton.of(cx + rw - roleW - addW - 2, bottomY - 1, roleW, 18, roleButtonLabel(), b -> {
                    addPlayerRole = addPlayerRole == Permission.USER ? Permission.ADMIN : Permission.USER;
                    if (roleButton != null) roleButton.setMessage(roleButtonLabel());
                }, this::accentColor);
        addRenderableWidget(roleButton);

        addRenderableWidget(PhotonButton.of(cx + rw - addW, bottomY - 1, addW, 18,
                Component.translatable("gui.quantumchanneling.channels.role.add"),
                b -> applyAddPlayer(), this::accentColor));
    }

    private Component roleButtonLabel() {
        return Component.translatable(addPlayerRole == Permission.ADMIN
                ? "gui.quantumchanneling.channels.role.admin"
                : "gui.quantumchanneling.channels.role.user");
    }

    private void buildSetupTab() {
        if (currentChannel == null || !currentChannel.canManage()) return;
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        int y = topPos + CONTENT_TOP + 4;

        renameInput = new EditBox(font, cx, y, rw, 16, Component.literal(currentChannel.name()));
        renameInput.setValue(currentChannel.name());
        renameInput.setMaxLength(32);
        addRenderableWidget(renameInput);
        y += 30;

        addRenderableWidget(PhotonButton.of(cx, y, rw, 18,
                Component.translatable(currentChannel.isPublic()
                        ? "gui.quantumchanneling.channels.public_on"
                        : "gui.quantumchanneling.channels.public_off"),
                b -> ModMessages.sendToServer(new SetChannelPublicPacket(currentChannel.id(), !currentChannel.isPublic())),
                this::accentColor));
        y += 30;

        // PIN row — admins see the current PIN (plaintext). Type and Enter to set, or use Clear.
        pinInput = new EditBox(font, cx, y, rw - 58, 16, Component.literal(""));
        pinInput.setHint(Component.translatable("gui.quantumchanneling.channels.pin_hint"));
        pinInput.setMaxLength(16);
        pinInput.setValue(currentChannel.pin());
        addRenderableWidget(pinInput);
        addRenderableWidget(PhotonButton.of(cx + rw - 54, y - 1, 54, 18,
                Component.translatable("gui.quantumchanneling.channels.pin_clear"),
                b -> {
                    if (pinInput != null) pinInput.setValue("");
                    ModMessages.sendToServer(new SetChannelPinPacket(currentChannel.id(), ""));
                },
                this::accentColor));
        y += 34;

        // Color picking on the Edit tab is purely the hue spectrum strip — rendered + click-handled
        // separately. No hex EditBox here.

        deleteButton = PhotonButton.of(cx, topPos + BG_H - 24, rw, 20, deleteLabel(), b -> tryDelete(), this::accentColor);
        addRenderableWidget(deleteButton);
    }

    /** Parses the hex input ("#RRGGBB" or "RRGGBB") and sends a SetChannelColorPacket. */
    // applyHexColor removed — color picking is purely the hue spectrum strip now.

    private Component deleteLabel() {
        return hasShiftDown()
                ? Component.translatable("gui.quantumchanneling.delete.confirm").withStyle(ChatFormatting.RED)
                : Component.translatable("gui.quantumchanneling.delete.hint").withStyle(ChatFormatting.GRAY);
    }
    private void tryDelete() {
        if (currentChannel == null) return;
        if (!hasShiftDown()) return;
        ModMessages.sendToServer(new DeleteChannelPacket(currentChannel.id()));
    }

    private void buildForgeTab() {
        int cx = leftPos + 8;
        int rw = BG_W - 16;

        // Name input
        int nameY = topPos + CONTENT_TOP + 24;
        forgeNameInput = new EditBox(font, cx, nameY, rw, 18,
                Component.translatable("gui.quantumchanneling.channels.name_input"));
        forgeNameInput.setMaxLength(32);
        forgeNameInput.setHint(Component.translatable("gui.quantumchanneling.channels.name_hint"));
        if (minecraft != null && minecraft.player != null && forgeNameInput.getValue().isEmpty()) {
            forgeNameInput.setValue(minecraft.player.getGameProfile().getName() + "'s Channel");
        }
        addRenderableWidget(forgeNameInput);

        // Color picker — the hue spectrum strip lives at CONTENT_TOP + 70 (see renderForge and
        // mouseClicked hit-test). Public toggle sits below with more breathing room.
        int pubY = topPos + CONTENT_TOP + 70 + COLOR_STRIP_H + 14;
        addRenderableWidget(PhotonButton.of(cx, pubY, rw, 18, Component.translatable(forgePublic
                        ? "gui.quantumchanneling.channels.public_on"
                        : "gui.quantumchanneling.channels.public_off"), b -> { forgePublic = !forgePublic; rebuildAll(); }, this::accentColor));

        // PIN input — empty leaves the channel without a PIN
        int pinY = pubY + 26;
        pinInput = new EditBox(font, cx, pinY, rw, 16, Component.literal(""));
        pinInput.setHint(Component.translatable("gui.quantumchanneling.channels.pin_hint"));
        pinInput.setMaxLength(16);
        addRenderableWidget(pinInput);

        // Forge it!
        int btnY = pinY + 26;
        addRenderableWidget(PhotonButton.of(cx, btnY, rw, 20, Component.translatable("gui.quantumchanneling.channels.create"), b -> {
                    String n = forgeNameInput.getValue().trim();
                    if (n.isEmpty()) return;
                    String pin = pinInput == null ? "" : pinInput.getValue().trim();
                    ModMessages.sendToServer(new CreateChannelPacket(n, forgeColor, pin, forgePublic));
                    forgeNameInput.setValue("");
                    if (pinInput != null) pinInput.setValue("");
                    switchTab(Tab.STATUS);
                }, this::accentColor));
    }

    private static Component sortLabel(String keyPrefix, String byKey) {
        return Component.translatable(keyPrefix).append(": ").append(Component.translatable(byKey));
    }

    /** Channel accent color or the default cyan when unbound — fed to every PhotonButton's supplier. */
    private int accentColor() {
        return currentChannel != null ? currentChannel.color() : 0xFF50DCF0;
    }

    /**
     * Suffix appended to a channel name in the list to disambiguate identically-named channels
     * across owners. Returns empty for channels owned by the local player; otherwise " §7ownerName"
     * (or a UUID fragment when no name is cached).
     */
    private String ownerSuffix(ChannelInfo c) {
        if (c.ownerId() == null) return "";
        if (minecraft != null && minecraft.player != null
                && minecraft.player.getUUID().equals(c.ownerId())) return "";
        String n = c.ownerName();
        if (n != null && !n.isEmpty()) return " §7" + n;
        return " §7#" + c.ownerId().toString().substring(0, 6);
    }

    /* -------- render -------- */

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partial);
        renderTabBar(gfx, mouseX, mouseY);
        renderActiveTabContent(gfx, mouseX, mouseY);
        renderSideTabs(gfx, mouseX, mouseY);
        renderChannelInfoSidebar(gfx, mouseX, mouseY);
        // HEAT and locked (mod-missing) modes still get a content-area splash via
        // renderFiltersComingSoon. Items / Fluids / Gas have full panels when their provider mod is
        // present; other tabs (Status, Tune, Nodes, Stats, Access, Setup, Forge) are always interactive.
        renderConfirmModal(gfx, mouseX, mouseY);
        renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partial, int mouseX, int mouseY) {
        int accent = currentChannel != null ? currentChannel.color() : 0xFF50DCF0;
        int accentDim = blendARGB(accent, 0xFF000000, 0.55f);

        // Outer accent border (2 px), then the dark panel inset 2 px.
        gfx.fill(leftPos - 2, topPos - 2, leftPos + BG_W + 2, topPos + BG_H + 2, accentDim);
        gfx.fill(leftPos, topPos, leftPos + BG_W, topPos + BG_H, 0xFF0B0E14);

        // Title bar — slightly darker top stripe with an accent underline.
        gfx.fill(leftPos, topPos, leftPos + BG_W, topPos + 14, 0xFF06080C);
        gfx.fill(leftPos, topPos + 14, leftPos + BG_W, topPos + 15, accent);

        // Tab strip background.
        gfx.fill(leftPos, topPos + TAB_BAR_Y, leftPos + BG_W, topPos + TAB_BAR_Y + TAB_BAR_H - 1, 0xFF11161F);
        // Accent bar under the tab strip — separates tabs from content.
        gfx.fill(leftPos, topPos + CONTENT_TOP - 2, leftPos + BG_W, topPos + CONTENT_TOP - 1, accentDim);

        // Bottom accent bar.
        gfx.fill(leftPos, topPos + BG_H - 1, leftPos + BG_W, topPos + BG_H, accent);

        // Subtle vertical accent strips on the side edges (1 px).
        gfx.fill(leftPos, topPos + CONTENT_TOP, leftPos + 1, topPos + BG_H, accentDim);
        gfx.fill(leftPos + BG_W - 1, topPos + CONTENT_TOP, leftPos + BG_W, topPos + BG_H, accentDim);

        renderInventoryDock(gfx, accent, accentDim);
    }

    /**
     * Paints the player-inventory dock below the content area: an accent-bordered panel, the
     * "Inventory" label region, and 36 slot squares (3×9 main + 9 hotbar) that match the slot
     * positions added in {@link PhotonNodeMenu#addPlayerInventorySlots}. The vanilla container
     * screen renders the item stacks on top of these squares automatically.
     */
    private void renderInventoryDock(GuiGraphics gfx, int accent, int accentDim) {
        int dockTop = topPos + BG_H;
        int dockBot = topPos + imageHeight;
        // Outer accent border for the dock (2 px) matching the main panel.
        gfx.fill(leftPos - 2, dockTop, leftPos + BG_W + 2, dockBot + 2, accentDim);
        // Dock panel body — slightly lighter than content to read as a separate region.
        gfx.fill(leftPos, dockTop, leftPos + BG_W, dockBot, 0xFF0E121A);
        // Top accent stripe to separate dock from content.
        gfx.fill(leftPos, dockTop, leftPos + BG_W, dockTop + 1, accent);
        // Side edge strips.
        gfx.fill(leftPos, dockTop, leftPos + 1, dockBot, accentDim);
        gfx.fill(leftPos + BG_W - 1, dockTop, leftPos + BG_W, dockBot, accentDim);
        // Bottom accent stripe.
        gfx.fill(leftPos, dockBot - 1, leftPos + BG_W, dockBot, accent);

        // 36 slot squares — 16×16 dark inset inside an 18×18 cell, matching vanilla container UX.
        int slotInner = 0xFF1A1F2A;
        int slotShadow = 0xFF06080C;
        // Main 3×9.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = leftPos + INV_DOCK_X + col * 18;
                int sy = topPos + BG_H + 16 + row * 18;
                paintSlot(gfx, sx, sy, slotInner, slotShadow);
            }
        }
        // Hotbar.
        for (int col = 0; col < 9; col++) {
            int sx = leftPos + INV_DOCK_X + col * 18;
            int sy = topPos + BG_H + 74;
            paintSlot(gfx, sx, sy, slotInner, slotShadow);
        }
    }

    /** Single 18×18 slot cell: dark base, 16×16 inset, faint hi-light/shadow for depth. */
    private static void paintSlot(GuiGraphics gfx, int x, int y, int inner, int shadow) {
        gfx.fill(x - 1, y - 1, x + 17, y + 17, shadow);
        gfx.fill(x, y, x + 16, y + 16, inner);
    }

    /**
     * Compact suffix-based number formatting for long FE values. Keeps two significant digits
     * after the point and uses K / M / G / T / P / E for thousands ... quintillions.
     */
    private static String formatFE(long fe) {
        long abs = Math.abs(fe);
        if (abs < 1_000L) return Long.toString(fe);
        String[] suffixes = { "K", "M", "G", "T", "P", "E" };
        int idx = 0;
        double v = fe;
        while (Math.abs(v) >= 1_000.0 && idx < suffixes.length) {
            v /= 1_000.0;
            idx++;
        }
        return String.format("%.2f%s", v, suffixes[idx - 1]);
    }

    /**
     * Display label for a top-bar tab. The CHARGE slot swaps to "Filters" in non-energy modes
     * because its content swaps too — energy = wireless charging UI, items/fluids/gases = filter
     * configuration. Every other tab is mode-agnostic and uses its static labelKey.
     */
    private Component topTabLabel(Tab t) {
        if (t == Tab.CHARGE && activeMode != ResourceMode.ENERGY) {
            return Component.translatable("gui.quantumchanneling.tab.filters");
        }
        return Component.translatable(t.labelKey);
    }

    /** Linear interpolation between two ARGB colors. {@code t} = 0 returns a, 1 returns b. */
    private static int blendARGB(int a, int b, float t) {
        int aA = (a >> 24) & 0xFF, aR = (a >> 16) & 0xFF, aG = (a >> 8) & 0xFF, aB = a & 0xFF;
        int bA = (b >> 24) & 0xFF, bR = (b >> 16) & 0xFF, bG = (b >> 8) & 0xFF, bB = b & 0xFF;
        int rA = Math.round(aA + (bA - aA) * t);
        int rR = Math.round(aR + (bR - aR) * t);
        int rG = Math.round(aG + (bG - aG) * t);
        int rB = Math.round(aB + (bB - aB) * t);
        return (rA << 24) | (rR << 16) | (rG << 8) | rB;
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        // Title — prefer the live name from the current ChannelInfo so renaming the device updates
        // the header without reopening. Fall back to the constructor-time menu copy, then the block name.
        ChannelInfo.MemberPos here = findThisDevice();
        String liveName = here != null ? here.customName() : "";
        String left = !liveName.isEmpty() ? liveName
                : (menu.getDeviceName().isEmpty() ? title.getString() : menu.getDeviceName());
        gfx.drawString(font, left, 8, 5, 0xFFFFFF, false);

        // Channel name + color swatch — right-aligned in the title bar. Unbound = grey "—".
        if (currentChannel != null) {
            String name = currentChannel.name();
            int color = currentChannel.color();
            int textW = font.width(name);
            int swatchX = BG_W - 8 - textW - 12;
            int textX = BG_W - 8 - textW;
            gfx.fill(swatchX, 5, swatchX + 8, 13, color);
            gfx.drawString(font, name, textX, 5, color, false);
        } else {
            String msg = "—";
            int textW = font.width(msg);
            gfx.drawString(font, msg, BG_W - 8 - textW, 5, 0xFF888888, false);
        }

        // "Inventory" label inside the dock — coords are relative to (leftPos, topPos).
        gfx.drawString(font, playerInventoryTitle, INV_DOCK_X, BG_H + 4, 0xFFC0C8D0, false);
    }

    private void renderTabBar(GuiGraphics gfx, int mouseX, int mouseY) {
        Tab[] tabs = Tab.values();
        int tabW = (BG_W - 8) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            int x = leftPos + 4 + i * tabW;
            int y = topPos + TAB_BAR_Y + 1;
            boolean active = tabs[i] == activeTab;
            boolean hover  = mouseX >= x && mouseX < x + tabW && mouseY >= y && mouseY < y + TAB_BAR_H - 2;
            int fill = active ? 0xFF1A1F2A : (hover ? 0xFF18202E : 0xFF0F141C);
            gfx.fill(x, y, x + tabW - 1, y + TAB_BAR_H - 2, fill);
            if (active) {
                // Active underline uses the channel color when bound, fallback to default accent.
                int accent = currentChannel != null ? currentChannel.color() : 0xFF50DCF0;
                gfx.fill(x, y + TAB_BAR_H - 3, x + tabW - 1, y + TAB_BAR_H - 1, accent);
            }
            Component label = topTabLabel(tabs[i]);
            int labelW = font.width(label);
            gfx.drawString(font, label, x + (tabW - labelW) / 2, y + 5,
                    active ? 0xFFFFFFFF : 0xFFC0C8D0, false);
        }
    }

    /* -------- side tabs (resource mode strip on the right edge) -------- */

    private static final int SIDE_TAB_W = 22;
    private static final int SIDE_TAB_H = 28;
    private static final int SIDE_TAB_GAP = 2;

    private void renderSideTabs(GuiGraphics gfx, int mouseX, int mouseY) {
        ResourceMode[] modes = ResourceMode.values();
        int startY = topPos + CONTENT_TOP;
        int slot = 0;
        for (int i = 0; i < modes.length; i++) {
            ResourceMode m = modes[i];
            if (!m.visible) continue;
            int x = leftPos + BG_W;
            int y = startY + slot * (SIDE_TAB_H + SIDE_TAB_GAP);
            slot++;
            boolean active = m == activeMode;
            boolean hover = mouseX >= x && mouseX < x + SIDE_TAB_W && mouseY >= y && mouseY < y + SIDE_TAB_H;
            boolean locked = isModeLocked(m);

            // Border. Locked tabs dim the accent to a dark grey so they read as "disabled" instead
            // of the channel-color border the other tabs use.
            int borderColor = locked ? 0xFF3A3F48 : m.color;
            gfx.fill(x - 1, y - 1, x + SIDE_TAB_W + 1, y + SIDE_TAB_H + 1, borderColor);
            int body;
            if (locked) {
                body = hover ? 0xFF14181F : 0xFF0A0D12;
            } else {
                body = active ? 0xFF1A1F2A : (hover ? 0xFF18202E : 0xFF0B0E14);
            }
            gfx.fill(x, y, x + SIDE_TAB_W, y + SIDE_TAB_H, body);

            // Glyph. Locked tabs use a muted grey to make the unavailable state obvious; hovering
            // a locked tab brightens slightly so the user knows it IS interactive (the click opens
            // the placeholder panel explaining what's needed to unlock it).
            int textColor;
            if (locked) {
                textColor = hover ? 0xFF8A929C : 0xFF606870;
            } else {
                textColor = active ? m.color : 0xFFC0C8D0;
            }
            int labelW = font.width(m.label);
            gfx.drawString(font, m.label, x + (SIDE_TAB_W - labelW) / 2, y + 10, textColor, false);
        }
    }

    /**
     * A side-mode is "locked" when the mod that provides its underlying capability isn't loaded.
     * Locked modes still render and switch (so the user can see the placeholder explaining what
     * to install), but they get a muted appearance so they read as unavailable at a glance.
     */
    private static boolean isModeLocked(ResourceMode m) {
        return switch (m) {
            case ENERGY, ITEMS, FLUIDS -> false;
            case GASES -> !Compat.gasProviderLoaded();
            case HEAT  -> !Compat.heatProviderLoaded();
        };
    }

    /* -------- Confirmation modal (e.g. ownership transfer) -------- */

    private static final int MODAL_W = 220;
    private static final int MODAL_H = 92;
    private static final int MODAL_BTN_W = 80;
    private static final int MODAL_BTN_H = 18;

    private int modalX() { return leftPos + (BG_W - MODAL_W) / 2; }
    private int modalY() { return topPos + (BG_H - MODAL_H) / 2; }

    private void renderConfirmModal(GuiGraphics gfx, int mouseX, int mouseY) {
        if (pendingModal == null) return;
        int mx = modalX();
        int my = modalY();
        int accent = accentColor();

        // Dim the rest of the panel (content area + inventory dock).
        gfx.fill(leftPos, topPos, leftPos + BG_W, topPos + imageHeight, 0xC0000000);
        // Modal frame
        gfx.fill(mx - 2, my - 2, mx + MODAL_W + 2, my + MODAL_H + 2, accent);
        gfx.fill(mx, my, mx + MODAL_W, my + MODAL_H, 0xFF0B0E14);
        // Title bar
        gfx.fill(mx, my, mx + MODAL_W, my + 14, 0xFF1A1F2A);
        gfx.fill(mx, my + 14, mx + MODAL_W, my + 15, accent);
        int titleW = font.width(pendingModal.title());
        gfx.drawString(font, pendingModal.title(), mx + (MODAL_W - titleW) / 2, my + 4, 0xFFFFFFFF, false);

        // Body — wrap to fit width.
        var lines = font.split(pendingModal.body(), MODAL_W - 16);
        int by = my + 20;
        for (var line : lines) {
            int lw = font.width(line);
            gfx.drawString(font, line, mx + (MODAL_W - lw) / 2, by, 0xFFD0D0D0, false);
            by += 10;
        }

        // Buttons — Confirm (accent) on left, Cancel (red) on right.
        int btnY = my + MODAL_H - MODAL_BTN_H - 6;
        int confirmX = mx + 12;
        int cancelX = mx + MODAL_W - MODAL_BTN_W - 12;
        drawModalButton(gfx, "gui.quantumchanneling.modal.confirm", confirmX, btnY, mouseX, mouseY, accent, false);
        drawModalButton(gfx, "gui.quantumchanneling.modal.cancel", cancelX, btnY, mouseX, mouseY, 0xFFE05050, true);
    }

    private void drawModalButton(GuiGraphics gfx, String key, int x, int y, int mouseX, int mouseY,
                                 int accent, boolean isCancel) {
        boolean hover = mouseX >= x && mouseX < x + MODAL_BTN_W && mouseY >= y && mouseY < y + MODAL_BTN_H;
        int border = hover ? accent : blendARGB(accent, 0xFF000000, 0.5f);
        int body = hover ? 0xFF1F2840 : 0xFF14202E;
        gfx.fill(x - 1, y - 1, x + MODAL_BTN_W + 1, y + MODAL_BTN_H + 1, border);
        gfx.fill(x, y, x + MODAL_BTN_W, y + MODAL_BTN_H, body);
        Component msg = Component.translatable(key);
        int textW = font.width(msg);
        int textColor = isCancel
                ? (hover ? 0xFFFFE0E0 : 0xFFD08080)
                : (hover ? 0xFFEAF0FF : 0xFFC8E0FF);
        gfx.drawString(font, msg, x + (MODAL_BTN_W - textW) / 2, y + (MODAL_BTN_H - 8) / 2, textColor, false);
    }

    /** True when a click on (mx, my) was handled by the modal. */
    private boolean handleModalClick(double mx, double my) {
        if (pendingModal == null) return false;
        int x = modalX();
        int y = modalY();
        int btnY = y + MODAL_H - MODAL_BTN_H - 6;
        int confirmX = x + 12;
        int cancelX = x + MODAL_W - MODAL_BTN_W - 12;
        boolean inConfirm = mx >= confirmX && mx < confirmX + MODAL_BTN_W && my >= btnY && my < btnY + MODAL_BTN_H;
        boolean inCancel = mx >= cancelX && mx < cancelX + MODAL_BTN_W && my >= btnY && my < btnY + MODAL_BTN_H;
        if (inConfirm) {
            Runnable cb = pendingModal.onConfirm();
            pendingModal = null;
            if (cb != null) cb.run();
            return true;
        }
        if (inCancel) {
            pendingModal = null;
            return true;
        }
        // Clicks anywhere on the dimmer eat the click (don't fall through to widgets underneath).
        return true;
    }

    /** Returns true if the click hit a side tab (and was consumed). */
    private boolean handleSideTabClick(double mx, double my) {
        ResourceMode[] modes = ResourceMode.values();
        int startY = topPos + CONTENT_TOP;
        int x = leftPos + BG_W;
        int slot = 0;
        for (int i = 0; i < modes.length; i++) {
            ResourceMode m = modes[i];
            if (!m.visible) continue;
            int y = startY + slot * (SIDE_TAB_H + SIDE_TAB_GAP);
            slot++;
            if (mx >= x && mx < x + SIDE_TAB_W && my >= y && my < y + SIDE_TAB_H) {
                if (activeMode != m) {
                    activeMode = m;
                    rebuildAll();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * True when a coming-soon splash currently covers the content area and should swallow any
     * click that lands inside it. Only happens when the user is looking at the Charge/Filters
     * tab in a side-mode that isn't implemented yet (fluids / gases).
     */
    private boolean overlayBlocking() {
        // All five modes now have real panels (gas/heat panels render a "Mekanism required"
        // message instead when no provider is present, but they accept clicks fine).
        return false;
    }

    /* -------- Channel info side panel (left edge tab) -------- */

    private static final int LEFT_TAB_W = 22;
    private static final int LEFT_TAB_H = 56;
    private static final int CHANNEL_PANEL_W = 200;

    /** Renders the small "CH" tab on the left edge. */
    private void renderChannelInfoTab(GuiGraphics gfx, int mouseX, int mouseY) {
        int x = leftPos - LEFT_TAB_W;
        int y = topPos + CONTENT_TOP;
        int accent = accentColor();
        boolean hover = mouseX >= x && mouseX < x + LEFT_TAB_W && mouseY >= y && mouseY < y + LEFT_TAB_H;
        // Border + body
        gfx.fill(x - 1, y - 1, x + LEFT_TAB_W + 1, y + LEFT_TAB_H + 1, accent);
        int body = channelInfoOpen ? 0xFF1A1F2A : (hover ? 0xFF18202E : 0xFF0B0E14);
        gfx.fill(x, y, x + LEFT_TAB_W, y + LEFT_TAB_H, body);
        // Vertically stacked label: 'C' 'H'
        Component c1 = Component.literal("C");
        Component c2 = Component.literal("H");
        int textColor = channelInfoOpen ? accent : 0xFFC0C8D0;
        gfx.drawString(font, c1, x + (LEFT_TAB_W - font.width(c1)) / 2, y + 16, textColor, false);
        gfx.drawString(font, c2, x + (LEFT_TAB_W - font.width(c2)) / 2, y + 30, textColor, false);
    }

    /**
     * Renders the channel-info panel OUTSIDE the main interface — anchored to the left of the
     * "CH" tab so the main content stays fully visible.
     */
    private void renderChannelInfoSidebar(GuiGraphics gfx, int mouseX, int mouseY) {
        // Always draw the tab so the user can open it.
        renderChannelInfoTab(gfx, mouseX, mouseY);
        if (!channelInfoOpen) return;

        int x = leftPos - LEFT_TAB_W - CHANNEL_PANEL_W;
        int y = topPos + CONTENT_TOP;
        int w = CHANNEL_PANEL_W;
        int h = BG_H - CONTENT_TOP - 4;
        int accent = accentColor();
        // Sidebar frame.
        gfx.fill(x - 1, y - 1, x + w + 1, y + h + 1, accent);
        gfx.fill(x, y, x + w, y + h, 0xFF0B0E14);
        // Header bar.
        gfx.fill(x, y, x + w, y + 16, 0xFF1A1F2A);
        gfx.fill(x, y + 16, x + w, y + 17, accent);
        Component header = Component.translatable("gui.quantumchanneling.channel.info.title");
        gfx.drawString(font, header, x + 8, y + 4, 0xFFFFFFFF, false);
        // Close (X) at the top-right of the sidebar.
        int closeX = x + w - 12;
        int closeY = y + 4;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + 8 && mouseY >= closeY && mouseY < closeY + 8;
        gfx.drawString(font, Component.literal("✕"), closeX, closeY,
                closeHover ? 0xFFFF7878 : 0xFFD0D0D0, false);

        // Content area: scrollable.
        int contentX = x + 8;
        int contentTop = y + 22;
        int contentBottom = y + h - 6;
        gfx.enableScissor(x + 1, contentTop, x + w - 1, contentBottom);
        int cy = contentTop - channelInfoScroll;

        if (currentChannel == null) {
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.unbound")
                    .withStyle(ChatFormatting.GRAY), contentX, cy, 0xAAAAAA, false);
            gfx.disableScissor();
            return;
        }

        // Color swatch + channel name.
        gfx.fill(contentX, cy, contentX + 10, cy + 10, currentChannel.color());
        gfx.drawString(font, Component.literal(currentChannel.name()), contentX + 14, cy + 1,
                currentChannel.color(), false);
        cy += 16;

        // Owner.
        cy = drawChInfoFormatted(gfx, contentX, cy,
                "gui.quantumchanneling.channel.info.owner_fmt", currentChannel.ownerName());

        // Access (public/private + PIN).
        String access = currentChannel.isPublic() ? "§aPublic" : "§cPrivate";
        cy = drawChInfoFormatted(gfx, contentX, cy, "gui.quantumchanneling.status.access", access);
        if (currentChannel.hasPin()) {
            cy = drawChInfoFormatted(gfx, contentX, cy, "gui.quantumchanneling.channel.info.pin_fmt", "§6●●●●");
        }

        // Counts.
        cy = drawChInfoFormatted(gfx, contentX, cy,
                "gui.quantumchanneling.stats.emitters", currentChannel.emitterCount());
        cy = drawChInfoFormatted(gfx, contentX, cy,
                "gui.quantumchanneling.stats.receivers", currentChannel.receiverCount());
        cy = drawChInfoFormatted(gfx, contentX, cy,
                "gui.quantumchanneling.channel.members", currentChannel.memberCount());

        // Rates — per-mode totals with units that match what the player is viewing.
        String modeSuffix = activeMode.name().toLowerCase();
        int totalIn = currentChannel.totalInputFor(activeMode.toChannelMode());
        cy = drawChInfoFormatted(gfx, contentX, cy,
                "gui.quantumchanneling.stats.total_input." + modeSuffix, totalIn, totalIn * 20);
        int totalOut = currentChannel.totalOutputFor(activeMode.toChannelMode());
        cy = drawChInfoFormatted(gfx, contentX, cy,
                "gui.quantumchanneling.stats.total_output." + modeSuffix, totalOut, totalOut * 20);

        // Energy / items / fluids / gas each render their own summary block. Heat stays in the
        // "coming soon" placeholder until the thermal-wire design is finished.
        cy = switch (activeMode) {
            case ENERGY -> renderEnergyInfoSections(gfx, contentX, cy);
            case ITEMS  -> renderItemsInfoSections(gfx, contentX, cy);
            case FLUIDS -> renderFluidsInfoSections(gfx, contentX, cy);
            case GASES  -> renderGasInfoSections(gfx, contentX, cy);
            case HEAT   -> renderComingSoonInfoSection(gfx, contentX, cy);
        };

        gfx.disableScissor();
        // Cache the total content height so wheel-scrolling can clamp. Also re-clamp the current
        // scroll value here so the next frame starts inside the valid range.
        int totalContentH = (cy + channelInfoScroll) - contentTop + 4;
        int viewH = contentBottom - contentTop;
        channelInfoContentHeight = totalContentH;
        int maxScroll = Math.max(0, totalContentH - viewH);
        if (channelInfoScroll > maxScroll) channelInfoScroll = maxScroll;
        if (channelInfoScroll < 0) channelInfoScroll = 0;
        if (totalContentH > viewH) {
            // Scrollbar on the right edge.
            int trackX = x + w - 4;
            gfx.fill(trackX, contentTop, trackX + 2, contentBottom, 0xFF202632);
            int thumbH = Math.max(8, viewH * viewH / totalContentH);
            int thumbY = contentTop + (viewH - thumbH) * channelInfoScroll / Math.max(1, maxScroll);
            gfx.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, accent);
        }
    }

    /**
     * Energy-mode subsections of the channel-info sidebar: charging-slot bullet list, the players
     * table, and the live charge-activity tree. Returns the next y-cursor.
     */
    private int renderEnergyInfoSections(GuiGraphics gfx, int contentX, int cy) {
        cy += 4;
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.info.charging_header")
                .withStyle(ChatFormatting.AQUA), contentX, cy, 0xC8E0FF, false);
        cy += 12;
        int chargeMask = currentChannel.chargingSlots();
        if (!ChargingSlots.any(chargeMask)) {
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.info.charging_off")
                    .withStyle(ChatFormatting.GRAY), contentX + 4, cy, 0xFF888888, false);
            cy += 11;
        } else {
            if (ChargingSlots.has(chargeMask, ChargingSlots.HAND))
                cy = drawChInfoBullet(gfx, contentX + 4, cy, "gui.quantumchanneling.charge.hand");
            if (ChargingSlots.has(chargeMask, ChargingSlots.HOTBAR))
                cy = drawChInfoBullet(gfx, contentX + 4, cy, "gui.quantumchanneling.charge.hotbar");
            if (ChargingSlots.has(chargeMask, ChargingSlots.INVENTORY))
                cy = drawChInfoBullet(gfx, contentX + 4, cy, "gui.quantumchanneling.charge.inventory");
            if (ChargingSlots.has(chargeMask, ChargingSlots.ARMOR))
                cy = drawChInfoBullet(gfx, contentX + 4, cy, "gui.quantumchanneling.charge.armor");
            if (ChargingSlots.has(chargeMask, ChargingSlots.CURIOS))
                cy = drawChInfoBullet(gfx, contentX + 4, cy, "gui.quantumchanneling.charge.curios");
        }

        cy += 4;
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.info.players_header")
                .withStyle(ChatFormatting.AQUA), contentX, cy, 0xC8E0FF, false);
        cy += 12;
        for (ChannelInfo.PlayerEntry e : currentChannel.players()) {
            boolean isOwner = currentChannel.ownerId() != null && currentChannel.ownerId().equals(e.id());
            String role = isOwner ? "♛"
                    : (e.permission() == Permission.ADMIN ? "★" : "•");
            int color = isOwner ? 0xFFFFE070
                    : (e.permission() == Permission.ADMIN ? 0xFFFFD080 : 0xFFFFFFFF);
            gfx.drawString(font, Component.literal(role + " " + e.name()), contentX + 4, cy, color, false);
            cy += 11;
        }

        cy += 4;
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.info.charge_header")
                .withStyle(ChatFormatting.AQUA), contentX, cy, 0xC8E0FF, false);
        cy += 12;
        var nowList = currentChannel.chargingNow();
        if (nowList.isEmpty()) {
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.info.no_active_charging")
                    .withStyle(ChatFormatting.GRAY), contentX + 4, cy, 0xFF888888, false);
            cy += 11;
        } else {
            for (ChannelInfo.ChargingNowEntry e : nowList) {
                String displayName = e.playerName().isEmpty() ? "—" : e.playerName();
                gfx.drawString(font, Component.literal("⚡ " + displayName),
                        contentX + 4, cy, 0xFF80E0A0, false);
                cy += 11;
                for (ChannelInfo.ChargingSlotEntry s : e.slots()) {
                    gfx.drawString(font, Component.translatable(
                                    "gui.quantumchanneling.channel.info.charge_slot_row",
                                    Component.translatable(s.slotKey()), s.feLastTick()),
                            contentX + 14, cy, 0xFFB0D0B0, false);
                    cy += 10;
                }
                cy += 2;
            }
        }
        return cy;
    }

    /**
     * Items-mode side panel. Walks every loaded emitter on the channel and lists the subchannels
     * each one hosts, with a receiver-subscriber tally for each. Ends with this device's void
     * filter summary when it's an emitter.
     */
    private int renderItemsInfoSections(GuiGraphics gfx, int contentX, int cy) {
        cy = renderHostedSubchannelsBlock(gfx, contentX, cy,
                "gui.quantumchanneling.channel.info.items_header",
                ResourceMode.ITEMS);

        ChannelInfo.MemberPos here = findThisDevice();
        if (here != null && here.type() == ChannelInfo.TYPE_EMITTER) {
            cy += 4;
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.info.void_header")
                    .withStyle(ChatFormatting.AQUA), contentX, cy, 0xC8E0FF, false);
            cy += 12;
            cy = drawChInfoFormatted(gfx, contentX + 4, cy,
                    "gui.quantumchanneling.channel.info.void_detail_fmt", here.voidFilter().size());
        }
        return cy;
    }

    /** Fluids-mode side panel — same shape as the items one. */
    private int renderFluidsInfoSections(GuiGraphics gfx, int contentX, int cy) {
        cy = renderHostedSubchannelsBlock(gfx, contentX, cy,
                "gui.quantumchanneling.channel.info.fluids_header",
                ResourceMode.FLUIDS);

        ChannelInfo.MemberPos here = findThisDevice();
        if (here != null && here.type() == ChannelInfo.TYPE_EMITTER) {
            cy += 4;
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.info.void_header")
                    .withStyle(ChatFormatting.AQUA), contentX, cy, 0xC8E0FF, false);
            cy += 12;
            cy = drawChInfoFormatted(gfx, contentX + 4, cy,
                    "gui.quantumchanneling.channel.info.void_detail_fmt", here.fluidVoidFilter().size());
        }
        return cy;
    }

    /**
     * Gas-mode side panel. When Mekanism is missing this still renders, but as a single grey
     * "locked" line — the panel itself stays informative rather than dropping back to "coming soon".
     */
    private int renderGasInfoSections(GuiGraphics gfx, int contentX, int cy) {
        if (isModeLocked(ResourceMode.GASES)) return renderComingSoonInfoSection(gfx, contentX, cy);
        cy = renderHostedSubchannelsBlock(gfx, contentX, cy,
                "gui.quantumchanneling.channel.info.gases_header",
                ResourceMode.GASES);

        ChannelInfo.MemberPos here = findThisDevice();
        if (here != null && here.type() == ChannelInfo.TYPE_EMITTER) {
            cy += 4;
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.info.void_header")
                    .withStyle(ChatFormatting.AQUA), contentX, cy, 0xC8E0FF, false);
            cy += 12;
            cy = drawChInfoFormatted(gfx, contentX + 4, cy,
                    "gui.quantumchanneling.channel.info.void_detail_fmt", here.gasVoidFilter().size());
        }
        return cy;
    }

    /**
     * Shared layout: header → "Hosted by Emitter X" group → each subchannel with WL/BL marker,
     * filter entry count, and receiver-subscriber tally. {@code mode} picks which resource pool
     * to iterate.
     */
    private int renderHostedSubchannelsBlock(GuiGraphics gfx, int contentX, int cy,
                                             String headerKey, ResourceMode mode) {
        cy += 4;
        gfx.drawString(font, Component.translatable(headerKey)
                .withStyle(ChatFormatting.AQUA), contentX, cy, 0xC8E0FF, false);
        cy += 12;

        // Receiver-subscriber tally per subchannel id, built once.
        Map<UUID, Integer> rcvTally = new HashMap<>();
        for (ChannelInfo.MemberPos m : currentChannel.memberPositions()) {
            if (m.type() != ChannelInfo.TYPE_RECEIVER) continue;
            List<UUID> set = switch (mode) {
                case ITEMS  -> m.subscribedSubchannels();
                case FLUIDS -> m.subscribedFluidSubchannels();
                case GASES  -> m.subscribedGasSubchannels();
                default     -> List.of();
            };
            for (UUID sid : set) rcvTally.merge(sid, 1, Integer::sum);
        }

        boolean anyHosted = false;
        for (ChannelInfo.MemberPos m : currentChannel.memberPositions()) {
            if (m.type() != ChannelInfo.TYPE_EMITTER) continue;
            List<? extends NamedSubchannel> hosted = switch (mode) {
                case ITEMS  -> namedView(m.itemSubchannels());
                case FLUIDS -> namedView(m.fluidSubchannels());
                case GASES  -> namedView(m.gasSubchannels());
                default     -> List.of();
            };
            if (hosted.isEmpty()) continue;
            anyHosted = true;
            String label = m.customName().isEmpty()
                    ? Component.translatable("gui.quantumchanneling.channel.info.emitter_label",
                            m.pos().toShortString()).getString()
                    : m.customName();
            gfx.drawString(font, Component.literal("⌁ " + label),
                    contentX + 2, cy, 0xFFFFE070, false);
            cy += 11;
            for (NamedSubchannel sub : hosted) {
                int rcvCount = rcvTally.getOrDefault(sub.id(), 0);
                String mark = sub.whitelist() ? "§aWL" : "§cBL";
                gfx.drawString(font, Component.literal("• " + sub.name()),
                        contentX + 14, cy, 0xFFFFFFFF, false);
                cy += 10;
                gfx.drawString(font, Component.translatable(
                                "gui.quantumchanneling.channel.info.sub_detail_fmt",
                                mark, sub.filterSize(), rcvCount),
                        contentX + 24, cy, 0xFFB0B8C0, false);
                cy += 11;
            }
        }
        if (!anyHosted) {
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.info.no_subchannels")
                    .withStyle(ChatFormatting.GRAY), contentX + 4, cy, 0xFF888888, false);
            cy += 11;
        }
        return cy;
    }

    /** Adapter so the shared layout doesn't need three near-identical loops. */
    private interface NamedSubchannel {
        UUID id();
        String name();
        boolean whitelist();
        int filterSize();
    }

    private static List<NamedSubchannel> namedView(List<? extends Object> subs) {
        List<NamedSubchannel> out = new ArrayList<>(subs.size());
        for (Object o : subs) {
            if (o instanceof ItemSubchannel s) {
                out.add(new NamedSubchannel() {
                    public UUID id() { return s.id(); }
                    public String name() { return s.name(); }
                    public boolean whitelist() { return s.filter().isWhitelist(); }
                    public int filterSize() { return s.filter().size(); }
                });
            } else if (o instanceof FluidSubchannel s) {
                out.add(new NamedSubchannel() {
                    public UUID id() { return s.id(); }
                    public String name() { return s.name(); }
                    public boolean whitelist() { return s.filter().isWhitelist(); }
                    public int filterSize() { return s.filter().size(); }
                });
            } else if (o instanceof com.quantumchanneling.channel.GasSubchannel s) {
                out.add(new NamedSubchannel() {
                    public UUID id() { return s.id(); }
                    public String name() { return s.name(); }
                    public boolean whitelist() { return s.filter().isWhitelist(); }
                    public int filterSize() { return s.filter().size(); }
                });
            }
        }
        return out;
    }

    /** Fluids / gases / heat side-panel placeholder. Locked variant calls out the missing mod. */
    private int renderComingSoonInfoSection(GuiGraphics gfx, int contentX, int cy) {
        boolean locked = isModeLocked(activeMode);
        cy += 4;
        String suffixKey = locked
                ? "gui.quantumchanneling.resource.locked_title"
                : "gui.quantumchanneling.resource.coming_soon_title";
        gfx.drawString(font, Component.translatable(activeMode.labelKey)
                        .copy().append(" — ")
                        .append(Component.translatable(suffixKey))
                        .withStyle(locked ? ChatFormatting.RED : ChatFormatting.GRAY),
                contentX, cy, locked ? 0xFFE08080 : 0xFF888888, false);
        cy += 11;
        if (locked) {
            String bodyKey = "gui.quantumchanneling.resource." + activeMode.name().toLowerCase() + ".locked_body";
            var lines = font.split(Component.translatable(bodyKey)
                    .withStyle(ChatFormatting.GRAY), CHANNEL_PANEL_W - 18);
            for (var line : lines) {
                gfx.drawString(font, line, contentX, cy, 0xFFAAAAAA, false);
                cy += 10;
            }
        }
        return cy;
    }

    /**
     * Renders one formatted line in the channel-info side panel. {@code labelKey}'s translation
     * is passed the values as format arguments, so a key like "Owner: %s" is filled in correctly.
     */
    private int drawChInfoFormatted(GuiGraphics gfx, int x, int y, String labelKey, Object... args) {
        gfx.drawString(font, Component.translatable(labelKey, args), x, y, 0xFFCCCCCC, false);
        return y + 11;
    }

    /** Renders a single "• Label" bullet row in the channel-info side panel. */
    private int drawChInfoBullet(GuiGraphics gfx, int x, int y, String labelKey) {
        gfx.drawString(font, Component.literal("• ").append(Component.translatable(labelKey)),
                x, y, 0xFFCCCCCC, false);
        return y + 11;
    }

    /** True when a click hit the left tab or somewhere inside the open sidebar. */
    private boolean handleChannelInfoClick(double mx, double my) {
        // Tab toggles open/close.
        int tabX = leftPos - LEFT_TAB_W;
        int tabY = topPos + CONTENT_TOP;
        if (mx >= tabX && mx < tabX + LEFT_TAB_W && my >= tabY && my < tabY + LEFT_TAB_H) {
            channelInfoOpen = !channelInfoOpen;
            channelInfoScroll = 0;
            return true;
        }
        if (!channelInfoOpen) return false;
        int x = leftPos - LEFT_TAB_W - CHANNEL_PANEL_W;
        int y = topPos + CONTENT_TOP;
        int w = CHANNEL_PANEL_W;
        int h = BG_H - CONTENT_TOP - 4;
        // Close button.
        int closeX = x + w - 12;
        int closeY = y + 4;
        if (mx >= closeX && mx < closeX + 8 && my >= closeY && my < closeY + 8) {
            channelInfoOpen = false;
            return true;
        }
        // Click anywhere inside the panel swallows the input so widgets underneath don't fire.
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void renderActiveTabContent(GuiGraphics gfx, int mouseX, int mouseY) {
        switch (activeTab) {
            case STATUS -> renderStatus(gfx);
            case TUNE   -> renderTune(gfx, mouseX, mouseY);
            case CHARGE -> renderChargeOrFilters(gfx, mouseX, mouseY);
            case NODES  -> renderNodes(gfx, mouseX, mouseY);
            case STATS  -> renderStats(gfx);
            case ACCESS -> renderAccess(gfx, mouseX, mouseY);
            case SETUP  -> renderSetup(gfx);
            case FORGE  -> renderForge(gfx);
        }
    }

    private void renderChargeOrFilters(GuiGraphics gfx, int mouseX, int mouseY) {
        switch (activeMode) {
            case ENERGY -> renderCharge(gfx);
            case ITEMS  -> renderItemsPanel(gfx, mouseX, mouseY);
            case FLUIDS -> renderFluidsPanel(gfx, mouseX, mouseY);
            case GASES  -> renderGasPanel(gfx);
            case HEAT   -> renderHeatPanel(gfx);
        }
    }

    /**
     * Placeholder splash drawn INSIDE the Charge/Filters tab content area for fluids/gases/heat.
     * Two states:
     * <ul>
     *   <li><b>Locked</b> — the mod that would provide this resource isn't loaded. The accent
     *       turns red-orange and the body text names what to install to unlock the mode.</li>
     *   <li><b>Coming soon</b> — provider IS loaded but the QC integration isn't shipped yet.
     *       Accent stays in the mode's color and the body says it's on the roadmap.</li>
     * </ul>
     */
    private void renderFiltersComingSoon(GuiGraphics gfx) {
        boolean locked = isModeLocked(activeMode);
        int accent = locked ? 0xFFE05050 : activeMode.color;
        int x1 = leftPos + 6;
        int y1 = topPos + CONTENT_TOP + 2;
        int x2 = leftPos + BG_W - 6;
        int y2 = topPos + BG_H - 6;
        gfx.fill(x1, y1, x2, y2, 0xE6000000);
        gfx.fill(x1, y1, x2, y1 + 1, accent);
        gfx.fill(x1, y2 - 1, x2, y2, accent);
        gfx.fill(x1, y1, x1 + 1, y2, accent);
        gfx.fill(x2 - 1, y1, x2, y2, accent);

        String suffixKey = locked
                ? "gui.quantumchanneling.resource.locked_title"
                : "gui.quantumchanneling.resource.coming_soon_title";
        Component title = Component.translatable(activeMode.labelKey)
                .append(" — ")
                .append(Component.translatable(suffixKey));
        int titleW = font.width(title);
        gfx.drawString(font, title, leftPos + (BG_W - titleW) / 2, topPos + BG_H / 2 - 18,
                accent, false);

        String bodyKey = locked
                ? "gui.quantumchanneling.resource." + activeMode.name().toLowerCase() + ".locked_body"
                : "gui.quantumchanneling.resource.coming_soon_body";
        Component body = Component.translatable(bodyKey);
        var lines = font.split(body, BG_W - 40);
        int y = topPos + BG_H / 2 - 2;
        for (var line : lines) {
            int w = font.width(line);
            gfx.drawString(font, line, leftPos + (BG_W - w) / 2, y, 0xFFC0C0C0, false);
            y += 11;
        }
    }

    private void renderStatus(GuiGraphics gfx) {
        int cx = leftPos + 8;
        // Inline "Device:" prefix label, aligned with the EditBox at nameY = CONTENT_TOP + 4.
        int nameY = topPos + CONTENT_TOP + 4;
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.status.device_label"),
                cx, nameY + 4, 0xFFFFFFFF, false);

        // Throughput / storage figure sits between the device row (ends y=+60) and the cap row.
        int infoY = nameY + 22;
        if (menu.isStorage()) {
            long stored = menu.getStoredLong();
            long capacity = menu.getStorageCapacity();
            int pct = capacity > 0 ? (int) (stored * 100L / capacity) : 0;
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.storage.stored",
                            formatFE(stored), formatFE(capacity), pct), cx, infoY, 0xC8E0FF, false);
        } else {
            // Per-mode throughput line — units match the resource the player is viewing. The
            // emitter and receiver block entities feed four separate counters; the menu exposes
            // the right one for the active mode. HEAT prints a "(unavailable)" placeholder since
            // the heat pipeline isn't shipped.
            int throughput = menu.getThroughputFor(activeMode.toChannelMode());
            String tkey = "gui.quantumchanneling.throughput." + activeMode.name().toLowerCase();
            gfx.drawString(font, Component.translatable(tkey, throughput, throughput * 20),
                    cx, infoY, 0xC8E0FF, false);
            // Loop warning — only emitters set this; it lingers for a few seconds after the last
            // detection. We push the line up close so the user can't miss it.
            if (menu.isLoopWarning()) {
                gfx.drawString(font,
                        Component.translatable("gui.quantumchanneling.loop.warning"),
                        cx, infoY + 12, 0xFFFF6060, false);
                gfx.drawString(font,
                        Component.translatable("gui.quantumchanneling.loop.warning.hint")
                                .withStyle(ChatFormatting.GRAY),
                        cx, infoY + 24, 0xFFAAAAAA, false);
            }
        }

        // Field labels above the cap / priority inputs. The build-side offsets are:
        //   capY = nameY + 52 = topPos + 96, label 10 px above → topPos + 86
        //   prY  = capY  + 38 = topPos + 134, label 10 px above → topPos + 124
        // Storage devices use the same input but the value caps OUTPUT only; intake is uncapped.
        String capLabelKey = menu.isStorage()
                ? "gui.quantumchanneling.cap.label_storage"
                : "gui.quantumchanneling.cap.label";
        gfx.drawString(font, Component.translatable(capLabelKey),
                cx, topPos + 86, 0xAAAAAA, false);
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.priority.label"),
                cx, topPos + 124, 0xAAAAAA, false);
    }

    private List<ChannelInfo> sortedChannels() {
        var list = new ArrayList<>(channels);
        switch (channelSort) {
            case NAME -> list.sort(Comparator.comparing(c -> c.name().toLowerCase()));
            case MEMBERS -> list.sort((a, b) -> Integer.compare(b.memberCount(), a.memberCount()));
            case ACTIVITY -> list.sort((a, b) ->
                    Integer.compare(b.totalInputRate() + b.totalOutputRate(),
                                    a.totalInputRate() + a.totalOutputRate()));
        }
        return list;
    }

    private void renderTune(GuiGraphics gfx, int mouseX, int mouseY) {
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        // List starts 20px below content top so the sort button (y=42..58) has clear space.
        int areaTop = topPos + CONTENT_TOP + 24;
        int areaBottom = topPos + BG_H - 30;
        int rowW = rw - 6;
        int visible = Math.max(1, (areaBottom - areaTop) / CHAN_ROW_H);
        var sortedList = sortedChannels();
        int total = sortedList.size();
        clampChannelsScroll();

        if (total == 0) {
            Component msg = Component.translatable("gui.quantumchanneling.tune.empty")
                    .withStyle(ChatFormatting.GRAY);
            int msgW = font.width(msg);
            gfx.drawString(font, msg, leftPos + (BG_W - msgW) / 2,
                    areaTop + (areaBottom - areaTop) / 2 - 4, 0xFFAAAAAA, false);
            return;
        }

        gfx.enableScissor(cx, areaTop, cx + rowW + 6, areaBottom);
        for (int i = 0; i < visible && (i + channelsScroll) < total; i++) {
            ChannelInfo c = sortedList.get(i + channelsScroll);
            int y = areaTop + i * CHAN_ROW_H;
            boolean isSel = selectedInList != null && selectedInList.id().equals(c.id());
            boolean isCur = currentChannel != null && currentChannel.id().equals(c.id());
            // Colored dot for the channel tag.
            gfx.fill(cx + 2, y + 4, cx + 8, y + 10, c.color());
            int color = isSel ? 0xFF80E0FF : (isCur ? 0xFFFFD080 : 0xFFFFFFFF);
            String pinFlag = c.hasPin() ? " §6🔒" : "";
            String name = c.name() + ownerSuffix(c) + (c.isPublic() ? " §a*" : "") + pinFlag + (isCur ? " §6✓" : "");
            gfx.drawString(font, Component.literal(name).withStyle(s -> s.withColor(color)),
                    cx + 12, y + 3, color, false);
            String right = "(" + c.memberCount() + ")";
            gfx.drawString(font, Component.literal(right), cx + rowW - font.width(right) - 4, y + 3, 0xAAAAAA, false);
        }
        gfx.disableScissor();

        if (total > visible) drawScrollbar(gfx, cx + rowW + 2, areaTop, areaBottom - areaTop,
                visible, total, channelsScroll, maxChannelsScroll());
    }

    private void renderCharge(GuiGraphics gfx) {
        int cx = leftPos + 8;
        int y = topPos + CONTENT_TOP + 4;
        if (currentChannel == null) {
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.unbound")
                    .withStyle(ChatFormatting.GRAY), cx, y, 0xAAAAAA, false);
            return;
        }
        // Diagnostic — augmented with manager awareness. Wrapped to the inner panel width so it
        // never spills under the right-side mode tabs.
        boolean sub = currentChannel.subscribed();
        boolean anySlot = currentChannel.chargesAnything();
        boolean hasManager = false;
        for (ChannelInfo.MemberPos m : currentChannel.memberPositions()) {
            if (m.type() == ChannelInfo.TYPE_MANAGER) { hasManager = true; break; }
        }
        String tipKey;
        ChatFormatting tipColor;
        if (!hasManager)           { tipKey = "gui.quantumchanneling.charge.tip.no_manager"; tipColor = ChatFormatting.RED; }
        else if (sub && anySlot)   { tipKey = "gui.quantumchanneling.charge.tip.active";        tipColor = ChatFormatting.GREEN; }
        else if (!sub)             { tipKey = "gui.quantumchanneling.charge.tip.no_sub";        tipColor = ChatFormatting.YELLOW; }
        else                       { tipKey = "gui.quantumchanneling.charge.tip.no_slots";      tipColor = ChatFormatting.YELLOW; }
        int innerW = BG_W - 16;
        // Clip to the inner panel rect so a long translation never bleeds under the right-side mode tabs.
        gfx.enableScissor(cx, y, cx + innerW, y + 40);
        var wrapped = font.split(Component.translatable(tipKey).withStyle(tipColor), innerW);
        int diagY = y;
        for (var line : wrapped) {
            gfx.drawString(font, line, cx, diagY, 0xFFFFFF, false);
            diagY += 10;
        }
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.status.charging.section")
                .withStyle(ChatFormatting.AQUA), cx, topPos + CONTENT_TOP + 14, 0xC8E0FF, false);
        gfx.disableScissor();

        // Visual slot "cards" — 2×2 grid below the subscribe button and diagnostic.
        // Layout is anchored from CHARGE_LAYOUT_TOP to keep hit-tests deterministic regardless of
        // how the diagnostic wrapped.
        if (currentChannel.canManage()) {
            int cardsTop = topPos + CHARGE_LAYOUT_TOP;
            renderChargeCards(gfx, cardsTop);
            int armorRowY = cardsTop + 2 * (CHARGE_CARD_H + CHARGE_CARD_GAP) + 2;
            renderArmorPieceRow(gfx, armorRowY);
            int curiosY = armorRowY + ARMOR_PIECE_H + 4;
            renderCuriosCard(gfx, curiosY);
        }
    }

    /* -------- Charge tab: visual slot cards -------- */

    /** Y-offset (relative to topPos) where the 2×2 card grid begins. Everything below scales from here. */
    private static final int CHARGE_LAYOUT_TOP = CONTENT_TOP + 56;
    private static final int CHARGE_CARD_W = 145;
    private static final int CHARGE_CARD_H = 42;
    private static final int CHARGE_CARD_GAP = 6;

    private static final int[] CHARGE_SLOT_BITS = {
            ChargingSlots.HAND, ChargingSlots.HOTBAR,
            ChargingSlots.INVENTORY, ChargingSlots.ARMOR
    };
    private static final String[] CHARGE_SLOT_KEYS = {
            "gui.quantumchanneling.charge.hand", "gui.quantumchanneling.charge.hotbar",
            "gui.quantumchanneling.charge.inventory", "gui.quantumchanneling.charge.armor"
    };
    /** Icon glow color per slot — warm for hand/hotbar, cool for inventory/armor. */
    private static final int[] CHARGE_SLOT_ICON = {
            0xFFFFC880, 0xFFE0E080, 0xFF80B8E0, 0xFFC8A0E0
    };

    /** Top-left corner (in screen coords) of card index {@code idx}, given {@code top}. */
    private int chargeCardX(int idx) {
        int leftX = leftPos + 8;
        int rightX = leftX + CHARGE_CARD_W + (BG_W - 16 - 2 * CHARGE_CARD_W);
        return (idx % 2 == 0) ? leftX : rightX;
    }
    private int chargeCardY(int top, int idx) {
        return top + (idx / 2) * (CHARGE_CARD_H + CHARGE_CARD_GAP);
    }

    private void renderChargeCards(GuiGraphics gfx, int top) {
        int accent = currentChannel != null ? currentChannel.color() : 0xFF50DCF0;
        for (int i = 0; i < 4; i++) {
            int x = chargeCardX(i);
            int y = chargeCardY(top, i);
            int slotBit = CHARGE_SLOT_BITS[i];
            boolean serverDisabled = !isSlotAllowedByServer(slotBit);
            // When the server forbids this slot, the channel may still have it set in its mask
            // (existing data) — but we display it as locked and ignore clicks. The dispatcher
            // already skips it tick-side; this just makes the UI reflect that.
            boolean on = !serverDisabled && currentChannel.charges(slotBit);
            int pr = currentChannel.slotPriority(slotBit);

            // Card body + border. Server-locked uses a dim red palette so it's obviously not just
            // "off"; active uses the channel accent; off-but-allowed uses neutral grey.
            int border = serverDisabled ? 0xFF4A2030
                    : (on ? accent : 0xFF333A48);
            int body = serverDisabled ? 0xFF1A0E14
                    : (on ? 0xFF192030 : 0xFF0F141C);
            gfx.fill(x - 1, y - 1, x + CHARGE_CARD_W + 1, y + CHARGE_CARD_H + 1, border);
            gfx.fill(x, y, x + CHARGE_CARD_W, y + CHARGE_CARD_H, body);

            int iconSize = 12;
            int iconX = x + CHARGE_CARD_W - iconSize - 6;
            int iconY = y + 6;
            int iconColor = serverDisabled
                    ? blendARGB(CHARGE_SLOT_ICON[i], 0xFF000000, 0.75f)
                    : (on ? CHARGE_SLOT_ICON[i] : blendARGB(CHARGE_SLOT_ICON[i], 0xFF000000, 0.55f));
            gfx.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, iconColor);
            gfx.fill(iconX - 1, iconY - 1, iconX + iconSize + 1, iconY, 0xFF000000);
            gfx.fill(iconX - 1, iconY + iconSize, iconX + iconSize + 1, iconY + iconSize + 1, 0xFF000000);
            gfx.fill(iconX - 1, iconY - 1, iconX, iconY + iconSize + 1, 0xFF000000);
            gfx.fill(iconX + iconSize, iconY - 1, iconX + iconSize + 1, iconY + iconSize + 1, 0xFF000000);

            Component title = Component.translatable(CHARGE_SLOT_KEYS[i]);
            int titleColor = serverDisabled ? 0xFF806060
                    : (on ? 0xFFFFFFFF : 0xFFA0A8B4);
            gfx.drawString(font, title, x + 8, y + 6, titleColor, false);

            Component state;
            int stateColor;
            if (serverDisabled) {
                state = Component.translatable("gui.quantumchanneling.charge.locked_by_server");
                stateColor = 0xFFE07070;
            } else if (on) {
                state = Component.literal("§a● Active");
                stateColor = 0xFF80E0A0;
            } else {
                state = Component.literal("§7○ Disabled");
                stateColor = 0xFF888888;
            }
            gfx.drawString(font, state, x + 8, y + 20, stateColor, false);

            String prText = "↕ P: " + pr;
            gfx.drawString(font, Component.literal(prText), x + 8, y + CHARGE_CARD_H - 12,
                    serverDisabled ? 0xFF50586A
                            : (on ? 0xFFC8E0FF : 0xFF6A7280), false);
        }
    }

    /**
     * Server-side gate for a charging slot group: a single slot is allowed only when both the
     * wireless master switch and the slot's own toggle are enabled in the synced config mirror.
     * Used to paint locked styling and to swallow clicks / scrolls on disabled cards.
     */
    private static boolean isSlotAllowedByServer(int slotBit) {
        if (!ClientServerConfig.wirelessEnabled) return false;
        return switch (slotBit) {
            case ChargingSlots.HAND      -> ClientServerConfig.slotHandEnabled;
            case ChargingSlots.HOTBAR    -> ClientServerConfig.slotHotbarEnabled;
            case ChargingSlots.INVENTORY -> ClientServerConfig.slotInventoryEnabled;
            case ChargingSlots.ARMOR     -> ClientServerConfig.slotArmorEnabled;
            case ChargingSlots.CURIOS    -> ClientServerConfig.slotCuriosEnabled;
            default -> true;
        };
    }

    /* -------- Charge tab: armor sub-priority row -------- */
    private static final int ARMOR_PIECE_H = 16;
    private static final int ARMOR_PIECE_GAP = 4;
    private static final String[] ARMOR_PIECE_KEYS = {
            "gui.quantumchanneling.charge.armor.head",
            "gui.quantumchanneling.charge.armor.chest",
            "gui.quantumchanneling.charge.armor.legs",
            "gui.quantumchanneling.charge.armor.feet"
    };

    private int armorPieceX(int idx) {
        int cx = leftPos + 8;
        int innerW = BG_W - 16;
        int pieceW = (innerW - 3 * ARMOR_PIECE_GAP) / 4;
        return cx + idx * (pieceW + ARMOR_PIECE_GAP);
    }
    private int armorPieceW() {
        int innerW = BG_W - 16;
        return (innerW - 3 * ARMOR_PIECE_GAP) / 4;
    }

    private void renderArmorPieceRow(GuiGraphics gfx, int rowY) {
        if (currentChannel == null) return;
        boolean armorOn = currentChannel.charges(ChargingSlots.ARMOR);
        int accent = currentChannel.color();
        int pieceW = armorPieceW();
        for (int i = 0; i < 4; i++) {
            int x = armorPieceX(i);
            int pr = currentChannel.armorPiecePriority(i);
            // Dim when the parent ARMOR group is off — sub-priorities only matter when armor charging is on.
            int border = armorOn ? blendARGB(accent, 0xFF000000, 0.4f) : 0xFF222B36;
            int body = armorOn ? 0xFF111824 : 0xFF0A0E14;
            gfx.fill(x - 1, rowY - 1, x + pieceW + 1, rowY + ARMOR_PIECE_H + 1, border);
            gfx.fill(x, rowY, x + pieceW, rowY + ARMOR_PIECE_H, body);

            Component label = Component.translatable(ARMOR_PIECE_KEYS[i]);
            int labelColor = armorOn ? 0xFFC8D0E0 : 0xFF606878;
            gfx.drawString(font, label, x + 3, rowY + 4, labelColor, false);

            String prText = "↕ " + pr;
            int prW = font.width(prText);
            gfx.drawString(font, Component.literal(prText), x + pieceW - prW - 3, rowY + 4,
                    armorOn ? 0xFFFFD080 : 0xFF6A7280, false);
        }
    }

    /** Returns the armor piece index 0..3 under the mouse on the sub-priority strip, or -1. */
    private int armorPieceAt(double mx, double my) {
        int armorRowY = topPos + CHARGE_LAYOUT_TOP + 2 * (CHARGE_CARD_H + CHARGE_CARD_GAP) + 2;
        int pieceW = armorPieceW();
        if (my < armorRowY || my >= armorRowY + ARMOR_PIECE_H) return -1;
        for (int i = 0; i < 4; i++) {
            int x = armorPieceX(i);
            if (mx >= x && mx < x + pieceW) return i;
        }
        return -1;
    }

    /* -------- Charge tab: Curios placeholder card -------- */
    private static final int CURIOS_CARD_H = 22;

    private int curiosCardTop() {
        return topPos + CHARGE_LAYOUT_TOP + 2 * (CHARGE_CARD_H + CHARGE_CARD_GAP) + 2
                + ARMOR_PIECE_H + 4;
    }

    private void renderCuriosCard(GuiGraphics gfx, int y) {
        if (currentChannel == null) return;
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        boolean curiosLoaded = Compat.curiosLoaded();
        boolean serverDisabled = !isSlotAllowedByServer(ChargingSlots.CURIOS);
        boolean on = curiosLoaded && !serverDisabled && currentChannel.charges(ChargingSlots.CURIOS);
        int pr = currentChannel.slotPriority(ChargingSlots.CURIOS);
        int accent = currentChannel.color();

        int border, body;
        int titleColor, stateColor, prColor;
        Component state;
        if (!curiosLoaded) {
            border = 0xFF252A35; body = 0xFF080C12;
            titleColor = 0xFF606878; stateColor = 0xFF707888; prColor = 0xFF50586A;
            state = Component.translatable("gui.quantumchanneling.charge.curios.not_installed");
        } else if (serverDisabled) {
            border = 0xFF4A2030; body = 0xFF1A0E14;
            titleColor = 0xFF806060; stateColor = 0xFFE07070; prColor = 0xFF50586A;
            state = Component.translatable("gui.quantumchanneling.charge.locked_by_server");
        } else if (on) {
            border = accent; body = 0xFF192030;
            titleColor = 0xFFFFFFFF; stateColor = 0xFF80E0A0; prColor = 0xFFC8E0FF;
            state = Component.literal("§a● Active");
        } else {
            border = 0xFF333A48; body = 0xFF0F141C;
            titleColor = 0xFFA0A8B4; stateColor = 0xFF888888; prColor = 0xFF6A7280;
            state = Component.literal("§7○ Disabled");
        }
        gfx.fill(cx - 1, y - 1, cx + rw + 1, y + CURIOS_CARD_H + 1, border);
        gfx.fill(cx, y, cx + rw, y + CURIOS_CARD_H, body);

        gfx.drawString(font, Component.translatable("gui.quantumchanneling.charge.curios"),
                cx + 8, y + 4, titleColor, false);
        gfx.drawString(font, state, cx + 8, y + CURIOS_CARD_H - 12, stateColor, false);

        String prText = "↕ P: " + pr;
        int prW = font.width(prText);
        gfx.drawString(font, Component.literal(prText), cx + rw - prW - 8, y + CURIOS_CARD_H - 12,
                prColor, false);
    }

    private boolean curiosCardAt(double mx, double my) {
        int y = curiosCardTop();
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        return mx >= cx && mx < cx + rw && my >= y && my < y + CURIOS_CARD_H;
    }

    /** Hit-test on the slot cards; returns the slot bit clicked, or 0 if none. */
    private int chargeCardSlotAt(double mx, double my) {
        int top = topPos + CHARGE_LAYOUT_TOP;
        for (int i = 0; i < 4; i++) {
            int x = chargeCardX(i);
            int y = chargeCardY(top, i);
            if (mx >= x && mx < x + CHARGE_CARD_W && my >= y && my < y + CHARGE_CARD_H) {
                return CHARGE_SLOT_BITS[i];
            }
        }
        return 0;
    }

    private List<ChannelInfo.MemberPos> sortedMembers() {
        if (currentChannel == null) return List.of();
        var list = new ArrayList<>(currentChannel.memberPositions());
        switch (nodeSort) {
            case POSITION -> list.sort((a, b) -> {
                int c = a.dim().compareTo(b.dim());
                if (c != 0) return c;
                return Long.compare(a.packedPos(), b.packedPos());
            });
            case TYPE -> list.sort(Comparator.comparingInt(ChannelInfo.MemberPos::type));
            case PRIORITY -> list.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        }
        return list;
    }

    private @Nullable ChannelInfo.MemberPos findExpandedMember() {
        if (expandedNodePos == null) return null;
        for (var m : sortedMembers()) if (m.packedPos() == expandedNodePos) return m;
        return null;
    }

    private void renderNodes(GuiGraphics gfx, int mouseX, int mouseY) {
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        if (currentChannel == null) {
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.unbound")
                    .withStyle(ChatFormatting.GRAY), cx, topPos + CONTENT_TOP + 4, 0xAAAAAA, false);
            return;
        }
        int areaTop = topPos + CONTENT_TOP + 24;
        int areaBottom = topPos + BG_H - (expandedNodePos != null && currentChannel.canManage() ? 56 : 6);
        int rowW = rw - 6;
        int visible = Math.max(1, (areaBottom - areaTop) / NODE_ROW_H);
        var list = sortedMembers();
        int total = list.size();
        if (nodesScroll < 0) nodesScroll = 0;
        int maxScroll = Math.max(0, total - visible);
        if (nodesScroll > maxScroll) nodesScroll = maxScroll;

        gfx.enableScissor(cx, areaTop, cx + rowW + 6, areaBottom);
        for (int i = 0; i < visible && (i + nodesScroll) < total; i++) {
            ChannelInfo.MemberPos m = list.get(i + nodesScroll);
            var p = m.pos();
            int y = areaTop + i * NODE_ROW_H;
            boolean expanded = expandedNodePos != null && expandedNodePos == m.packedPos();
            int color = expanded ? 0xFFFFD080 : 0xC8E0FF;
            String dimShort = shortenDim(m.dim());
            String label = m.customName().isEmpty() ? m.typeName() : (m.customName() + " §7(" + m.typeName() + ")");
            String left = (expanded ? "▼ " : "▶ ") + label + " · " + dimShort;
            gfx.drawString(font, Component.literal(left), cx, y, color, false);
            String coords = p.getX() + ", " + p.getY() + ", " + p.getZ();
            int coordsW = font.width(coords);
            gfx.drawString(font, Component.literal(coords),
                    cx + rowW - coordsW - 4, y, 0xFFFFFF, false);
        }
        gfx.disableScissor();

        if (total > visible) drawScrollbar(gfx, cx + rowW + 2, areaTop, areaBottom - areaTop,
                visible, total, nodesScroll, maxScroll);

        // Render expanded details above the (optional) Unbind button.
        ChannelInfo.MemberPos sel = findExpandedMember();
        if (sel != null) {
            int dy = areaBottom + 2;
            String line1 = "Priority: " + sel.priority()
                    + "    Surge: " + (sel.surge() ? "ON" : "OFF")
                    + "    Chunkload: " + (sel.chunkLoaded() ? "ON" : "OFF");
            String line2 = "Cap: " + (sel.cap() < 0 ? "Unlimited" : String.valueOf(sel.cap()) + " FE/t")
                    + "    Last tick: " + sel.lastTickRate();
            // Only show if there's space (when we reserved the bottom for the Unbind button or not)
            if (dy + 20 < topPos + BG_H) {
                gfx.drawString(font, Component.literal(line1), cx, dy, 0xC8E0FF, false);
                gfx.drawString(font, Component.literal(line2), cx, dy + 10, 0xC8E0FF, false);
            }
        }
    }

    private static String shortenDim(String dim) {
        if (dim.startsWith("minecraft:")) return dim.substring("minecraft:".length());
        return dim;
    }

    /** Draws the 30-bucket FE/s history as a thin polyline. Includes Y-axis max-value label. */
    private void renderStatsGraph(GuiGraphics gfx, int x, int y, int w, int h) {
        int n = com.quantumchanneling.menu.PhotonNodeMenu.GRAPH_BUCKETS;
        int accent = currentChannel != null ? currentChannel.color() : 0xFF50DCF0;

        // Frame + background.
        gfx.fill(x - 1, y - 1, x + w + 1, y + h + 1, blendARGB(accent, 0xFF000000, 0.55f));
        gfx.fill(x, y, x + w, y + h, 0xFF080C12);
        // 3 horizontal grid lines at 25 / 50 / 75 %.
        for (int i = 1; i < 4; i++) {
            int gy = y + (h * i) / 4;
            gfx.fill(x + 1, gy, x + w - 1, gy + 1, 0x33FFFFFF);
        }

        // Find max value across the 30 buckets.
        int max = 0;
        for (int i = 0; i < n; i++) max = Math.max(max, menu.getGraphBucket(statsWindow, i));

        // Polyline — draw thin line segments between successive sample y positions.
        int padX = 4;
        int padTop = 8;       // top padding so the max-scale label has room
        int padBottom = 9;    // bottom padding for the X-axis label
        int innerW = w - 2 * padX;
        int innerH = h - padTop - padBottom;
        int xStride = Math.max(1, innerW / (n - 1));
        int prevPx = -1, prevPy = -1;
        for (int i = 0; i < n; i++) {
            int v = menu.getGraphBucket(statsWindow, i);
            int px = x + padX + i * xStride;
            int py = y + padTop + innerH - (max <= 0 ? 0 : (int) ((long) innerH * v / max));
            if (prevPx >= 0) drawLine(gfx, prevPx, prevPy, px, py, accent);
            // Small dot at each sample point.
            gfx.fill(px - 1, py - 1, px + 1, py + 1, accent);
            prevPx = px; prevPy = py;
        }

        // Scale label (top-left of graph) — show the max value.
        if (max > 0) {
            String maxLabel = formatFE(max) + " FE/s";
            gfx.drawString(font, Component.literal(maxLabel), x + 3, y + 1, 0xFFC8E0FF, false);
        }
        // X-axis label (bottom-left).
        String label = "↤ " + statsWindow + " min   now ↦";
        gfx.drawString(font, Component.literal(label), x + 3, y + h - 9, 0x55FFFFFF, false);
    }

    /** Simple 2D line via Bresenham — gfx.fill doesn't draw diagonals. */
    private static void drawLine(GuiGraphics gfx, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0, y = y0;
        while (true) {
            gfx.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x += sx; }
            if (e2 <= dx) { err += dx; y += sy; }
        }
    }

    /** Hit-test on the Stats tab's window buttons. Returns the chosen window minute or 0. */
    private int statsWindowAt(double mx, double my) {
        int cx = leftPos + 8;
        int btnW = 36, btnH = 14, btnGap = 4;
        int btnY = topPos + CONTENT_TOP + 4;
        if (my < btnY || my >= btnY + btnH) return 0;
        int[] windows = { 1, 5, 10 };
        for (int i = 0; i < 3; i++) {
            int bx = cx + i * (btnW + btnGap);
            if (mx >= bx && mx < bx + btnW) return windows[i];
        }
        return 0;
    }

    private void renderStats(GuiGraphics gfx) {
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        int y = topPos + CONTENT_TOP + 4;

        // Branch by device type: storage shows a fill bar; everyone else (Emitter/Receiver/Manager)
        // shows the throughput history line graph + window switcher.
        if (menu.isStorage()) {
            y = renderStorageStats(gfx, cx, rw, y);
        } else {
            y = renderHistoryStats(gfx, cx, rw, y);
        }

        // Channel-level info (emitters/receivers/totals/access/etc.) now lives in the left-side
        // channel-info panel — open it with the "CH" tab on the left edge.
        if (currentChannel == null) {
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.unbound")
                    .withStyle(ChatFormatting.GRAY), cx, y, 0xAAAAAA, false);
        }
    }

    /** Stats rendering for emitter/receiver/manager — window switcher + line graph + Instant text. */
    private int renderHistoryStats(GuiGraphics gfx, int cx, int rw, int y) {
        // Window switch buttons (1m / 5m / 10m).
        int btnW = 36, btnH = 14, btnGap = 4;
        int[] windows = { 1, 5, 10 };
        String[] labels = { "1m", "5m", "10m" };
        for (int i = 0; i < 3; i++) {
            int bx = cx + i * (btnW + btnGap);
            boolean active = statsWindow == windows[i];
            int accent = currentChannel != null ? currentChannel.color() : 0xFF50DCF0;
            int border = active ? accent : 0xFF333A48;
            int body = active ? 0xFF192030 : 0xFF0F141C;
            gfx.fill(bx - 1, y - 1, bx + btnW + 1, y + btnH + 1, border);
            gfx.fill(bx, y, bx + btnW, y + btnH, body);
            int textW = font.width(labels[i]);
            gfx.drawString(font, labels[i], bx + (btnW - textW) / 2, y + 3,
                    active ? 0xFFFFFFFF : 0xFF888888, false);
        }
        y += btnH + 3;

        // Line graph. Currently sourced from the FE history bank — items/fluids/gas have their
        // own histories on the BE but aren't wired to the ContainerData graph slots yet; the
        // Instant line below already uses the per-mode counter so the live number is correct.
        renderStatsGraph(gfx, cx, y, rw, 50);
        y += 50 + 3;

        // Instant — per-mode counter with the unit that matches activeMode.
        int throughput = menu.getThroughputFor(activeMode.toChannelMode());
        String ikey = "gui.quantumchanneling.stats.instant." + activeMode.name().toLowerCase();
        gfx.drawString(font, Component.translatable(ikey, throughput, throughput * 20),
                cx, y, 0xC8E0FF, false);
        y += 14;
        return y;
    }

    /** Stats rendering for storage — capacity progress bar + stored / cap / percent. */
    private int renderStorageStats(GuiGraphics gfx, int cx, int rw, int y) {
        long stored = menu.getStoredLong();
        long capacity = menu.getStorageCapacity();
        int pct = capacity > 0 ? (int) (stored * 100L / capacity) : 0;

        // Header
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.stats.storage_buffer")
                .withStyle(ChatFormatting.AQUA), cx, y, 0xC8E0FF, false);
        y += 12;

        // Progress bar.
        int barH = 16;
        int accent = currentChannel != null ? currentChannel.color() : 0xFF80E0FF;
        gfx.fill(cx - 1, y - 1, cx + rw + 1, y + barH + 1, blendARGB(accent, 0xFF000000, 0.55f));
        gfx.fill(cx, y, cx + rw, y + barH, 0xFF080C12);
        int fillW = capacity > 0 ? (int) ((long) rw * stored / capacity) : 0;
        if (fillW > 0) gfx.fill(cx, y, cx + fillW, y + barH, accent);
        // Percent label centered in the bar.
        String pctLabel = pct + "%";
        int pctW = font.width(pctLabel);
        gfx.drawString(font, Component.literal(pctLabel),
                cx + (rw - pctW) / 2, y + (barH - 8) / 2, 0xFFFFFFFF, false);
        y += barH + 4;

        // Stored / capacity figures.
        String line = formatFE(stored) + " / " + formatFE(capacity) + " FE";
        gfx.drawString(font, Component.literal(line), cx, y, 0xCCCCCC, false);
        y += 14;
        return y;
    }

    /** X position of action slot {@code slotIdx} within a row whose right edge is at {@code rowRight}. */
    private static int permActionX(int rowRight, int slotIdx) {
        // Lay them out right-to-left: ACT_REMOVE rightmost, then ACT_TOGGLE_ROLE, then ACT_TRANSFER.
        int fromRight = PERM_ACTION_COUNT - slotIdx;
        return rowRight - fromRight * (PERM_ACTION_W + 2) - 2;
    }

    /**
     * Whether the local player can see {@code slot}'s action for {@code target}.
     * Mirrors {@link com.quantumchanneling.channel.ChannelData#setChargingBlocked} for the
     * BLOCK_CHARGE pill so the UI never shows a button the server would reject.
     */
    private boolean isActionAvailable(int slot, ChannelInfo.PlayerEntry target,
                                      boolean iAmOwner, boolean targetIsOwner, boolean targetIsSelf) {
        return switch (slot) {
            // Transferring to yourself / blank target / non-owner viewers — all no-ops.
            case ACT_TRANSFER     -> iAmOwner && !targetIsOwner;
            // Owner row never has a role to toggle (owner's role is implicit).
            case ACT_TOGGLE_ROLE  -> !targetIsOwner;
            case ACT_BLOCK_CHARGE -> {
                if (targetIsOwner) {
                    // Only the owner can flip the owner's own block flag.
                    yield iAmOwner && targetIsSelf;
                }
                // Self always allowed (admin self-block); admin↔admin only the owner can flip;
                // user targets are always available to whoever has manage rights.
                if (targetIsSelf) yield true;
                if (target.permission() == Permission.ADMIN) yield iAmOwner;
                yield true;
            }
            // KICK on the owner is meaningless — they own the channel.
            case ACT_REMOVE       -> !targetIsOwner;
            default               -> false;
        };
    }

    /**
     * Each action gets a small dark "pill" button with a short label so it reads at a glance.
     * Pills slightly brighten on hover; labels track the tone (warm gold for promote, cool blue
     * for demote, red for remove, accent gold for transfer).
     */
    private void drawPermActionIcon(GuiGraphics gfx, int slot, ChannelInfo.PlayerEntry target,
                                    int x, int y, boolean hover) {
        String label;
        int border, body, textColor;
        switch (slot) {
            case ACT_TRANSFER -> {
                label = "OWNR";
                int accent = 0xFFE0C060;
                border = hover ? accent : blendARGB(accent, 0xFF000000, 0.5f);
                body = hover ? 0xFF26200E : 0xFF1A1608;
                textColor = hover ? 0xFFFFE080 : 0xFFE0C060;
            }
            case ACT_TOGGLE_ROLE -> {
                boolean isAdmin = target.permission() == Permission.ADMIN;
                label = isAdmin ? "USR" : "ADM";
                int accent = isAdmin ? 0xFF6090E0 : 0xFFE0B060;
                border = hover ? accent : blendARGB(accent, 0xFF000000, 0.5f);
                body = hover ? 0xFF1A1F2A : 0xFF12161E;
                textColor = hover ? (isAdmin ? 0xFF80B0FF : 0xFFFFD080)
                                  : (isAdmin ? 0xFF6088C8 : 0xFFC8A050);
            }
            case ACT_BLOCK_CHARGE -> {
                // Single ⚡ glyph carries the state via color: GREEN = currently allowed (click
                // to block), RED = currently admin-blocked (click to re-allow).
                boolean blocked = target.chargingBlocked();
                label = "⚡";
                int accent = blocked ? 0xFFD06060 : 0xFF80D060;
                border = hover ? accent : blendARGB(accent, 0xFF000000, 0.5f);
                body = hover ? (blocked ? 0xFF2A1212 : 0xFF15201A)
                             : (blocked ? 0xFF1A0C0C : 0xFF0C140F);
                textColor = hover ? (blocked ? 0xFFFFB0B0 : 0xFFB0FFB0)
                                  : (blocked ? 0xFFD06060 : 0xFF80C060);
            }
            case ACT_REMOVE -> {
                label = "KICK";
                border = hover ? 0xFFE05050 : 0xFF803030;
                body = hover ? 0xFF2A1212 : 0xFF1A0C0C;
                textColor = hover ? 0xFFFFB0B0 : 0xFFD06060;
            }
            default -> { return; }
        }
        // Pill body + border
        gfx.fill(x, y - 1, x + PERM_ACTION_W, y + 10, border);
        gfx.fill(x + 1, y, x + PERM_ACTION_W - 1, y + 9, body);
        // Centered label
        int textW = font.width(label);
        int textX = x + (PERM_ACTION_W - textW) / 2;
        gfx.drawString(font, Component.literal(label), textX, y + 1, textColor, false);
    }

    /** Dispatches one of the three per-row Access actions. Destructive ones go through a modal. */
    private void onAccessActionClicked(int slot, ChannelInfo.PlayerEntry target) {
        if (currentChannel == null || target == null) return;
        UUID channelId = currentChannel.id();
        switch (slot) {
            case ACT_TRANSFER -> {
                // Confirm before transferring ownership. Demotion-to-admin is irreversible.
                pendingModal = new PendingModal(
                        Component.translatable("gui.quantumchanneling.access.transfer.title"),
                        Component.translatable("gui.quantumchanneling.access.transfer.body",
                                target.name(), currentChannel.name()),
                        () -> ModMessages.sendToServer(new TransferChannelOwnerPacket(
                                channelId, target.id(), target.name())));
            }
            case ACT_TOGGLE_ROLE -> {
                Permission newRole = target.permission() == Permission.ADMIN
                        ? Permission.USER : Permission.ADMIN;
                ModMessages.sendToServer(new SetChannelPermissionPacket(channelId, target.name(), newRole.name()));
            }
            case ACT_BLOCK_CHARGE -> ModMessages.sendToServer(
                    new SetChannelChargeBlockedPacket(channelId, target.id(), !target.chargingBlocked()));
            case ACT_REMOVE -> ModMessages.sendToServer(
                    new SetChannelPermissionPacket(channelId, target.name(), ""));
        }
    }

    private void renderAccess(GuiGraphics gfx, int mouseX, int mouseY) {
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        if (currentChannel == null) {
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.unbound")
                    .withStyle(ChatFormatting.GRAY), cx, topPos + CONTENT_TOP + 4, 0xAAAAAA, false);
            return;
        }
        if (!currentChannel.canManage()) {
            gfx.drawString(font, Component.literal("§7Read-only — admin permission required"),
                    cx, topPos + CONTENT_TOP + 4, 0xAAAAAA, false);
            return;
        }
        int areaTop = topPos + CONTENT_TOP + 4;
        int areaBottom = topPos + BG_H - 30;
        int rowW = rw - 6;
        int visible = Math.max(1, (areaBottom - areaTop) / PERM_ROW_H);
        int total = currentChannel.players().size();
        clampPermScroll();

        boolean iAmOwner = currentChannel.ownerId() != null
                && minecraft != null && minecraft.player != null
                && currentChannel.ownerId().equals(minecraft.player.getUUID());

        gfx.enableScissor(cx, areaTop, cx + rowW + 6, areaBottom);
        for (int i = 0; i < visible && (i + permScroll) < total; i++) {
            ChannelInfo.PlayerEntry e = currentChannel.players().get(i + permScroll);
            int y = areaTop + i * PERM_ROW_H;
            boolean isOwner = currentChannel.ownerId() != null && currentChannel.ownerId().equals(e.id());

            // Subtle row background — zebra striping for readability.
            int rowBg = (i % 2 == 0) ? 0xFF0E141C : 0xFF11171F;
            gfx.fill(cx, y, cx + rowW, y + PERM_ROW_H, rowBg);

            // Name + role indicator.
            String label = (isOwner ? "♛ " : (e.permission() == Permission.ADMIN ? "★ " : "• ")) + e.name();
            int textColor = isOwner ? 0xFFFFE070
                    : (e.permission() == Permission.ADMIN ? 0xFFFFD080 : 0xFFFFFFFF);
            gfx.drawString(font, Component.literal(label), cx + 4, y + 3, textColor, false);

            // Action icons on the right. Each pill is shown only when isActionAvailable says so —
            // the owner row now keeps the ⚡ self-block pill when the viewer is the owner.
            boolean targetIsSelf = minecraft != null && minecraft.player != null
                    && minecraft.player.getUUID().equals(e.id());
            for (int slot = 0; slot < PERM_ACTION_COUNT; slot++) {
                if (!isActionAvailable(slot, e, iAmOwner, isOwner, targetIsSelf)) continue;
                int ix = permActionX(cx + rowW, slot);
                int iy = y + 2;
                boolean hover = mouseX >= ix && mouseX < ix + PERM_ACTION_W
                        && mouseY >= iy && mouseY < iy + 10;
                drawPermActionIcon(gfx, slot, e, ix, iy, hover);
            }
        }
        gfx.disableScissor();

        if (total > visible) drawScrollbar(gfx, cx + rowW + 2, areaTop, areaBottom - areaTop,
                visible, total, permScroll, maxPermScroll());
    }

    private void renderSetup(GuiGraphics gfx) {
        int cx = leftPos + 8;
        int y = topPos + CONTENT_TOP + 4;
        if (currentChannel == null) {
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.unbound")
                    .withStyle(ChatFormatting.GRAY), cx, y, 0xAAAAAA, false);
            return;
        }
        if (!currentChannel.canManage()) {
            gfx.drawString(font, Component.literal("§7Read-only — admin permission required"), cx, y, 0xAAAAAA, false);
            return;
        }
        // Color section header + hue spectrum strip. Click hit-test in mouseClicked → Tab.SETUP.
        // Mirrors buildSetupTab's offsets: 4 (initial y) + 30 (rename) + 30 (public) + 34 (PIN).
        int stripY = topPos + CONTENT_TOP + 4 + 30 + 30 + 34;
        int rw = BG_W - 16;
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.setup.color"),
                cx, stripY - 12, 0xAAAAAA, false);
        int accent = currentChannel.color();
        gfx.fill(cx - 1, stripY - 1, cx + rw + 1, stripY + COLOR_STRIP_H + 1,
                blendARGB(accent, 0xFF000000, 0.55f));
        for (int i = 0; i < rw; i++) {
            float hue = (float) i / (float) rw;
            int c = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) | 0xFF000000;
            gfx.fill(cx + i, stripY, cx + i + 1, stripY + COLOR_STRIP_H, c);
        }
        float curHue = rgbToHue(accent);
        int markerX = cx + (int) (curHue * rw);
        gfx.fill(markerX - 1, stripY - 2, markerX + 2, stripY, 0xFFFFFFFF);
        gfx.fill(markerX - 1, stripY + COLOR_STRIP_H, markerX + 2, stripY + COLOR_STRIP_H + 2, 0xFFFFFFFF);
    }

    /** Returns hue in [0, 1) of the RGB component of an ARGB color. */
    private static float rgbToHue(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        return hsb[0];
    }

    private void renderForge(GuiGraphics gfx) {
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.tab.forge.header")
                .withStyle(ChatFormatting.AQUA), cx, topPos + CONTENT_TOP + 4, 0xC8E0FF, false);
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.tab.forge.hint"),
                cx, topPos + CONTENT_TOP + 16, 0xAAAAAA, false);

        // Color label + hue spectrum strip. Click hit-test lives in mouseClicked.
        int stripY = topPos + CONTENT_TOP + 70;
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.setup.color"),
                cx, stripY - 12, 0xAAAAAA, false);
        // Outer frame (dim accent) + rainbow strip + position marker.
        gfx.fill(cx - 1, stripY - 1, cx + rw + 1, stripY + COLOR_STRIP_H + 1,
                blendARGB(forgeColor, 0xFF000000, 0.55f));
        for (int i = 0; i < rw; i++) {
            float hue = (float) i / (float) rw;
            int c = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) | 0xFF000000;
            gfx.fill(cx + i, stripY, cx + i + 1, stripY + COLOR_STRIP_H, c);
        }
        // Marker triangle above + below the strip at the current selected hue.
        float curHue = rgbToHue(forgeColor);
        int markerX = cx + (int) (curHue * rw);
        gfx.fill(markerX - 1, stripY - 2, markerX + 2, stripY, 0xFFFFFFFF);
        gfx.fill(markerX - 1, stripY + COLOR_STRIP_H, markerX + 2, stripY + COLOR_STRIP_H + 2, 0xFFFFFFFF);
    }

    private void drawScrollbar(GuiGraphics gfx, int trackX, int areaTop, int trackH,
                               int visible, int total, int scroll, int maxScroll) {
        gfx.fill(trackX, areaTop, trackX + 3, areaTop + trackH, 0xFF202632);
        int thumbH = Math.max(8, trackH * visible / total);
        int thumbY = areaTop + (trackH - thumbH) * scroll / Math.max(1, maxScroll);
        int color = currentChannel != null ? currentChannel.color() : 0xFF50DCF0;
        gfx.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, color);
    }

    private int maxPermScroll() {
        if (currentChannel == null) return 0;
        int areaH = (topPos + BG_H - 30) - (topPos + CONTENT_TOP + 4);
        int visible = Math.max(1, areaH / PERM_ROW_H);
        return Math.max(0, currentChannel.players().size() - visible);
    }
    private void clampPermScroll() {
        if (permScroll < 0) permScroll = 0;
        else if (permScroll > maxPermScroll()) permScroll = maxPermScroll();
    }
    private int maxChannelsScroll() {
        int areaH = (topPos + BG_H - 30) - (topPos + CONTENT_TOP + 24);
        int visible = Math.max(1, areaH / CHAN_ROW_H);
        return Math.max(0, channels.size() - visible);
    }
    private void clampChannelsScroll() {
        if (channelsScroll < 0) channelsScroll = 0;
        else if (channelsScroll > maxChannelsScroll()) channelsScroll = maxChannelsScroll();
    }

    /* -------- input handling -------- */

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        EditBox eb = focusedEditBox();
        if (eb != null) {
            // Enter / Return on a focused EditBox commits the value via its apply hook.
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                Runnable apply = applyHookFor(eb);
                if (apply != null) {
                    apply.run();
                    eb.setFocused(false);
                    setFocused(null);
                    return true;
                }
            }
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                eb.keyPressed(key, scan, mods);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    /**
     * Apply hook for a given EditBox — fired on Enter and on focus loss. {@code null} means the
     * input has no auto-apply (still backed by an explicit button, e.g. the Forge "create" form).
     */
    private @Nullable Runnable applyHookFor(EditBox eb) {
        if (eb == deviceNameInput) return this::applyDeviceName;
        if (eb == capInput) return this::applyCap;
        if (eb == priorityInput) return this::applyPriority;
        if (eb == renameInput) return this::applyChannelRename;
        if (eb == pinInput) return this::applyChannelPin;
        // addPlayerInput intentionally has no auto-apply: the Access tab has an explicit Add button.
        // Otherwise focus-loss from clicking the role toggle would silently submit.
        return null;
    }

    private void applyDeviceName() {
        if (deviceNameInput == null) return;
        ModMessages.sendToServer(new RenameDevicePacket(menu.getBlockPos(),
                deviceNameInput.getValue().trim()));
    }

    private void applyChannelRename() {
        if (renameInput == null || currentChannel == null) return;
        String n = renameInput.getValue().trim();
        if (!n.isEmpty()) ModMessages.sendToServer(new RenameChannelPacket(currentChannel.id(), n));
    }

    private void applyChannelPin() {
        if (pinInput == null || currentChannel == null) return;
        ModMessages.sendToServer(new SetChannelPinPacket(currentChannel.id(), pinInput.getValue().trim()));
    }

    private void applyAddPlayer() {
        if (addPlayerInput == null || currentChannel == null) return;
        String n = addPlayerInput.getValue().trim();
        if (n.isEmpty()) return;
        ModMessages.sendToServer(new SetChannelPermissionPacket(currentChannel.id(), n, addPlayerRole.name()));
        addPlayerInput.setValue("");
    }

    private @Nullable EditBox focusedEditBox() {
        for (var child : children()) {
            if (child instanceof EditBox e && e.isFocused()) return e;
        }
        return null;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (activeTab == Tab.STATUS) {
            if (chunkloadButton != null) chunkloadButton.setMessage(chunkloadLabel());
            if (surgeButton != null) surgeButton.setMessage(surgeLabel());
            if (resetCapButton != null) resetCapButton.visible = menu.getThroughputCap() != ChannelBoundBlockEntity.DEFAULT_CAP;
        }
        if (activeTab == Tab.SETUP && deleteButton != null) {
            deleteButton.setMessage(deleteLabel());
        }
        // Focus-loss detection: when the previously-focused EditBox is no longer focused, fire its
        // apply hook. This lets users click away from an input to commit (in addition to Enter).
        EditBox nowFocused = focusedEditBox();
        if (lastFocusedEditBox != null && lastFocusedEditBox != nowFocused) {
            Runnable apply = applyHookFor(lastFocusedEditBox);
            if (apply != null) apply.run();
        }
        lastFocusedEditBox = nowFocused;
        resolveCurrentChannel();
        // Real-time refresh: poll every 2 seconds. onChannelsRefreshed only triggers a widget
        // rebuild when the snapshot actually changed, so a steady-state screen costs almost nothing
        // beyond the periodic packet. Tuned to a longer interval than the original 1 s so a few
        // open screens don't push noticeable bandwidth.
        if (++refreshTickCounter >= 40) {
            refreshTickCounter = 0;
            ModMessages.sendToServer(new OpenChannelsRequestPacket());
        }
    }

    private int refreshTickCounter = 0;

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Modal swallows everything else while open.
        if (handleModalClick(mx, my)) return true;
        // Channel-info side tab — toggles open/close; swallows panel clicks.
        if (handleChannelInfoClick(mx, my)) return true;
        // Side-tab strip always takes priority — it's the only interactive layer that survives the
        // "Coming soon" overlay (so the user can switch back to ENERGY).
        if (handleSideTabClick(mx, my)) return true;
        // When a non-ENERGY mode is active the overlay covers the content area; swallow any click
        // that lands on it so widgets underneath don't get triggered.
        if (overlayBlocking()
                && mx >= leftPos + 6 && mx < leftPos + BG_W - 6
                && my >= topPos + CONTENT_TOP + 2 && my < topPos + BG_H - 6) {
            return true;
        }
        // Items panel has its own click logic — try it before tab routing. Do NOT fall through
        // to super.mouseClicked here: AbstractContainerScreen treats clicks-with-carried-stack
        // inside the GUI rect but outside any real Slot as "drop carried on the ground". The
        // filter-slot grid sits exactly in that no-real-Slot region, so we must terminate the
        // click here to preserve the cursor stack for the user-facing pick-up→show-filter→put-back
        // workflow. No PhotonButton or EditBox shares the filter-slot region, so widgets won't miss
        // input by skipping super.
        if (handleItemsPanelClick(mx, my)) return true;
        if (handleFluidsPanelClick(mx, my)) return true;
        if (handleGasPanelClick(mx, my)) return true;
        Tab[] tabs = Tab.values();
        int tabW = (BG_W - 8) / tabs.length;
        int tabBarTop = topPos + TAB_BAR_Y + 1;
        if (my >= tabBarTop && my < tabBarTop + TAB_BAR_H - 2) {
            for (int i = 0; i < tabs.length; i++) {
                int x = leftPos + 4 + i * tabW;
                if (mx >= x && mx < x + tabW) {
                    if (tabs[i] != activeTab) switchTab(tabs[i]);
                    return true;
                }
            }
        }
        if (activeTab == Tab.TUNE) {
            int cx = leftPos + 8;
            int rowW = BG_W - 16 - 6;
            int areaTop = topPos + CONTENT_TOP + 24;
            int areaBottom = topPos + BG_H - 30;
            int visible = Math.max(1, (areaBottom - areaTop) / CHAN_ROW_H);
            var sortedList = sortedChannels();
            for (int i = 0; i < visible && (i + channelsScroll) < sortedList.size(); i++) {
                int y = areaTop + i * CHAN_ROW_H;
                if (mx >= cx && mx < cx + rowW && my >= y && my < y + CHAN_ROW_H) {
                    ChannelInfo clicked = sortedList.get(i + channelsScroll);
                    selectedInList = clicked;
                    boolean alreadyBound = currentChannel != null && currentChannel.id().equals(clicked.id());
                    if (alreadyBound) {
                        // No-op: the dedicated Disconnect button at the bottom handles disengage.
                        return true;
                    }
                    if (clicked.hasPin() && !clicked.canUse()) {
                        // Needs a PIN — selection now shows the PIN entry row; user fills it and Joins.
                        rebuildAll();
                        return true;
                    }
                    ModMessages.sendToServer(new SetDeviceChannelPacket(menu.getBlockPos(), clicked.id()));
                    return true;
                }
            }
        }
        if (activeTab == Tab.STATS) {
            int chosen = statsWindowAt(mx, my);
            if (chosen != 0) {
                statsWindow = chosen;
                return true;
            }
        }
        // Forge tab: clicking the hue strip picks the new channel's accent color.
        if (activeTab == Tab.FORGE) {
            int stripY = topPos + CONTENT_TOP + 70;
            int cx = leftPos + 8;
            int rw = BG_W - 16;
            if (mx >= cx && mx < cx + rw && my >= stripY && my < stripY + COLOR_STRIP_H) {
                float hue = (float) ((mx - cx) / (double) rw);
                int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
                forgeColor = (rgb & 0xFFFFFF) | 0xFF000000;
                return true;
            }
        }
        // Edit/Setup tab: clicking the hue strip mutates the bound channel's accent live.
        if (activeTab == Tab.SETUP && currentChannel != null && currentChannel.canManage()) {
            int stripY = topPos + CONTENT_TOP + 4 + 30 + 30 + 34;
            int cx = leftPos + 8;
            int rw = BG_W - 16;
            if (mx >= cx && mx < cx + rw && my >= stripY && my < stripY + COLOR_STRIP_H) {
                float hue = (float) ((mx - cx) / (double) rw);
                int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
                int argb = (rgb & 0xFFFFFF) | 0xFF000000;
                ModMessages.sendToServer(new SetChannelColorPacket(currentChannel.id(), argb));
                return true;
            }
        }
        if (activeTab == Tab.CHARGE && currentChannel != null && currentChannel.canManage()) {
            int slot = chargeCardSlotAt(mx, my);
            if (slot != 0) {
                if (isSlotAllowedByServer(slot)) {
                    ModMessages.sendToServer(new SetChannelChargingPacket(
                            currentChannel.id(), ChargingSlots.toggle(currentChannel.chargingSlots(), slot)));
                }
                return true;   // swallow even when locked so the click doesn't fall through
            }
            if (Compat.curiosLoaded() && curiosCardAt(mx, my)) {
                if (isSlotAllowedByServer(ChargingSlots.CURIOS)) {
                    ModMessages.sendToServer(new SetChannelChargingPacket(
                            currentChannel.id(), ChargingSlots.toggle(currentChannel.chargingSlots(), ChargingSlots.CURIOS)));
                }
                return true;
            }
        }
        if (activeTab == Tab.NODES && currentChannel != null) {
            int cx = leftPos + 8;
            int rowW = BG_W - 16 - 6;
            int areaTop = topPos + CONTENT_TOP + 24;
            int areaBottom = topPos + BG_H - (expandedNodePos != null && currentChannel.canManage() ? 56 : 6);
            int visible = Math.max(1, (areaBottom - areaTop) / NODE_ROW_H);
            var list = sortedMembers();
            for (int i = 0; i < visible && (i + nodesScroll) < list.size(); i++) {
                int y = areaTop + i * NODE_ROW_H;
                if (mx >= cx && mx < cx + rowW && my >= y && my < y + NODE_ROW_H) {
                    long packed = list.get(i + nodesScroll).packedPos();
                    if (expandedNodePos != null && expandedNodePos == packed) {
                        expandedNodePos = null;
                    } else {
                        expandedNodePos = packed;
                    }
                    rebuildAll();
                    return true;
                }
            }
        }
        if (activeTab == Tab.ACCESS && currentChannel != null && currentChannel.canManage()) {
            int cx = leftPos + 8;
            int rowW = BG_W - 16 - 6;
            int areaTop = topPos + CONTENT_TOP + 4;
            int visible = Math.max(1, ((topPos + BG_H - 30) - areaTop) / PERM_ROW_H);
            boolean iAmOwner = currentChannel.ownerId() != null
                    && minecraft != null && minecraft.player != null
                    && currentChannel.ownerId().equals(minecraft.player.getUUID());
            for (int i = 0; i < visible && (i + permScroll) < currentChannel.players().size(); i++) {
                int y = areaTop + i * PERM_ROW_H;
                ChannelInfo.PlayerEntry e = currentChannel.players().get(i + permScroll);
                boolean isOwner = currentChannel.ownerId() != null && currentChannel.ownerId().equals(e.id());
                boolean targetIsSelf = minecraft != null && minecraft.player != null
                        && minecraft.player.getUUID().equals(e.id());
                for (int slot = 0; slot < PERM_ACTION_COUNT; slot++) {
                    if (!isActionAvailable(slot, e, iAmOwner, isOwner, targetIsSelf)) continue;
                    int ix = permActionX(cx + rowW, slot);
                    int iy = y + 2;
                    if (mx < ix || mx >= ix + PERM_ACTION_W || my < iy || my >= iy + 10) continue;
                    onAccessActionClicked(slot, e);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        // Channel-info sidebar scrolls vertically with the wheel. Clamp to content height so the
        // panel never scrolls past the end of its contents.
        if (channelInfoOpen) {
            int x = leftPos - LEFT_TAB_W - CHANNEL_PANEL_W;
            int y = topPos + CONTENT_TOP;
            int w = CHANNEL_PANEL_W, h = BG_H - CONTENT_TOP - 4;
            if (mx >= x && mx < x + w && my >= y && my < y + h) {
                int viewH = h - 28; // 22 px header + 6 px bottom padding (mirrors render)
                int maxScroll = Math.max(0, channelInfoContentHeight - viewH);
                channelInfoScroll -= (int) Math.signum(delta) * 10;
                if (channelInfoScroll < 0) channelInfoScroll = 0;
                if (channelInfoScroll > maxScroll) channelInfoScroll = maxScroll;
                return true;
            }
        }
        if (activeTab == Tab.CHARGE && currentChannel != null && currentChannel.canManage()) {
            int step = (int) Math.signum(delta);
            int slot = chargeCardSlotAt(mx, my);
            if (slot != 0) {
                if (isSlotAllowedByServer(slot)) {
                    int newPri = currentChannel.slotPriority(slot) + step;
                    ModMessages.sendToServer(new SetChannelSlotPriorityPacket(
                            currentChannel.id(), slot, newPri));
                }
                return true;
            }
            int armorIdx = armorPieceAt(mx, my);
            if (armorIdx >= 0) {
                if (isSlotAllowedByServer(ChargingSlots.ARMOR)) {
                    int newPri = currentChannel.armorPiecePriority(armorIdx) + step;
                    ModMessages.sendToServer(new SetChannelArmorPriorityPacket(
                            currentChannel.id(), armorIdx, newPri));
                }
                return true;
            }
            if (Compat.curiosLoaded() && curiosCardAt(mx, my)) {
                if (!isSlotAllowedByServer(ChargingSlots.CURIOS)) return true;
                int newPri = currentChannel.slotPriority(ChargingSlots.CURIOS) + step;
                ModMessages.sendToServer(new SetChannelSlotPriorityPacket(
                        currentChannel.id(), ChargingSlots.CURIOS, newPri));
                return true;
            }
        }
        if (activeTab == Tab.TUNE) {
            int cx = leftPos + 8;
            int rowW = BG_W - 16 - 6;
            int areaTop = topPos + CONTENT_TOP + 24;
            int areaBottom = topPos + BG_H - 30;
            if (mx >= cx && mx < cx + rowW + 6 && my >= areaTop && my < areaBottom) {
                channelsScroll -= (int) Math.signum(delta);
                clampChannelsScroll();
                return true;
            }
        }
        if (activeTab == Tab.NODES && currentChannel != null) {
            int cx = leftPos + 8;
            int rowW = BG_W - 16 - 6;
            int areaTop = topPos + CONTENT_TOP + 24;
            int areaBottom = topPos + BG_H - (expandedNodePos != null && currentChannel.canManage() ? 56 : 6);
            if (mx >= cx && mx < cx + rowW + 6 && my >= areaTop && my < areaBottom) {
                nodesScroll -= (int) Math.signum(delta);
                return true;
            }
        }
        if (activeTab == Tab.ACCESS && currentChannel != null && currentChannel.canManage()) {
            int cx = leftPos + 8;
            int rowW = BG_W - 16 - 6;
            int areaTop = topPos + CONTENT_TOP + 4;
            int areaBottom = topPos + BG_H - 30;
            if (mx >= cx && mx < cx + rowW + 6 && my >= areaTop && my < areaBottom) {
                permScroll -= (int) Math.signum(delta);
                clampPermScroll();
                return true;
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }

    /* ===================== Items panel v2 (dynamic subchannels) ===================== */

    public static final int ITEMS_SLOT_COLS = 9;
    public static final int ITEMS_SLOT_ROWS = 3;
    public static final int ITEMS_SLOT_SIZE = 18;
    public static final int ITEMS_SLOTS_VISIBLE = ITEMS_SLOT_COLS * ITEMS_SLOT_ROWS;

    public boolean isItemsModeActive() {
        // Filter slots only exist when the user is actually looking at the Filters panel — the
        // CHARGE tab slot in ITEMS side-mode. Other tabs (Tune, Status, Stats, etc.) are reachable
        // from items mode now but don't host the slot grid, so JEI/EMI shouldn't see drop targets there.
        return activeTab == Tab.CHARGE && activeMode == ResourceMode.ITEMS && currentChannel != null;
    }

    public int getItemsSlotCount() { return ITEMS_SLOTS_VISIBLE; }

    public @Nullable net.minecraft.client.renderer.Rect2i getItemsSlotRect(int slotIdx) {
        if (!isItemsModeActive() || slotIdx < 0 || slotIdx >= ITEMS_SLOTS_VISIBLE) return null;
        if (resolveCurrentFilter() == null) return null;
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        int gridW = ITEMS_SLOT_COLS * ITEMS_SLOT_SIZE;
        int gridX = cx + (rw - gridW) / 2;
        int gridY = topPos + CONTENT_TOP + 80;
        int col = slotIdx % ITEMS_SLOT_COLS;
        int row = slotIdx / ITEMS_SLOT_COLS;
        return new net.minecraft.client.renderer.Rect2i(
                gridX + col * ITEMS_SLOT_SIZE,
                gridY + row * ITEMS_SLOT_SIZE,
                ITEMS_SLOT_SIZE, ITEMS_SLOT_SIZE);
    }

    public int getItemsSlotAt(int x, int y) {
        if (!isItemsModeActive()) return -1;
        for (int i = 0; i < ITEMS_SLOTS_VISIBLE; i++) {
            var r = getItemsSlotRect(i);
            if (r != null && x >= r.getX() && x < r.getX() + r.getWidth()
                    && y >= r.getY() && y < r.getY() + r.getHeight()) return i;
        }
        return -1;
    }

    /* ---- per-emitter subchannel resolution ----
     * On an emitter the visible list is just its own subchannels. Anything else (receiver,
     * storage, manager) sees the union from every loaded emitter on the channel — that's how
     * a receiver picks which streams to listen to. */

    private List<ItemSubchannel> visibleItemSubchannels() {
        if (currentChannel == null) return List.of();
        ChannelInfo.MemberPos here = findThisDevice();
        if (here != null && here.type() == ChannelInfo.TYPE_EMITTER) return here.itemSubchannels();
        List<ItemSubchannel> out = new ArrayList<>();
        for (ChannelInfo.MemberPos m : currentChannel.memberPositions()) {
            if (m.type() == ChannelInfo.TYPE_EMITTER) out.addAll(m.itemSubchannels());
        }
        return out;
    }

    private @Nullable ItemSubchannel findVisibleItemSubchannel(UUID id) {
        if (id == null) return null;
        for (ItemSubchannel s : visibleItemSubchannels()) if (s.id().equals(id)) return s;
        return null;
    }

    private @Nullable net.minecraft.core.BlockPos itemSubchannelOwner(UUID id) {
        if (id == null || currentChannel == null) return null;
        for (ChannelInfo.MemberPos m : currentChannel.memberPositions()) {
            if (m.type() != ChannelInfo.TYPE_EMITTER) continue;
            for (ItemSubchannel s : m.itemSubchannels()) if (s.id().equals(id)) return m.pos();
        }
        return null;
    }

    private List<FluidSubchannel> visibleFluidSubchannels() {
        if (currentChannel == null) return List.of();
        ChannelInfo.MemberPos here = findThisDevice();
        if (here != null && here.type() == ChannelInfo.TYPE_EMITTER) return here.fluidSubchannels();
        List<FluidSubchannel> out = new ArrayList<>();
        for (ChannelInfo.MemberPos m : currentChannel.memberPositions()) {
            if (m.type() == ChannelInfo.TYPE_EMITTER) out.addAll(m.fluidSubchannels());
        }
        return out;
    }

    private @Nullable FluidSubchannel findVisibleFluidSubchannel(UUID id) {
        if (id == null) return null;
        for (FluidSubchannel s : visibleFluidSubchannels()) if (s.id().equals(id)) return s;
        return null;
    }

    private @Nullable net.minecraft.core.BlockPos fluidSubchannelOwner(UUID id) {
        if (id == null || currentChannel == null) return null;
        for (ChannelInfo.MemberPos m : currentChannel.memberPositions()) {
            if (m.type() != ChannelInfo.TYPE_EMITTER) continue;
            for (FluidSubchannel s : m.fluidSubchannels()) if (s.id().equals(id)) return m.pos();
        }
        return null;
    }

    private List<com.quantumchanneling.channel.GasSubchannel> visibleGasSubchannels() {
        if (currentChannel == null) return List.of();
        ChannelInfo.MemberPos here = findThisDevice();
        if (here != null && here.type() == ChannelInfo.TYPE_EMITTER) return here.gasSubchannels();
        List<com.quantumchanneling.channel.GasSubchannel> out = new ArrayList<>();
        for (ChannelInfo.MemberPos m : currentChannel.memberPositions()) {
            if (m.type() == ChannelInfo.TYPE_EMITTER) out.addAll(m.gasSubchannels());
        }
        return out;
    }

    private @Nullable com.quantumchanneling.channel.GasSubchannel findVisibleGasSubchannel(UUID id) {
        if (id == null) return null;
        for (var s : visibleGasSubchannels()) if (s.id().equals(id)) return s;
        return null;
    }

    private @Nullable net.minecraft.core.BlockPos gasSubchannelOwner(UUID id) {
        if (id == null || currentChannel == null) return null;
        for (ChannelInfo.MemberPos m : currentChannel.memberPositions()) {
            if (m.type() != ChannelInfo.TYPE_EMITTER) continue;
            for (var s : m.gasSubchannels()) if (s.id().equals(id)) return m.pos();
        }
        return null;
    }

    /**
     * Create a new item subchannel on this emitter. Rejects empty names, case-insensitive
     * duplicates, and either of the two server-enforced caps (per-emitter + per-channel). The
     * server runs the same checks — these mirror them so the UI doesn't fire packets that the
     * server is just going to drop on the floor.
     */
    private void sendCreateItemSubchannel() {
        if (currentChannel == null || itemsNewSubInput == null) return;
        String name = itemsNewSubInput.getValue().trim();
        if (name.isEmpty()) return;
        ChannelInfo.MemberPos here = findThisDevice();
        if (here == null || here.type() != ChannelInfo.TYPE_EMITTER) return;
        if (here.itemSubchannels().size() >= ClientServerConfig.itemsMaxSubsPerEmitter) return;
        int channelTotal = 0;
        for (ChannelInfo.MemberPos m : currentChannel.memberPositions()) {
            if (m.type() == ChannelInfo.TYPE_EMITTER) channelTotal += m.itemSubchannels().size();
        }
        if (channelTotal >= ClientServerConfig.itemsMaxSubsPerChannel) return;
        for (ItemSubchannel s : here.itemSubchannels()) {
            if (s.name().equalsIgnoreCase(name)) return;
        }
        ModMessages.sendToServer(new CreateSubchannelPacket(menu.getBlockPos(), name));
        itemsNewSubInput.setValue("");
    }

    private void sendCreateFluidSubchannel() {
        if (currentChannel == null || fluidsNewSubInput == null) return;
        String name = fluidsNewSubInput.getValue().trim();
        if (name.isEmpty()) return;
        ChannelInfo.MemberPos here = findThisDevice();
        if (here == null || here.type() != ChannelInfo.TYPE_EMITTER) return;
        if (here.fluidSubchannels().size() >= ClientServerConfig.fluidsMaxSubsPerEmitter) return;
        int channelTotal = 0;
        for (ChannelInfo.MemberPos m : currentChannel.memberPositions()) {
            if (m.type() == ChannelInfo.TYPE_EMITTER) channelTotal += m.fluidSubchannels().size();
        }
        if (channelTotal >= ClientServerConfig.fluidsMaxSubsPerChannel) return;
        for (FluidSubchannel s : here.fluidSubchannels()) {
            if (s.name().equalsIgnoreCase(name)) return;
        }
        ModMessages.sendToServer(new CreateFluidSubchannelPacket(menu.getBlockPos(), name));
        fluidsNewSubInput.setValue("");
    }

    private void sendCreateGasSubchannel() {
        if (currentChannel == null || gasesNewSubInput == null) return;
        String name = gasesNewSubInput.getValue().trim();
        if (name.isEmpty()) return;
        ChannelInfo.MemberPos here = findThisDevice();
        if (here == null || here.type() != ChannelInfo.TYPE_EMITTER) return;
        if (here.gasSubchannels().size() >= ClientServerConfig.gasesMaxSubsPerEmitter) return;
        int channelTotal = 0;
        for (ChannelInfo.MemberPos m : currentChannel.memberPositions()) {
            if (m.type() == ChannelInfo.TYPE_EMITTER) channelTotal += m.gasSubchannels().size();
        }
        if (channelTotal >= ClientServerConfig.gasesMaxSubsPerChannel) return;
        for (var s : here.gasSubchannels()) {
            if (s.name().equalsIgnoreCase(name)) return;
        }
        ModMessages.sendToServer(new com.quantumchanneling.channel.CreateGasSubchannelPacket(menu.getBlockPos(), name));
        gasesNewSubInput.setValue("");
    }

    /** Project the ordered subchannel list down to just its UUIDs, preserving order. */
    private static List<UUID> orderedIds(List<? extends Object> subs) {
        List<UUID> out = new ArrayList<>(subs.size());
        for (Object o : subs) {
            if (o instanceof ItemSubchannel s) out.add(s.id());
            else if (o instanceof FluidSubchannel s) out.add(s.id());
            else if (o instanceof com.quantumchanneling.channel.GasSubchannel s) out.add(s.id());
        }
        return out;
    }

    public void acceptDroppedFilterItem(ResourceLocation id, int slotIdx) {
        if (currentChannel == null || !isItemsModeActive() || id == null) return;
        ItemFilter f = resolveCurrentFilter();
        if (f == null) return;
        ResourceLocation existing = filterItemAtSlot(f, slotIdx);
        if (existing != null && existing.equals(id)) return;
        if (existing != null) sendRemoveItem(existing);
        sendAddItem(id);
    }

    public java.util.List<net.minecraft.client.renderer.Rect2i> getExtraGuiAreas() {
        java.util.List<net.minecraft.client.renderer.Rect2i> areas = new java.util.ArrayList<>(3);
        int rightTabsH = 4 * SIDE_TAB_H + 3 * SIDE_TAB_GAP;
        areas.add(new net.minecraft.client.renderer.Rect2i(
                leftPos + BG_W - 1, topPos + CONTENT_TOP - 1,
                SIDE_TAB_W + 2, rightTabsH + 2));
        areas.add(new net.minecraft.client.renderer.Rect2i(
                leftPos - LEFT_TAB_W - 1, topPos + CONTENT_TOP - 1,
                LEFT_TAB_W + 2, LEFT_TAB_H + 2));
        if (channelInfoOpen) {
            areas.add(new net.minecraft.client.renderer.Rect2i(
                    leftPos - LEFT_TAB_W - CHANNEL_PANEL_W - 1, topPos + CONTENT_TOP - 1,
                    CHANNEL_PANEL_W + 2, BG_H - CONTENT_TOP - 2));
        }
        return areas;
    }

    private void buildItemsPanel() {
        int cx = leftPos + 8;
        int rw = BG_W - 16;

        ensureSelectionValid();
        ChannelInfo.MemberPos here = findThisDevice();
        boolean emitter = here != null && here.type() == ChannelInfo.TYPE_EMITTER;

        // Per-device participation flag — this toggles only the device whose GUI is open. Devices
        // default to enabled; the button shows the current device's state via `here`.
        boolean enabled = here != null && here.itemsEnabled();
        addRenderableWidget(PhotonButton.of(cx, topPos + CONTENT_TOP + 4, 100, 18,
                Component.translatable(enabled
                        ? "gui.quantumchanneling.items.enabled"
                        : "gui.quantumchanneling.items.disabled"),
                b -> ModMessages.sendToServer(new SetItemEnabledPacket(menu.getBlockPos(), !enabled)),
                this::accentColor));

        itemsNewSubInput = new EditBox(font, cx + 104, topPos + CONTENT_TOP + 5,
                rw - 104 - 44, 16, Component.literal(""));
        itemsNewSubInput.setHint(Component.translatable("gui.quantumchanneling.items.new_sub_hint"));
        itemsNewSubInput.setMaxLength(32);
        addRenderableWidget(itemsNewSubInput);
        addRenderableWidget(PhotonButton.of(cx + rw - 40, topPos + CONTENT_TOP + 4, 40, 18,
                Component.translatable("gui.quantumchanneling.items.new_sub_btn"),
                b -> sendCreateItemSubchannel(),
                this::accentColor));

        // Dispatch strategy — shown on emitter (controls receiver distribution) and on receiver
        // (controls adjacent-inv distribution). Storage / manager devices have no dispatch surface.
        if (here != null && (here.type() == ChannelInfo.TYPE_EMITTER || here.type() == ChannelInfo.TYPE_RECEIVER)) {
            com.quantumchanneling.channel.DispatchStrategy curS = here.itemDispatch();
            com.quantumchanneling.channel.DispatchStrategy nextS = curS.next();
            addRenderableWidget(PhotonButton.of(cx, topPos + CONTENT_TOP + 50, 160, 18,
                    Component.translatable("gui.quantumchanneling.dispatch.label",
                            Component.translatable(curS.labelKey())),
                    b -> ModMessages.sendToServer(
                            new com.quantumchanneling.channel.SetDispatchStrategyPacket(
                                    menu.getBlockPos(), (byte) 0, (byte) nextS.ordinal())),
                    this::accentColor));
        }

        // Editable targets = void (for emitters only) + every subchannel. With zero targets the
        // entire editor section collapses to a single empty-state message rendered later.
        int subCount = currentChannel == null ? 0 : visibleItemSubchannels().size();
        int cycleSize = subCount + (emitter ? 1 : 0);
        boolean hasEditTarget = cycleSize > 0;
        boolean showArrows = cycleSize > 1;
        if (!hasEditTarget) return;   // renderItemsPanel paints the empty state

        int selY = topPos + CONTENT_TOP + 28;
        int labelX = showArrows ? cx + 16 : cx;
        int labelW = showArrows ? 130 : 162;
        if (showArrows) {
            addRenderableWidget(PhotonButton.of(cx, selY, 14, 18, Component.literal("◀"),
                    b -> cycleSelection(-1), this::accentColor));
        }
        addRenderableWidget(PhotonButton.of(labelX, selY, labelW, 18, currentEditingLabel(),
                b -> cycleSelection(+1), this::accentColor));
        if (showArrows) {
            addRenderableWidget(PhotonButton.of(cx + 148, selY, 14, 18, Component.literal("▶"),
                    b -> cycleSelection(+1), this::accentColor));
        }

        // Subchannel filters have a WL/BL toggle; the void filter is single-mode by design
        // (items in list → voided) so it never shows the toggle.
        ItemFilter cur = resolveCurrentFilter();
        if (cur != null && !editingVoid) {
            addRenderableWidget(PhotonButton.of(cx + 166, selY, 70, 18,
                    Component.translatable(cur.isWhitelist()
                            ? "gui.quantumchanneling.items.filter_whitelist"
                            : "gui.quantumchanneling.items.filter_blacklist"),
                    b -> sendToggleFilterMode(), this::accentColor));
        }

        // Subscribe only makes sense for receivers — emitters own their own subchannels.
        if (!emitter && selectedSubchannelId != null && !editingVoid && here != null) {
            boolean subbed = here.subscribedSubchannels().contains(selectedSubchannelId);
            UUID subId = selectedSubchannelId;
            addRenderableWidget(PhotonButton.of(cx + 240, selY, 60, 18,
                    Component.translatable(subbed
                            ? "gui.quantumchanneling.items.unsubscribe"
                            : "gui.quantumchanneling.items.subscribe"),
                    b -> ModMessages.sendToServer(new SubscribeDevicePacket(
                            menu.getBlockPos(), subId, !subbed)),
                    this::accentColor));
        }

        // No "minecraft:item_id…" text input row — filter entries are populated exclusively by
        // (a) picking up an inventory item and clicking a slot (cursor preserved), (b) JEI/EMI
        // ghost-drag onto a slot, or (c) deleted by clicking a populated slot with an empty cursor.
        if (selectedSubchannelId != null && !editingVoid && currentChannel != null) {
            UUID subId = selectedSubchannelId;
            // Delete-the-subchannel-from-the-channel button always available to the editor.
            net.minecraft.core.BlockPos owner = itemSubchannelOwner(subId);
            if (owner != null) {
                addRenderableWidget(PhotonButton.danger(cx + rw - 40, topPos + CONTENT_TOP + 160, 40, 18,
                        Component.translatable("gui.quantumchanneling.items.delete_sub_btn"),
                        b -> ModMessages.sendToServer(new DeleteSubchannelPacket(owner, subId))));
            }

            // Priority reorder buttons — only meaningful when this emitter owns the selected sub.
            // The order being reordered is the emitter's own iteration order (= routing priority).
            if (emitter && here != null) {
                java.util.List<UUID> mySubs = orderedIds(here.itemSubchannels());
                int myIdx = mySubs.indexOf(subId);
                int total = mySubs.size();
                boolean canReorder = myIdx >= 0 && total >= 2;
                if (canReorder) {
                    boolean canUp = myIdx > 0;
                    boolean canDown = myIdx < total - 1;
                    PhotonButton up = PhotonButton.of(cx, topPos + CONTENT_TOP + 160, 56, 18,
                            Component.translatable("gui.quantumchanneling.items.priority_up"),
                            b -> ModMessages.sendToServer(new MoveDeviceSubchannelPacket(menu.getBlockPos(), subId, -1)),
                            this::accentColor);
                    up.active = canUp;
                    up.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable("gui.quantumchanneling.items.priority_up.tip")));
                    addRenderableWidget(up);

                    PhotonButton down = PhotonButton.of(cx + 60, topPos + CONTENT_TOP + 160, 56, 18,
                            Component.translatable("gui.quantumchanneling.items.priority_down"),
                            b -> ModMessages.sendToServer(new MoveDeviceSubchannelPacket(menu.getBlockPos(), subId, +1)),
                            this::accentColor);
                    down.active = canDown;
                    down.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable("gui.quantumchanneling.items.priority_down.tip")));
                    addRenderableWidget(down);
                }
            }
        }
    }

    private void renderItemsPanel(GuiGraphics gfx, int mouseX, int mouseY) {
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        int accent = accentColor();

        if (currentChannel == null) {
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.unbound")
                    .withStyle(ChatFormatting.GRAY), cx, topPos + CONTENT_TOP + 4, 0xAAAAAA, false);
            return;
        }

        // Nothing to edit yet (receiver on a channel with no subchannels). Paint a single,
        // centered empty-state instead of three overlapping labels + dead cycle arrows.
        ChannelInfo.MemberPos here = findThisDevice();
        boolean emitter = here != null && here.type() == ChannelInfo.TYPE_EMITTER;
        int subCount = visibleItemSubchannels().size();
        int cycleSize = subCount + (emitter ? 1 : 0);
        if (cycleSize == 0) {
            // y starts below the dispatch button row (y=50 + 18 = 68) with a small gap so the
            // helper text doesn't bleed into the button when there are zero subchannels.
            Component msg = Component.translatable("gui.quantumchanneling.items.empty_state")
                    .withStyle(ChatFormatting.GRAY);
            var lines = font.split(msg, rw - 16);
            int y = topPos + CONTENT_TOP + 78;
            for (var line : lines) {
                int w = font.width(line);
                gfx.drawString(font, line, leftPos + (BG_W - w) / 2, y, 0xFF888888, false);
                y += 11;
            }
            return;
        }

        ItemFilter f = resolveCurrentFilter();
        ItemStack hoveredStack = null;
        if (f == null) {
            // Defensive — shouldn't hit this when cycleSize>0, but if a race nullifies the filter
            // mid-frame we still want a clean grey hint instead of nothing.
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.items.no_subs")
                    .withStyle(ChatFormatting.GRAY),
                    cx + 4, topPos + CONTENT_TOP + 90, 0xFF888888, false);
        } else {
            for (int i = 0; i < ITEMS_SLOTS_VISIBLE; i++) {
                var r = getItemsSlotRect(i);
                if (r == null) continue;
                int sx = r.getX(), sy = r.getY();
                ResourceLocation id = filterItemAtSlot(f, i);
                ItemStack stack = ItemStack.EMPTY;
                if (id != null) {
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
                    if (item != net.minecraft.world.item.Items.AIR) stack = new ItemStack(item);
                }
                boolean hover = mouseX >= sx && mouseX < sx + ITEMS_SLOT_SIZE
                        && mouseY >= sy && mouseY < sy + ITEMS_SLOT_SIZE;
                int border = hover ? accent : blendARGB(accent, 0xFF000000, 0.6f);
                int inner  = hover ? 0xFF1A2030 : 0xFF0C1018;
                gfx.fill(sx, sy, sx + ITEMS_SLOT_SIZE, sy + ITEMS_SLOT_SIZE, border);
                gfx.fill(sx + 1, sy + 1, sx + ITEMS_SLOT_SIZE - 1, sy + ITEMS_SLOT_SIZE - 1, inner);
                if (!stack.isEmpty()) {
                    gfx.renderItem(stack, sx + 1, sy + 1);
                    gfx.renderItemDecorations(font, stack, sx + 1, sy + 1);
                    if (hover) hoveredStack = stack;
                }
            }
            if (hoveredStack != null) gfx.renderTooltip(font, hoveredStack, mouseX, mouseY);
            if (f.size() > ITEMS_SLOTS_VISIBLE) {
                gfx.drawString(font, Component.literal("… +" + (f.size() - ITEMS_SLOTS_VISIBLE) + " more"),
                        cx + 4, topPos + CONTENT_TOP + 80 + ITEMS_SLOT_ROWS * ITEMS_SLOT_SIZE + 2,
                        0xFF808890, false);
            }
        }

        // Priority row header — only when the reorder buttons are actually visible.
        if (emitter && here != null && !editingVoid && selectedSubchannelId != null) {
            java.util.List<UUID> mySubs = orderedIds(here.itemSubchannels());
            int myIdx = mySubs.indexOf(selectedSubchannelId);
            if (myIdx >= 0 && mySubs.size() >= 2) {
                gfx.drawString(font,
                        Component.translatable("gui.quantumchanneling.items.priority_header",
                                myIdx + 1, mySubs.size())
                                .withStyle(ChatFormatting.GRAY),
                        cx, topPos + CONTENT_TOP + 148, 0xFFB0B8C8, false);
            }
        }

        int n = visibleItemSubchannels().size();
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.items.sub_count", n),
                cx, topPos + BG_H - 18, 0xFFB0B8C8, false);
    }

    private boolean handleItemsPanelClick(double mx, double my) {
        if (!isItemsModeActive() || currentChannel == null) return false;
        int slot = getItemsSlotAt((int) mx, (int) my);
        if (slot >= 0) {
            // Cursor-holds-stack path: capture the item id and add it to the filter without
            // consuming the stack. Lets the user "show" filter slots an item from their inventory
            // dock by picking it up and clicking, then dropping it back. The cursor stack is
            // preserved because we never call menu.setCarried(EMPTY) and we return true so
            // super.mouseClicked never sees a slot to deposit into.
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(carried.getItem());
                if (id != null) sendAddItem(id);
                return true;
            }
            // Empty cursor on a populated slot → remove the entry (existing behavior).
            ItemFilter f = resolveCurrentFilter();
            ResourceLocation id = filterItemAtSlot(f, slot);
            if (id != null) sendRemoveItem(id);
            return true;
        }
        return false;
    }

    private void ensureSelectionValid() {
        if (currentChannel == null) {
            selectedSubchannelId = null;
            editingVoid = false;
            return;
        }
        if (editingVoid) {
            if (!isCurrentEmitter()) {
                editingVoid = false;
                selectedSubchannelId = firstSubchannelId();
            }
            return;
        }
        if (selectedSubchannelId != null
                && findVisibleItemSubchannel(selectedSubchannelId) != null) return;
        UUID first = firstSubchannelId();
        if (first != null) {
            selectedSubchannelId = first;
            editingVoid = false;
        } else if (isCurrentEmitter()) {
            editingVoid = true;
            selectedSubchannelId = null;
        } else {
            selectedSubchannelId = null;
            editingVoid = false;
        }
    }

    private @Nullable UUID firstSubchannelId() {
        if (currentChannel == null) return null;
        for (var s : visibleItemSubchannels()) return s.id();
        return null;
    }

    private @Nullable ItemFilter resolveCurrentFilter() {
        if (currentChannel == null) return null;
        if (editingVoid) {
            ChannelInfo.MemberPos here = findThisDevice();
            if (here == null || here.type() != ChannelInfo.TYPE_EMITTER) return null;
            return here.voidFilter();
        }
        if (selectedSubchannelId == null) return null;
        ItemSubchannel sub = findVisibleItemSubchannel(selectedSubchannelId);
        return sub == null ? null : sub.filter();
    }

    private Component currentEditingLabel() {
        if (currentChannel == null) {
            return Component.translatable("gui.quantumchanneling.items.no_subs");
        }
        if (editingVoid) return Component.translatable("gui.quantumchanneling.items.editing_void");
        if (selectedSubchannelId == null) {
            return Component.translatable("gui.quantumchanneling.items.no_subs");
        }
        ItemSubchannel sub = findVisibleItemSubchannel(selectedSubchannelId);
        String name = sub == null ? "?" : (sub.name().isEmpty() ? "(unnamed)" : sub.name());
        return Component.translatable("gui.quantumchanneling.items.editing_sub", name);
    }

    private void cycleSelection(int direction) {
        if (currentChannel == null) return;
        boolean emitter = isCurrentEmitter();
        java.util.List<Object> cycle = new java.util.ArrayList<>();
        if (emitter) cycle.add("VOID");
        for (var s : visibleItemSubchannels()) cycle.add(s.id());
        if (cycle.isEmpty()) return;
        int idx = -1;
        for (int i = 0; i < cycle.size(); i++) {
            Object o = cycle.get(i);
            if (editingVoid && o.equals("VOID")) { idx = i; break; }
            if (!editingVoid && selectedSubchannelId != null
                    && o instanceof UUID u && u.equals(selectedSubchannelId)) { idx = i; break; }
        }
        if (idx == -1) idx = 0;
        int next = ((idx + direction) % cycle.size() + cycle.size()) % cycle.size();
        Object n = cycle.get(next);
        if (n.equals("VOID")) { editingVoid = true; selectedSubchannelId = null; }
        else { editingVoid = false; selectedSubchannelId = (UUID) n; }
        rebuildAll();
    }

    private boolean isCurrentEmitter() {
        ChannelInfo.MemberPos here = findThisDevice();
        return here != null && here.type() == ChannelInfo.TYPE_EMITTER;
    }

    /**
     * Cached lookup for "the MemberPos that represents the device this menu was opened on". The
     * underlying scan is O(memberCount), and several render-path callers ask for it per frame —
     * so we hold a reference to the resolved entry plus the {@link ChannelInfo} it came from. A
     * different ChannelInfo (post-refresh) invalidates the cache automatically.
     */
    private @Nullable ChannelInfo cachedFindFor = null;
    private @Nullable ChannelInfo.MemberPos cachedThisDevice = null;

    private @Nullable ChannelInfo.MemberPos findThisDevice() {
        if (currentChannel == null) { cachedFindFor = null; cachedThisDevice = null; return null; }
        if (cachedFindFor == currentChannel) return cachedThisDevice;
        var pos = menu.getBlockPos();
        ChannelInfo.MemberPos found = null;
        for (var m : currentChannel.memberPositions()) {
            if (m.pos().equals(pos)) { found = m; break; }
        }
        cachedFindFor = currentChannel;
        cachedThisDevice = found;
        return found;
    }

    private static @Nullable ResourceLocation filterItemAtSlot(ItemFilter f, int slotIdx) {
        if (f == null || slotIdx < 0) return null;
        int i = 0;
        for (var id : f.items()) {
            if (i == slotIdx) return id;
            i++;
        }
        return null;
    }

    private void sendAddItem(ResourceLocation id) {
        if (currentChannel == null) return;
        if (editingVoid) {
            ModMessages.sendToServer(new AddEmitterVoidItemPacket(menu.getBlockPos(), id));
        } else if (selectedSubchannelId != null) {
            net.minecraft.core.BlockPos owner = itemSubchannelOwner(selectedSubchannelId);
            if (owner != null) {
                ModMessages.sendToServer(new AddSubchannelItemPacket(owner, selectedSubchannelId, id));
            }
        }
    }

    private void sendRemoveItem(ResourceLocation id) {
        if (currentChannel == null) return;
        if (editingVoid) {
            ModMessages.sendToServer(new RemoveEmitterVoidItemPacket(menu.getBlockPos(), id));
        } else if (selectedSubchannelId != null) {
            net.minecraft.core.BlockPos owner = itemSubchannelOwner(selectedSubchannelId);
            if (owner != null) {
                ModMessages.sendToServer(new RemoveSubchannelItemPacket(owner, selectedSubchannelId, id));
            }
        }
    }

    private void sendToggleFilterMode() {
        if (currentChannel == null) return;
        // Void is locked to "items in list → voided" semantics; only subchannel filters expose WL/BL.
        if (editingVoid || selectedSubchannelId == null) return;
        ItemFilter cur = resolveCurrentFilter();
        if (cur == null) return;
        net.minecraft.core.BlockPos owner = itemSubchannelOwner(selectedSubchannelId);
        if (owner == null) return;
        ModMessages.sendToServer(new SetSubchannelFilterModePacket(owner, selectedSubchannelId, !cur.isWhitelist()));
    }

    /* ===================== Fluids panel (clone of items, vanilla IFluidHandler) ===================== */

    /** True when the fluid filter slot grid is currently the visible panel. */
    public boolean isFluidsModeActive() {
        return activeTab == Tab.CHARGE && activeMode == ResourceMode.FLUIDS && currentChannel != null;
    }

    private @Nullable FluidFilter resolveCurrentFluidFilter() {
        if (currentChannel == null) return null;
        if (editingFluidVoid) {
            ChannelInfo.MemberPos here = findThisDevice();
            if (here != null && here.type() == ChannelInfo.TYPE_EMITTER) return here.fluidVoidFilter();
            return null;
        }
        if (selectedFluidSubchannelId == null) return null;
        FluidSubchannel sub = findVisibleFluidSubchannel(selectedFluidSubchannelId);
        return sub == null ? null : sub.filter();
    }

    private Component currentEditingFluidLabel() {
        if (currentChannel == null || (selectedFluidSubchannelId == null && !editingFluidVoid)) {
            return Component.translatable("gui.quantumchanneling.items.no_subs");
        }
        if (editingFluidVoid) return Component.translatable("gui.quantumchanneling.items.editing_void");
        FluidSubchannel sub = findVisibleFluidSubchannel(selectedFluidSubchannelId);
        String name = sub == null ? "?" : (sub.name().isEmpty() ? "(unnamed)" : sub.name());
        return Component.translatable("gui.quantumchanneling.items.editing_sub", name);
    }

    private @Nullable java.util.UUID firstFluidSubchannelId() {
        if (currentChannel == null) return null;
        var it = visibleFluidSubchannels().iterator();
        return it.hasNext() ? it.next().id() : null;
    }

    private void ensureFluidSelectionValid() {
        if (currentChannel == null) { selectedFluidSubchannelId = null; editingFluidVoid = false; return; }
        if (editingFluidVoid) {
            if (!isCurrentEmitter()) { editingFluidVoid = false; selectedFluidSubchannelId = firstFluidSubchannelId(); }
            return;
        }
        if (selectedFluidSubchannelId != null
                && findVisibleFluidSubchannel(selectedFluidSubchannelId) != null) return;
        java.util.UUID first = firstFluidSubchannelId();
        if (first != null) { selectedFluidSubchannelId = first; editingFluidVoid = false; }
        else if (isCurrentEmitter()) { editingFluidVoid = true; selectedFluidSubchannelId = null; }
        else { selectedFluidSubchannelId = null; editingFluidVoid = false; }
    }

    private void cycleFluidSelection(int direction) {
        if (currentChannel == null) return;
        boolean emitter = isCurrentEmitter();
        java.util.List<Object> cycle = new java.util.ArrayList<>();
        if (emitter) cycle.add("VOID");
        for (FluidSubchannel s : visibleFluidSubchannels()) cycle.add(s.id());
        if (cycle.isEmpty()) return;
        int idx = -1;
        if (editingFluidVoid) {
            for (int i = 0; i < cycle.size(); i++) if ("VOID".equals(cycle.get(i))) { idx = i; break; }
        } else if (selectedFluidSubchannelId != null) {
            for (int i = 0; i < cycle.size(); i++) if (selectedFluidSubchannelId.equals(cycle.get(i))) { idx = i; break; }
        }
        if (idx < 0) idx = 0;
        idx = ((idx + direction) % cycle.size() + cycle.size()) % cycle.size();
        Object chosen = cycle.get(idx);
        if ("VOID".equals(chosen)) { editingFluidVoid = true; selectedFluidSubchannelId = null; }
        else { editingFluidVoid = false; selectedFluidSubchannelId = (java.util.UUID) chosen; }
    }

    private net.minecraft.resources.@Nullable ResourceLocation fluidFilterAtSlot(@Nullable FluidFilter f, int slotIdx) {
        if (f == null) return null;
        int i = 0;
        for (net.minecraft.resources.ResourceLocation id : f.fluids()) { if (i == slotIdx) return id; i++; }
        return null;
    }

    private void sendAddFluid(net.minecraft.resources.ResourceLocation id) {
        if (currentChannel == null) return;
        if (editingFluidVoid) {
            ModMessages.sendToServer(new AddEmitterFluidVoidPacket(menu.getBlockPos(), id));
        } else if (selectedFluidSubchannelId != null) {
            net.minecraft.core.BlockPos owner = fluidSubchannelOwner(selectedFluidSubchannelId);
            if (owner != null) {
                ModMessages.sendToServer(new AddSubchannelFluidPacket(owner, selectedFluidSubchannelId, id));
            }
        }
    }

    private void sendRemoveFluid(net.minecraft.resources.ResourceLocation id) {
        if (currentChannel == null) return;
        if (editingFluidVoid) {
            ModMessages.sendToServer(new RemoveEmitterFluidVoidPacket(menu.getBlockPos(), id));
        } else if (selectedFluidSubchannelId != null) {
            net.minecraft.core.BlockPos owner = fluidSubchannelOwner(selectedFluidSubchannelId);
            if (owner != null) {
                ModMessages.sendToServer(new RemoveSubchannelFluidPacket(owner, selectedFluidSubchannelId, id));
            }
        }
    }

    private void sendToggleFluidFilterMode() {
        if (currentChannel == null || editingFluidVoid || selectedFluidSubchannelId == null) return;
        FluidFilter cur = resolveCurrentFluidFilter();
        if (cur == null) return;
        net.minecraft.core.BlockPos owner = fluidSubchannelOwner(selectedFluidSubchannelId);
        if (owner == null) return;
        ModMessages.sendToServer(new SetFluidSubchannelFilterModePacket(
                owner, selectedFluidSubchannelId, !cur.isWhitelist()));
    }

    public int getFluidsSlotCount() { return ITEMS_SLOTS_VISIBLE; }

    public @Nullable net.minecraft.client.renderer.Rect2i getFluidsSlotRect(int slotIdx) {
        if (!isFluidsModeActive() || slotIdx < 0 || slotIdx >= ITEMS_SLOTS_VISIBLE) return null;
        if (resolveCurrentFluidFilter() == null) return null;
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        int gridW = ITEMS_SLOT_COLS * ITEMS_SLOT_SIZE;
        int gridX = cx + (rw - gridW) / 2;
        int gridY = topPos + CONTENT_TOP + 80;
        int col = slotIdx % ITEMS_SLOT_COLS;
        int row = slotIdx / ITEMS_SLOT_COLS;
        return new net.minecraft.client.renderer.Rect2i(
                gridX + col * ITEMS_SLOT_SIZE,
                gridY + row * ITEMS_SLOT_SIZE,
                ITEMS_SLOT_SIZE, ITEMS_SLOT_SIZE);
    }

    public int getFluidsSlotAt(int x, int y) {
        if (!isFluidsModeActive()) return -1;
        for (int i = 0; i < ITEMS_SLOTS_VISIBLE; i++) {
            var r = getFluidsSlotRect(i);
            if (r != null && x >= r.getX() && x < r.getX() + r.getWidth()
                    && y >= r.getY() && y < r.getY() + r.getHeight()) return i;
        }
        return -1;
    }

    private void buildFluidsPanel() {
        int cx = leftPos + 8;
        int rw = BG_W - 16;

        ensureFluidSelectionValid();
        ChannelInfo.MemberPos here = findThisDevice();
        boolean emitter = here != null && here.type() == ChannelInfo.TYPE_EMITTER;

        // Per-device participation — toggles only this emitter/receiver.
        boolean enabled = here != null && here.fluidsEnabled();
        addRenderableWidget(PhotonButton.of(cx, topPos + CONTENT_TOP + 4, 100, 18,
                Component.translatable(enabled
                        ? "gui.quantumchanneling.fluids.enabled"
                        : "gui.quantumchanneling.fluids.disabled"),
                b -> ModMessages.sendToServer(new SetFluidEnabledPacket(menu.getBlockPos(), !enabled)),
                this::accentColor));

        fluidsNewSubInput = new EditBox(font, cx + 104, topPos + CONTENT_TOP + 5,
                rw - 104 - 44, 16, Component.literal(""));
        fluidsNewSubInput.setHint(Component.translatable("gui.quantumchanneling.items.new_sub_hint"));
        fluidsNewSubInput.setMaxLength(32);
        addRenderableWidget(fluidsNewSubInput);
        addRenderableWidget(PhotonButton.of(cx + rw - 40, topPos + CONTENT_TOP + 4, 40, 18,
                Component.translatable("gui.quantumchanneling.items.new_sub_btn"),
                b -> sendCreateFluidSubchannel(),
                this::accentColor));

        if (here != null && (here.type() == ChannelInfo.TYPE_EMITTER || here.type() == ChannelInfo.TYPE_RECEIVER)) {
            com.quantumchanneling.channel.DispatchStrategy curS = here.fluidDispatch();
            com.quantumchanneling.channel.DispatchStrategy nextS = curS.next();
            addRenderableWidget(PhotonButton.of(cx, topPos + CONTENT_TOP + 50, 160, 18,
                    Component.translatable("gui.quantumchanneling.dispatch.label",
                            Component.translatable(curS.labelKey())),
                    b -> ModMessages.sendToServer(
                            new com.quantumchanneling.channel.SetDispatchStrategyPacket(
                                    menu.getBlockPos(), (byte) 1, (byte) nextS.ordinal())),
                    this::accentColor));
        }

        int subCount = currentChannel == null ? 0 : visibleFluidSubchannels().size();
        int cycleSize = subCount + (emitter ? 1 : 0);
        if (cycleSize == 0) return;
        boolean showArrows = cycleSize > 1;

        int selY = topPos + CONTENT_TOP + 28;
        int labelX = showArrows ? cx + 16 : cx;
        int labelW = showArrows ? 130 : 162;
        if (showArrows) {
            addRenderableWidget(PhotonButton.of(cx, selY, 14, 18, Component.literal("◀"),
                    b -> cycleFluidSelection(-1), this::accentColor));
        }
        addRenderableWidget(PhotonButton.of(labelX, selY, labelW, 18, currentEditingFluidLabel(),
                b -> cycleFluidSelection(+1), this::accentColor));
        if (showArrows) {
            addRenderableWidget(PhotonButton.of(cx + 148, selY, 14, 18, Component.literal("▶"),
                    b -> cycleFluidSelection(+1), this::accentColor));
        }

        FluidFilter cur = resolveCurrentFluidFilter();
        if (cur != null && !editingFluidVoid) {
            addRenderableWidget(PhotonButton.of(cx + 166, selY, 70, 18,
                    Component.translatable(cur.isWhitelist()
                            ? "gui.quantumchanneling.items.filter_whitelist"
                            : "gui.quantumchanneling.items.filter_blacklist"),
                    b -> sendToggleFluidFilterMode(), this::accentColor));
        }

        if (!emitter && selectedFluidSubchannelId != null && !editingFluidVoid && here != null) {
            boolean subbed = here.subscribedFluidSubchannels().contains(selectedFluidSubchannelId);
            java.util.UUID subId = selectedFluidSubchannelId;
            addRenderableWidget(PhotonButton.of(cx + 240, selY, 60, 18,
                    Component.translatable(subbed
                            ? "gui.quantumchanneling.items.unsubscribe"
                            : "gui.quantumchanneling.items.subscribe"),
                    b -> ModMessages.sendToServer(new SubscribeDeviceFluidPacket(menu.getBlockPos(), subId, !subbed)),
                    this::accentColor));
        }

        if (selectedFluidSubchannelId != null && !editingFluidVoid && currentChannel != null) {
            java.util.UUID subId = selectedFluidSubchannelId;
            net.minecraft.core.BlockPos owner = fluidSubchannelOwner(subId);
            if (owner != null) {
                addRenderableWidget(PhotonButton.danger(cx + rw - 40, topPos + CONTENT_TOP + 160, 40, 18,
                        Component.translatable("gui.quantumchanneling.items.delete_sub_btn"),
                        b -> ModMessages.sendToServer(new DeleteFluidSubchannelPacket(owner, subId))));
            }
            if (emitter && here != null) {
                java.util.List<java.util.UUID> mySubs = orderedIds(here.fluidSubchannels());
                int myIdx = mySubs.indexOf(subId);
                int total = mySubs.size();
                if (myIdx >= 0 && total >= 2) {
                    boolean canUp = myIdx > 0;
                    boolean canDown = myIdx < total - 1;
                    PhotonButton up = PhotonButton.of(cx, topPos + CONTENT_TOP + 160, 56, 18,
                            Component.translatable("gui.quantumchanneling.items.priority_up"),
                            b -> ModMessages.sendToServer(new MoveDeviceFluidSubchannelPacket(menu.getBlockPos(), subId, -1)),
                            this::accentColor);
                    up.active = canUp;
                    addRenderableWidget(up);
                    PhotonButton down = PhotonButton.of(cx + 60, topPos + CONTENT_TOP + 160, 56, 18,
                            Component.translatable("gui.quantumchanneling.items.priority_down"),
                            b -> ModMessages.sendToServer(new MoveDeviceFluidSubchannelPacket(menu.getBlockPos(), subId, +1)),
                            this::accentColor);
                    down.active = canDown;
                    addRenderableWidget(down);
                }
            }
        }
    }

    private void renderFluidsPanel(GuiGraphics gfx, int mouseX, int mouseY) {
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        int accent = accentColor();

        if (currentChannel == null) {
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.unbound")
                    .withStyle(ChatFormatting.GRAY), cx, topPos + CONTENT_TOP + 4, 0xAAAAAA, false);
            return;
        }

        ChannelInfo.MemberPos here = findThisDevice();
        boolean emitter = here != null && here.type() == ChannelInfo.TYPE_EMITTER;
        int subCount = visibleFluidSubchannels().size();
        int cycleSize = subCount + (emitter ? 1 : 0);
        if (cycleSize == 0) {
            // y starts below the dispatch button row (y=50 + 18 = 68) with a small gap so the
            // helper text doesn't bleed into the button when there are zero subchannels.
            Component msg = Component.translatable("gui.quantumchanneling.items.empty_state")
                    .withStyle(ChatFormatting.GRAY);
            var lines = font.split(msg, rw - 16);
            int y = topPos + CONTENT_TOP + 78;
            for (var line : lines) {
                int w = font.width(line);
                gfx.drawString(font, line, leftPos + (BG_W - w) / 2, y, 0xFF888888, false);
                y += 11;
            }
            return;
        }

        FluidFilter f = resolveCurrentFluidFilter();
        FluidStack hoveredFluid = null;
        if (f != null) {
            for (int i = 0; i < ITEMS_SLOTS_VISIBLE; i++) {
                var r = getFluidsSlotRect(i);
                if (r == null) continue;
                int sx = r.getX(), sy = r.getY();
                net.minecraft.resources.ResourceLocation id = fluidFilterAtSlot(f, i);
                boolean hover = mouseX >= sx && mouseX < sx + ITEMS_SLOT_SIZE && mouseY >= sy && mouseY < sy + ITEMS_SLOT_SIZE;
                int border = hover ? accent : blendARGB(accent, 0xFF000000, 0.6f);
                int inner = hover ? 0xFF1A2030 : 0xFF0C1018;
                gfx.fill(sx, sy, sx + ITEMS_SLOT_SIZE, sy + ITEMS_SLOT_SIZE, border);
                gfx.fill(sx + 1, sy + 1, sx + ITEMS_SLOT_SIZE - 1, sy + ITEMS_SLOT_SIZE - 1, inner);
                if (id != null) {
                    net.minecraft.world.level.material.Fluid fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id);
                    if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                        // Render the bucket icon if the fluid provides one — otherwise paint a flat
                        // tile in the fluid's tint color as a fallback for moditemless fluids.
                        net.minecraft.world.item.ItemStack bucket = new net.minecraft.world.item.ItemStack(fluid.getBucket());
                        if (!bucket.isEmpty()) {
                            gfx.renderItem(bucket, sx + 1, sy + 1);
                            if (hover) hoveredFluid = new FluidStack(fluid, 1000);
                        } else {
                            int tint = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid).getTintColor();
                            if ((tint & 0xFF000000) == 0) tint |= 0xFF000000;
                            gfx.fill(sx + 2, sy + 2, sx + ITEMS_SLOT_SIZE - 2, sy + ITEMS_SLOT_SIZE - 2, tint);
                            if (hover) hoveredFluid = new FluidStack(fluid, 1000);
                        }
                    }
                }
            }
            if (hoveredFluid != null) {
                gfx.renderTooltip(font, hoveredFluid.getDisplayName(), mouseX, mouseY);
            }
        }

        // Priority header row for emitters that own multiple fluid subchannels.
        if (emitter && here != null && !editingFluidVoid && selectedFluidSubchannelId != null) {
            java.util.List<java.util.UUID> mySubs = orderedIds(here.fluidSubchannels());
            int myIdx = mySubs.indexOf(selectedFluidSubchannelId);
            if (myIdx >= 0 && mySubs.size() >= 2) {
                gfx.drawString(font, Component.translatable(
                                "gui.quantumchanneling.items.priority_header", myIdx + 1, mySubs.size())
                                .withStyle(ChatFormatting.GRAY),
                        cx, topPos + CONTENT_TOP + 148, 0xFFB0B8C8, false);
            }
        }

        int n = visibleFluidSubchannels().size();
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.fluids.sub_count", n),
                cx, topPos + BG_H - 18, 0xFFB0B8C8, false);
    }

    private boolean handleFluidsPanelClick(double mx, double my) {
        if (!isFluidsModeActive() || currentChannel == null) return false;
        int slot = getFluidsSlotAt((int) mx, (int) my);
        if (slot >= 0) {
            // Cursor-holds-fluid-bucket path: capture the fluid id from the held bucket and add it
            // to the filter without consuming the cursor stack. Useful for users who pick up a
            // bucket from their inventory dock and click a filter slot.
            net.minecraft.world.item.ItemStack carried = menu.getCarried();
            if (!carried.isEmpty()) {
                net.minecraft.world.level.material.Fluid fluid = carriedFluidOf(carried);
                if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                    net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
                    if (id != null) sendAddFluid(id);
                    return true;
                }
            }
            // Empty cursor on populated slot → remove the entry.
            FluidFilter f = resolveCurrentFluidFilter();
            net.minecraft.resources.@Nullable ResourceLocation id = fluidFilterAtSlot(f, slot);
            if (id != null) sendRemoveFluid(id);
            return true;
        }
        return false;
    }

    /** Reads the fluid embedded in a carried ItemStack (filled bucket, fluid-container item, etc.). */
    private static net.minecraft.world.level.material.@Nullable Fluid carriedFluidOf(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return null;
        // Filled buckets are the common case — BucketItem exposes its Fluid directly.
        if (stack.getItem() instanceof net.minecraft.world.item.BucketItem bucket) {
            return bucket.getFluid();
        }
        // Generic fluid-container items expose IFluidHandlerItem.
        var cap = stack.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (cap != null && cap.getTanks() > 0) {
            FluidStack fs = cap.getFluidInTank(0);
            if (!fs.isEmpty()) return fs.getFluid();
        }
        return null;
    }

    /** Public ghost-drop entry point used by the JEI/EMI handlers (parity with acceptDroppedFilterItem). */
    public void acceptDroppedFilterFluid(net.minecraft.resources.ResourceLocation id, int slotIdx) {
        if (currentChannel == null || !isFluidsModeActive() || id == null) return;
        FluidFilter f = resolveCurrentFluidFilter();
        if (f == null) return;
        net.minecraft.resources.ResourceLocation existing = fluidFilterAtSlot(f, slotIdx);
        if (existing != null && existing.equals(id)) return;
        if (existing != null) sendRemoveFluid(existing);
        sendAddFluid(id);
    }

    /* ===================== Gas + Heat panels (Mekanism, single-bus v1) ===================== */

    /* --- Gas panel: full subchannel UI mirroring fluids. --- */

    public boolean isGasModeActive() {
        return activeTab == Tab.CHARGE && activeMode == ResourceMode.GASES && currentChannel != null;
    }

    private @Nullable com.quantumchanneling.channel.GasFilter resolveCurrentGasFilter() {
        if (currentChannel == null) return null;
        if (editingGasVoid) {
            ChannelInfo.MemberPos here = findThisDevice();
            if (here != null && here.type() == ChannelInfo.TYPE_EMITTER) return here.gasVoidFilter();
            return null;
        }
        if (selectedGasSubchannelId == null) return null;
        var sub = findVisibleGasSubchannel(selectedGasSubchannelId);
        return sub == null ? null : sub.filter();
    }

    private Component currentEditingGasLabel() {
        if (currentChannel == null || (selectedGasSubchannelId == null && !editingGasVoid)) {
            return Component.translatable("gui.quantumchanneling.items.no_subs");
        }
        if (editingGasVoid) return Component.translatable("gui.quantumchanneling.items.editing_void");
        var sub = findVisibleGasSubchannel(selectedGasSubchannelId);
        String name = sub == null ? "?" : (sub.name().isEmpty() ? "(unnamed)" : sub.name());
        return Component.translatable("gui.quantumchanneling.items.editing_sub", name);
    }

    private @Nullable java.util.UUID firstGasSubchannelId() {
        if (currentChannel == null) return null;
        var it = visibleGasSubchannels().iterator();
        return it.hasNext() ? it.next().id() : null;
    }

    private void ensureGasSelectionValid() {
        if (currentChannel == null) { selectedGasSubchannelId = null; editingGasVoid = false; return; }
        if (editingGasVoid) {
            if (!isCurrentEmitter()) { editingGasVoid = false; selectedGasSubchannelId = firstGasSubchannelId(); }
            return;
        }
        if (selectedGasSubchannelId != null
                && findVisibleGasSubchannel(selectedGasSubchannelId) != null) return;
        java.util.UUID first = firstGasSubchannelId();
        if (first != null) { selectedGasSubchannelId = first; editingGasVoid = false; }
        else if (isCurrentEmitter()) { editingGasVoid = true; selectedGasSubchannelId = null; }
        else { selectedGasSubchannelId = null; editingGasVoid = false; }
    }

    private void cycleGasSelection(int direction) {
        if (currentChannel == null) return;
        boolean emitter = isCurrentEmitter();
        java.util.List<Object> cycle = new java.util.ArrayList<>();
        if (emitter) cycle.add("VOID");
        for (var s : visibleGasSubchannels()) cycle.add(s.id());
        if (cycle.isEmpty()) return;
        int idx = -1;
        if (editingGasVoid) {
            for (int i = 0; i < cycle.size(); i++) if ("VOID".equals(cycle.get(i))) { idx = i; break; }
        } else if (selectedGasSubchannelId != null) {
            for (int i = 0; i < cycle.size(); i++) if (selectedGasSubchannelId.equals(cycle.get(i))) { idx = i; break; }
        }
        if (idx < 0) idx = 0;
        idx = ((idx + direction) % cycle.size() + cycle.size()) % cycle.size();
        Object chosen = cycle.get(idx);
        if ("VOID".equals(chosen)) { editingGasVoid = true; selectedGasSubchannelId = null; }
        else { editingGasVoid = false; selectedGasSubchannelId = (java.util.UUID) chosen; }
    }

    private net.minecraft.resources.@Nullable ResourceLocation gasFilterAtSlot(@Nullable com.quantumchanneling.channel.GasFilter f, int slotIdx) {
        if (f == null) return null;
        int i = 0;
        for (net.minecraft.resources.ResourceLocation id : f.gases()) { if (i == slotIdx) return id; i++; }
        return null;
    }

    private void sendAddGas(net.minecraft.resources.ResourceLocation id) {
        if (currentChannel == null) return;
        if (editingGasVoid) {
            ModMessages.sendToServer(new com.quantumchanneling.channel.AddEmitterGasVoidPacket(menu.getBlockPos(), id));
        } else if (selectedGasSubchannelId != null) {
            net.minecraft.core.BlockPos owner = gasSubchannelOwner(selectedGasSubchannelId);
            if (owner != null) {
                ModMessages.sendToServer(new com.quantumchanneling.channel.AddSubchannelGasPacket(
                        owner, selectedGasSubchannelId, id));
            }
        }
    }
    private void sendRemoveGas(net.minecraft.resources.ResourceLocation id) {
        if (currentChannel == null) return;
        if (editingGasVoid) {
            ModMessages.sendToServer(new com.quantumchanneling.channel.RemoveEmitterGasVoidPacket(menu.getBlockPos(), id));
        } else if (selectedGasSubchannelId != null) {
            net.minecraft.core.BlockPos owner = gasSubchannelOwner(selectedGasSubchannelId);
            if (owner != null) {
                ModMessages.sendToServer(new com.quantumchanneling.channel.RemoveSubchannelGasPacket(
                        owner, selectedGasSubchannelId, id));
            }
        }
    }
    private void sendToggleGasFilterMode() {
        if (currentChannel == null || editingGasVoid || selectedGasSubchannelId == null) return;
        var cur = resolveCurrentGasFilter();
        if (cur == null) return;
        net.minecraft.core.BlockPos owner = gasSubchannelOwner(selectedGasSubchannelId);
        if (owner == null) return;
        ModMessages.sendToServer(new com.quantumchanneling.channel.SetGasSubchannelFilterModePacket(
                owner, selectedGasSubchannelId, !cur.isWhitelist()));
    }

    public int getGasesSlotCount() { return ITEMS_SLOTS_VISIBLE; }

    public @Nullable net.minecraft.client.renderer.Rect2i getGasesSlotRect(int slotIdx) {
        if (!isGasModeActive() || slotIdx < 0 || slotIdx >= ITEMS_SLOTS_VISIBLE) return null;
        if (resolveCurrentGasFilter() == null) return null;
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        int gridW = ITEMS_SLOT_COLS * ITEMS_SLOT_SIZE;
        int gridX = cx + (rw - gridW) / 2;
        int gridY = topPos + CONTENT_TOP + 80;
        int col = slotIdx % ITEMS_SLOT_COLS;
        int row = slotIdx / ITEMS_SLOT_COLS;
        return new net.minecraft.client.renderer.Rect2i(
                gridX + col * ITEMS_SLOT_SIZE,
                gridY + row * ITEMS_SLOT_SIZE,
                ITEMS_SLOT_SIZE, ITEMS_SLOT_SIZE);
    }

    public int getGasesSlotAt(int x, int y) {
        if (!isGasModeActive()) return -1;
        for (int i = 0; i < ITEMS_SLOTS_VISIBLE; i++) {
            var r = getGasesSlotRect(i);
            if (r != null && x >= r.getX() && x < r.getX() + r.getWidth()
                    && y >= r.getY() && y < r.getY() + r.getHeight()) return i;
        }
        return -1;
    }

    public void acceptDroppedFilterGas(net.minecraft.resources.ResourceLocation id, int slotIdx) {
        if (currentChannel == null || !isGasModeActive() || id == null) return;
        var f = resolveCurrentGasFilter();
        if (f == null) return;
        net.minecraft.resources.ResourceLocation existing = gasFilterAtSlot(f, slotIdx);
        if (existing != null && existing.equals(id)) return;
        if (existing != null) sendRemoveGas(existing);
        sendAddGas(id);
    }

    private void buildGasPanel() {
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        boolean providerLoaded = Compat.gasProviderLoaded();
        if (currentChannel == null) return;

        ensureGasSelectionValid();
        ChannelInfo.MemberPos here = findThisDevice();
        boolean emitter = here != null && here.type() == ChannelInfo.TYPE_EMITTER;

        // Per-device participation — toggles only this emitter/receiver.
        boolean enabled = here != null && here.gasEnabled();
        PhotonButton master = PhotonButton.of(cx, topPos + CONTENT_TOP + 4, 100, 18,
                Component.translatable(enabled
                        ? "gui.quantumchanneling.gas.enabled"
                        : "gui.quantumchanneling.gas.disabled"),
                b -> ModMessages.sendToServer(new SetGasEnabledPacket(menu.getBlockPos(), !enabled)),
                this::accentColor);
        master.active = providerLoaded;
        addRenderableWidget(master);

        gasesNewSubInput = new EditBox(font, cx + 104, topPos + CONTENT_TOP + 5,
                rw - 104 - 44, 16, Component.literal(""));
        gasesNewSubInput.setHint(Component.translatable("gui.quantumchanneling.items.new_sub_hint"));
        gasesNewSubInput.setMaxLength(32);
        addRenderableWidget(gasesNewSubInput);
        addRenderableWidget(PhotonButton.of(cx + rw - 40, topPos + CONTENT_TOP + 4, 40, 18,
                Component.translatable("gui.quantumchanneling.items.new_sub_btn"),
                b -> sendCreateGasSubchannel(),
                this::accentColor));

        if (here != null && (here.type() == ChannelInfo.TYPE_EMITTER || here.type() == ChannelInfo.TYPE_RECEIVER)) {
            com.quantumchanneling.channel.DispatchStrategy curS = here.gasDispatch();
            com.quantumchanneling.channel.DispatchStrategy nextS = curS.next();
            addRenderableWidget(PhotonButton.of(cx, topPos + CONTENT_TOP + 50, 160, 18,
                    Component.translatable("gui.quantumchanneling.dispatch.label",
                            Component.translatable(curS.labelKey())),
                    b -> ModMessages.sendToServer(
                            new com.quantumchanneling.channel.SetDispatchStrategyPacket(
                                    menu.getBlockPos(), (byte) 2, (byte) nextS.ordinal())),
                    this::accentColor));
        }

        int subCount = visibleGasSubchannels().size();
        int cycleSize = subCount + (emitter ? 1 : 0);
        if (cycleSize == 0) return;
        boolean showArrows = cycleSize > 1;

        int selY = topPos + CONTENT_TOP + 28;
        int labelX = showArrows ? cx + 16 : cx;
        int labelW = showArrows ? 130 : 162;
        if (showArrows) {
            addRenderableWidget(PhotonButton.of(cx, selY, 14, 18, Component.literal("◀"),
                    b -> cycleGasSelection(-1), this::accentColor));
        }
        addRenderableWidget(PhotonButton.of(labelX, selY, labelW, 18, currentEditingGasLabel(),
                b -> cycleGasSelection(+1), this::accentColor));
        if (showArrows) {
            addRenderableWidget(PhotonButton.of(cx + 148, selY, 14, 18, Component.literal("▶"),
                    b -> cycleGasSelection(+1), this::accentColor));
        }

        var cur = resolveCurrentGasFilter();
        if (cur != null && !editingGasVoid) {
            addRenderableWidget(PhotonButton.of(cx + 166, selY, 70, 18,
                    Component.translatable(cur.isWhitelist()
                            ? "gui.quantumchanneling.items.filter_whitelist"
                            : "gui.quantumchanneling.items.filter_blacklist"),
                    b -> sendToggleGasFilterMode(), this::accentColor));
        }

        if (!emitter && selectedGasSubchannelId != null && !editingGasVoid && here != null) {
            boolean subbed = here.subscribedGasSubchannels().contains(selectedGasSubchannelId);
            java.util.UUID subId = selectedGasSubchannelId;
            addRenderableWidget(PhotonButton.of(cx + 240, selY, 60, 18,
                    Component.translatable(subbed
                            ? "gui.quantumchanneling.items.unsubscribe"
                            : "gui.quantumchanneling.items.subscribe"),
                    b -> ModMessages.sendToServer(new com.quantumchanneling.channel.SubscribeDeviceGasPacket(menu.getBlockPos(), subId, !subbed)),
                    this::accentColor));
        }

        if (selectedGasSubchannelId != null && !editingGasVoid && currentChannel != null) {
            java.util.UUID subId = selectedGasSubchannelId;
            net.minecraft.core.BlockPos owner = gasSubchannelOwner(subId);
            if (owner != null) {
                addRenderableWidget(PhotonButton.danger(cx + rw - 40, topPos + CONTENT_TOP + 160, 40, 18,
                        Component.translatable("gui.quantumchanneling.items.delete_sub_btn"),
                        b -> ModMessages.sendToServer(new com.quantumchanneling.channel.DeleteGasSubchannelPacket(owner, subId))));
            }
            if (emitter && here != null) {
                java.util.List<java.util.UUID> mySubs = orderedIds(here.gasSubchannels());
                int myIdx = mySubs.indexOf(subId);
                int total = mySubs.size();
                if (myIdx >= 0 && total >= 2) {
                    PhotonButton up = PhotonButton.of(cx, topPos + CONTENT_TOP + 160, 56, 18,
                            Component.translatable("gui.quantumchanneling.items.priority_up"),
                            b -> ModMessages.sendToServer(new com.quantumchanneling.channel.MoveDeviceGasSubchannelPacket(menu.getBlockPos(), subId, -1)),
                            this::accentColor);
                    up.active = myIdx > 0; addRenderableWidget(up);
                    PhotonButton down = PhotonButton.of(cx + 60, topPos + CONTENT_TOP + 160, 56, 18,
                            Component.translatable("gui.quantumchanneling.items.priority_down"),
                            b -> ModMessages.sendToServer(new com.quantumchanneling.channel.MoveDeviceGasSubchannelPacket(menu.getBlockPos(), subId, +1)),
                            this::accentColor);
                    down.active = myIdx < total - 1; addRenderableWidget(down);
                }
            }
        }
    }

    private void renderGasPanel(GuiGraphics gfx) {
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        int accent = accentColor();
        if (currentChannel == null) {
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.unbound")
                    .withStyle(ChatFormatting.GRAY), cx, topPos + CONTENT_TOP + 4, 0xAAAAAA, false);
            return;
        }
        boolean providerLoaded = Compat.gasProviderLoaded();
        if (!providerLoaded) {
            var lines = font.split(Component.translatable("gui.quantumchanneling.resource.gases.locked_body")
                    .withStyle(ChatFormatting.RED), rw - 8);
            int y = topPos + CONTENT_TOP + 36;
            for (var line : lines) {
                gfx.drawString(font, line, cx, y, 0xFFE08080, false);
                y += 11;
            }
            return;
        }

        ChannelInfo.MemberPos here = findThisDevice();
        boolean emitter = here != null && here.type() == ChannelInfo.TYPE_EMITTER;
        int subCount = visibleGasSubchannels().size();
        int cycleSize = subCount + (emitter ? 1 : 0);
        if (cycleSize == 0) {
            // y starts below the dispatch button row (y=50 + 18 = 68) with a small gap so the
            // helper text doesn't bleed into the button when there are zero subchannels.
            Component msg = Component.translatable("gui.quantumchanneling.items.empty_state")
                    .withStyle(ChatFormatting.GRAY);
            var lines = font.split(msg, rw - 16);
            int y = topPos + CONTENT_TOP + 78;
            for (var line : lines) {
                int w = font.width(line);
                gfx.drawString(font, line, leftPos + (BG_W - w) / 2, y, 0xFF888888, false);
                y += 11;
            }
            return;
        }

        var f = resolveCurrentGasFilter();
        if (f != null) {
            for (int i = 0; i < ITEMS_SLOTS_VISIBLE; i++) {
                var r = getGasesSlotRect(i);
                if (r == null) continue;
                int sx = r.getX(), sy = r.getY();
                net.minecraft.resources.ResourceLocation id = gasFilterAtSlot(f, i);
                boolean hover = mouseInside(sx, sy, gfx);
                int border = blendARGB(accent, 0xFF000000, 0.6f);
                int inner = 0xFF0C1018;
                gfx.fill(sx, sy, sx + ITEMS_SLOT_SIZE, sy + ITEMS_SLOT_SIZE, border);
                gfx.fill(sx + 1, sy + 1, sx + ITEMS_SLOT_SIZE - 1, sy + ITEMS_SLOT_SIZE - 1, inner);
                if (id != null) {
                    // Render the gas id as a 3-character chip label inside the slot — the actual
                    // gas icon would need Mekanism's client API. Hover tooltip shows the full id.
                    String chip = id.getPath().length() <= 4 ? id.getPath() : id.getPath().substring(0, 4);
                    gfx.drawString(font, chip, sx + 2, sy + 5, 0xFFD8E0F0, false);
                }
            }
        }

        if (emitter && here != null && !editingGasVoid && selectedGasSubchannelId != null) {
            java.util.List<java.util.UUID> mySubs = here.subscribedGasSubchannels();
            int myIdx = mySubs.indexOf(selectedGasSubchannelId);
            if (myIdx >= 0 && mySubs.size() >= 2) {
                gfx.drawString(font, Component.translatable(
                                "gui.quantumchanneling.items.priority_header", myIdx + 1, mySubs.size())
                                .withStyle(ChatFormatting.GRAY),
                        cx, topPos + CONTENT_TOP + 148, 0xFFB0B8C8, false);
            }
        }

        int n = visibleGasSubchannels().size();
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.gas.sub_count", n),
                cx, topPos + BG_H - 18, 0xFFB0B8C8, false);
    }

    /** Stub used only as a placeholder so the slot render-loop compiles without a mouse arg. */
    private static boolean mouseInside(int x, int y, GuiGraphics gfx) { return false; }

    private boolean handleGasPanelClick(double mx, double my) {
        if (!isGasModeActive() || currentChannel == null) return false;
        int slot = getGasesSlotAt((int) mx, (int) my);
        if (slot >= 0) {
            // Cursor → filter slot. Mekanism filled gas tanks (chemical tanks, canisters) expose
            // IGasHandler on their item cap; we read the first gas they contain.
            net.minecraft.world.item.ItemStack carried = menu.getCarried();
            if (!carried.isEmpty()) {
                net.minecraft.resources.ResourceLocation gasId = tryReadGasFromItem(carried);
                if (gasId != null) { sendAddGas(gasId); return true; }
            }
            var f = resolveCurrentGasFilter();
            net.minecraft.resources.@Nullable ResourceLocation id = gasFilterAtSlot(f, slot);
            if (id != null) sendRemoveGas(id);
            return true;
        }
        return false;
    }

    /** Mekanism-gated: read a Gas registry id from a carried ItemStack via the Mekanism cap path. */
    private static net.minecraft.resources.@Nullable ResourceLocation tryReadGasFromItem(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty() || !Compat.mekanismLoaded()) return null;
        return com.quantumchanneling.compat.mekanism.GasItemRead.read(stack);
    }

    private void buildHeatPanel() {
        if (currentChannel == null) return;
        int cx = leftPos + 8;
        boolean enabled = currentChannel.heatConfig().isEnabled();
        boolean locked = !Compat.heatProviderLoaded();
        PhotonButton btn = PhotonButton.of(cx, topPos + CONTENT_TOP + 4, 120, 18,
                Component.translatable(enabled
                        ? "gui.quantumchanneling.heat.enabled"
                        : "gui.quantumchanneling.heat.disabled"),
                b -> {
                    if (currentChannel != null) ModMessages.sendToServer(
                            new SetHeatEnabledPacket(currentChannel.id(), !enabled));
                }, this::accentColor);
        btn.active = !locked;
        addRenderableWidget(btn);
    }

    private void renderHeatPanel(GuiGraphics gfx) {
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        boolean locked = !Compat.heatProviderLoaded();
        if (currentChannel == null) {
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.unbound")
                    .withStyle(ChatFormatting.GRAY), cx, topPos + CONTENT_TOP + 4, 0xAAAAAA, false);
            return;
        }
        String body = locked
                ? "gui.quantumchanneling.resource.heat.locked_body"
                : "gui.quantumchanneling.heat.body";
        var lines = font.split(Component.translatable(body).withStyle(ChatFormatting.GRAY), rw - 8);
        int y = topPos + CONTENT_TOP + 36;
        for (var line : lines) {
            gfx.drawString(font, line, cx, y, locked ? 0xFFE08080 : 0xFFAAAAAA, false);
            y += 11;
        }
    }
}

