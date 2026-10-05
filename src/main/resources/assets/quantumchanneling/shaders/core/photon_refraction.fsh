#version 150

// Phase-1 stray light beams. White-cored rays with a faint rainbow fringe along their edges, as
// if the beam were passing through a prism (chromatic dispersion). Applied to the camera-aligned
// ribbon strips the renderer builds radiating out from the supernova.
//
// UV layout (from the ribbon geometry):
//   u = texCoord.x — along the beam, 0 at the core end, 1 at the tip.
//   v = texCoord.y — across the beam, 0..1, with 0.5 the centerline.
//
// Additive blend, like the supernova — the beams add light to the scene.

in vec4 vertexColor;
in vec2 texCoord;

uniform vec4 ColorModulator;
uniform float GameTime;

out vec4 fragColor;

// Cheap hue → RGB (full-saturation, full-value). h wraps on 1.0.
vec3 hue2rgb(float h) {
    h = fract(h);
    vec3 k = abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0;
    return clamp(k, 0.0, 1.0);
}

void main() {
    float u = texCoord.x;
    float v = texCoord.y;
    float dPerp = abs(v - 0.5) * 2.0;          // 0 on the axis, 1 at the edges

    if (u < 0.0 || u > 1.0 || dPerp > 1.0) discard;

    // Beam cross-section: bright tight core + softer surrounding glow.
    float coreBand = exp(-pow(dPerp * 3.6, 2.0));
    float glowBand = exp(-pow(dPerp * 1.5, 2.0)) * 0.4;

    // Taper — fade at the root so it plugs into the flash, and at the tip so it points to nothing.
    float taper = smoothstep(0.0, 0.12, u) * (1.0 - smoothstep(0.65, 1.0, u));

    // Flowing pulse along the beam so it doesn't look static.
    float flow = 0.75 + 0.25 * sin(u * 9.0 - GameTime * 60000.0);

    // Chromatic dispersion: hue depends on both how far along the beam (u) and which side of the
    // centerline a pixel sits (sign of v-0.5). The two edges split toward opposite ends of the
    // spectrum like a prism; the core stays white.
    float side = (v - 0.5);
    float hue = u * 0.6 + side * 1.2 + GameTime * 5000.0;
    vec3 rainbow = hue2rgb(hue);

    // Keep it "white with a slight hint of rainbow": the fringe is only ~22% saturated, the rest
    // is white, and the core washes fully back to white. White dominates — the rainbow is a faint
    // chromatic edge, not a coloured beam.
    vec3 fringe = mix(vec3(1.0), rainbow, 0.22);
    vec3 color  = mix(fringe, vec3(1.0), coreBand);

    float intensity = (coreBand * 1.3 + glowBand) * taper * flow;
    float alpha = intensity * vertexColor.a;
    if (alpha < 0.012) discard;

    fragColor = vec4(color * intensity, alpha) * ColorModulator;
}
