#version 300 es
// P1 (still capture): decoded JPEG texture -> scene FBO.
precision mediump float;
in vec2 vUv;
out vec4 outColor;
uniform sampler2D uTexture;
void main() {
    outColor = vec4(texture(uTexture, vUv).rgb, 1.0);
}
