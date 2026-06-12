package com.generationcamera.ui

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.generationcamera.R
import com.generationcamera.camera.CameraController
import com.generationcamera.capture.MediaStorage
import com.generationcamera.capture.PhotoComposer
import com.generationcamera.engine.EraEngine
import com.generationcamera.engine.Eras
import com.generationcamera.gl.EraRenderer
import com.generationcamera.sound.ShutterSounds
import com.generationcamera.ui.theme.Amber
import com.generationcamera.ui.theme.Charcoal
import com.generationcamera.viewmodel.CameraViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CameraScreen(
    onOpenGallery: () -> Unit,
    viewModel: CameraViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val eraIndex by viewModel.eraIndex.collectAsState()
    val degree by viewModel.degree.collectAsState()
    val frameOn by viewModel.frameOn.collectAsState()
    val lensFront by viewModel.lensFront.collectAsState()
    val flashMode by viewModel.flashMode.collectAsState()
    val timestampOn by viewModel.timestampOn.collectAsState()
    val gridOn by viewModel.gridOn.collectAsState()
    val capturing by viewModel.capturing.collectAsState()
    val era = Eras.all[eraIndex]

    val cameraController = remember { CameraController(context.applicationContext) }
    val sounds = remember { ShutterSounds(context.applicationContext) }
    var surfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var pendingPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }

    // GLSurfaceView + renderer live across recompositions.
    val glBundle = remember {
        val view = GLSurfaceView(context)
        val renderer = EraRenderer(context.applicationContext, view) { st ->
            surfaceTexture = st
        }
        view.setEGLContextClientVersion(3)
        view.setRenderer(renderer)
        // Continuous: latch the newest camera frame every vsync instead of
        // depending on frame-available callbacks (robust against stalls that
        // leave the surface black).
        view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        view.setZOrderMediaOverlay(true)
        Pair(view, renderer)
    }
    val (glView, renderer) = glBundle

    // Bind/rebind camera when the GL surface or the lens changes.
    LaunchedEffect(surfaceTexture, lensFront) {
        val st = surfaceTexture ?: return@LaunchedEffect
        cameraController.bind(lifecycleOwner, st, lensFront)
    }

    // Push engine params on any dial/degree change.
    LaunchedEffect(eraIndex, degree) {
        renderer.params = EraEngine.compute(eraIndex, degree, forStill = false)
        glView.requestRender()
    }
    LaunchedEffect(flashMode) {
        cameraController.flashMode = when (flashMode) {
            1 -> ImageCapture.FLASH_MODE_ON
            2 -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    // GLSurfaceView pause/resume with the lifecycle.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraController.unbind()
            sounds.release()
        }
    }

    val savedToast = stringResource(R.string.saved_toast)

    fun saveAndFinish(bitmap: Bitmap) {
        MainScope().launch(Dispatchers.IO) {
            MediaStorage.saveJpeg(context.applicationContext, bitmap, era.id, degree)
            bitmap.recycle()
            launch(Dispatchers.Main) {
                Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                saving = false
                pendingPhoto = null
                viewModel.capturing.value = false
            }
        }
    }

    fun capture() {
        if (capturing) return
        viewModel.capturing.value = true
        sounds.play(era)
        val stillParams = EraEngine.compute(eraIndex, degree, forStill = true)
        val stampTime = timestampOn && era.hasTimestamp
        val toPolaroid = frameOn
        cameraController.capture { bitmap ->
            if (bitmap == null) {
                viewModel.capturing.value = false
                return@capture
            }
            glView.queueEvent {
                val processed = renderer.processStill(bitmap, stillParams)
                bitmap.recycle()
                if (stampTime) PhotoComposer.stampTimestamp(processed, era)
                MainScope().launch(Dispatchers.Main) {
                    if (toPolaroid) {
                        pendingPhoto = processed   // PolaroidOverlay takes over
                        sounds.playPrint()
                    } else {
                        PhotoComposer.stampCaption(processed, era)
                        saveAndFinish(processed)
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Charcoal)) {
            // ---------- live filtered preview ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
            ) {
                AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())

                if (gridOn && era.hasGrid) RuleOfThirdsGrid()
                if (timestampOn && era.hasTimestamp) TimestampPreview(era.id)

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // era-authentic controls only (the dial is a time machine)
                    Row {
                        if (era.hasFlash) {
                            IconButton(onClick = { viewModel.cycleFlash() }) {
                                Icon(
                                    imageVector = when (flashMode) {
                                        1 -> Icons.Filled.FlashOn
                                        2 -> Icons.Filled.FlashAuto
                                        else -> Icons.Filled.FlashOff
                                    },
                                    contentDescription = stringResource(R.string.flash),
                                    tint = Color.White,
                                )
                            }
                        }
                        if (era.hasTimestamp) {
                            IconButton(onClick = { viewModel.toggleTimestamp() }) {
                                Icon(
                                    Icons.Filled.Timer,
                                    contentDescription = stringResource(R.string.timestamp),
                                    tint = if (timestampOn) Amber else Color.White,
                                )
                            }
                        }
                        if (era.hasGrid) {
                            IconButton(onClick = { viewModel.toggleGrid() }) {
                                Icon(
                                    Icons.Filled.Grid3x3,
                                    contentDescription = stringResource(R.string.grid),
                                    tint = if (gridOn) Amber else Color.White,
                                )
                            }
                        }
                    }
                    if (era.hasSelfie) {
                        IconButton(onClick = { viewModel.toggleLens() }) {
                            Icon(
                                Icons.Filled.Cameraswitch,
                                contentDescription = stringResource(R.string.flip_camera),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }

            // ---------- control deck ----------
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text(
                    text = era.tagline,
                    style = MaterialTheme.typography.labelMedium,
                    color = Amber.copy(alpha = 0.8f),
                )
                EraDial(selected = eraIndex, onSelected = { viewModel.setEra(it) })

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${stringResource(R.string.degree)} $degree",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.width(86.dp),
                    )
                    Slider(
                        value = degree.toFloat(),
                        onValueChange = { viewModel.setDegree(it.toInt()) },
                        valueRange = 0f..10f,
                        steps = 9,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.frame_switch),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Switch(checked = frameOn, onCheckedChange = { viewModel.setFrameOn(it) })
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onOpenGallery, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.PhotoLibrary,
                            contentDescription = stringResource(R.string.gallery),
                            tint = Color.White,
                        )
                    }
                    // shutter
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(4.dp, Amber, CircleShape)
                            .background(if (capturing) Amber.copy(alpha = 0.4f) else Color.Transparent)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (capturing && pendingPhoto == null) {
                            CircularProgressIndicator(color = Amber)
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Amber)
                                    .clickable { capture() }
                            )
                        }
                    }
                    Spacer(Modifier.size(48.dp))
                }
            }
        }

        // ---------- instant-print review ----------
        val pending = pendingPhoto
        if (pending != null) {
            PolaroidOverlay(
                photo = pending,
                era = era,
                saving = saving,
                onSave = { note ->
                    saving = true
                    val framed = PhotoComposer.polaroid(pending, note, era)
                    pending.recycle()
                    saveAndFinish(framed)
                },
                onRetake = {
                    pending.recycle()
                    pendingPhoto = null
                    viewModel.capturing.value = false
                },
            )
        }
    }
}

@Composable
private fun RuleOfThirdsGrid() {
    Canvas(Modifier.fillMaxSize()) {
        val c = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            drawLine(c, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), 1.dp.toPx())
            drawLine(c, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1.dp.toPx())
        }
    }
}

@Composable
private fun TimestampPreview(eraId: String) {
    val now = remember { Date() }
    Box(Modifier.fillMaxSize()) {
        if (eraId == "1990s") {
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(
                    SimpleDateFormat("MMM. d yyyy", Locale.US).format(now).uppercase(Locale.US),
                    color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                )
                Text(
                    SimpleDateFormat("a h:mm", Locale.US).format(now).uppercase(Locale.US),
                    color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                )
            }
        } else {
            Text(
                SimpleDateFormat("MM dd yyyy", Locale.US).format(now),
                color = Color(0xFFFF9620), fontFamily = FontFamily.Monospace, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}
