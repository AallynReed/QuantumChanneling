package com.quantumchanneling.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.channel.ChargingMode;
import com.quantumchanneling.channel.CreateChannelPacket;
import com.quantumchanneling.channel.DeleteChannelPacket;
import com.quantumchanneling.channel.ModMessages;
import com.quantumchanneling.channel.ChannelInfo;
import com.quantumchanneling.channel.Permission;
import com.quantumchanneling.channel.RenameChannelPacket;
import com.quantumchanneling.channel.SelectChannelOnTunerPacket;
import com.quantumchanneling.channel.SetChannelChargingPacket;
import com.quantumchanneling.channel.SetChannelPermissionPacket;
import com.quantumchanneling.channel.SetChannelPublicPacket;
import com.quantumchanneling.channel.SubscribeChargingPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChannelsScreen extends Screen {
    private static final ResourceLocation BG = new ResourceLocation(QuantumChanneling.MODID, "textures/gui/channels.png");
    private static final int W = 320;
    private static final int H = 220;
    private static final int DIVIDER_X = 140;

    private List<ChannelInfo> channels;
    private @Nullable ChannelInfo selected;

    private ChannelList list;
    private EditBox createNameInput;
    private Button createButton;

    // Detail-pane widgets (only built when something is selected)
    private @Nullable EditBox renameInput;
    private @Nullable Button renameButton;
    private @Nullable Button publicButton;
    private @Nullable Button chargingButton;
    private @Nullable Button subscribeButton;
    private @Nullable EditBox addPlayerInput;
    private @Nullable Button addPlayerButton;
    private @Nullable Button addPlayerRoleButton;
    private Permission addPlayerRole = Permission.USER;
    private @Nullable Button selectButton;
    private @Nullable Button deleteButton;

    private int leftPos, topPos;

    public ChannelsScreen(List<ChannelInfo> initial) {
        super(Component.translatable("gui.quantumchanneling.channels.title"));
        this.channels = new ArrayList<>(initial);
    }

    public void refresh(List<ChannelInfo> latest) {
        this.channels = new ArrayList<>(latest);
        if (selected != null) {
            this.selected = channels.stream().filter(n -> n.id().equals(selected.id())).findFirst().orElse(null);
        }
        rebuildAll();
    }

    private void rebuildAll() {
        if (minecraft == null) return;
        clearWidgets();
        buildLeft();
        buildRight();
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - W) / 2;
        this.topPos = (this.height - H) / 2;
        rebuildAll();
    }

    private void buildLeft() {
        int listY = topPos + 22;
        int listH = H - 22 - 28;
        list = new ChannelList(minecraft, DIVIDER_X - 12, listH, listY, listY + listH, 14);
        list.setLeftPos(leftPos + 6);
        addRenderableWidget(list);

        int formY = topPos + H - 22;
        createNameInput = new EditBox(font, leftPos + 6, formY, 70, 16,
                Component.translatable("gui.quantumchanneling.channels.name_input"));
        createNameInput.setMaxLength(32);
        createNameInput.setHint(Component.translatable("gui.quantumchanneling.channels.name_hint"));
        addRenderableWidget(createNameInput);

        createButton = Button.builder(
                Component.translatable("gui.quantumchanneling.channels.create"),
                b -> tryCreate())
                .bounds(leftPos + 80, formY - 1, 50, 18).build();
        addRenderableWidget(createButton);
        createButton.active = false;
    }

    private void buildRight() {
        if (selected == null) return;
        int rx = leftPos + DIVIDER_X + 6;
        int ry = topPos + 24;
        int rw = W - DIVIDER_X - 12;

        // rename row
        renameInput = new EditBox(font, rx, ry, rw - 56, 16, Component.literal(selected.name()));
        renameInput.setValue(selected.name());
        renameInput.setMaxLength(32);
        renameInput.setEditable(selected.canManage());
        addRenderableWidget(renameInput);
        renameButton = Button.builder(Component.translatable("gui.quantumchanneling.channels.rename"),
                b -> tryRename()).bounds(rx + rw - 52, ry - 1, 52, 18).build();
        renameButton.active = selected.canManage();
        addRenderableWidget(renameButton);
        ry += 22;

        // public toggle (manage only)
        publicButton = Button.builder(
                Component.translatable(selected.isPublic()
                        ? "gui.quantumchanneling.channels.public_on"
                        : "gui.quantumchanneling.channels.public_off"),
                b -> togglePublic()).bounds(rx, ry, rw, 18).build();
        publicButton.active = selected.canManage();
        addRenderableWidget(publicButton);
        ry += 22;

        // charging mode cycle (manage only)
        chargingButton = Button.builder(chargingLabel(),
                b -> cycleCharging()).bounds(rx, ry, rw, 18).build();
        chargingButton.active = selected.canManage();
        addRenderableWidget(chargingButton);
        ry += 22;

        // subscribe-to-charging-from-this-channel for any user
        subscribeButton = Button.builder(
                Component.translatable(selected.subscribed()
                        ? "gui.quantumchanneling.channels.subscribed_on"
                        : "gui.quantumchanneling.channels.subscribed_off"),
                b -> toggleSubscribe()).bounds(rx, ry, rw, 18).build();
        addRenderableWidget(subscribeButton);
        ry += 22;

        // permissions list (text-only; click name in list to remove)
        // … players are listed in renderRight()

        // bottom: add player + role + add button (manage only)
        int bottomY = topPos + H - 40;
        addPlayerInput = new EditBox(font, rx, bottomY, rw - 80, 16, Component.literal(""));
        addPlayerInput.setHint(Component.translatable("gui.quantumchanneling.channels.player_hint"));
        addPlayerInput.setMaxLength(32);
        addPlayerInput.setEditable(selected.canManage());
        addRenderableWidget(addPlayerInput);

        addPlayerRoleButton = Button.builder(
                Component.translatable(addPlayerRole == Permission.ADMIN
                        ? "gui.quantumchanneling.channels.role.admin"
                        : "gui.quantumchanneling.channels.role.user"),
                b -> { addPlayerRole = addPlayerRole == Permission.USER ? Permission.ADMIN : Permission.USER; rebuildAll(); })
                .bounds(rx + rw - 76, bottomY - 1, 40, 18).build();
        addPlayerRoleButton.active = selected.canManage();
        addRenderableWidget(addPlayerRoleButton);

        addPlayerButton = Button.builder(Component.translatable("gui.quantumchanneling.channels.player_add"),
                b -> tryAddPlayer()).bounds(rx + rw - 34, bottomY - 1, 34, 18).build();
        addPlayerButton.active = selected.canManage();
        addRenderableWidget(addPlayerButton);

        // very bottom: select-into-tuner + delete (manage)
        int finalY = topPos + H - 20;
        selectButton = Button.builder(Component.translatable("gui.quantumchanneling.channels.select"),
                b -> trySelectIntoTuner()).bounds(rx, finalY, rw - 50, 18).build();
        addRenderableWidget(selectButton);

        deleteButton = Button.builder(Component.translatable("gui.quantumchanneling.channels.delete"),
                b -> tryDelete()).bounds(rx + rw - 46, finalY, 46, 18).build();
        deleteButton.active = selected.canManage();
        addRenderableWidget(deleteButton);
    }

    private Component chargingLabel() {
        String key = switch (selected.charging()) {
            case OFF -> "gui.quantumchanneling.channels.charging.off";
            case HAND -> "gui.quantumchanneling.channels.charging.hand";
            case HOTBAR -> "gui.quantumchanneling.channels.charging.hotbar";
            case INVENTORY -> "gui.quantumchanneling.channels.charging.inventory";
            case ARMOR -> "gui.quantumchanneling.channels.charging.armor";
            case ALL -> "gui.quantumchanneling.channels.charging.all";
        };
        return Component.translatable("gui.quantumchanneling.channels.charging", Component.translatable(key));
    }

    private void tryCreate() {
        if (selected != null) { /* keep current selection */ }
        String name = createNameInput.getValue().trim();
        if (name.isEmpty()) return;
        ModMessages.sendToServer(new CreateChannelPacket(name));
        createNameInput.setValue("");
    }

    private void tryRename() {
        if (selected == null || renameInput == null) return;
        String name = renameInput.getValue().trim();
        if (name.isEmpty()) return;
        ModMessages.sendToServer(new RenameChannelPacket(selected.id(), name));
    }

    private void togglePublic() {
        if (selected == null) return;
        ModMessages.sendToServer(new SetChannelPublicPacket(selected.id(), !selected.isPublic()));
    }

    private void cycleCharging() {
        if (selected == null) return;
        ChargingMode[] modes = ChargingMode.values();
        int next = (selected.charging().ordinal() + 1) % modes.length;
        ModMessages.sendToServer(new SetChannelChargingPacket(selected.id(), next));
    }

    private void toggleSubscribe() {
        if (selected == null) return;
        // If already subscribed to this channel, unsubscribe; otherwise subscribe.
        ModMessages.sendToServer(new SubscribeChargingPacket(
                selected.subscribed() ? SubscribeChargingPacket.NONE : selected.id()));
    }

    private void tryAddPlayer() {
        if (selected == null || addPlayerInput == null) return;
        String name = addPlayerInput.getValue().trim();
        if (name.isEmpty()) return;
        ModMessages.sendToServer(new SetChannelPermissionPacket(selected.id(), name, addPlayerRole.name()));
        addPlayerInput.setValue("");
    }

    private void removePlayer(UUID playerId, String playerName) {
        if (selected == null) return;
        ModMessages.sendToServer(new SetChannelPermissionPacket(selected.id(), playerName, ""));
    }

    private void trySelectIntoTuner() {
        if (selected == null) return;
        ModMessages.sendToServer(new SelectChannelOnTunerPacket(selected.id()));
        onClose();
    }

    private void tryDelete() {
        if (selected == null || !selected.canManage()) return;
        ModMessages.sendToServer(new DeleteChannelPacket(selected.id()));
    }

    @Override
    public void tick() {
        super.tick();
        if (createButton != null && createNameInput != null) {
            createButton.active = !createNameInput.getValue().trim().isEmpty();
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        renderBackground(gfx);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        gfx.blit(BG, leftPos, topPos, 0, 0, W, H, W, H);
        gfx.drawString(font, title, leftPos + 8, topPos + 5, 0xFFFFFF, false);
        super.render(gfx, mouseX, mouseY, partial);
        renderRight(gfx, mouseX, mouseY);
    }

    private void renderRight(GuiGraphics gfx, int mouseX, int mouseY) {
        if (selected == null) {
            gfx.drawString(font, Component.literal("Select a channel"),
                    leftPos + DIVIDER_X + 6, topPos + 30, 0xAAAAAA, false);
            return;
        }
        // Players list rendered below the existing widgets
        int rx = leftPos + DIVIDER_X + 6;
        int rw = W - DIVIDER_X - 12;
        int listY = topPos + 24 + 22 + 22 + 22 + 22 + 4;
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.channels.players")
                .withStyle(ChatFormatting.AQUA), rx, listY, 0xC8E0FF, false);
        listY += 12;
        int row = 0;
        for (ChannelInfo.PlayerEntry e : selected.players()) {
            String label = (e.permission() == Permission.ADMIN ? "★ " : "• ") + e.name();
            gfx.drawString(font, Component.literal(label),
                    rx, listY + row * 10, e.permission() == Permission.ADMIN ? 0xFFD080 : 0xFFFFFF, false);
            if (selected.canManage()) {
                int xButton = rx + rw - 14;
                int yButton = listY + row * 10;
                boolean hovering = mouseX >= xButton && mouseX < xButton + 10
                        && mouseY >= yButton && mouseY < yButton + 10;
                gfx.drawString(font, Component.literal("✕"), xButton, yButton,
                        hovering ? 0xFF6666 : 0x999999, false);
            }
            row++;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // intercept clicks on permission "✕" markers
        if (selected != null && selected.canManage()) {
            int rx = leftPos + DIVIDER_X + 6;
            int rw = W - DIVIDER_X - 12;
            int listY = topPos + 24 + 22 + 22 + 22 + 22 + 4 + 12;
            int row = 0;
            for (ChannelInfo.PlayerEntry e : selected.players()) {
                int xButton = rx + rw - 14;
                int yButton = listY + row * 10;
                if (mx >= xButton && mx < xButton + 10 && my >= yButton && my < yButton + 10) {
                    removePlayer(e.id(), e.name());
                    return true;
                }
                row++;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private class ChannelList extends ObjectSelectionList<ChannelEntry> {
        public ChannelList(net.minecraft.client.Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
            for (ChannelInfo info : channels) addEntry(new ChannelEntry(info));
            if (selected != null) {
                children().stream().filter(e -> e.info.id().equals(selected.id()))
                        .findFirst().ifPresent(this::setSelected);
            }
        }
        @Override public int getRowWidth() { return this.width - 8; }
        @Override protected int getScrollbarPosition() { return this.x1 - 6; }
    }

    private class ChannelEntry extends ObjectSelectionList.Entry<ChannelEntry> {
        final ChannelInfo info;
        ChannelEntry(ChannelInfo info) { this.info = info; }

        @Override public @NotNull Component getNarration() { return Component.literal(info.name()); }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (selected == null || !selected.id().equals(info.id())) {
                selected = info;
                rebuildAll();
            }
            list.setSelected(this);
            return true;
        }

        @Override
        public void render(GuiGraphics gfx, int idx, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partial) {
            boolean sel = selected != null && selected.id().equals(info.id());
            int color = sel ? 0xFF80E0FF : (hovered ? 0xFFCCCCCC : 0xFFFFFFFF);
            String tag = info.isPublic() ? " §a*" : "";
            gfx.drawString(font, Component.literal(info.name() + tag).withStyle(s -> s.withColor(color)),
                    left + 2, top + 3, color, false);
            String right = "(" + info.memberCount() + ")";
            gfx.drawString(font, Component.literal(right), left + width - font.width(right) - 6, top + 3, 0xAAAAAA, false);
        }
    }

    @Override
    protected void updateNarrationState(@NotNull NarrationElementOutput out) { /* no-op */ }
}
