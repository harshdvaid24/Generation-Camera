package com.generationcamera.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/** Saves processed era photos to Pictures/GenerationCamera via MediaStore. */
object MediaStorage {

    private const val DIR = "GenerationCamera"

    fun saveJpeg(context: Context, bitmap: Bitmap, eraId: String, degree: Int): Uri? {
        val name = "GC_${eraId}_${System.currentTimeMillis()}.jpg"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveModern(context, bitmap, name, eraId, degree)
        } else {
            saveLegacy(context, bitmap, name, eraId, degree)
        }
    }

    private fun saveModern(
        context: Context, bitmap: Bitmap, name: String, eraId: String, degree: Int,
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$DIR")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            tagAndSave(ExifInterface(pfd.fileDescriptor), eraId, degree)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        context: Context, bitmap: Bitmap, name: String, eraId: String, degree: Int,
    ): Uri? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), DIR
        )
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, name)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        tagAndSave(ExifInterface(file.absolutePath), eraId, degree)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DATA, file.absolutePath)
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun tagAndSave(exif: ExifInterface, eraId: String, degree: Int) {
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "GenerationCamera era=$eraId degree=$degree")
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Generation Camera 1.0")
        try {
            exif.saveAttributes()
        } catch (_: Exception) {
            // EXIF tagging is best-effort; the image itself is already saved.
        }
    }
}
