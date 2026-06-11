package com.generationcamera

import com.generationcamera.engine.EraEngine
import com.generationcamera.engine.Eras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EraEngineTest {

    @Test
    fun degreeTenMatchesEraMaxima() {
        Eras.all.forEachIndexed { i, era ->
            val p = EraEngine.compute(i, 10, forStill = false)
            assertEquals(era.grain, p.grain, 1e-5f)
            assertEquals(era.vignette, p.vignette, 1e-5f)
            assertEquals(era.dust, p.dust, 1e-5f)
            assertEquals(era.resScale, p.resScale, 1e-5f)
            assertEquals(1f, p.lutStrength, 1e-5f)
        }
    }

    @Test
    fun degreeZeroIsSubtleNotOff() {
        val p = EraEngine.compute(0, 0, forStill = false) // 1900s
        assertEquals(0.30f, p.lutStrength, 1e-5f)         // color identity remains
        assertEquals(0f, p.dust, 1e-5f)                   // pure defects vanish
        assertEquals(0f, p.leak, 1e-5f)
        assertTrue(p.grain > 0f)                          // a hint of grain stays
        assertEquals(1f, p.resScale, 1e-5f)               // no down-res at 0
    }

    @Test
    fun intensityIsMonotonicInDegree() {
        for (i in Eras.all.indices) {
            var prevGrain = -1f
            var prevLut = -1f
            for (d in 0..10) {
                val p = EraEngine.compute(i, d, forStill = false)
                assertTrue(p.grain >= prevGrain)
                assertTrue(p.lutStrength >= prevLut)
                prevGrain = p.grain
                prevLut = p.lutStrength
            }
        }
    }

    @Test
    fun stillsDropTemporalLayersOnly() {
        val preview = EraEngine.compute(9, 10, forStill = false) // 1990s VHS
        val still = EraEngine.compute(9, 10, forStill = true)
        assertTrue(preview.flutter > 0f)
        assertEquals(0f, still.flutter, 1e-6f)
        assertEquals(0f, still.flicker, 1e-6f)
        assertEquals(0f, still.weave, 1e-6f)
        // spatial signature survives in the still
        assertEquals(preview.scanline, still.scanline, 1e-6f)
        assertEquals(preview.grain, still.grain, 1e-6f)
        assertEquals(preview.resScale, still.resScale, 1e-6f)
    }

    @Test
    fun degreeIsClamped() {
        val low = EraEngine.compute(0, -5, forStill = false)
        val high = EraEngine.compute(0, 99, forStill = false)
        assertEquals(EraEngine.compute(0, 0, false), low)
        assertEquals(EraEngine.compute(0, 10, false), high)
    }
}
