package com.generationcamera.ui

import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.widget.Toast
import androidx.camera.core.ImageCapture
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
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.generationcamera.R
import com.generationcamera.camera.CameraController
import com.generationcamera.capture.MediaStorage
import com.generationcamera.engine.EraEngine
import com.generationcamera.engine.Eras
import com.generationcamera.gl.EraRenderer
import com.generationcamera.ui.theme.Amber
import com.generationcamera.ui.theme.Charcoal
import com.generationcamera.viewmodel.CameraViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope

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
    val capturing by viewModel.capturing.collectAsState()

    val cameraController = remember { CameraController(context.applicationContext) }
    var surfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }

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

    // Push engine params on any dial/degree/frame change.
    LaunchedEffect(eraIndex, degree, frameOn) {
        renderer.params = EraEngine.compute(eraIndex, degree, forStill = false)
        renderer.frameOn = frameOn
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
        }
    }

    val savedToast = stringResource(R.string.saved_toast)
    fun capture() {
        if (capturing) return
        viewModel.capturing.value = true
        val stillParams = EraEngine.compute(eraIndex, degree, forStill = true)
        val eraId = Eras.all[eraIndex].id
        val withFrame = frameOn
        cameraController.capture { bitmap ->
            if (bitmap == null) {
                viewModel.capturing.value = false
                return@capture
            }
            glView.queueEvent {
                val frameBmp = if (withFrame) renderer.loadFrameBitmap(eraIndex) else null
                val processed = renderer.processStill(bitmap, stillParams, frameBmp)
                bitmap.recycle()
                frameBmp?.recycle()
                MainScope().launch(Dispatchers.IO) {
                    MediaStorage.saveJpeg(context.applicationContext, processed, eraId, degree)
                    processed.recycle()
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                        viewModel.capturing.value = false
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Charcoal)) {
        // ---------- live filtered preview ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
        ) {
            AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
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
                IconButton(onClick = { viewModel.toggleLens() }) {
                    Icon(
                        Icons.Filled.Cameraswitch,
                        contentDescription = stringResource(R.string.flip_camera),
                        tint = Color.White,
                    )
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
                text = Eras.all[eraIndex].tagline,
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
                    if (capturing) {
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
}
