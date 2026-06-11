#version 300 es
// P2: 9-tap separable Gaussian, run twice (uDirection = (1,0) then (0,1))
// at half resolution. Feeds soft focus, halation and unsharp masking.
precision mediump float;
in vec2 vUv;
out vec4 outColor;
uniform sampler2D uTexture;
uniform vec2 uDirection;   // (1/w, 0) or (0, 1/h)
void main() {
    float w[5];
    w[0] = 0.227027; w[1] = 0.194594; w[2] = 0.121622; w[3] = 0.054054; w[4] = 0.016216;
    vec3 c = texture(uTexture, vUv).rgb * w[0];
    for (int i = 1; i < 5; i++) {
        vec2 off = uDirection * float(i) * 1.5;
        c += texture(uTexture, vUv + off).rgb * w[i];
        c += texture(uTexture, vUv - off).rgb * w[i];
    }
    outColor = vec4(c, 1.0);
}
