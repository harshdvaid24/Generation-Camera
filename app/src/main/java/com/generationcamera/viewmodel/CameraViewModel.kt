package com.generationcamera.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for camera state. Dial/degree/frame survive process
 * death via SavedStateHandle (SPEC F9).
 */
class CameraViewModel(private val savedState: SavedStateHandle) : ViewModel() {

    val eraIndex: StateFlow<Int> = savedState.getStateFlow(KEY_ERA, DEFAULT_ERA)
    val degree: StateFlow<Int> = savedState.getStateFlow(KEY_DEGREE, 5)
    val frameOn: StateFlow<Boolean> = savedState.getStateFlow(KEY_FRAME, false)
    val lensFront: StateFlow<Boolean> = savedState.getStateFlow(KEY_LENS, false)
    val flashMode: StateFlow<Int> = savedState.getStateFlow(KEY_FLASH, 0) // 0 off, 1 on, 2 auto

    val capturing = MutableStateFlow(false)

    fun setEra(index: Int) { savedState[KEY_ERA] = index }
    fun setDegree(value: Int) { savedState[KEY_DEGREE] = value.coerceIn(0, 10) }
    fun setFrameOn(on: Boolean) { savedState[KEY_FRAME] = on }
    fun toggleLens() { savedState[KEY_LENS] = !(lensFront.value) }
    fun cycleFlash() { savedState[KEY_FLASH] = (flashMode.value + 1) % 3 }

    private companion object {
        const val KEY_ERA = "eraIndex"
        const val KEY_DEGREE = "degree"
        const val KEY_FRAME = "frameOn"
        const val KEY_LENS = "lensFront"
        const val KEY_FLASH = "flashMode"
        const val DEFAULT_ERA = 8 // 1980s — the crowd-pleaser stop
    }
}
