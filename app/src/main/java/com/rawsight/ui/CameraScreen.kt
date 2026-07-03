package com.rawsight.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rawsight.ai.AIAnalyzer
import com.rawsight.ai.PreviewFrame
import com.rawsight.camera.*
import com.rawsight.decision.DecisionEngine
import com.rawsight.settings.SettingsEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt
import android.util.Log

@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    cameraService: CameraService,
    lensManager: LensManager,
    decisionEngine: DecisionEngine,
    settingsEngine: SettingsEngine,
    aiAnalyzer: AIAnalyzer,
    activeCameraId: String,
    cameraReady: Boolean,
    cameraError: String?,
    onOpenCamera: (String) -> Unit,
    onSwitchLens: (String) -> Unit,
    onCloseCamera: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cameraState by cameraService.cameraState.collectAsState()
    val decisionReport by decisionEngine.report.collectAsState()

    var showFullControls by remember { mutableStateOf(false) }
    var captureMsg by remember { mutableStateOf<String?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // Feed AI
    LaunchedEffect(cameraReady) {
        if (!cameraReady) return@LaunchedEffect
        delay(1000)
        while (true) {
            val frame = PreviewFrame(640, 480, ByteArray(0))
            val r = aiAnalyzer.analyze(frame)
            r?.let { decisionEngine.updateFromAI(it) }
            decisionEngine.updateFocusStable(cameraService.focusStable.value)
            delay(500)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

        // ═══════════ Layer 1: Preview ═══════════
        var textureView by remember { mutableStateOf<TextureView?>(null) }

        key(activeCameraId) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).also { textureView = it }.apply {
                        val self = this
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                cameraService.startPreview(Surface(st))
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                            self.applyCropTransform(cameraService)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } // end key

        // ═══════════ Layer 2: Overlay ═══════════
        OverlayRenderer(
            modifier = Modifier.fillMaxSize(),
            decisionReport = decisionReport,
            aiResult = null, tiltDegrees = 0f, showGrid = true
        )

        // ═══════════ Top HUD ═══════════
        TopHUD(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 4.dp, start = 8.dp, end = 8.dp),
            decisionReport = decisionReport
        )

        // ═══════════ Lens switcher ═══════════
        val lenses = remember(lensManager) { lensManager.enumerateAll() }
        if (lenses.size > 1) {
            val rearLenses = lenses.filter { it.isRear }
            val frontLenses = lenses.filter { it.isFront }
            val allActive = lenses.any { it.cameraId == activeCameraId }
            if (!allActive && activeCameraId.isNotEmpty()) {
                // activeCameraId may be from initial camera open; find the matching lens
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 4.dp, end = 8.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Rear cameras
                if (rearLenses.isNotEmpty()) {
                    Text("REAR", color = Color.Gray.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    rearLenses.forEach { lens ->
                        val mainFl = rearLenses.first().primaryFocalLength
                        val zoomLabel = String.format("%.1fx", lens.primaryFocalLength / mainFl)
                        LensBadge(zoomLabel, lens.cameraId == activeCameraId) {
                            onSwitchLens(lens.cameraId)
                        }
                    }
                }
                // Front cameras
                if (frontLenses.isNotEmpty()) {
                    Text("FRONT", color = Color.Gray.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    frontLenses.forEach { lens ->
                        LensBadge("F", lens.cameraId == activeCameraId) {
                            onSwitchLens(lens.cameraId)
                        }
                    }
                }
            }
        }

        // ═══════════ Slider param control ═══════════
        var activeParam by remember { mutableStateOf(CameraParam.EV_COMPENSATION) }
        ParamSlider(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
            cameraState = cameraState,
            settingsEngine = settingsEngine,
            activeParam = activeParam,
            onActiveParam = { activeParam = it },
            onParam = { p, m, iso, ss, wb, f, fd, ev ->
                cameraService.updateParameter(p, m, iso, ss, wb, f, fd, ev)
            },
            onExpand = { showFullControls = !showFullControls }
        )

        // ═══════════ Shutter ═══════════
        ShutterButton(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            readinessColor = decisionReport.state.color,
            enabled = !isCapturing,
            onCapture = {
                isCapturing = true
                scope.launch {
                    try {
                        val surf = cameraService.getPreviewSurface()
                        if (surf == null) { captureMsg = "Preview not ready\n预览未就绪"; isCapturing = false; return@launch }
                        val dev = cameraService.getCameraDevice()
                        val ch = cameraService.getCameraCharacteristics()
                        val h = cameraService.getBackgroundHandler()
                        if (dev == null || ch == null) { captureMsg = "Camera not ready\n相机未就绪"; isCapturing = false; return@launch }

                        val ctrl = CaptureController(context, dev, ch, h)
                        val path = ctrl.captureBoth(cameraState, surf)

                        cameraService.resumePreviewAfterCapture()
                        captureMsg = if (path.isNotEmpty()) "Saved\n已保存" else "Capture failed\n拍摄失败"
                    } catch (e: Exception) {
                        Log.e("RawSight", "Capture", e)
                        captureMsg = "Error: ${e.message}\n错误"
                    } finally { isCapturing = false }
                }
            }
        )

        // ═══════════ Capture result toast ═══════════
        AnimatedVisibility(visible = captureMsg != null, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 120.dp)) {
            Surface(color = Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(8.dp)) {
                Text(captureMsg ?: "", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(12.dp, 8.dp))
            }
            LaunchedEffect(captureMsg) { if (captureMsg != null) { delay(2000); captureMsg = null } }
        }

        // ═══════════ Error ═══════════
        AnimatedVisibility(visible = cameraError != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(cameraError ?: "", color = Color(0xFFFF5252), fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { onOpenCamera(lensManager.getMainRearCamera()?.cameraId ?: "") }) { Text("Retry") }
                }
            }
        }

        // ═══════════ Loading ═══════════
        if (!cameraReady && cameraError == null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF4CAF50))
                    Spacer(Modifier.height(16.dp))
                    Text("Initializing camera...\n初始化相机...", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                }
            }
        }
    }

    // Bottom sheet for full controls
    if (showFullControls) {
        BottomSheetPanel(
            cameraState = cameraState,
            settingsEngine = settingsEngine,
            onParamChange = { p, m, iso, ss, wb, f, fd, ev ->
                cameraService.updateParameter(p, m, iso, ss, wb, f, fd, ev)
            },
            onDismiss = { showFullControls = false }
        )
    }

    DisposableEffect(Unit) { onDispose { cameraService.stopPreview() } }
}

