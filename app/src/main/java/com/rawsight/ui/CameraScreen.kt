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
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                cameraService.startPreview(Surface(st))
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
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
                cameraService.updateParameter(p, m, iso, ss, wb, f, fd, ev, null, null)
            },
            onTint = { tint -> cameraService.updateParameter(CameraParam.WHITE_BALANCE, null, null, null, null, null, null, null, tint, null) },
            onZoom = { zoom -> cameraService.updateParameter(CameraParam.EV_COMPENSATION, null, null, null, null, null, null, null, null, zoom) }
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
    onTint: (Float) -> Unit,
    onZoom: (Float) -> Unit
) {
    val allParams = listOf(CameraParam.ISO, CameraParam.SHUTTER_SPEED, CameraParam.WHITE_BALANCE, CameraParam.FOCUS, CameraParam.EV_COMPENSATION, CameraParam.SHUTTER_SPEED /* ZOOM placeholder */)
    val uniqueParams = allParams.distinct()
    val paramLabels = mapOf(
        CameraParam.ISO to "ISO", CameraParam.SHUTTER_SPEED to "SPD",
        CameraParam.WHITE_BALANCE to "WB", CameraParam.FOCUS to "FOC",
        CameraParam.EV_COMPENSATION to "EV"
    )

    val isAuto = when (activeParam) {
        CameraParam.ISO -> cameraState.isoMode == ControlMode.AUTO
        CameraParam.SHUTTER_SPEED -> cameraState.shutterMode == ControlMode.AUTO
        CameraParam.WHITE_BALANCE -> cameraState.wbMode == ControlMode.AUTO
        CameraParam.FOCUS -> cameraState.focusControlMode == ControlMode.AUTO
        CameraParam.EV_COMPENSATION -> cameraState.evMode == ControlMode.AUTO
        else -> true
    }

    // ZOOM is always manual
    var zoomActive by remember { mutableStateOf(false) }
    val isZoom = zoomActive
    val sliderEnabled = isZoom || !isAuto

    val steps: List<Float> = when {
        isZoom -> (10..50).map { it / 10f } // 1.0x to 5.0x
        activeParam == CameraParam.ISO -> IsoValues.values.map { it.toFloat() }
        activeParam == CameraParam.SHUTTER_SPEED -> ShutterValues.values.indices.map { it.toFloat() }
        activeParam == CameraParam.WHITE_BALANCE -> WbValues.values.map { it.toFloat() }
        activeParam ==         CameraParam.FOCUS -> listOf(-1f) // "continuous" signal
        activeParam == CameraParam.EV_COMPENSATION -> (-4..4).map { it / 2f }
        else -> listOf(0f)
    }

    val curIdx: Int = when {
        isZoom -> ((cameraState.zoomLevel * 10f).roundToInt() - 10).coerceIn(0, steps.size - 1)
        steps.firstOrNull() == -1f -> 0 // continuous, not index-based
        activeParam == CameraParam.ISO -> IsoValues.values.indexOf(cameraState.iso).coerceAtLeast(0)
        activeParam == CameraParam.SHUTTER_SPEED -> ShutterValues.values.indexOf(cameraState.shutterSpeed).coerceAtLeast(0)
        activeParam == CameraParam.WHITE_BALANCE -> WbValues.values.indexOf(cameraState.whiteBalance).coerceAtLeast(0)
        activeParam == CameraParam.FOCUS -> {
            val fd = cameraState.focusDistance
            when { fd <= 0.05f -> 0; fd <= 0.15f -> 1; fd <= 0.25f -> 2; fd <= 0.4f -> 3; fd <= 0.6f -> 4; fd <= 0.85f -> 5; else -> 6 }
        }
        activeParam == CameraParam.EV_COMPENSATION -> ((cameraState.evCompensation * 2f).roundToInt() + 4).coerceIn(0, 8)
        else -> 0
    }

    val displayF: String = when {
        isZoom -> String.format("%.1fx", cameraState.zoomLevel)
        activeParam == CameraParam.ISO -> cameraState.iso.toString()
        activeParam == CameraParam.SHUTTER_SPEED -> cameraState.shutterSpeed.display
        activeParam == CameraParam.WHITE_BALANCE -> "${cameraState.whiteBalance}K"
        activeParam ==         CameraParam.FOCUS -> if (cameraState.focusMode == FocusMode.AF) "AF" else String.format("%.2f", cameraState.focusDistance)
        activeParam == CameraParam.EV_COMPENSATION -> String.format("%+.1f", cameraState.evCompensation)
        else -> "?"
    }

    val evDisabledNote = (activeParam == CameraParam.EV_COMPENSATION &&
        (cameraState.isoMode == ControlMode.MANUAL || cameraState.shutterMode == ControlMode.MANUAL))

    Column(modifier = modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(8.dp)) {
        // Slider row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(displayF, color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(64.dp))

            if (evDisabledNote) {
                Text("EV needs AUTO exp", color = Color(0xFFFFC107), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            } else {
                val isContinuous = steps.firstOrNull() == -1f
                if (isContinuous && activeParam == CameraParam.FOCUS) {
                    // Continuous focus slider
                    var focusVal by remember(cameraState.focusDistance) { mutableFloatStateOf(cameraState.focusDistance) }
                    Slider(
                        value = focusVal, onValueChange = { focusVal = it },
                        onValueChangeFinished = {
                            if (!isAuto) {
                                val fm = if (cameraState.focusControlMode == ControlMode.AUTO) FocusMode.AF else FocusMode.MF
                                onParam(activeParam, cameraState.focusControlMode, null, null, null, fm, focusVal, null)
                            }
                        },
                        valueRange = 0f..1f, enabled = sliderEnabled,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF4CAF50), activeTrackColor = Color(0xFF4CAF50), inactiveTrackColor = Color.Gray.copy(alpha = 0.3f))
                    )
                } else {
                    // Discrete step slider
                    var sliderIdx by remember(curIdx) { mutableIntStateOf(curIdx) }
                    Slider(
                        value = sliderIdx.toFloat(),
                        onValueChange = { sliderIdx = it.toInt().coerceIn(0, steps.size - 1) },
                        onValueChangeFinished = {
                            when {
                                isZoom -> onZoom(steps[sliderIdx])
                                activeParam == CameraParam.ISO && !isAuto -> onParam(activeParam, cameraState.isoMode, steps[sliderIdx].toInt(), null, null, null, null, null)
                                activeParam == CameraParam.SHUTTER_SPEED && !isAuto -> {
                                    val ss = ShutterValues.values[sliderIdx.coerceIn(0, ShutterValues.values.size - 1)]
                                    onParam(activeParam, cameraState.shutterMode, null, ss, null, null, null, null)
                                }
                                activeParam == CameraParam.WHITE_BALANCE && !isAuto -> onParam(activeParam, cameraState.wbMode, null, null, steps[sliderIdx].toInt(), null, null, null)
                                activeParam == CameraParam.EV_COMPENSATION && !isAuto -> onParam(activeParam, cameraState.evMode, null, null, null, null, null, steps[sliderIdx])
                            }
                        },
                        valueRange = 0f..(steps.size - 1).toFloat(),
                        enabled = sliderEnabled,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF4CAF50), activeTrackColor = Color(0xFF4CAF50), inactiveTrackColor = Color.Gray.copy(alpha = 0.3f))
                    )
                }
            }

            // AUTO/MANUAL (not for zoom)
            if (!isZoom) {
                Text(
                    if (isAuto) "AUTO" else "MANUAL",
                    color = if (isAuto) Color.Gray else Color(0xFF4CAF50),
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        when (activeParam) {
                            CameraParam.ISO -> { settingsEngine.toggleMode(CameraParam.ISO); onParam(CameraParam.ISO, settingsEngine.getMode(CameraParam.ISO), cameraState.iso, null, null, null, null, null) }
                            CameraParam.SHUTTER_SPEED -> { settingsEngine.toggleMode(CameraParam.SHUTTER_SPEED); onParam(CameraParam.SHUTTER_SPEED, settingsEngine.getMode(CameraParam.SHUTTER_SPEED), null, cameraState.shutterSpeed, null, null, null, null) }
                            CameraParam.WHITE_BALANCE -> { settingsEngine.toggleMode(CameraParam.WHITE_BALANCE); onParam(CameraParam.WHITE_BALANCE, settingsEngine.getMode(CameraParam.WHITE_BALANCE), null, null, cameraState.whiteBalance, null, null, null) }
                            CameraParam.FOCUS -> { settingsEngine.toggleMode(CameraParam.FOCUS); onParam(CameraParam.FOCUS, settingsEngine.getMode(CameraParam.FOCUS), null, null, null, cameraState.focusMode, cameraState.focusDistance, null) }
                            CameraParam.EV_COMPENSATION -> { settingsEngine.toggleMode(CameraParam.EV_COMPENSATION); onParam(CameraParam.EV_COMPENSATION, settingsEngine.getMode(CameraParam.EV_COMPENSATION), null, null, null, null, null, cameraState.evCompensation) }
                            else -> {}
                        }
                    }.padding(horizontal = 8.dp, vertical = 4.dp)
                        .background(if (isAuto) Color.Transparent else Color(0xFF4CAF50).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                )
            }
        }

        // Tint slider (only when WB is selected)
        if (activeParam == CameraParam.WHITE_BALANCE && !isAuto) {
            val tintSteps = (-5..5).map { it * 10f } // -50..+50
            val tintIdx = ((cameraState.wbTint / 10f).roundToInt() + 5).coerceIn(0, 10)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("Tint", color = if (cameraState.wbTint != 0f) Color(0xFF4CAF50) else Color.Gray,
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(64.dp))
                Text(String.format("%+.0f", cameraState.wbTint), color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(32.dp))
                var tIdx by remember(tintIdx) { mutableIntStateOf(tintIdx) }
                Slider(
                    value = tIdx.toFloat(), onValueChange = { tIdx = it.toInt().coerceIn(0, 10) },
                    onValueChangeFinished = { onTint(tintSteps[tIdx]) },
                    valueRange = 0f..10f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF64B5F6), activeTrackColor = Color(0xFF64B5F6), inactiveTrackColor = Color.Gray.copy(alpha = 0.3f))
                )
            }
        }

        // Param tabs
        val tabParams = listOf(CameraParam.ISO, CameraParam.SHUTTER_SPEED, CameraParam.WHITE_BALANCE, CameraParam.FOCUS, CameraParam.EV_COMPENSATION)
        val tabLabels = paramLabels + ("ZOOM" to "ZOOM") // hack to add ZOOM tab
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabParams.forEach { p ->
                val active = p == activeParam && !isZoom
                Text(tabLabels[p] ?: "?", color = if (active) Color(0xFF4CAF50) else Color.Gray,
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { onActiveParam(p) }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            // ZOOM tab
            Text("ZOOM", color = if (isZoom) Color(0xFF4CAF50) else Color.Gray,
                fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = if (isZoom) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { zoomActive = !zoomActive; onActiveParam(tabParams.first()) }.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

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
        matrix.setRectToRect(viewRect, bufRect, Matrix.ScaleToFit.CENTER)
    } else {
        val bufRect = android.graphics.RectF(0f, 0f, ps.width.toFloat(), ps.height.toFloat())
        matrix.setRectToRect(viewRect, bufRect, Matrix.ScaleToFit.CENTER)
    }
    setTransform(matrix)
    Log.d("RawSight", "Transform: preview=${ps.width}x${ps.height} view=${vw}x${vh} orient=$orient")
}
