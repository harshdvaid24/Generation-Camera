package com.generationcamera

import com.generationcamera.gl.CubeLutParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CubeLutParserTest {

    private fun parse(text: String) = CubeLutParser.parse(text.byteInputStream().bufferedReader())

    @Test
    fun parsesMinimalIdentityCube() {
        val cube = buildString {
            appendLine("TITLE \"test\"")
            appendLine("# a comment")
            appendLine("LUT_3D_SIZE 2")
            appendLine("DOMAIN_MIN 0.0 0.0 0.0")
            appendLine("DOMAIN_MAX 1.0 1.0 1.0")
            // red fastest: (r,g,b) for b-major iteration
            for (b in 0..1) for (g in 0..1) for (r in 0..1) {
                appendLine("$r.0 $g.0 $b.0")
            }
        }
        val lut = parse(cube)
        assertEquals(2, lut.size)
        assertEquals(24, lut.data.size)
        // first entry = black, second = pure red
        assertEquals(0f, lut.data[0], 1e-6f)
        assertEquals(1f, lut.data[3], 1e-6f)
        assertEquals(0f, lut.data[4], 1e-6f)
        // last entry = white
        assertEquals(1f, lut.data[23], 1e-6f)
    }

    @Test
    fun rejectsMissingSize() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("0.0 0.0 0.0\n")
        }
    }

    @Test
    fun rejectsWrongRowCount() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("LUT_3D_SIZE 2\n0.0 0.0 0.0\n")
        }
    }
}
