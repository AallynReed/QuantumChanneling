package com.quantumchanneling.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;

/**
 * Custom-rendered {@link Button} variant that matches the dark-panel + accent-border styling of
 * the rest of the screen. Body fill darkens on hover; the border uses the per-channel accent
 * color (lazily resolved through {@code accentSupplier}) when active, a dim grey when disabled.
 *
 * <p>Tone variants let specific buttons stand out semantically:
 * {@link Tone#NEUTRAL} (default), {@link Tone#DANGER} (red border, used for Disconnect / Delete),
 * {@link Tone#PRIMARY} (always-accent border, used for primary actions like "Forge channel").</p>
 */
public class PhotonButton extends Button {
    public enum Tone { NEUTRAL, PRIMARY, DANGER }

    private final IntSupplier accentSupplier;
    private final Tone tone;

    public PhotonButton(int x, int y, int w, int h, Component msg, OnPress onPress,
                        IntSupplier accentSupplier, Tone tone) {
        super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
        this.accentSupplier = accentSupplier;
        this.tone = tone;
    }

    public static PhotonButton of(int x, int y, int w, int h, Component msg, OnPress onPress,
                                  IntSupplier accentSupplier) {
        return new PhotonButton(x, y, w, h, msg, onPress, accentSupplier, Tone.NEUTRAL);
    }

    public static PhotonButton primary(int x, int y, int w, int h, Component msg, OnPress onPress,
                                       IntSupplier accentSupplier) {
        return new PhotonButton(x, y, w, h, msg, onPress, accentSupplier, Tone.PRIMARY);
    }

    public static PhotonButton danger(int x, int y, int w, int h, Component msg, OnPress onPress) {
        return new PhotonButton(x, y, w, h, msg, onPress, () -> 0xFFE05050, Tone.DANGER);
    }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int accent = accentSupplier.getAsInt();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = isHoveredOrFocused() && active;
        boolean enabled = active;

        // Border color picks based on tone and state.
        int border;
        switch (tone) {
            case PRIMARY -> border = enabled ? accent : 0xFF2A3140;
            case DANGER  -> border = enabled ? (hover ? 0xFFFF7878 : 0xFFE05050) : 0xFF402020;
            default      -> border = enabled
                    ? (hover ? accent : blendARGB(accent, 0xFF000000, 0.55f))
                    : 0xFF2A3140;
        }
        int body = enabled
                ? (hover ? 0xFF1F2840 : 0xFF14202E)
                : 0xFF0A0E14;

        // Outer border + inner body.
        gfx.fill(x - 1, y - 1, x + w + 1, y + h + 1, border);
        gfx.fill(x, y, x + w, y + h, body);

        // 1-px brighter top edge gives a subtle highlight that reads as "raised".
        if (enabled) {
            gfx.fill(x, y, x + w, y + 1, blendARGB(body, 0xFFFFFFFF, 0.18f));
        }

        // Label centered. Color tracks the tone.
        var font = Minecraft.getInstance().font;
        int textColor;
        switch (tone) {
            case DANGER  -> textColor = enabled ? 0xFFFFE0E0 : 0xFF806060;
            case PRIMARY -> textColor = enabled ? 0xFFFFFFFF : 0xFF7088A0;
            default      -> textColor = enabled ? 0xFFEAF0FF : 0xFF666E80;
        }
        Component msg = getMessage();
        int textW = font.width(msg);
        int textX = x + (w - textW) / 2;
        int textY = y + (h - 8) / 2;
        gfx.drawString(font, msg, textX, textY, textColor, false);
    }

    private static int blendARGB(int a, int b, float t) {
        int aA = (a >> 24) & 0xFF, aR = (a >> 16) & 0xFF, aG = (a >> 8) & 0xFF, aB = a & 0xFF;
        int bA = (b >> 24) & 0xFF, bR = (b >> 16) & 0xFF, bG = (b >> 8) & 0xFF, bB = b & 0xFF;
        int rA = Math.round(aA + (bA - aA) * t);
        int rR = Math.round(aR + (bR - aR) * t);
        int rG = Math.round(aG + (bG - aG) * t);
        int rB = Math.round(aB + (bB - aB) * t);
        return (rA << 24) | (rR << 16) | (rG << 8) | rB;
    }
}
