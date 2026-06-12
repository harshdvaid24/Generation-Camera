package com.generationcamera.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.generationcamera.engine.Eras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for camera state. Dial/degree/frame survive process
 * death via SavedStateHandle (SPEC F9). Switching eras enforces that era's
 * authentic capabilities (no flash before the 1930s, no selfie before the
 * 2000s, …) — see Eras.kt.
 */
class CameraViewModel(private val savedState: SavedStateHandle) : ViewModel() {

    val eraIndex: StateFlow<Int> = savedState.getStateFlow(KEY_ERA, DEFAULT_ERA)
    val degree: StateFlow<Int> = savedState.getStateFlow(KEY_DEGREE, 5)
    val frameOn: StateFlow<Boolean> = savedState.getStateFlow(KEY_FRAME, false)
    val lensFront: StateFlow<Boolean> = savedState.getStateFlow(KEY_LENS, false)
    val flashMode: StateFlow<Int> = savedState.getStateFlow(KEY_FLASH, 0) // 0 off, 1 on, 2 auto
    val timestampOn: StateFlow<Boolean> = savedState.getStateFlow(KEY_TS, false)
    val gridOn: StateFlow<Boolean> = savedState.getStateFlow(KEY_GRID, false)

    val capturing = MutableStateFlow(false)

    fun setEra(index: Int) {
        val clamped = index.coerceIn(0, Eras.all.lastIndex)
        savedState[KEY_ERA] = clamped
        val era = Eras.all[clamped]
        // The dial is a time machine: drop controls this era didn't have.
        if (!era.hasSelfie && lensFront.value) savedState[KEY_LENS] = false
        if (!era.hasFlash && flashMode.value != 0) savedState[KEY_FLASH] = 0
    }

    fun setDegree(value: Int) { savedState[KEY_DEGREE] = value.coerceIn(0, 10) }
    fun setFrameOn(on: Boolean) { savedState[KEY_FRAME] = on }
    fun toggleLens() { savedState[KEY_LENS] = !(lensFront.value) }
    fun cycleFlash() { savedState[KEY_FLASH] = (flashMode.value + 1) % 3 }
    fun toggleTimestamp() { savedState[KEY_TS] = !(timestampOn.value) }
    fun toggleGrid() { savedState[KEY_GRID] = !(gridOn.value) }

    private companion object {
        const val KEY_ERA = "eraIndex"
        const val KEY_DEGREE = "degree"
        const val KEY_FRAME = "frameOn"
        const val KEY_LENS = "lensFront"
        const val KEY_FLASH = "flashMode"
        const val KEY_TS = "timestampOn"
        const val KEY_GRID = "gridOn"
        const val DEFAULT_ERA = 8 // 1980s — the crowd-pleaser stop
    }
}
