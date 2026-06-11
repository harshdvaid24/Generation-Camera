package com.generationcamera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.generationcamera.engine.Eras
import com.generationcamera.ui.theme.Amber
import com.generationcamera.ui.theme.AmberDim
import com.generationcamera.ui.theme.OffWhite
import com.generationcamera.ui.theme.PanelGray
import kotlin.math.roundToInt

private const val DEGREES_PER_ERA = 16f

/**
 * Tactile rotary Eras Dial. The dial is a big wheel whose center sits below
 * the visible strip; drag horizontally to spin it, with haptic detents per
 * decade. The selected era sits under the top-center pointer.
 */
@Composable
fun EraDial(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val eras = Eras.all
    val haptics = LocalHapticFeedback.current
    val currentSelected by rememberUpdatedState(selected)
    val onSelectedState by rememberUpdatedState(onSelected)
    var dragDegrees by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { dragDegrees = 0f },
                    onDragCancel = { dragDegrees = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    // dragging right spins the dial right -> earlier eras
                    dragDegrees -= dragAmount * 0.18f
                    val steps = (dragDegrees / DEGREES_PER_ERA).roundToInt()
                    if (steps != 0) {
                        val next = (currentSelected + steps).coerceIn(0, eras.lastIndex)
                        if (next != currentSelected) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelectedState(next)
                        }
                        dragDegrees -= steps * DEGREES_PER_ERA
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val radius = w * 0.85f
        val center = Offset(w / 2f, h * 0.35f + radius)

        // dial body
        drawCircle(PanelGray, radius = radius, center = center)
        drawCircle(AmberDim, radius = radius, center = center, style =
            androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))

        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 15.dp.toPx()
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }

        eras.forEachIndexed { i, era ->
            val angle = (i - selected) * DEGREES_PER_ERA - dragDegrees
            if (angle < -55f || angle > 55f) return@forEachIndexed
            val isSel = i == selected
            rotate(degrees = angle, pivot = center) {
                // tick
                drawLine(
                    color = if (isSel) Amber else AmberDim,
                    start = Offset(center.x, center.y - radius + 6.dp.toPx()),
                    end = Offset(center.x, center.y - radius + if (isSel) 22.dp.toPx() else 16.dp.toPx()),
                    strokeWidth = if (isSel) 4.dp.toPx() else 2.dp.toPx(),
                )
                labelPaint.color = (if (isSel) Amber else OffWhite.copy(alpha = 0.55f)).toArgb()
                labelPaint.textSize = (if (isSel) 17 else 14).dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(
                    era.label, center.x, center.y - radius + 44.dp.toPx(), labelPaint)
            }
        }

        // top-center pointer
        val px = w / 2f
        val py = h * 0.35f - 10.dp.toPx()
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(px, py + 14.dp.toPx())
            lineTo(px - 8.dp.toPx(), py)
            lineTo(px + 8.dp.toPx(), py)
            close()
        }
        drawPath(path, Amber)
    }
}
