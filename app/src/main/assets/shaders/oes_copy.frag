#version 300 es
#extension GL_OES_EGL_image_external_essl3 : enable
#extension GL_OES_EGL_image_external : enable
// P1 (preview): camera external-OES texture -> scene FBO.
precision mediump float;
in vec2 vUv;
out vec4 outColor;
uniform samplerExternalOES uTexture;
void main() {
    outColor = vec4(texture(uTexture, vUv).rgb, 1.0);
}
