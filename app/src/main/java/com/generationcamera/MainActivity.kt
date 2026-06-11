package com.generationcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.generationcamera.ui.CameraScreen
import com.generationcamera.ui.GalleryScreen
import com.generationcamera.ui.theme.GenerationCameraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GenerationCameraTheme {
                AppRoot()
            }
        }
    }
}

private val requiredPermissions: Array<String> =
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    } else {
        arrayOf(Manifest.permission.CAMERA)
    }

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> granted = result.values.all { it } }

    if (!granted) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.permission_rationale),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = { launcher.launch(requiredPermissions) },
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text(stringResource(R.string.grant_permission))
            }
        }
        return
    }

    var screen by rememberSaveable { mutableStateOf("camera") }
    when (screen) {
        "gallery" -> GalleryScreen(onBack = { screen = "camera" })
        else -> CameraScreen(onOpenGallery = { screen = "gallery" })
    }
}
