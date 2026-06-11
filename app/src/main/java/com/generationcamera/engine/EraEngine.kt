package com.generationcamera.engine

import kotlin.math.pow

/**
 * Maps (eraIndex, degree 0–10, still/preview) → [EffectParams] using the
 * intensity model of ERA_ANALYSIS.md §0.3. Degree 0 is "subtle", never "off";
 * degree 10 is the era's tuned maximum ([EraConfig] values).
 */
object EraEngine {

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    fun compute(eraIndex: Int, degree: Int, forStill: Boolean): EffectParams {
        val era = Eras.all[eraIndex.coerceIn(0, Eras.all.lastIndex)]
        val d = (degree.coerceIn(0, 10)) / 10f
        val d2 = d * d

        val lutScale = lerp(0.30f, 1f, d)                 // linear: color = identity
        val grainScale = lerp(0.15f, 1f, d2)              // quadratic: defects ramp late
        val vigScale = lerp(0.25f, 1f, d)
        val softScale = lerp(0.20f, 1f, d.pow(1.5f))      // ease-in: softness eats detail
        val defectScale = d2                              // dust/leaks absent at 0
        val artifactScale = lerp(0.30f, 1f, d2)           // scanlines/flutter/weave/flicker
        val sharpScale = lerp(0.30f, 1f, d)

        val temporal = if (forStill) 0f else 1f           // a still can't weave or flicker

        return EffectParams(
            eraIndex = eraIndex,
            lutStrength = lutScale,
            grain = era.grain * grainScale,
            grainSize = era.grainSize,
            chromaNoise = era.chromaNoise * grainScale,
            vignette = era.vignette * vigScale,
            vignetteHard = era.vignetteHard,
            softFocus = era.softFocus * softScale,
            halation = era.halation * softScale,
            sharpness = era.sharpness * sharpScale,
            scanline = era.scanline * artifactScale,
            chromaShift = era.chromaShift * artifactScale,
            flutter = era.flutter * artifactScale * temporal,
            flicker = era.flicker * artifactScale * temporal,
            weave = era.weave * artifactScale * temporal,
            dust = era.dust * defectScale,
            leak = era.leak * defectScale,
            leakIndex = era.leakIndex,
            resScale = lerp(1f, era.resScale, d),
        )
    }
}
