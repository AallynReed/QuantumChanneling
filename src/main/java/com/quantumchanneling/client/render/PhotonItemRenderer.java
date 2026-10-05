package com.quantumchanneling.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * The photon shader effect on items, as a special model ({@code quantumchanneling:photon_effect}).
 * Device items layer it over their block model in a composite item model, so the black hole shows
 * in the hotbar, inventory, item frames, hands and on the ground — not just in the world. The
 * Uncontained Black Hole and White Dwarf are the effect alone, with no block under them.
 *
 * <p>Beams aren't drawn here — they only mean something on a placed device with connected
 * neighbours.
 */
public class PhotonItemRenderer implements NoDataSpecialModelRenderer {
    /** White Dwarf billboard — a touch larger so the sun fills the slot. */
    private static final float STAR_QUAD_HALF = 0.40f;
    /** Accent for the free-floating Uncontained Black Hole — gold. */
    private static final int BLACK_HOLE_RGB = 0xFFD060;

    private final Effect effect;

    public PhotonItemRenderer(Effect effect) {
        this.effect = effect;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
                       boolean hasFoil, int outlineColor) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        if (effect == Effect.WHITE_DWARF) {
            collector.submitCustomGeometry(poseStack, PhotonRenderTypes.PHOTON_WHITE_DWARF,
                    (pose, vc) -> PhotonOrb.draw(pose, vc, STAR_QUAD_HALF, 255, 255, 255, 255, 0.0f));
        } else {
            PhotonNodeRenderer.submitOrb(collector, poseStack, effect.accent(), effect == Effect.MANAGER, seconds());
        }
        poseStack.popPose();
    }

    /** Drives the manager's ring tumble. Without a level (main menu, recipe viewers) the world clock
     *  is frozen, so fall back to the wall clock rather than leave the rings stuck. */
    private static float seconds() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Util.getMillis() / 1000.0f;
        return (mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20.0f;
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0.0f, 0.0f, 0.0f));
        output.accept(new Vector3f(1.0f, 1.0f, 1.0f));
    }

    public enum Effect implements StringRepresentable {
        EMITTER("emitter", PhotonAccent.EMITTER),
        RECEIVER("receiver", PhotonAccent.RECEIVER),
        MANAGER("manager", PhotonAccent.MANAGER),
        STORAGE_1("storage_1", PhotonAccent.STORAGE[0]),
        STORAGE_2("storage_2", PhotonAccent.STORAGE[1]),
        STORAGE_3("storage_3", PhotonAccent.STORAGE[2]),
        STORAGE_4("storage_4", PhotonAccent.STORAGE[3]),
        STORAGE_5("storage_5", PhotonAccent.STORAGE[4]),
        BLACK_HOLE("black_hole", BLACK_HOLE_RGB),
        WHITE_DWARF("white_dwarf", 0xFFFFFF);

        public static final Codec<Effect> CODEC = StringRepresentable.fromEnum(Effect::values);

        private final String name;
        private final int accent;

        Effect(String name, int accent) {
            this.name = name;
            this.accent = accent;
        }

        public int accent() { return accent; }

        @Override
        public String getSerializedName() { return name; }
    }

    public record Unbaked(Effect effect) implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Effect.CODEC.fieldOf("effect").forGetter(Unbaked::effect)
        ).apply(i, Unbaked::new));

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            return new PhotonItemRenderer(effect);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
