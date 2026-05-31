#version 150

// Black-hole event-horizon shader. Smooth dark sphere with a thin bright photon-sphere lensing
// rim. Drawn ON TOP of the accretion halo so it occludes the halo's center and overpaints with
// the bright rim at the edge.
//
// Time multipliers around 7540 = 1 cycle/sec (GameTime is 0..1 over 1200 s).

in vec4 vertexColor;
in vec2 texCoord;

uniform vec4 ColorModulator;
uniform float GameTime;

out vec4 fragColor;

float gaussianRing(float d, float peak, float sharpness) {
    float x = (d - peak) * sharpness;
    return exp(-x * x);
}

void main() {
    vec2 centered = texCoord - vec2(0.5);
    float d = length(centered) * 2.0;
    float theta = atan(centered.y, centered.x);

    if (d > 1.0) discard;

    // Dark sphere body — smooth fade from fully opaque inside ~55% to transparent at the edge.
    float core = 1.0 - smoothstep(0.55, 1.0, d);
    float wellDarken = 1.0 - 0.18 * (1.0 - d * d);
    vec3 color = vertexColor.rgb * wellDarken;
    float alpha = core * vertexColor.a;

    // Photon-sphere rim — bright thin ring where gravitational lensing piles up photons.
    float photonRing  = gaussianRing(d, 0.74, 28.0);
    float secondImage = gaussianRing(d, 0.82, 16.0);

    // Rotating bright spots along the rim — fakes the chromatic / orbiting-photon appearance.
    float orbit = sin(theta * 5.0 - GameTime * 22000.0);
    photonRing  *= 0.70 + 0.30 * (0.5 + 0.5 * orbit);
    // Second-image rim has its own slower orbit at a different phase so the two bands don't
    // lockstep.
    float orbit2 = sin(theta * 3.0 + GameTime * 14000.0);
    secondImage *= (0.55 + 0.45 * (0.5 + 0.5 * orbit2)) * 0.65;

    // ~1.2 Hz flicker on top of the orbit.
    float pulse = 0.85 + 0.15 * sin(GameTime * 9000.0);
    photonRing  *= pulse;
    secondImage *= pulse;

    vec3 rimColor = vec3(1.0);
    color = mix(color, rimColor, clamp(photonRing * 0.92 + secondImage * 0.55, 0.0, 1.0));
    alpha = max(alpha, photonRing * 0.95 + secondImage * 0.45);

    // Discard barely-visible pixels — keeps the soft fringe from claiming depth.
    if (alpha < 0.012) discard;

    fragColor = vec4(color, alpha) * ColorModulator;
}
