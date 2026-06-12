package com.generationcamera.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.generationcamera.engine.EraConfig
import com.generationcamera.engine.Eras

/**
 * Era-appropriate shutter sounds. All clips are synthesized by
 * tools/generate_assets.py (plate clack, leaf click, Super-8 motor, Polaroid
 * eject whirr, SLR mirror+advance, camcorder beep, digicam beep, soft click).
 */
class ShutterSounds(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids: Map<String, Int>

    init {
        val res = context.resources
        ids = Eras.all.map { it.shutterSound }.distinct().mapNotNull { name ->
            val resId = res.getIdentifier(name, "raw", context.packageName)
            if (resId != 0) name to pool.load(context, resId, 1) else null
        }.toMap()
    }

    fun play(era: EraConfig) {
        ids[era.shutterSound]?.let { pool.play(it, 0.9f, 0.9f, 1, 0, 1f) }
    }

    /** The Polaroid eject whirr, reused for the print animation. */
    fun playPrint() {
        ids["shutter_polaroid"]?.let { pool.play(it, 0.7f, 0.7f, 1, 0, 1f) }
    }

    fun release() = pool.release()
}
