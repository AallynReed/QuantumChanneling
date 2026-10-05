#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

// White Dwarf star. A brilliant white stellar disc with churning surface granulation (convection
// cells), limb darkening toward the edge, and a soft corona + outer glow — a sun, but white-hot.
// Drawn on a camera-facing billboard for the free-floating White Dwarf item (no block model).
//
// Additive blend (LIGHTNING_TRANSPARENCY render type), so it reads as a glowing light source on
// the dark inventory background rather than a flat decal.

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

// --- cheap value noise + fbm for the surface granulation ---
float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += amp * noise(p);
        p *= 2.0;
        amp *= 0.5;
    }
    return v;
}

void main() {
    vec2 c = texCoord - vec2(0.5);
    float d = length(c) * 2.0;        // 0 at center, 1 at the billboard edge

    if (d > 1.0) discard;

    // The solid stellar disc occupies d < bodyR; a thin corona lives just beyond it. Large so the
    // opaque body dominates the billboard and the translucent glow is only a thin rim.
    float bodyR = 0.82;

    // --- surface granulation ---
    // Churn the noise domain over time so the convection cells boil. Fake a spherical bulge by
    // expanding the sampling coordinate near the limb (cells look compressed at the edges). Two
    // octave-scales are combined: coarse cells + fine speckle, both HIGH-CONTRAST so the texture
    // reads as a visibly mottled photosphere rather than a flat glow.
    float bulge = 1.0 / sqrt(max(0.04, 1.0 - pow(min(d / bodyR, 1.0), 2.0)));
    vec2 sp = c * 9.0 * bulge;
    float t = GameTime * 4500.0;

    float coarse = fbm(sp + vec2(t * 0.30, -t * 0.20));
    float fine   = fbm(sp * 2.3 + vec2(-t * 0.5, t * 0.4));
    float gran = mix(coarse, fine, 0.35);
    // Push contrast: deepen the dips between cells so the mottling is clearly visible. Range lands
    // roughly 0.30 (dark cell boundaries) .. 1.0 (bright cell centers).
    gran = pow(clamp(gran, 0.0, 1.0), 1.6);
    gran = 0.30 + 0.85 * gran;

    // Darker "sunspot" patches — a slow low-frequency layer that dims occasional regions, adding
    // depth to the surface without tinting it (still white, just darker where spots sit).
    float spots = fbm(sp * 0.35 + vec2(t * 0.1, -t * 0.08));
    float spotDarken = 1.0 - smoothstep(0.55, 0.78, spots) * 0.55;

    // Limb darkening — bright center, dimming toward the disc edge (never fully dark until bodyR).
    float limb = sqrt(max(0.0, 1.0 - pow(min(d / bodyR, 1.0), 2.0)));
    limb = 0.45 + 0.55 * limb;

    // Disc mask — drives OPACITY (alpha). Hard edge (only a ~2px feather) so the body is solidly
    // opaque right up to the limb; no broad semi-transparent fringe. The surface texture only
    // modulates brightness, never alpha, so the background never shows through the disc.
    float body = smoothstep(bodyR, bodyR - 0.02, d);

    // Photosphere brightness: limb darkening × granulation × sunspots. Floor kept high so even the
    // darkest mottling is a clearly-white opaque surface — the texture is a subtle modulation on an
    // otherwise bright white sun, not a hole.
    float photo = limb * gran * spotDarken;
    photo = 0.62 + 0.38 * photo;

    // ~1 Hz gentle breathing pulse.
    float pulse = 0.97 + 0.03 * sin(GameTime * 7500.0);
    photo *= pulse;

    // Thin corona rim just outside the disc — the only translucent part, and deliberately small so
    // the star reads as a solid body, not a faint glow.
    float corona = exp(-pow((d - bodyR) * 8.0, 2.0)) * 0.35;
    float glow   = pow(max(0.0, 1.0 - d), 4.0) * 0.10;
    float bloom  = clamp(corona + glow, 0.0, 1.0);

    // Colour: white, faint blue-white cast toward the limb (white dwarfs run very hot).
    vec3 white     = vec3(1.0, 1.0, 1.0);
    vec3 blueWhite = vec3(0.88, 0.94, 1.0);
    vec3 tint = mix(white, blueWhite, smoothstep(0.2, 1.0, d) * 0.5);

    // Composite for TRANSLUCENT (src.a, 1-src.a) blending:
    //   inside the disc (body→1): opaque textured surface, alpha = 1 (fully solid).
    //   outside (body→0): plain white corona rim at alpha = bloom.
    vec3 surfColor = tint * photo;
    vec3 finalRgb  = mix(tint, surfColor, body);
    float finalA   = max(body, bloom) * vertexColor.a;

    if (finalA < 0.012) discard;

    fragColor = vec4(finalRgb, finalA) * ColorModulator;
}
