#version 150

// Phase-2 implosion vortex. Reads as matter being vacuumed into a forming singularity:
//   - A set of "stretched balls" (blobs) spiral inward on a loop. Each blob elongates radially
//     (spaghettification) the closer it gets to the centre, and accelerates as it falls, vanishing
//     into the central void at high speed.
//   - A logarithmic-spiral swirl rotates inward underneath the blobs, the vacuum funnel.
//   - A pitch-black void core where everything collapses.
//
// Palette is black → dark-purple. The render type uses TRANSLUCENT (alpha) blending, NOT additive,
// so these dark colours DARKEN the framebuffer — the implosion sucks light out of the scene.
//
// The per-blob inward motion is driven by GameTime on a fract() loop with per-blob phase offsets,
// so at any instant the blobs are spread across all stages of the fall — a continuous stream that
// doesn't need the effect's start time. The Java side handles the overall fade in/out.

in vec4 vertexColor;
in vec2 texCoord;

uniform vec4 ColorModulator;
uniform float GameTime;

out vec4 fragColor;

const int NUM_BLOBS = 7;
const float TAU = 6.28318530718;

void main() {
    vec2 c = texCoord - vec2(0.5);
    float r = length(c) * 2.0;
    float theta = atan(c.y, c.x);

    if (r > 1.0) discard;

    float darkness = 0.0;

    // ---- vacuum swirl ----
    // Logarithmic spiral rotating inward. +k*log(r) makes the arms curve; the -GameTime term
    // spins them toward the centre.
    float k = 5.0;
    float spiralPhase = theta + k * log(max(r, 0.04)) - GameTime * 42000.0;
    float swirl = 0.5 + 0.5 * sin(spiralPhase * 3.0);
    swirl = smoothstep(0.45, 1.0, swirl);
    swirl *= exp(-pow((r - 0.5) * 2.4, 2.0));   // strongest mid-radius
    darkness = max(darkness, swirl * 0.55);

    // ---- stretched blobs falling in ----
    for (int i = 0; i < NUM_BLOBS; i++) {
        float fi = float(i);

        // Inward progress, looping. Each blob is phase-offset so the stream is continuous.
        float prog = fract(GameTime * 9000.0 + fi * 0.1377);

        // Radius: edge (1.0) → centre (0.0), accelerating. d(blobR)/d(prog) = -2*prog, so the
        // blob speeds up as it nears the centre — "sucked at high speed."
        float blobR = 1.0 - prog * prog;

        // Angle: fixed slot per blob, plus an inward spiral twist as it falls (more twist closer
        // to the centre) so the blobs corkscrew into the void instead of falling straight.
        float blobTheta = fi * TAU / float(NUM_BLOBS) + (1.0 - blobR) * 2.4;

        // Angular + radial deltas (angle wrapped to -pi..pi).
        float dTheta = theta - blobTheta;
        dTheta = atan(sin(dTheta), cos(dTheta));
        float dr = r - blobR;

        // Spaghettification: radial extent grows toward the centre (stretchier), tangential
        // extent shrinks (thinner) — the blob is drawn out into a streak pointing at the core.
        float pull = 1.0 - blobR;                     // 0 at edge, 1 at centre
        float sigmaR = 0.10 * mix(1.0, 7.0, pull);    // radial: 0.10 → 0.70
        float sigmaT = 0.16 * mix(1.0, 0.35, pull);   // tangential: 0.16 → 0.056

        float blob = exp(-pow(dr / sigmaR, 2.0) - pow(dTheta * max(r, 0.12) / sigmaT, 2.0));

        // Fade the blob in just inside the edge, and out as it reaches the void.
        float blobFade = smoothstep(1.0, 0.88, blobR) * smoothstep(0.0, 0.12, blobR);
        darkness = max(darkness, blob * blobFade);
    }

    // ---- central void ----
    float voidMask = 1.0 - smoothstep(0.0, 0.26, r);
    darkness = max(darkness, voidMask);

    // ---- soft outer edge ----
    float outerFade = 1.0 - smoothstep(0.72, 1.0, r);
    darkness *= outerFade;

    // Colour: dark-purple in the structures (blobs + swirl), pitch black in the void core.
    vec3 darkPurple = vec3(0.16, 0.03, 0.26);
    vec3 black      = vec3(0.0);
    vec3 color = mix(black, darkPurple, darkness * (1.0 - voidMask));
    color = mix(color, black, voidMask);

    // A faint ambient darkening across the whole disc so the region visibly dims even between
    // the blobs — light being drawn away from the area.
    float ambient = exp(-pow(r * 1.7, 2.0)) * 0.45 * outerFade;
    float alpha = max(darkness, ambient) * vertexColor.a;

    if (alpha < 0.012) discard;

    fragColor = vec4(color, alpha) * ColorModulator;
}