// ── Slider control bar ──────────────────────
@Composable
private fun ParamSlider(
    modifier: Modifier,
    cameraState: CameraState,
    settingsEngine: SettingsEngine,
    activeParam: CameraParam,
    onActiveParam: (CameraParam) -> Unit,
    onParam: (CameraParam, ControlMode, Int?, ShutterSpeed?, Int?, FocusMode?, Float?, Float?) -> Unit,
    onExpand: () -> Unit
) {
    val params = listOf(CameraParam.ISO, CameraParam.SHUTTER_SPEED, CameraParam.WHITE_BALANCE, CameraParam.EV_COMPENSATION)
    val paramLabels = mapOf(
        CameraParam.ISO to "ISO",
        CameraParam.SHUTTER_SPEED to "SPD",
        CameraParam.WHITE_BALANCE to "WB",
        CameraParam.EV_COMPENSATION to "EV"
    )

    // Current value & range for active param
    val isAuto = when (activeParam) {
        CameraParam.ISO -> cameraState.isoMode == ControlMode.AUTO
        CameraParam.SHUTTER_SPEED -> cameraState.shutterMode == ControlMode.AUTO
        CameraParam.WHITE_BALANCE -> cameraState.wbMode == ControlMode.AUTO
        CameraParam.EV_COMPENSATION -> cameraState.evMode == ControlMode.AUTO
        else -> true
    }

    val (minF, maxF, curF, displayF) = paramRange(cameraState, activeParam)

    Column(modifier = modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(8.dp)) {
        // Slider row
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Value display
            Text(displayF, color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(72.dp))

            // Slider
            var sliderVal by remember(curF, isAuto) { mutableFloatStateOf(curF) }
            Slider(
                value = sliderVal,
                onValueChange = {
                    sliderVal = it
                },
                onValueChangeFinished = {
                    if (!isAuto) {
                        val v = sliderVal
                        val valToSet = when (activeParam) {
                            CameraParam.ISO -> v.toInt()
                            CameraParam.SHUTTER_SPEED -> {
                                val idx = ((v / (maxF - minF) * (ShutterValues.values.size - 1)).toInt()).coerceIn(0, ShutterValues.values.size - 1)
                                val ss = ShutterValues.values[idx]
                                onParam(activeParam, cameraState.shutterMode, null, ss, null, null, null, null)
                                return@Slider
                            }
                            CameraParam.WHITE_BALANCE -> v.toInt()
                            CameraParam.EV_COMPENSATION -> {
                                val ev = (v * 2f).roundToInt() / 2f
                                onParam(activeParam, cameraState.evMode, null, null, null, null, null, ev)
                                return@Slider
                            }
                            else -> v.toInt()
                        }
                        when (activeParam) {
                            CameraParam.ISO -> onParam(activeParam, cameraState.isoMode, valToSet, null, null, null, null, null)
                            CameraParam.WHITE_BALANCE -> onParam(activeParam, cameraState.wbMode, null, null, valToSet, null, null, null)
                            else -> {}
                        }
                    }
                },
                valueRange = minF..maxF,
                enabled = !isAuto,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF4CAF50),
                    activeTrackColor = Color(0xFF4CAF50),
                    inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                )
            )

            // AUTO / MANUAL toggle
            Text(
                if (isAuto) "AUTO" else "MANUAL",
                color = if (isAuto) Color.Gray else Color(0xFF4CAF50),
                fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    when (activeParam) {
                        CameraParam.ISO -> { settingsEngine.toggleMode(CameraParam.ISO); onParam(CameraParam.ISO, settingsEngine.getMode(CameraParam.ISO), cameraState.iso, null, null, null, null, null) }
                        CameraParam.SHUTTER_SPEED -> { settingsEngine.toggleMode(CameraParam.SHUTTER_SPEED); onParam(CameraParam.SHUTTER_SPEED, settingsEngine.getMode(CameraParam.SHUTTER_SPEED), null, cameraState.shutterSpeed, null, null, null, null) }
                        CameraParam.WHITE_BALANCE -> { settingsEngine.toggleMode(CameraParam.WHITE_BALANCE); onParam(CameraParam.WHITE_BALANCE, settingsEngine.getMode(CameraParam.WHITE_BALANCE), null, null, cameraState.whiteBalance, null, null, null) }
                        CameraParam.EV_COMPENSATION -> { settingsEngine.toggleMode(CameraParam.EV_COMPENSATION); onParam(CameraParam.EV_COMPENSATION, settingsEngine.getMode(CameraParam.EV_COMPENSATION), null, null, null, null, null, cameraState.evCompensation) }
                        else -> {}
                    }
                }.padding(horizontal = 8.dp, vertical = 4.dp)
                    .background(if (isAuto) Color.Transparent else Color(0xFF4CAF50).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            )
        }

        // Param selector tabs
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            params.forEach { p ->
                val active = p == activeParam
                Text(
                    paramLabels[p] ?: "?",
                    color = if (active) Color(0xFF4CAF50) else Color.Gray,
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { onActiveParam(p) }.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text("...", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { onExpand() }.padding(horizontal = 4.dp))
        }
    }
}

