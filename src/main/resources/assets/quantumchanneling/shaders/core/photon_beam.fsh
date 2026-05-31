#version 150

// Beam shader — tapered glowing tube from the ball edge to a face port. UV layout:
//   u (texCoord.x) runs along the beam (0 = ball end on receivers, 1 = face end; flipped for
//     emitters via the renderer so the scroll direction reads as inward instead of outward)
//   v (texCoord.y) runs perpendicular (0 / 1 at the edges, 0.5 on the axis)
//
// Gaussian perpendicular intensity tapers narrower toward the face end — fakes a tapered tube
// without geometric narrowing. Scrolling sin pattern along u + slow envelope on top gives a
// visible "flowing energy" effect.
//
// Time multipliers: a wave sin(k*u - ω*t) traverses u=0..1 in T seconds at ω = k/T. For the
// carrier wave (k = 22, T = 0.35 s): ω = 63 rad/sec → GameTime multiplier = 63 × 1200 ≈ 75600.

in vec4 vertexColor;
in vec2 texCoord;

uniform vec4 ColorModulator;
uniform float GameTime;

out vec4 fragColor;

void main() {
    float u = texCoord.x;
    float v = texCoord.y;
    float dPerp = abs(v - 0.5) * 2.0;

    if (u < 0.0 || u > 1.0 || dPerp > 1.0) discard;

    // Wider Gaussian near the ball, narrower toward the face — visual taper. Aggressive 1.0 →
    // 0.40 falloff makes the beam fit through the 2x2 ring hollow and port frame even with the
    // wider geometric half-width (BEAM_HALF_WIDTH = 0.13 in the renderer).
    float widthFactor = mix(1.0, 0.40, u);
    float dNorm = dPerp / widthFactor;
    if (dNorm > 1.0) discard;

    // Three perpendicular intensity bands.
    float core = exp(-pow(dNorm * 4.5, 2.0));
    float body = exp(-pow(dNorm * 2.0, 2.0));
    float halo = exp(-pow(dNorm * 1.0, 2.0)) * 0.40;

    // Fade at both endpoints so the beam plugs into the accretion ring at u≈0 and tapers into
    // the port at u≈1.
    float taperLen    = 1.0 - smoothstep(0.78, 1.0, u);
    float taperOrigin = smoothstep(0.0, 0.08, u);

    // Flowing pattern along u. Carrier wave traverses u=0..1 in ~0.3 sec (fast visible flow),
    // envelope traverses in ~2 sec (slower modulation gives the carrier varying intensity).
    float carrier  = 0.5 + 0.5 * sin(u * 22.0 - GameTime * 80000.0);
    float envelope = 0.6 + 0.4 * sin(u *  5.5 - GameTime * 13000.0);
    float flow = mix(0.55, 1.0, carrier * envelope);

    // ~1 Hz breathing pulse.
    float pulse = 0.85 + 0.15 * sin(GameTime * 7500.0);

    vec3 accent   = vertexColor.rgb;
    vec3 hotColor = mix(accent, vec3(1.0), 0.72);

    vec3 emission = hotColor * (core * 1.45)
                  + accent   * (body * 1.05 + halo);
    emission *= flow * pulse * taperLen * taperOrigin;

    float alpha = (core * 0.95 + body * 0.65 + halo * 0.45)
                  * vertexColor.a * flow * pulse * taperLen * taperOrigin;

    // Same discard threshold as the central shaders — faint pixels don't claim depth.
    if (alpha < 0.012) discard;

    fragColor = vec4(emission, alpha) * ColorModulator;
}
