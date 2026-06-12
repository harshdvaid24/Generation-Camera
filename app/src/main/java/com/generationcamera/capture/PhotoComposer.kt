package com.generationcamera.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.generationcamera.engine.EraConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** CPU composition of the final saved photo: polaroid frame, captions, stamps. */
object PhotoComposer {

    private const val POLAROID_WHITE = 0xFFFAF7F0.toInt()

    /**
     * Wraps the photo in an instant-print frame, Instagram-ready: the image
     * is center-cropped square (authentic instant film) and the canvas is
     * exactly 4:5 portrait (1080×1350-class) — IG post native, and shares
     * cleanly to stories. Bottom margin carries the handwritten note and a
     * small "Year · Camera" line.
     */
    fun polaroid(photo: Bitmap, userNote: String, era: EraConfig): Bitmap {
        // center-crop square
        val side = minOf(photo.width, photo.height)
        val srcLeft = (photo.width - side) / 2
        val srcTop = (photo.height - side) / 2

        val border = (side * 0.068f).toInt()          // image fills ~88% width
        val outW = side + 2 * border
        val outH = (outW * 1.25f).toInt()             // 4:5 portrait
        val bottom = outH - side - 2 * border         // note area ≈ 30% of width
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(POLAROID_WHITE)
        c.drawBitmap(
            photo,
            android.graphics.Rect(srcLeft, srcTop, srcLeft + side, srcTop + side),
            android.graphics.Rect(border, border, border + side, border + side),
            null,
        )

        val noteY = border + side + bottom * 0.48f
        if (userNote.isNotBlank()) {
            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF2E2A26.toInt()
                textSize = bottom * 0.30f
                typeface = Typeface.create("cursive", Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            fitText(notePaint, userNote.take(48), outW * 0.88f)
            c.drawText(userNote.take(48), outW / 2f, noteY, notePaint)
        }
        drawCaptionLine(c, era.caption, outW / 2f, border + side + bottom * 0.85f,
            bottom * 0.085f, centered = true, dark = true, maxWidth = outW * 0.80f)
        return out
    }

    /** Small "Year · Camera" line at the bottom of a frameless photo. */
    fun stampCaption(photo: Bitmap, era: EraConfig) {
        val c = Canvas(photo)
        val size = photo.width * 0.020f
        drawCaptionLine(c, era.caption, photo.width * 0.03f,
            photo.height - size * 1.2f, size, centered = false, dark = false,
            maxWidth = photo.width * 0.60f)
    }

    /** 1990s VHS OSD / 2000s digicam date stamp, using the real capture time. */
    fun stampTimestamp(photo: Bitmap, era: EraConfig) {
        val c = Canvas(photo)
        val now = Date()
        if (era.id == "1990s") {
            val p = osdPaint(photo.width * 0.045f, 0xFFF2F2F2.toInt())
            val date = SimpleDateFormat("MMM. d yyyy", Locale.US).format(now).uppercase(Locale.US)
            val time = SimpleDateFormat("a h:mm", Locale.US).format(now).uppercase(Locale.US)
            c.drawText(date, photo.width * 0.05f, photo.height * 0.88f, p)
            c.drawText(time, photo.width * 0.05f, photo.height * 0.93f, p)
        } else {
            val p = osdPaint(photo.width * 0.05f, 0xFFFF9620.toInt())
            val date = SimpleDateFormat("MM dd yyyy", Locale.US).format(now)
            p.textAlign = Paint.Align.RIGHT
            c.drawText(date, photo.width * 0.95f, photo.height * 0.93f, p)
        }
    }

    private fun osdPaint(size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.MONOSPACE
        setShadowLayer(size * 0.08f, 0f, 0f, Color.BLACK)
    }

    private fun drawCaptionLine(
        c: Canvas, text: String, x: Float, y: Float, size: Float,
        centered: Boolean, dark: Boolean, maxWidth: Float,
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0xFF8A8378.toInt() else 0xCCFFFFFF.toInt()
            textSize = size
            typeface = Typeface.MONOSPACE
            textAlign = if (centered) Paint.Align.CENTER else Paint.Align.LEFT
            if (!dark) setShadowLayer(size * 0.15f, 0f, 0f, 0xAA000000.toInt())
        }
        fitText(p, text, maxWidth)
        c.drawText(text, x, y, p)
    }

    /** Shrinks the paint's text size until [text] fits within [maxWidth]. */
    private fun fitText(p: Paint, text: String, maxWidth: Float) {
        val w = p.measureText(text)
        if (w > maxWidth) p.textSize = p.textSize * maxWidth / w
    }
}
