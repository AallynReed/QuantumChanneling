#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

// Phase-1 supernova flash. A white-hot core blooming into a white/gold light shell with a thin
// bright shockwave ring near the outer edge and faint radial filaments. The macro expansion is
// driven by the Java side scaling the billboard; this shader supplies the brilliance + texture.
//
// Additive blend (the render type uses LIGHTNING_TRANSPARENCY), so every channel adds to the
// framebuffer — the effect reads as blinding light, not a coloured decal.
//
// Time multipliers follow the house convention: 1 Hz ≈ GameTime × 7540.

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 c = texCoord - vec2(0.5);
    float r = length(c) * 2.0;
    float theta = atan(c.y, c.x);

    if (r > 1.0) discard;

    // Brilliant white core — tight, very bright.
    float core = exp(-pow(r * 2.4, 2.0));

    // Broad body glow filling out to the radius.
    float body = exp(-pow(r * 1.25, 2.0)) * 0.75;

    // Shockwave ring — a thin bright band near the outer edge that reinforces the "spreading to
    // the radius" reading as the billboard scales up.
    float ring = exp(-pow((r - 0.78) * 7.0, 2.0)) * 0.65;

    // Radial filaments — faint bright rays for surface texture so the flash isn't a flat disc.
    // A slow rotation keeps them alive without looking busy.
    float rays = 0.5 + 0.5 * sin(theta * 18.0 - GameTime * 9000.0);
    rays = pow(rays, 4.0) * exp(-pow(r * 1.4, 2.0)) * 0.35;

    float intensity = core * 1.6 + body + ring + rays;

    // Pure white — every band, every radius. No tint. The supernova flash is blinding white light.
    vec3 color = vec3(1.0, 1.0, 1.0);

    float alpha = clamp(intensity, 0.0, 1.0) * vertexColor.a;
    if (alpha < 0.012) discard;

    fragColor = vec4(color * intensity, alpha) * ColorModulator;
}