private fun paramRange(state: CameraState, param: CameraParam): Pair4 {
    return when (param) {
        CameraParam.ISO -> Pair4(
            IsoValues.values.first().toFloat(), IsoValues.values.last().toFloat(),
            state.iso.toFloat(), state.iso.toString()
        )
        CameraParam.SHUTTER_SPEED -> Pair4(
            0f, (ShutterValues.values.size - 1).toFloat(),
            ShutterValues.values.indexOf(state.shutterSpeed).toFloat(),
            state.shutterSpeed.display
        )
        CameraParam.WHITE_BALANCE -> Pair4(
            WbValues.values.first().toFloat(), WbValues.values.last().toFloat(),
            state.whiteBalance.toFloat(), "${state.whiteBalance}K"
        )
        CameraParam.EV_COMPENSATION -> Pair4(
            -2f, 2f, state.evCompensation, String.format("%+.1f", state.evCompensation)
        )
        else -> Pair4(0f, 100f, 0f, "0")
    }
}

private data class Pair4(val first: Float, val second: Float, val third: Float, val fourth: String)

// ── Slider param chip (replaces old ParamStrip) ──

@Composable
private fun LensBadge(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (active) Color(0xFF4CAF50).copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.4f),
        shape = CircleShape,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ── CENTER-CROP transform ─────────────────
private fun TextureView.applyCropTransform(cameraService: CameraService) {
    val vw = width.toFloat(); val vh = height.toFloat()
    if (vw == 0f || vh == 0f) return
    val chars = cameraService.getCameraCharacteristics() ?: return
    val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return
    val sizes = map.getOutputSizes(SurfaceTexture::class.java) ?: return
    val ps = sizes.maxByOrNull { it.width * it.height } ?: return
    val orient = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

    val matrix = Matrix()
    val viewRect = android.graphics.RectF(0f, 0f, vw, vh)

    if (orient == 90 || orient == 270) {
        // Camera outputs landscape (w×h). Rotate to portrait (h×w) for view.
        val bufRect = android.graphics.RectF(0f, 0f, ps.height.toFloat(), ps.width.toFloat())
        val cx = viewRect.centerX(); val cy = viewRect.centerY()
        bufRect.offset(cx - bufRect.centerX(), cy - bufRect.centerY())
        matrix.setRectToRect(viewRect, bufRect, Matrix.ScaleToFit.FILL)
        val s = max(vh / ps.height.toFloat(), vw / ps.width.toFloat())
        matrix.postScale(s, s, cx, cy)
    } else {
        val bufRect = android.graphics.RectF(0f, 0f, ps.width.toFloat(), ps.height.toFloat())
        matrix.setRectToRect(viewRect, bufRect, Matrix.ScaleToFit.CENTER)
    }
    setTransform(matrix)
    Log.d("RawSight", "Transform: preview=${ps.width}x${ps.height} view=${vw}x${vh} orient=$orient")
}
