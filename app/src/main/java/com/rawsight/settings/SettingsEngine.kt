package com.rawsight.settings

import com.rawsight.camera.CameraParam
import com.rawsight.camera.ControlMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages AUTO/MANUAL state for each camera parameter independently.
 */
class SettingsEngine {

    private val _paramModes = MutableStateFlow(
        mapOf(
            CameraParam.ISO to ControlMode.AUTO,
            CameraParam.SHUTTER_SPEED to ControlMode.AUTO,
            CameraParam.WHITE_BALANCE to ControlMode.AUTO,
            CameraParam.FOCUS to ControlMode.AUTO,
            CameraParam.EV_COMPENSATION to ControlMode.AUTO
        )
    )

    val paramModes: StateFlow<Map<CameraParam, ControlMode>> = _paramModes.asStateFlow()

    fun toggleMode(param: CameraParam) {
        _paramModes.value = _paramModes.value.toMutableMap().apply {
            this[param] = when (this[param]) {
                ControlMode.AUTO -> ControlMode.MANUAL
                ControlMode.MANUAL -> ControlMode.AUTO
                null -> ControlMode.AUTO
            }
        }
    }

    fun setMode(param: CameraParam, mode: ControlMode) {
        _paramModes.value = _paramModes.value.toMutableMap().apply {
            this[param] = mode
        }
    }

    fun getMode(param: CameraParam): ControlMode {
        return _paramModes.value[param] ?: ControlMode.AUTO
    }

    fun isManual(param: CameraParam): Boolean {
        return getMode(param) == ControlMode.MANUAL
    }
}
