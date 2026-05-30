package com.quantumchanneling.client;

import com.quantumchanneling.channel.AddItemFilterEntryPacket;
import com.quantumchanneling.channel.ChannelInfo;
import com.quantumchanneling.channel.ChargingSlots;
import com.quantumchanneling.blockentity.ChannelBoundBlockEntity;
import com.quantumchanneling.channel.CreateChannelPacket;
import com.quantumchanneling.channel.DeleteChannelPacket;
import com.quantumchanneling.channel.ItemChannelConfig;
import com.quantumchanneling.channel.ItemFilter;
import com.quantumchanneling.channel.ItemSubchannel;
import com.quantumchanneling.channel.JoinByPinPacket;
import com.quantumchanneling.channel.ModMessages;
import com.quantumchanneling.channel.OpenChannelsRequestPacket;
import com.quantumchanneling.channel.Permission;
import com.quantumchanneling.channel.RemoteUnbindDevicePacket;
import com.quantumchanneling.channel.RemoveItemFilterEntryPacket;
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
import com.quantumchanneling.channel.SetDeviceResourceModePacket;
import com.quantumchanneling.channel.SetDeviceSubchannelPacket;
import com.quantumchanneling.channel.SetDeviceSurgePacket;
import com.quantumchanneling.channel.SetDeviceThroughputPacket;
import com.quantumchanneling.channel.SetItemEnabledPacket;
import com.quantumchanneling.channel.SetItemFilterModePacket;
import com.quantumchanneling.channel.SubscribeChargingPacket;
import com.quantumchanneling.channel.ToggleChunkLoadPacket;
import com.quantumchanneling.channel.TransferChannelOwnerPacket;
import net.minecraft.resources.ResourceLocation;
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
import java.util.List;
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

    /** Resource the side-tab strip configures the channel for. Only ENERGY is implemented. */
    public enum ResourceMode {
        ENERGY("⚡", 0xFFFFD080, "gui.quantumchanneling.resource.energy"),   // ⚡ lightning
        ITEMS ("◆", 0xFFA0C0A0, "gui.quantumchanneling.resource.items"),    // ◆ gem
        FLUIDS("≈", 0xFF80C0FF, "gui.quantumchanneling.resource.fluids"),   // ≈ waves
        GASES ("☁", 0xFFC080FF, "gui.quantumchanneling.resource.gases");    // ☁ cloud
        public final String label;
        public final int color;
        public final String labelKey;
        ResourceMode(String label, int color, String labelKey) {
            this.label = label; this.color = color; this.labelKey = labelKey;
        }
    }

    private Tab activeTab = Tab.STATUS;
    private ChannelSort channelSort = ChannelSort.NAME;
    private NodeSort nodeSort = NodeSort.POSITION;
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

    // ---- Items panel state ----
    /** Active filter target on the Items panel. 0 = main, 1 = void, 2..6 = sub-channel 0..4. */
    private int itemsTarget = 0;
    /** "Add item id" text input shown on the Items panel. */
    private @Nullable EditBox itemsFilterAddInput;

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
        this.imageHeight = BG_H;
        this.inventoryLabelY = imageHeight;
    }

    @Override
    protected void init() {
        super.init();
        ModMessages.sendToServer(new OpenChannelsRequestPacket());
        resolveCurrentChannel();
        rebuildAll();
    }

    public void onChannelsRefreshed(List<ChannelInfo> latest) {
        this.channels = new ArrayList<>(latest);
        resolveCurrentChannel();
        if (selectedInList != null) {
            this.selectedInList = channels.stream()
                    .filter(c -> c.id().equals(selectedInList.id())).findFirst().orElse(null);
        }
        rebuildAll();
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
        // Items mode owns the whole tab area regardless of the selected left-tab.
        if (activeMode == ResourceMode.ITEMS) { buildItemsPanel(); return; }
        switch (activeTab) {
            case STATUS -> buildStatusTab();
            case TUNE   -> buildTuneTab();
            case CHARGE -> buildChargeTab();
            case NODES  -> buildNodesTab();
            case STATS  -> {}
            case ACCESS -> buildAccessTab();
            case SETUP  -> buildSetupTab();
            case FORGE  -> buildForgeTab();
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

        // Type a player name, hit Enter (or click away) to add. Role toggle picks USER vs ADMIN.
        addPlayerInput = new EditBox(font, cx, bottomY, rw - 56, 16, Component.literal(""));
        addPlayerInput.setHint(Component.translatable("gui.quantumchanneling.channels.player_hint"));
        addPlayerInput.setMaxLength(32);
        addRenderableWidget(addPlayerInput);

        roleButton = PhotonButton.of(cx + rw - 52, bottomY - 1, 52, 18, roleButtonLabel(), b -> {
                    addPlayerRole = addPlayerRole == Permission.USER ? Permission.ADMIN : Permission.USER;
                    if (roleButton != null) roleButton.setMessage(roleButtonLabel());
                }, this::accentColor);
        addRenderableWidget(roleButton);
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
        renderComingSoonOverlay(gfx);
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
        // Title (block name or custom device name) — left-aligned.
        String left = menu.getDeviceName().isEmpty() ? title.getString() : menu.getDeviceName();
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
            Component label = Component.translatable(tabs[i].labelKey);
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
        for (int i = 0; i < modes.length; i++) {
            ResourceMode m = modes[i];
            int x = leftPos + BG_W;
            int y = startY + i * (SIDE_TAB_H + SIDE_TAB_GAP);
            boolean active = m == activeMode;
            boolean hover = mouseX >= x && mouseX < x + SIDE_TAB_W && mouseY >= y && mouseY < y + SIDE_TAB_H;

            // Accent border (per-mode color), then body fill. Active is brighter.
            gfx.fill(x - 1, y - 1, x + SIDE_TAB_W + 1, y + SIDE_TAB_H + 1, m.color);
            int body = active ? 0xFF1A1F2A : (hover ? 0xFF18202E : 0xFF0B0E14);
            gfx.fill(x, y, x + SIDE_TAB_W, y + SIDE_TAB_H, body);

            // Two-letter code centered.
            int labelW = font.width(m.label);
            int textColor = active ? m.color : 0xFFC0C8D0;
            gfx.drawString(font, m.label, x + (SIDE_TAB_W - labelW) / 2, y + 10, textColor, false);
        }
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

        // Dim the rest of the panel
        gfx.fill(leftPos, topPos, leftPos + BG_W, topPos + BG_H, 0xC0000000);
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

    private void renderComingSoonOverlay(GuiGraphics gfx) {
        // ENERGY uses the full tab system; ITEMS has its own end-to-end implementation in
        // renderItemsPanel — only FLUIDS / GASES still show the "Coming soon" splash.
        if (activeMode == ResourceMode.ENERGY || activeMode == ResourceMode.ITEMS) return;
        int x1 = leftPos + 6;
        int y1 = topPos + CONTENT_TOP + 2;
        int x2 = leftPos + BG_W - 6;
        int y2 = topPos + BG_H - 6;
        // Semi-opaque blocker covering the content + tab content area.
        gfx.fill(x1, y1, x2, y2, 0xE6000000);
        // Accent frame in the mode's color.
        gfx.fill(x1, y1, x2, y1 + 1, activeMode.color);
        gfx.fill(x1, y2 - 1, x2, y2, activeMode.color);
        gfx.fill(x1, y1, x1 + 1, y2, activeMode.color);
        gfx.fill(x2 - 1, y1, x2, y2, activeMode.color);
        // Headline + body.
        Component title = Component.translatable(activeMode.labelKey)
                .append(" — ")
                .append(Component.translatable("gui.quantumchanneling.resource.coming_soon_title"));
        int titleW = font.width(title);
        gfx.drawString(font, title, leftPos + (BG_W - titleW) / 2, topPos + BG_H / 2 - 12,
                activeMode.color, false);
        Component body = Component.translatable("gui.quantumchanneling.resource.coming_soon_body");
        int bodyW = font.width(body);
        gfx.drawString(font, body, leftPos + (BG_W - bodyW) / 2, topPos + BG_H / 2 + 2,
                0xFFD0D0D0, false);
    }

    /** Returns true if the click hit a side tab (and was consumed). */
    private boolean handleSideTabClick(double mx, double my) {
        ResourceMode[] modes = ResourceMode.values();
        int startY = topPos + CONTENT_TOP;
        int x = leftPos + BG_W;
        for (int i = 0; i < modes.length; i++) {
            int y = startY + i * (SIDE_TAB_H + SIDE_TAB_GAP);
            if (mx >= x && mx < x + SIDE_TAB_W && my >= y && my < y + SIDE_TAB_H) {
                if (activeMode != modes[i]) {
                    activeMode = modes[i];
                    rebuildAll();
                }
                return true;
            }
        }
        return false;
    }

    /** True when the overlay should swallow content clicks (non-ENERGY mode is active). */
    private boolean overlayBlocking() {
        // ITEMS has its own interactive panel — only FLUIDS / GASES still eat clicks.
        return activeMode != ResourceMode.ENERGY && activeMode != ResourceMode.ITEMS;
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

        // Rates — the lang keys carry two %s slots (FE/t, FE/s).
        int totalIn = currentChannel.totalInputRate();
        cy = drawChInfoFormatted(gfx, contentX, cy,
                "gui.quantumchanneling.stats.total_input", totalIn, totalIn * 20);
        int totalOut = currentChannel.totalOutputRate();
        cy = drawChInfoFormatted(gfx, contentX, cy,
                "gui.quantumchanneling.stats.total_output", totalOut, totalOut * 20);

        // Charging slots — header + one bullet per enabled group. Reads at a glance and lines up
        // visually with the Charge Activity and Players sections below.
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
                cy = drawChInfoBullet(gfx, contentX + 4, cy, "gui.quantumchanneling.charge.baubles");
        }

        // Players header + list.
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

        // Charge Activity — only players who actually drew FE last tick, tree-style with the
        // specific slots that absorbed energy and their FE/t rate.
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
        if (activeMode == ResourceMode.ITEMS) { renderItemsPanel(gfx, mouseX, mouseY); return; }
        switch (activeTab) {
            case STATUS -> renderStatus(gfx);
            case TUNE   -> renderTune(gfx, mouseX, mouseY);
            case CHARGE -> renderCharge(gfx);
            case NODES  -> renderNodes(gfx, mouseX, mouseY);
            case STATS  -> renderStats(gfx);
            case ACCESS -> renderAccess(gfx, mouseX, mouseY);
            case SETUP  -> renderSetup(gfx);
            case FORGE  -> renderForge(gfx);
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
            int throughput = menu.getThroughput();
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.throughput",
                    throughput, throughput * 20), cx, infoY, 0xC8E0FF, false);
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
            boolean on = currentChannel.charges(slotBit);
            int pr = currentChannel.slotPriority(slotBit);

            // Card body + border. Active = brighter body + accent glow border.
            int border = on ? accent : 0xFF333A48;
            int body = on ? 0xFF192030 : 0xFF0F141C;
            gfx.fill(x - 1, y - 1, x + CHARGE_CARD_W + 1, y + CHARGE_CARD_H + 1, border);
            gfx.fill(x, y, x + CHARGE_CARD_W, y + CHARGE_CARD_H, body);

            // Icon swatch (top-right corner) — colored block hinting at the slot group.
            int iconSize = 12;
            int iconX = x + CHARGE_CARD_W - iconSize - 6;
            int iconY = y + 6;
            int iconColor = on ? CHARGE_SLOT_ICON[i] : blendARGB(CHARGE_SLOT_ICON[i], 0xFF000000, 0.55f);
            gfx.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, iconColor);
            // Small accent border around the icon.
            gfx.fill(iconX - 1, iconY - 1, iconX + iconSize + 1, iconY, 0xFF000000);
            gfx.fill(iconX - 1, iconY + iconSize, iconX + iconSize + 1, iconY + iconSize + 1, 0xFF000000);
            gfx.fill(iconX - 1, iconY - 1, iconX, iconY + iconSize + 1, 0xFF000000);
            gfx.fill(iconX + iconSize, iconY - 1, iconX + iconSize + 1, iconY + iconSize + 1, 0xFF000000);

            // Title
            Component title = Component.translatable(CHARGE_SLOT_KEYS[i]);
            int titleColor = on ? 0xFFFFFFFF : 0xFFA0A8B4;
            gfx.drawString(font, title, x + 8, y + 6, titleColor, false);

            // State pill
            Component state = Component.literal(on ? "§a● Active" : "§7○ Disabled");
            gfx.drawString(font, state, x + 8, y + 20, on ? 0xFF80E0A0 : 0xFF888888, false);

            // Priority badge (bottom-left).
            String prText = "↕ P: " + pr;
            gfx.drawString(font, Component.literal(prText), x + 8, y + CHARGE_CARD_H - 12,
                    on ? 0xFFC8E0FF : 0xFF6A7280, false);
        }
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

    /* -------- Charge tab: Curios / Baubles placeholder card -------- */
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
        boolean on = curiosLoaded && currentChannel.charges(ChargingSlots.CURIOS);
        int pr = currentChannel.slotPriority(ChargingSlots.CURIOS);
        int accent = currentChannel.color();

        int border = !curiosLoaded ? 0xFF252A35
                : (on ? accent : 0xFF333A48);
        int body = !curiosLoaded ? 0xFF080C12
                : (on ? 0xFF192030 : 0xFF0F141C);
        gfx.fill(cx - 1, y - 1, cx + rw + 1, y + CURIOS_CARD_H + 1, border);
        gfx.fill(cx, y, cx + rw, y + CURIOS_CARD_H, body);

        // Title
        Component title = Component.translatable("gui.quantumchanneling.charge.baubles");
        int titleColor = !curiosLoaded ? 0xFF606878 : (on ? 0xFFFFFFFF : 0xFFA0A8B4);
        gfx.drawString(font, title, cx + 8, y + 4, titleColor, false);

        // State or "Disabled — install Curios" message
        Component state;
        int stateColor;
        if (!curiosLoaded) {
            state = Component.translatable("gui.quantumchanneling.charge.baubles.not_installed");
            stateColor = 0xFF707888;
        } else if (on) {
            state = Component.literal("§a● Active");
            stateColor = 0xFF80E0A0;
        } else {
            state = Component.literal("§7○ Disabled");
            stateColor = 0xFF888888;
        }
        gfx.drawString(font, state, cx + 8, y + CURIOS_CARD_H - 12, stateColor, false);

        // Priority badge (right side)
        String prText = "↕ P: " + pr;
        int prW = font.width(prText);
        gfx.drawString(font, Component.literal(prText), cx + rw - prW - 8, y + CURIOS_CARD_H - 12,
                !curiosLoaded ? 0xFF50586A : (on ? 0xFFC8E0FF : 0xFF6A7280), false);
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

        // Line graph.
        renderStatsGraph(gfx, cx, y, rw, 50);
        y += 50 + 3;

        // Instant FE/t • FE/s.
        int throughput = menu.getThroughput();
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.stats.instant",
                throughput, throughput * 20), cx, y, 0xC8E0FF, false);
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
        if (eb == addPlayerInput) return this::applyAddPlayer;
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
    }

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
        // Items panel has its own click logic — try it before tab routing.
        if (handleItemsPanelClick(mx, my)) {
            super.mouseClicked(mx, my, button);    // let widgets (EditBox / PhotonButton) still fire
            return true;
        }
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
                ModMessages.sendToServer(new SetChannelChargingPacket(
                        currentChannel.id(), ChargingSlots.toggle(currentChannel.chargingSlots(), slot)));
                return true;
            }
            // Curios placeholder card — only togglable when the Curios mod is loaded.
            if (Compat.curiosLoaded() && curiosCardAt(mx, my)) {
                ModMessages.sendToServer(new SetChannelChargingPacket(
                        currentChannel.id(), ChargingSlots.toggle(currentChannel.chargingSlots(), ChargingSlots.CURIOS)));
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
                int newPri = currentChannel.slotPriority(slot) + step;
                ModMessages.sendToServer(new SetChannelSlotPriorityPacket(
                        currentChannel.id(), slot, newPri));
                return true;
            }
            int armorIdx = armorPieceAt(mx, my);
            if (armorIdx >= 0) {
                int newPri = currentChannel.armorPiecePriority(armorIdx) + step;
                ModMessages.sendToServer(new SetChannelArmorPriorityPacket(
                        currentChannel.id(), armorIdx, newPri));
                return true;
            }
            if (Compat.curiosLoaded() && curiosCardAt(mx, my)) {
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

    /* ===================== Items panel ===================== */

    private static final int ITEMS_TARGET_COUNT = 7;        // 0=Main, 1=Void, 2..6=Sub 0..4

    /* ---- Inventory-style slot grid ---- */

    public static final int ITEMS_SLOT_COLS = 9;
    public static final int ITEMS_SLOT_ROWS = 3;
    public static final int ITEMS_SLOT_SIZE = 18;
    public static final int ITEMS_SLOTS_VISIBLE = ITEMS_SLOT_COLS * ITEMS_SLOT_ROWS;   // 27

    /** Used by the JEI / EMI ghost-ingredient handlers to gate registration on items-mode being open. */
    public boolean isItemsModeActive() {
        return activeMode == ResourceMode.ITEMS && currentChannel != null;
    }

    /** Number of visible filter slots (the grid is always rendered at full size). */
    public int getItemsSlotCount() { return ITEMS_SLOTS_VISIBLE; }

    /**
     * Screen-space rectangle for slot {@code slotIdx} (0..26, row-major top-left to bottom-right).
     * Returns null when items mode isn't open. Used by JEI per-slot ghost-targets and by EMI's
     * point-hit-test fallback.
     */
    public @org.jetbrains.annotations.Nullable net.minecraft.client.renderer.Rect2i getItemsSlotRect(int slotIdx) {
        if (!isItemsModeActive() || slotIdx < 0 || slotIdx >= ITEMS_SLOTS_VISIBLE) return null;
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

    /** Returns the slot index at screen coords (x, y), or -1 when not over a slot. */
    public int getItemsSlotAt(int x, int y) {
        if (!isItemsModeActive()) return -1;
        for (int i = 0; i < ITEMS_SLOTS_VISIBLE; i++) {
            var r = getItemsSlotRect(i);
            if (r != null && x >= r.getX() && x < r.getX() + r.getWidth()
                    && y >= r.getY() && y < r.getY() + r.getHeight()) return i;
        }
        return -1;
    }

    /**
     * Accepts a ghost-drop onto slot {@code slotIdx}. If the slot already holds a different item,
     * the existing entry is removed first and the new one added (effectively a "swap"). If the
     * slot is empty, the new entry is appended to the current target's filter.
     */
    public void acceptDroppedFilterItem(net.minecraft.resources.ResourceLocation id, int slotIdx) {
        if (currentChannel == null || activeMode != ResourceMode.ITEMS || id == null) return;
        ItemFilter f = resolveItemsTargetFilter();
        if (f == null) return;
        net.minecraft.resources.ResourceLocation existing = filterItemAtSlot(f, slotIdx);
        if (existing != null && existing.equals(id)) return;   // dropping the same item — no-op
        if (existing != null) {
            ModMessages.sendToServer(new RemoveItemFilterEntryPacket(
                    currentChannel.id(), itemsTarget, existing));
        }
        ModMessages.sendToServer(new AddItemFilterEntryPacket(currentChannel.id(), itemsTarget, id));
    }

    /** Returns the ResourceLocation at slot {@code slotIdx} in iteration order, or null. */
    private static @org.jetbrains.annotations.Nullable net.minecraft.resources.ResourceLocation
            filterItemAtSlot(ItemFilter f, int slotIdx) {
        if (f == null || slotIdx < 0) return null;
        int i = 0;
        for (var id : f.items()) {
            if (i == slotIdx) return id;
            i++;
        }
        return null;
    }

    private void buildItemsPanel() {
        int cx = leftPos + 8;
        int rw = BG_W - 16;
        // "Add item" text input — typing fallback for users without JEI/EMI installed. Lives
        // below the slot grid (which ends at CONTENT_TOP + 80 + 3*18 = +134).
        int addY = topPos + CONTENT_TOP + 140;
        itemsFilterAddInput = new EditBox(font, cx, addY, rw - 60, 16, Component.literal(""));
        itemsFilterAddInput.setHint(Component.translatable("gui.quantumchanneling.items.add_hint"));
        itemsFilterAddInput.setMaxLength(64);
        addRenderableWidget(itemsFilterAddInput);

        addRenderableWidget(PhotonButton.of(cx + rw - 56, addY - 1, 56, 18,
                Component.translatable("gui.quantumchanneling.items.add_btn"),
                b -> applyAddFilterEntry(), this::accentColor));

        // Per-channel master enable (top of panel).
        boolean enabled = currentChannel != null && currentChannel.itemConfig().isEnabled();
        Component enableLabel = Component.translatable(enabled
                ? "gui.quantumchanneling.items.enabled"
                : "gui.quantumchanneling.items.disabled");
        addRenderableWidget(PhotonButton.of(cx, topPos + CONTENT_TOP + 4, 110, 18, enableLabel,
                b -> {
                    if (currentChannel == null) return;
                    ModMessages.sendToServer(new SetItemEnabledPacket(currentChannel.id(),
                            !currentChannel.itemConfig().isEnabled()));
                }, this::accentColor));

        // Per-device resource-mode toggle (this screen always represents one specific device).
        ChannelInfo.MemberPos here = findThisDevice();
        com.quantumchanneling.channel.ResourceMode deviceMode = here != null
                ? here.resourceMode() : com.quantumchanneling.channel.ResourceMode.ENERGY;
        Component modeLabel = Component.translatable(
                deviceMode == com.quantumchanneling.channel.ResourceMode.ITEMS
                        ? "gui.quantumchanneling.items.device_mode_items"
                        : "gui.quantumchanneling.items.device_mode_energy");
        addRenderableWidget(PhotonButton.of(cx + 116, topPos + CONTENT_TOP + 4, 96, 18, modeLabel,
                b -> {
                    com.quantumchanneling.channel.ResourceMode next =
                            deviceMode == com.quantumchanneling.channel.ResourceMode.ITEMS
                                    ? com.quantumchanneling.channel.ResourceMode.ENERGY
                                    : com.quantumchanneling.channel.ResourceMode.ITEMS;
                    ModMessages.sendToServer(new SetDeviceResourceModePacket(menu.getBlockPos(), next));
                }, this::accentColor));

        // Whitelist/Blacklist toggle for the current target — hidden for Void (target=1) since the
        // void list is conceptually always "whitelist of items-to-discard".
        if (currentChannel != null && itemsTarget != 1) {
            ItemFilter f = resolveItemsTargetFilter();
            boolean white = f != null && f.isWhitelist();
            Component modeLbl = Component.translatable(white
                    ? "gui.quantumchanneling.items.filter_whitelist"
                    : "gui.quantumchanneling.items.filter_blacklist");
            addRenderableWidget(PhotonButton.of(cx + rw - 100, topPos + CONTENT_TOP + 60, 100, 18,
                    modeLbl,
                    b -> {
                        if (currentChannel == null) return;
                        ItemFilter cur = resolveItemsTargetFilter();
                        if (cur == null) return;
                        ModMessages.sendToServer(new SetItemFilterModePacket(currentChannel.id(),
                                itemsTarget, !cur.isWhitelist()));
                    }, this::accentColor));
        }
    }

    /** Returns the MemberPos describing the device this menu represents (or null if unbound). */
    private @Nullable ChannelInfo.MemberPos findThisDevice() {
        if (currentChannel == null) return null;
        var pos = menu.getBlockPos();
        for (var m : currentChannel.memberPositions()) {
            if (m.pos().equals(pos)) return m;
        }
        return null;
    }

    /** Returns the filter the user is currently editing on the Items panel, or null when invalid. */
    private @Nullable ItemFilter resolveItemsTargetFilter() {
        if (currentChannel == null) return null;
        ItemChannelConfig cfg = currentChannel.itemConfig();
        if (itemsTarget == 0) return cfg.mainFilter();
        if (itemsTarget == 1) return cfg.voidFilter();
        int subIdx = itemsTarget - 2;
        ItemSubchannel sub = cfg.subchannel(subIdx);
        return sub == null ? null : sub.filter();
    }

    /** "Add" button handler: parses the input, applies "minecraft:" prefix if missing, sends packet. */
    private void applyAddFilterEntry() {
        if (currentChannel == null || itemsFilterAddInput == null) return;
        String raw = itemsFilterAddInput.getValue().trim();
        if (raw.isEmpty()) return;
        if (!raw.contains(":")) raw = "minecraft:" + raw;
        ResourceLocation id;
        try { id = new ResourceLocation(raw); } catch (Exception e) { return; }
        ModMessages.sendToServer(new AddItemFilterEntryPacket(currentChannel.id(), itemsTarget, id));
        itemsFilterAddInput.setValue("");
    }

    private void renderItemsPanel(GuiGraphics gfx, int mouseX, int mouseY) {
        int cx = leftPos + 8;
        int rw = BG_W - 16;

        if (currentChannel == null) {
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.unbound")
                    .withStyle(ChatFormatting.GRAY), cx, topPos + CONTENT_TOP + 4, 0xAAAAAA, false);
            return;
        }

        // Top row labels already covered by the two PhotonButtons; nothing extra needed there.

        // Per-device sub-channel label + click-to-cycle hit box.
        ChannelInfo.MemberPos here = findThisDevice();
        int subIdx = here != null ? here.subchannelIndex() : -1;
        String subLabel = subIdx < 0 ? "Main"
                : ("Sub " + (subIdx + 1) + " — " + currentChannel.itemConfig().subchannel(subIdx).name());
        int subRowY = topPos + CONTENT_TOP + 28;
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.items.device_subchannel"),
                cx, subRowY + 4, 0xFFCCCCCC, false);
        int subPillX = cx + 110;
        int subPillW = rw - 110;
        boolean hoverSub = mouseX >= subPillX && mouseX < subPillX + subPillW
                && mouseY >= subRowY && mouseY < subRowY + 14;
        int accent = accentColor();
        gfx.fill(subPillX, subRowY, subPillX + subPillW, subRowY + 14, hoverSub ? 0xFF1F2840 : 0xFF14202E);
        gfx.fill(subPillX, subRowY, subPillX + subPillW, subRowY + 1, accent);
        gfx.drawString(font, Component.literal(subLabel), subPillX + 6, subRowY + 4, 0xFFEAF0FF, false);

        // Target selector row — 7 small pills: Main / Void / 1..5.
        int targetRowY = topPos + CONTENT_TOP + 46;
        int pillW = (rw - 6) / ITEMS_TARGET_COUNT;
        for (int i = 0; i < ITEMS_TARGET_COUNT; i++) {
            int px = cx + i * (pillW + 1);
            boolean sel = i == itemsTarget;
            boolean hover = mouseX >= px && mouseX < px + pillW && mouseY >= targetRowY && mouseY < targetRowY + 14;
            int border = sel ? accent : (hover ? blendARGB(accent, 0xFF000000, 0.4f) : 0xFF2A3140);
            int body = sel ? 0xFF1F2840 : (hover ? 0xFF18202E : 0xFF0B0E14);
            gfx.fill(px - 1, targetRowY - 1, px + pillW + 1, targetRowY + 14 + 1, border);
            gfx.fill(px, targetRowY, px + pillW, targetRowY + 14, body);
            String label = i == 0 ? "Main" : i == 1 ? "Void" : String.valueOf(i - 1);
            int lw = font.width(label);
            gfx.drawString(font, Component.literal(label),
                    px + (pillW - lw) / 2, targetRowY + 3,
                    sel ? 0xFFFFFFFF : 0xFFB0B8C8, false);
        }

        // Filter mode toggle button is rendered by buildItemsPanel via PhotonButton. We just draw
        // the section header here.
        gfx.drawString(font, targetHeaderLabel(),
                cx, topPos + CONTENT_TOP + 66, accent, false);

        // Inventory-style slot grid — each slot is a JEI / EMI ghost-drop target. Filled slots
        // render the item icon and accept clicks to remove. Empty slots accept drops to add.
        ItemFilter f = resolveItemsTargetFilter();
        ItemStack hoveredStack = null;
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
        // Tooltip for the hovered slot — rendered last so it stacks above the grid.
        if (hoveredStack != null) gfx.renderTooltip(font, hoveredStack, mouseX, mouseY);
        // Overflow indicator below the grid when more items are in the filter than the grid shows.
        int filterSize = f == null ? 0 : f.size();
        if (filterSize > ITEMS_SLOTS_VISIBLE) {
            gfx.drawString(font, Component.literal("… +" + (filterSize - ITEMS_SLOTS_VISIBLE) + " more"),
                    cx + 4, topPos + CONTENT_TOP + 80 + ITEMS_SLOT_ROWS * ITEMS_SLOT_SIZE + 2,
                    0xFF808890, false);
        }

        // Footer: buffer fill indicator + a brief hint line.
        int footerY = topPos + BG_H - 40;
        Component buf = Component.translatable("gui.quantumchanneling.items.buffer",
                currentChannel.itemBufferFill(), currentChannel.itemBufferCap());
        gfx.drawString(font, buf, cx, footerY, 0xFFB0B8C8, false);
    }

    /** Translated label for the currently-selected filter target ("Main filter", "Void list", "Sub N"). */
    private Component targetHeaderLabel() {
        if (itemsTarget == 0) return Component.translatable("gui.quantumchanneling.items.header_main");
        if (itemsTarget == 1) return Component.translatable("gui.quantumchanneling.items.header_void");
        int subIdx = itemsTarget - 2;
        String subName = currentChannel != null
                ? currentChannel.itemConfig().subchannel(subIdx).name() : "";
        return Component.translatable("gui.quantumchanneling.items.header_sub",
                subIdx + 1, subName.isEmpty() ? "—" : subName);
    }

    /** Trims a string to fit within the given pixel width, appending "…" when truncated. */
    private String trimToWidth(String s, int maxPx) {
        if (font.width(s) <= maxPx) return s;
        while (s.length() > 1 && font.width(s + "…") > maxPx) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /**
     * Handles clicks inside the Items panel. Returns true when the click is consumed
     * (a target pill, a sub-channel pill, or a row remove ✕).
     */
    private boolean handleItemsPanelClick(double mx, double my) {
        if (activeMode != ResourceMode.ITEMS || currentChannel == null) return false;
        int cx = leftPos + 8;
        int rw = BG_W - 16;

        // Sub-channel cycler row
        int subRowY = topPos + CONTENT_TOP + 28;
        int subPillX = cx + 110;
        int subPillW = rw - 110;
        if (mx >= subPillX && mx < subPillX + subPillW && my >= subRowY && my < subRowY + 14) {
            ChannelInfo.MemberPos here = findThisDevice();
            int cur = here != null ? here.subchannelIndex() : -1;
            int next = cur + 1;
            if (next >= ItemChannelConfig.SUBCHANNEL_COUNT) next = -1;
            ModMessages.sendToServer(new SetDeviceSubchannelPacket(menu.getBlockPos(), next));
            return true;
        }

        // Target selector row
        int targetRowY = topPos + CONTENT_TOP + 46;
        if (my >= targetRowY && my < targetRowY + 14) {
            int pillW = (rw - 6) / ITEMS_TARGET_COUNT;
            for (int i = 0; i < ITEMS_TARGET_COUNT; i++) {
                int px = cx + i * (pillW + 1);
                if (mx >= px && mx < px + pillW) {
                    itemsTarget = i;
                    rebuildAll();
                    return true;
                }
            }
        }

        // Click on a filled slot in the inventory-style grid → remove that entry from the filter.
        int slot = getItemsSlotAt((int) mx, (int) my);
        if (slot >= 0) {
            ItemFilter f = resolveItemsTargetFilter();
            ResourceLocation id = filterItemAtSlot(f, slot);
            if (id != null) {
                ModMessages.sendToServer(new RemoveItemFilterEntryPacket(
                        currentChannel.id(), itemsTarget, id));
            }
            // Even for empty slots we consume the click — JEI/EMI handle ghost-drops separately
            // and we don't want this click to fall through to widgets below.
            return true;
        }
        return false;
    }
}
