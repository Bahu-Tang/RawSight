package com.rawsight.ui

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
        val previewRatio = remember(cameraService) {
            val chars = cameraService.getCameraCharacteristics()
            val map = chars?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(SurfaceTexture::class.java)
            val ps = sizes?.maxByOrNull { it.width * it.height }
            if (ps != null && ps.width > 0 && ps.height > 0) {
                // Camera2 outputs landscape; effective display ratio is h/w for portrait
                ps.height.toFloat() / ps.width.toFloat()
            } else {
                4f / 3f // default fallback
            }
        }

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
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
                    .aspectRatio(previewRatio)
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

        // ═══════════ Param control strip (above shutter) ═══════════
        ParamStrip(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
            cameraState = cameraState,
            settingsEngine = settingsEngine,
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

// ── Compact param strip ────────────────────────────
@Composable
private fun ParamStrip(
    modifier: Modifier,
    cameraState: CameraState,
    settingsEngine: SettingsEngine,
    onParam: (CameraParam, ControlMode, Int?, ShutterSpeed?, Int?, FocusMode?, Float?, Float?) -> Unit,
    onExpand: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ParamChip("ISO", cameraState.iso.toString(), cameraState.isoMode,
            { settingsEngine.toggleMode(CameraParam.ISO); onParam(CameraParam.ISO, settingsEngine.getMode(CameraParam.ISO), null, null, null, null, null, null) },
            if (cameraState.isoMode == ControlMode.MANUAL) {
                { val n = IsoValues.values[(IsoValues.values.indexOf(cameraState.iso) + 1) % IsoValues.values.size]; onParam(CameraParam.ISO, cameraState.isoMode, n, null, null, null, null, null) }
            } else null)

        ParamChip("SPD", cameraState.shutterSpeed.display, cameraState.shutterMode,
            { settingsEngine.toggleMode(CameraParam.SHUTTER_SPEED); onParam(CameraParam.SHUTTER_SPEED, settingsEngine.getMode(CameraParam.SHUTTER_SPEED), null, null, null, null, null, null) },
            if (cameraState.shutterMode == ControlMode.MANUAL) {
                { val n = ShutterValues.values[(ShutterValues.values.indexOf(cameraState.shutterSpeed) + 1) % ShutterValues.values.size]; onParam(CameraParam.SHUTTER_SPEED, cameraState.shutterMode, null, n, null, null, null, null) }
            } else null)

        ParamChip("WB", "${cameraState.whiteBalance}K", cameraState.wbMode,
            { settingsEngine.toggleMode(CameraParam.WHITE_BALANCE); onParam(CameraParam.WHITE_BALANCE, settingsEngine.getMode(CameraParam.WHITE_BALANCE), null, null, null, null, null, null) },
            null)

        ParamChip("EV", String.format("%+.1f", cameraState.evCompensation), cameraState.evMode,
            { settingsEngine.toggleMode(CameraParam.EV_COMPENSATION); onParam(CameraParam.EV_COMPENSATION, settingsEngine.getMode(CameraParam.EV_COMPENSATION), null, null, null, null, null, null) },
            if (cameraState.evMode == ControlMode.MANUAL) {
                { val n = EvValues.values[(EvValues.values.indexOf(cameraState.evCompensation) + 1) % EvValues.values.size]; onParam(CameraParam.EV_COMPENSATION, cameraState.evMode, null, null, null, null, null, n) }
            } else null)

        // Expand button
        Text("...", color = Color.Gray, fontSize = 16.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable { onExpand() }.padding(4.dp))
    }
}

@Composable
private fun ParamChip(
    label: String, value: String, mode: ControlMode,
    onToggle: () -> Unit, onCycle: (() -> Unit)?
) {
    val modeColor = if (mode == ControlMode.MANUAL) Color(0xFF4CAF50) else Color.Gray
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(label, color = modeColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
            modifier = if (onCycle != null) Modifier.clickable { onCycle() } else Modifier)
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
