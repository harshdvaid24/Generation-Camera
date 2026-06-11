#version 300 es
// P3 über-shader: composites the full era look in one pass.
// Layer order (see ERA_ANALYSIS.md §0.1): sharpness/softness -> 3D LUT color
// science -> grain -> halation -> video artifacts -> vignette -> dust -> leak
// -> flicker. Temporal uniforms (flutter/flicker) are zeroed for stills.
//
// precision: highp is REQUIRED here. On real mobile GPUs mediump is FP16
// (max 65504); pixel-coordinate hashes overflow to Inf/NaN and poison the
// whole frame to black. ES 3.0 guarantees fragment highp support.
precision highp float;
precision mediump sampler3D;

in vec2 vUv;
out vec4 outColor;

uniform sampler2D uScene;
uniform sampler2D uBlur;
uniform sampler3D uLut;
uniform sampler2D uDust;
uniform sampler2D uLeak;

uniform float uLutStrength;
uniform float uLutSize;        // texels per LUT axis (17)
uniform float uGrain;          // luminance grain amount
uniform float uGrainSize;      // grain cell size in px
uniform float uChromaNoise;    // per-channel color noise (autochrome/VHS/CCD)
uniform float uVignette;       // darkening amount
uniform float uVignetteHard;   // 0 = soft lens falloff, 1 = hard plate mask
uniform float uSoftFocus;      // blur blend (pictorialist glow)
uniform float uHalation;       // blurred-highlight warm screen
uniform float uSharpness;      // unsharp amount (can be 0)
uniform float uScanline;       // VHS scanlines + head-switch bar
uniform float uChromaShift;    // VHS chroma horizontal misregistration
uniform float uFlutter;        // tape flutter line bursts (preview only)
uniform float uFlicker;        // projector flicker (preview only)
uniform float uDustOpacity;
uniform float uLeakOpacity;
uniform float uTime;
uniform float uSeed;           // fixed per still, animated for preview
uniform vec2 uResolution;

float hash(vec2 p) {
    // inputs are pre-bounded (mod) so this stays finite even on FP16 ALUs
    p = mod(p, 512.0);
    p = fract(p * vec2(0.1031, 0.1097));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}

void main() {
    vec2 uv = vUv;

    // -- tape flutter: per-line horizontal displacement bursts ------------
    if (uFlutter > 0.001) {
        float line = floor(uv.y * 240.0);
        float t = floor(mod(uTime * 18.0, 512.0));
        float burst = step(0.992, hash(vec2(line, t)));
        uv.x += burst * (hash(vec2(line, mod(uTime, 64.0))) - 0.5) * 0.08 * uFlutter;
    }

    vec3 scene = texture(uScene, uv).rgb;
    vec3 blurred = texture(uBlur, uv).rgb;

    // -- VHS chroma misregistration ---------------------------------------
    if (uChromaShift > 0.001) {
        // fixed UV-space shift so preview and full-res still match visually
        float off = uChromaShift * 0.0023;
        scene.r = texture(uScene, uv + vec2(off, 0.0)).r;
        scene.b = texture(uScene, uv - vec2(off, 0.0)).b;
    }

    // -- sharpness / soft focus -------------------------------------------
    vec3 col = scene + (scene - blurred) * uSharpness;
    col = mix(col, blurred, uSoftFocus);
    col = clamp(col, 0.0, 1.0);

    // -- color science: 3D LUT (half-texel corrected) ----------------------
    vec3 lutUv = col * ((uLutSize - 1.0) / uLutSize) + 0.5 / uLutSize;
    col = mix(col, texture(uLut, lutUv).rgb, uLutStrength);

    // -- grain: luminance-weighted animated value noise --------------------
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    vec2 cell = floor(gl_FragCoord.xy / max(uGrainSize, 1.0));
    float midWeight = 0.35 + 1.3 * luma * (1.0 - luma);   // film grain lives in midtones
    float g = hash(cell + vec2(uSeed, uSeed * 1.7)) - 0.5;
    col += g * uGrain * midWeight;
    if (uChromaNoise > 0.001) {
        col.r += (hash(cell + vec2(uSeed + 3.1, 0.0)) - 0.5) * uChromaNoise * midWeight;
        col.b += (hash(cell + vec2(0.0, uSeed + 7.7)) - 0.5) * uChromaNoise * midWeight;
    }

    // -- halation: warm screen of blurred highlights -----------------------
    vec3 hal = max(blurred - 0.55, 0.0) * uHalation * vec3(1.0, 0.55, 0.30);
    col = 1.0 - (1.0 - col) * (1.0 - hal);

    // -- scanlines + head-switch noise bar ---------------------------------
    if (uScanline > 0.001) {
        // fixed line count (not per-pixel) for preview/still parity
        float sl = 0.5 + 0.5 * sin(uv.y * 480.0 * 3.14159);
        col *= 1.0 - uScanline * 0.22 * sl;
        // head-switch noise bar at the image bottom (uv.y = 0 is the bottom
        // row in both the preview and still paths — see EraRenderer notes)
        if (uv.y < 0.028) {
            col = mix(col, vec3(hash(vec2(uv.x * 200.0, uSeed))), uScanline * 0.5);
        }
    }

    // -- vignette: soft lens falloff vs hard plate mask ---------------------
    vec2 d = (uv - 0.5) * vec2(1.0, uResolution.y / uResolution.x);
    float dist = length(d) * 1.9;
    float inner = mix(0.45, 0.62, uVignetteHard);
    float outer = mix(1.35, 0.95, uVignetteHard);
    float vig = smoothstep(inner, outer, dist);
    col *= 1.0 - vig * uVignette;

    // -- dust / scratches / plate defects -----------------------------------
    if (uDustOpacity > 0.001) {
        float dust = texture(uDust, uv).a;
        col = mix(col, vec3(0.92), dust * uDustOpacity);
    }

    // -- light leak (slow drift + opacity flutter in preview) ---------------
    if (uLeakOpacity > 0.001) {
        vec2 leakUv = uv + vec2(sin(uTime * 0.13) * 0.02, cos(uTime * 0.09) * 0.02);
        vec3 leak = texture(uLeak, leakUv).rgb;
        col += leak * uLeakOpacity * (0.85 + 0.15 * sin(uTime * 0.7));
    }

    // -- projector flicker (preview only) ------------------------------------
    if (uFlicker > 0.001) {
        col *= 1.0 + (hash(vec2(floor(uTime * 24.0), 1.0)) - 0.5) * 0.10 * uFlicker;
    }

    outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
