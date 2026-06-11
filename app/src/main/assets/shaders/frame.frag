#version 300 es
// P4: era frame overlay, straight alpha blend over the rendered image.
precision mediump float;
in vec2 vUv;
out vec4 outColor;
uniform sampler2D uTexture;
void main() {
    outColor = texture(uTexture, vUv);
}
