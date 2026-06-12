package com.generationcamera.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.generationcamera.R
import com.generationcamera.engine.EraConfig
import kotlinx.coroutines.delay

private val PolaroidWhite = Color(0xFFFAF7F0)

/**
 * Instant-print review: the photo ejects from a printer slot, "develops"
 * from dark to full image, then offers a handwriting line on the white
 * bottom margin before saving.
 */
@Composable
fun PolaroidOverlay(
    photo: Bitmap,
    era: EraConfig,
    saving: Boolean,
    onSave: (String) -> Unit,
    onRetake: () -> Unit,
) {
    var note by remember { mutableStateOf("") }
    val eject = remember { Animatable(0f) }
    val develop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        eject.animateTo(1f, tween(durationMillis = 2200, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(900)
        develop.animateTo(1f, tween(durationMillis = 3000, easing = LinearEasing))
    }
    val printed = eject.value >= 0.999f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
    ) {
        // printer slot
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
                .width(320.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF3A352F)),
        )

        Column(
            modifier = Modifier.align(Alignment.Center).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // the print
            Column(
                modifier = Modifier
                    .width(290.dp)
                    .graphicsLayer {
                        translationY = (1f - eject.value) * -720.dp.toPx()
                        rotationZ = (1f - eject.value) * -2f
                    }
                    .background(PolaroidWhite)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // square instant-film window (final output is center-cropped square)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                ) {
                    Image(
                        bitmap = photo.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // instant-film develop: fades from a dark chemical brown
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF221A12).copy(alpha = 1f - develop.value)),
                    )
                }
                // handwriting line on the bottom margin
                BasicTextField(
                    value = note,
                    onValueChange = { if (it.length <= 48) note = it },
                    enabled = printed && !saving,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF2E2A26),
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Cursive,
                        textAlign = TextAlign.Center,
                    ),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (note.isEmpty()) {
                                Text(
                                    stringResource(R.string.polaroid_note_hint),
                                    color = Color(0xFFB9B2A6),
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.Cursive,
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
                )
                Text(
                    text = era.caption,
                    color = Color(0xFF8A8378),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(28.dp))
            if (printed) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    OutlinedButton(onClick = onRetake, enabled = !saving) {
                        Text(stringResource(R.string.retake))
                    }
                    Button(onClick = { onSave(note) }, enabled = !saving) {
                        Text(
                            if (saving) stringResource(R.string.saving)
                            else stringResource(R.string.save_photo)
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.printing),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
