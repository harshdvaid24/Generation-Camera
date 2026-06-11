package com.generationcamera.gl

import java.io.BufferedReader

/**
 * Minimal Adobe/Resolve `.cube` 3D LUT parser. Supports TITLE, LUT_3D_SIZE,
 * DOMAIN_MIN/MAX (assumed 0..1) and data rows in standard red-fastest order.
 * Pure Kotlin — unit tested in CubeLutParserTest.
 */
object CubeLutParser {

    data class Lut(val size: Int, val data: FloatArray)

    fun parse(reader: BufferedReader): Lut {
        var size = 0
        var data: FloatArray? = null
        var i = 0
        reader.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            when {
                line.startsWith("TITLE") || line.startsWith("DOMAIN_") -> Unit
                line.startsWith("LUT_3D_SIZE") -> {
                    size = line.substringAfter("LUT_3D_SIZE").trim().toInt()
                    require(size in 2..129) { "Unsupported LUT size $size" }
                    data = FloatArray(size * size * size * 3)
                }
                line.startsWith("LUT_1D_SIZE") ->
                    throw IllegalArgumentException("1D LUTs not supported")
                else -> {
                    val d = data ?: throw IllegalArgumentException("Data before LUT_3D_SIZE")
                    var start = 0
                    for (c in 0..2) {
                        while (start < line.length && line[start] == ' ') start++
                        var end = start
                        while (end < line.length && line[end] != ' ') end++
                        d[i++] = line.substring(start, end).toFloat()
                        start = end
                    }
                }
            }
        }
        val d = data ?: throw IllegalArgumentException("Missing LUT_3D_SIZE")
        require(i == d.size) { "Expected ${d.size} floats, got $i" }
        return Lut(size, d)
    }
}
