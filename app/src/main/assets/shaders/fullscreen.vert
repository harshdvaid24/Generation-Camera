#version 300 es
// Fullscreen quad. uTexMatrix carries the SurfaceTexture transform (identity
// for non-OES passes); uWeave is the gate-weave UV offset (P1 only).
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aUv;
uniform mat4 uTexMatrix;
uniform vec2 uWeave;
out vec2 vUv;
void main() {
    vUv = (uTexMatrix * vec4(aUv, 0.0, 1.0)).xy + uWeave;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
