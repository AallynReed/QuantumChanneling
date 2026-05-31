#version 150

// Gyroscope ring shader. One annular band rendered per quad. The BER spawns three of these per
// manager — each at its own 3D orientation — and rotates the orientations over time, producing
// three interlocking rings that tumble on different axes. The shader itself adds:
//   - Counter-rotating orbital bright spots so the ring's own surface looks alive even when the
//     plane isn't moving.
//   - A whole-ring breathing pulse.
//   - A soft inner glow that bleeds toward the central black-hole.
//
// Time multipliers follow the same convention as the other photon shaders:
// 1 Hz ≈ GameTime × 7540 (GameTime is normalised 0..1 over 24000 ticks = 1200 s).

in vec4 vertexColor;
in vec2 texCoord;

uniform vec4 ColorModulator;
uniform float GameTime;

out vec4 fragColor;

void main() {
    vec2 c = texCoord - vec2(0.5);
    float d = length(c) * 2.0;
    float theta = atan(c.y, c.x);

    if (d > 1.0) discard;

    // Main ring band — sharp narrow Gaussian centered at d=0.82.
    float ringSharp = exp(-pow((d - 0.82) * 30.0, 2.0));
    float ringSoft  = exp(-pow((d - 0.82) *  8.0, 2.0)) * 0.45;

    // Counter-rotating orbital bright spots — gives the ring's surface visible motion.
    float orbitA = 0.5 + 0.5 * sin(theta * 4.0 - GameTime * 50000.0);
    float orbitB = 0.5 + 0.5 * sin(theta * 7.0 + GameTime * 32000.0);
    float orbital = mix(0.55, 1.6, orbitA * orbitB);

    // Whole-ring breathing pulse at ~0.8 Hz.
    float pulse = 0.85 + 0.15 * sin(GameTime * 6000.0);

    float intensity = (ringSharp * 1.2 + ringSoft) * orbital * pulse;

    // Mix toward white at the sharp peak — gives the band a hot core. Outer glow keeps the
    // accent tint pure.
    vec3 accent = vertexColor.rgb;
    vec3 hot    = mix(accent, vec3(1.0), 0.55);
    vec3 emission = mix(accent, hot, ringSharp);

    float alpha = intensity * vertexColor.a;

    // Same alpha-discard threshold as the other photon shaders so faint outer fringe pixels
    // don't claim depth and shadow distant blocks behind the device.
    if (alpha < 0.012) discard;

    fragColor = vec4(emission * intensity, alpha) * ColorModulator;
}
