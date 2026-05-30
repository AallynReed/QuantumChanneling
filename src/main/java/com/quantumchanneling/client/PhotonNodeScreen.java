package com.quantumchanneling.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.quantumchanneling.QuantumChanneling;
import com.quantumchanneling.menu.PhotonNodeMenu;
import com.quantumchanneling.channel.ModMessages;
import com.quantumchanneling.channel.SetDeviceThroughputPacket;
import com.quantumchanneling.channel.ToggleChunkLoadPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class PhotonNodeScreen extends AbstractContainerScreen<PhotonNodeMenu> {
    private static final ResourceLocation BG = new ResourceLocation(QuantumChanneling.MODID, "textures/gui/photon_node.png");
    private static final int BG_W = 200;
    private static final int BG_H = 140;

    private Button chunkloadButton;
    private EditBox capInput;
    private Button capApplyButton;

    public PhotonNodeScreen(PhotonNodeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = BG_W;
        this.imageHeight = BG_H;
        this.inventoryLabelY = imageHeight;
    }

    @Override
    protected void init() {
        super.init();
        int rowY = topPos + BG_H - 70;
        capInput = new EditBox(font, leftPos + 10, rowY, 100, 16, Component.literal(""));
        capInput.setHint(Component.translatable("gui.quantumchanneling.cap.hint"));
        capInput.setMaxLength(11);
        capInput.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        int initialCap = menu.getThroughputCap();
        if (initialCap >= 0) capInput.setValue(Integer.toString(initialCap));
        addRenderableWidget(capInput);

        capApplyButton = Button.builder(Component.translatable("gui.quantumchanneling.cap.set"),
                b -> applyCap()).bounds(leftPos + 114, rowY - 1, 60, 18).build();
        addRenderableWidget(capApplyButton);

        chunkloadButton = Button.builder(chunkloadLabel(), btn ->
                ModMessages.sendToServer(new ToggleChunkLoadPacket(menu.getBlockPos(), !menu.isChunkLoaded())))
                .bounds(leftPos + 10, topPos + BG_H - 28, BG_W - 20, 20).build();
        addRenderableWidget(chunkloadButton);
    }

    private void applyCap() {
        String s = capInput.getValue().trim();
        int cap;
        if (s.isEmpty()) cap = -1;
        else {
            try { cap = Integer.parseInt(s); }
            catch (NumberFormatException ex) { cap = Integer.MAX_VALUE; }
        }
        ModMessages.sendToServer(new SetDeviceThroughputPacket(menu.getBlockPos(), cap));
    }

    private Component chunkloadLabel() {
        return Component.translatable(menu.isChunkLoaded()
                ? "gui.quantumchanneling.chunkload.on"
                : "gui.quantumchanneling.chunkload.off");
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partial);
        renderTooltip(gfx, mouseX, mouseY);
        if (chunkloadButton != null) chunkloadButton.setMessage(chunkloadLabel());
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partial, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        gfx.blit(BG, leftPos, topPos, 0, 0, BG_W, BG_H, BG_W, BG_H);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFFF, false);
        int y = 22;
        int throughput = menu.getThroughput();
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.throughput", throughput, throughput * 20),
                8, y, 0xC8E0FF, false);
        y += 14;
        if (menu.isChannelBound()) {
            String name = menu.getChannelName();
            Component line = name.isEmpty()
                    ? Component.translatable("gui.quantumchanneling.channel.bound").withStyle(ChatFormatting.AQUA)
                    : Component.translatable("gui.quantumchanneling.channel.name", name).withStyle(ChatFormatting.AQUA);
            gfx.drawString(font, line, 8, y, 0xFFFFFF, false);
            y += 12;
            String owner = menu.getChannelOwner();
            if (!owner.isEmpty()) {
                gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.owner", owner)
                        .withStyle(ChatFormatting.GRAY), 8, y, 0xAAAAAA, false);
            }
        } else {
            gfx.drawString(font, Component.translatable("gui.quantumchanneling.channel.unbound")
                    .withStyle(ChatFormatting.GRAY), 8, y, 0xAAAAAA, false);
        }
        // Cap label above the editbox
        int capLabelY = BG_H - 70 - 12;
        gfx.drawString(font, Component.translatable("gui.quantumchanneling.cap.label"),
                10, capLabelY, 0xAAAAAA, false);
    }
}
