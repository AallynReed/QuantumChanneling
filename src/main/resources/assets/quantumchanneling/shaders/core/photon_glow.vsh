#version 150

// Shared vertex shader for the Quantum Channeling photon-glow shaders. Position-color-tex format
// — the UV0 input is reused as a normalised 0..1 quad coordinate, not as a real texture sample,
// so the fragment shader can compute radial distance from the quad center analytically.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord = UV0;
}
