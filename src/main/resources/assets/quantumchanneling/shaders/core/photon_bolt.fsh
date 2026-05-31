#version 150

// Lightning-bolt shader. The BER draws eight tubes per manager — one from the central orb to
// each corner vault — and this shader paints a jagged strobing bolt along each tube's u axis.
// Two perpendicular tube quads per bolt give it volume from any view angle, like photon_beam.
//
// UV layout:
//   u (texCoord.x) — 0 at the central orb, 1 at the corner vault.
//   v (texCoord.y) — 0..1 perpendicular; bolt centerline wanders inside this range.
//
// Per-bolt phase comes in through {@code vertexColor.a} (0..1 mapped to 0..2π). Each of the
// eight bolts gets a distinct alpha so they never strobe in lockstep — the structure looks like
// real electricity arcing rather than a synchronised pulse.
//
// Color: {@code vertexColor.rgb} is the accent tint (manager violet by default); the shader
// blends toward white at the bolt's hot core so the bolt reads as "violet electricity with a
// white-hot center."
//
// Time multipliers: 1 Hz ≈ GameTime × 7540. The carrier waves (≥ 80000) are intentionally above
// perceptual fusion so the jagged centerline visibly moves frame-to-frame.

in vec4 vertexColor;
in vec2 texCoord;

uniform vec4 ColorModulator;
uniform float GameTime;

out vec4 fragColor;

void main() {
    float u = texCoord.x;
    float v = texCoord.y;

    if (u < 0.0 || u > 1.0 || v < 0.0 || v > 1.0) discard;

    float phase = vertexColor.a * 6.2832;   // 0..2π

    // Phase-modulator — a slow wandering signal that warps the timing of the carrier waves
    // below so they no longer repeat at a fixed rhythm. The carrier frequencies are
    // deliberately non-integer and mutually irrational so they never realign exactly; the
    // modulator amplifies the apparent randomness.
    float pm = sin(u * 2.7 + GameTime * 6500.0 + phase * 0.9);

    // Jagged centerline. Frequencies (11.3 / 36.7 / 61.1) are non-integer so the wave pattern
    // along u doesn't snap to repeating grid spacing. Time multipliers are roughly half of the
    // previous tuning — slower visible motion, more "menacing arc" than "fast electrical hum."
    float centerline = 0.5
        + 0.20 * sin(u * 11.3 + GameTime *  40000.0 + phase        + pm * 1.4)
        + 0.10 * sin(u * 36.7 + GameTime *  65000.0 + phase * 1.7  + pm * 0.8)
        + 0.05 * sin(u * 61.1 - GameTime * 100000.0 + phase * 2.3  + pm * 0.5);

    // Half-width tapers from the orb (thicker) to the vault (thinner) so the bolt reads as
    // "spreading from the central hole." Both ends bumped up — the user found the previous bolt
    // too thin; doubling at the corner end (0.022 → 0.045) is the bigger visual change since the
    // corner side reads as the bolt's "tip."
    float halfWidth = mix(0.11, 0.045, u);
    float distFromLine = abs(v - centerline);

    // Two intensity bands: a tight bright core and a softer body for the glow halo.
    float core = exp(-pow((distFromLine / halfWidth) * 3.2, 2.0));
    float body = exp(-pow((distFromLine / halfWidth) * 1.1, 2.0)) * 0.45;

    // Strobe — sum of two incommensurate sines. A single sine produces an obviously periodic
    // flicker; sum of two with non-integer-ratio frequencies (ratio ≈ 1.53) gives a beating
    // pattern with a long apparent period, reading as "random arc events" rather than
    // "metronome." Time multipliers are 1/4 the previous tuning — events fire roughly 4× less
    // often, giving the manager a "calm idle with occasional arc" rhythm instead of constant
    // activity. (Carrier-wave centerline animation above is unchanged so each fire still has
    // visibly jittery shape when it does happen.)
    float strobeA = sin(GameTime * 3750.0 + phase * 3.0);
    float strobeB = sin(GameTime * 5750.0 + phase * 1.7);
    float strobeRaw = strobeA * 0.6 + strobeB * 0.4;
    // Tighter smoothstep window — with the slower oscillators the lower tail of each peak would
    // be visible for a long time and "smear" the events together. Raising the bottom threshold
    // hides everything except the peak third of each cycle, so each arc reads as a discrete
    // event with clear dark gaps between fires.
    float strobe = smoothstep(0.35, 0.92, strobeRaw);

    // Soft fade at both endpoints so the bolt doesn't terminate sharply at the orb or vault.
    float endFade = smoothstep(0.0, 0.06, u) * smoothstep(1.0, 0.93, u);

    float intensity = (core + body) * strobe * endFade;

    vec3 accent = vertexColor.rgb;
    vec3 color  = mix(accent, vec3(1.0), core);    // hot white core, accent at the fringe

    if (intensity < 0.012) discard;

    fragColor = vec4(color * intensity, intensity) * ColorModulator;
}
