package com.generationcamera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor

/**
 * Binds CameraX to the renderer's SurfaceTexture (zero-copy OES path) and
 * provides full-resolution JPEG capture. The captured bitmap is rotated
 * upright (and mirrored for the front lens, matching what the preview shows)
 * before being handed to the GL still pipeline.
 */
class CameraController(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var boundSurface: Surface? = null

    var lensFront = false
        private set
    var flashMode = ImageCapture.FLASH_MODE_OFF
        set(value) {
            field = value
            imageCapture?.flashMode = value
        }
    /** 0..1, CameraX linear zoom. Re-applied after every (re)bind. */
    var linearZoom = 0f
        set(value) {
            field = value
            camera?.cameraControl?.setLinearZoom(value)
        }
    /** -1..1 fraction of the device's EV-compensation range. */
    var exposureFraction = 0f
        set(value) {
            field = value
            applyExposure()
        }

    private fun applyExposure() {
        val cam = camera ?: return
        val range = cam.cameraInfo.exposureState.exposureCompensationRange
        if (range.lower == 0 && range.upper == 0) return
        val f = exposureFraction
        val idx = (f * (if (f >= 0) range.upper else -range.lower)).toInt()
        cam.cameraControl.setExposureCompensationIndex(idx)
    }

    private val mainExecutor: Executor get() = ContextCompat.getMainExecutor(context)

    fun bind(lifecycleOwner: LifecycleOwner, surfaceTexture: SurfaceTexture, front: Boolean) {
        lensFront = front
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cameraProvider = future.get()
            provider = cameraProvider

            val selector43 = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(selector43)
                .build()
            preview.setSurfaceProvider { request ->
                Log.i(TAG, "SurfaceRequest: ${request.resolution}")
                surfaceTexture.setDefaultBufferSize(
                    request.resolution.width, request.resolution.height
                )
                boundSurface?.release()
                val surface = Surface(surfaceTexture)
                boundSurface = surface
                request.provideSurface(surface, mainExecutor) { result ->
                    Log.i(TAG, "provideSurface result: ${result.resultCode}")
                    surface.release()
                }
            }

            val capture = ImageCapture.Builder()
                .setResolutionSelector(selector43)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashMode)
                .build()
            imageCapture = capture

            val cameraSelector =
                if (front) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, capture
                )
                if (linearZoom > 0f) camera?.cameraControl?.setLinearZoom(linearZoom)
                if (exposureFraction != 0f) applyExposure()
                Log.i(TAG, "Camera bound (front=$front)")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, mainExecutor)
    }

    fun capture(onResult: (Bitmap?) -> Unit) {
        val capture = imageCapture ?: return onResult(null)
        capture.takePicture(mainExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = image.use { decodeUpright(it) }
                onResult(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture failed", exception)
                onResult(null)
            }
        })
    }

    private fun decodeUpright(image: ImageProxy): Bitmap? {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0 && !lensFront) return raw
        val m = Matrix()
        m.postRotate(rotation.toFloat())
        if (lensFront) m.postScale(-1f, 1f)   // match the mirrored preview
        val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (out !== raw) raw.recycle()
        return out
    }

    fun unbind() {
        provider?.unbindAll()
        boundSurface?.release()
        boundSurface = null
    }

    private companion object {
        const val TAG = "CameraController"
    }
}
