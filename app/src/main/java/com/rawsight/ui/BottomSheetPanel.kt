package com.rawsight.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawsight.camera.*
import com.rawsight.settings.SettingsEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetPanel(
    cameraState: CameraState,
    settingsEngine: SettingsEngine,
    onParamChange: (
        CameraParam,
        ControlMode,
        Int?,
        ShutterSpeed?,
        Int?,
        FocusMode?,
        Float?,
        Float?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // ISO
            ParamRow(
                label = "ISO",
                value = cameraState.iso.toString(),
                mode = cameraState.isoMode,
                onToggle = {
                    settingsEngine.toggleMode(CameraParam.ISO)
                    onParamChange(CameraParam.ISO, settingsEngine.getMode(CameraParam.ISO), null, null, null, null, null, null)
                },
                onCycleValue = takeIf { cameraState.isoMode == ControlMode.MANUAL }?.let {
                    {
                        val idx = IsoValues.values.indexOf(cameraState.iso)
                        val n = IsoValues.values[(idx + 1) % IsoValues.values.size]
                        onParamChange(CameraParam.ISO, cameraState.isoMode, n, null, null, null, null, null)
                    }
                }
            )
            Divider(color = Color.White.copy(alpha = 0.08f))

            // Shutter Speed
            ParamRow(
                label = "SPEED",
                value = cameraState.shutterSpeed.display,
                mode = cameraState.shutterMode,
                onToggle = {
                    settingsEngine.toggleMode(CameraParam.SHUTTER_SPEED)
                    onParamChange(CameraParam.SHUTTER_SPEED, settingsEngine.getMode(CameraParam.SHUTTER_SPEED), null, null, null, null, null, null)
                },
                onCycleValue = takeIf { cameraState.shutterMode == ControlMode.MANUAL }?.let {
                    {
                        val idx = ShutterValues.values.indexOf(cameraState.shutterSpeed)
                        val n = ShutterValues.values[(idx + 1) % ShutterValues.values.size]
                        onParamChange(CameraParam.SHUTTER_SPEED, cameraState.shutterMode, null, n, null, null, null, null)
                    }
                }
            )
            Divider(color = Color.White.copy(alpha = 0.08f))

            // White Balance
            ParamRow(
                label = "WB",
                value = "${cameraState.whiteBalance}K",
                mode = cameraState.wbMode,
                onToggle = {
                    settingsEngine.toggleMode(CameraParam.WHITE_BALANCE)
                    onParamChange(CameraParam.WHITE_BALANCE, settingsEngine.getMode(CameraParam.WHITE_BALANCE), null, null, null, null, null, null)
                },
                onCycleValue = takeIf { cameraState.wbMode == ControlMode.MANUAL }?.let {
                    {
                        val idx = WbValues.values.indexOf(cameraState.whiteBalance)
                        val n = WbValues.values[(idx + 1) % WbValues.values.size]
                        onParamChange(CameraParam.WHITE_BALANCE, cameraState.wbMode, null, null, n, null, null, null)
                    }
                }
            )
            Divider(color = Color.White.copy(alpha = 0.08f))

            // Focus
            ParamRow(
                label = "FOCUS",
                value = if (cameraState.focusMode == FocusMode.AF) "AF" else "MF",
                mode = cameraState.focusControlMode,
                onToggle = {
                    settingsEngine.toggleMode(CameraParam.FOCUS)
                    onParamChange(CameraParam.FOCUS, settingsEngine.getMode(CameraParam.FOCUS), null, null, null, null, null, null)
                },
                onCycleValue = takeIf { cameraState.focusControlMode == ControlMode.MANUAL }?.let {
                    {
                        val fm = if (cameraState.focusMode == FocusMode.AF) FocusMode.MF else FocusMode.AF
                        onParamChange(CameraParam.FOCUS, cameraState.focusControlMode, null, null, null, fm, null, null)
                    }
                }
            )
            Divider(color = Color.White.copy(alpha = 0.08f))

            // EV Compensation
            ParamRow(
                label = "EV",
                value = String.format("%+.1f", cameraState.evCompensation),
                mode = cameraState.evMode,
                onToggle = {
                    settingsEngine.toggleMode(CameraParam.EV_COMPENSATION)
                    onParamChange(CameraParam.EV_COMPENSATION, settingsEngine.getMode(CameraParam.EV_COMPENSATION), null, null, null, null, null, null)
                },
                onCycleValue = takeIf { cameraState.evMode == ControlMode.MANUAL }?.let {
                    {
                        val idx = EvValues.values.indexOf(cameraState.evCompensation)
                        val n = EvValues.values[(idx + 1) % EvValues.values.size]
                        onParamChange(CameraParam.EV_COMPENSATION, cameraState.evMode, null, null, null, null, null, n)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ParamRow(
    label: String,
    value: String,
    mode: ControlMode,
    onToggle: () -> Unit,
    onCycleValue: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier.width(70.dp)
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .weight(1f)
                .then(if (onCycleValue != null) Modifier.clickable { onCycleValue() } else Modifier)
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (mode == ControlMode.MANUAL) Color(0xFF4CAF50) else Color.Transparent,
            border = if (mode == ControlMode.AUTO) BorderStroke(1.dp, Color.Gray) else null,
            modifier = Modifier.clickable { onToggle() }
        ) {
            Text(
                text = mode.name,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (mode == ControlMode.MANUAL) Color.Black else Color.Gray,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}
