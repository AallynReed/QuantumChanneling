#version 150

// Accretion-disk + gravitational-lensing shader. Five intensity bands stacked into a single
// analytical radial gradient:
//   - photon-sphere peak (super-narrow super-bright white) — primary lensed light
//   - hot accretion edge (narrow, near-white) — disk material closest to the horizon
//   - main accretion ring (broad, accent color) — the disk's brightest body
//   - Einstein-ring secondary (very thin, accent) — light from the far side of the disk
//     bent around the hole; the giveaway visual cue of gravitational lensing
//   - atmospheric outer glow (exponential falloff) — diffuse halo around the system
//
// Time multipliers are scaled around 1 Hz ≈ GameTime × 7540 (GameTime is normalised 0..1 over
// 24000 ticks = 1200 s, so phase change ω rad/sec = multiplier/1200). Anything below ~3000 is
// barely perceptible motion. Additive blending — each pixel's RGB is added to the framebuffer.

in vec4 vertexColor;
in vec2 texCoord;

uniform vec4 ColorModulator;
uniform float GameTime;

out vec4 fragColor;

// Narrow Gaussian peak centered at {@code peak}. Larger sharpness = thinner band.
float gaussianBand(float d, float peak, float sharpness) {
    float x = (d - peak) * sharpness;
    return exp(-x * x);
}

void main() {
    vec2 centered = texCoord - vec2(0.5);
    float d = length(centered) * 2.0;
    // Angle around the center — used to add per-direction phase to the shimmer so the rings
    // rotate visibly instead of just twinkling in place.
    float theta = atan(centered.y, centered.x);

    if (d > 1.0) discard;

    // Five-band lensing stack.
    float photon   = gaussianBand(d, 0.28, 34.0);   // primary photon sphere
    float hot      = gaussianBand(d, 0.34, 18.0);   // hot accretion edge
    float ring     = gaussianBand(d, 0.44,  6.5);   // main accretion ring
    float einstein = gaussianBand(d, 0.58, 26.0);   // Einstein-ring secondary
    float halo     = pow(max(0.0, 1.0 - d), 3.2) * 0.55;

    // Animated shimmer + rotation on the thin lensing bands. Phase = theta * N - t * ω makes
    // the bright spots appear to orbit the hole at ω/N rad/sec.
    float shimmerPhoton   = sin(theta * 6.0 - GameTime * 30000.0 + d * 14.0);
    float shimmerEinstein = sin(theta * 4.0 - GameTime * 18000.0 + d * 9.0);
    photon   *= 0.70 + 0.30 * (0.5 + 0.5 * shimmerPhoton);
    einstein *= 0.55 + 0.45 * (0.5 + 0.5 * shimmerEinstein);

    // Main accretion ring rotates more slowly — represents orbiting disk material. Theta * 3
    // gives three visible bright spots circling once per ~3 seconds.
    float diskFlow = sin(theta * 3.0 - GameTime * 12000.0);
    ring *= 0.78 + 0.22 * (0.5 + 0.5 * diskFlow);

    // Whole-system breathing pulse at ~0.6 Hz.
    float pulse = 0.88 + 0.12 * sin(GameTime * 4500.0);

    // Color mixing: photon + hot peaks lean almost pure white; ring + einstein + halo keep the
    // accent tint.
    vec3 accent   = vertexColor.rgb;
    vec3 hotColor = mix(accent, vec3(1.0), 0.78);
    vec3 whiteish = mix(accent, vec3(1.0), 0.90);

    vec3 emission = whiteish * (photon * 1.35)
                  + hotColor * (hot    * 1.10)
                  + accent   * (ring   * 1.60 + einstein * 1.20 + halo);
    emission *= pulse;

    float alpha = (photon * 0.95 + hot * 0.85 + ring * 0.80 + einstein * 0.75 + halo * 0.55)
                  * vertexColor.a * pulse;

    // Discard barely-visible pixels so they don't claim depth — keeps the soft outer fringe from
    // blocking distant blocks behind the device through the depth buffer.
    if (alpha < 0.012) discard;

    fragColor = vec4(emission, alpha) * ColorModulator;
}
