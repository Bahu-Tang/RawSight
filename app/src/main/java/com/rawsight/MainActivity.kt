package com.rawsight

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.rawsight.ai.StubAIAnalyzer
import com.rawsight.camera.CameraService
import com.rawsight.camera.LensManager
import com.rawsight.decision.DecisionEngine
import com.rawsight.imu.IMUService
import com.rawsight.settings.SettingsEngine
import com.rawsight.ui.CameraScreen
import com.rawsight.ui.theme.RawSightTheme
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    // ── Services (initialized before setContent) ───────
    private lateinit var cameraService: CameraService
    private lateinit var lensManager: LensManager
    private lateinit var imuService: IMUService
    private lateinit var aiAnalyzer: StubAIAnalyzer
    private lateinit var decisionEngine: DecisionEngine
    private lateinit var settingsEngine: SettingsEngine

    // ── Reactive state ─────────────────────────────────
    private val cameraReady = mutableStateOf(false)
    private val cameraError = mutableStateOf<String?>(null)
    private val activeCameraId = mutableStateOf("")

    // ── Permission launcher ───────────────────────────
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openCamera() else cameraError.value = "Camera permission required"
        }

    // ───────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init all services early — camera opens only after permission
        cameraService = CameraService(this).apply {
            onCameraOpened = { cameraReady.value = true }
            onCameraError = { msg -> cameraError.value = msg }
            onCameraDisconnected = {
                cameraReady.value = false
                cameraError.value = "Camera disconnected"
            }
        }
        lensManager = LensManager(this)
        imuService = IMUService(this)
        aiAnalyzer = StubAIAnalyzer()
        decisionEngine = DecisionEngine(aiAnalyzer)
        settingsEngine = SettingsEngine()

        setContent {
            val ready by cameraReady
            val error by cameraError
            val cameraId by activeCameraId

            RawSightTheme {
                CameraScreen(
                    modifier = Modifier.fillMaxSize(),
                    cameraService = cameraService,
                    lensManager = lensManager,
                    decisionEngine = decisionEngine,
                    settingsEngine = settingsEngine,
                    aiAnalyzer = aiAnalyzer,
                    activeCameraId = cameraId,
                    cameraReady = ready,
                    cameraError = error,
                    onOpenCamera = { id -> openCamera(id) },
                    onSwitchLens = { id -> switchLens(id) },
                    onCloseCamera = { closeCamera() }
                )
            }
        }

        checkPermission()
    }

    // ── Permission ────────────────────────────────────
    private fun checkPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED -> openCamera()
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Toast.makeText(this, "Camera access is required for RawSight", Toast.LENGTH_LONG).show()
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── Camera ────────────────────────────────────────
    private fun openCamera(cameraId: String? = null) {
        val id = cameraId ?: lensManager.getMainRearCamera()?.cameraId
        if (id == null) {
            cameraError.value = "No rear camera found"
            return
        }
        cameraReady.value = false
        activeCameraId.value = id
        cameraService.openCamera(id)
        imuService.start()
        aiAnalyzer.start()

        lifecycleScope.launch {
            imuService.imuFlow.collect { d -> decisionEngine.updateFromIMU(d) }
        }
    }

    private fun switchLens(id: String) {
        closeCamera()
        openCamera(id)
    }

    private fun closeCamera() {
        cameraReady.value = false
        imuService.stop()
        aiAnalyzer.stop()
        cameraService.close()
    }

    // ── Lifecycle ─────────────────────────────────────
    override fun onResume() {
        super.onResume()
        // Preview resumed by CameraScreen when TextureView surface is ready
    }

    override fun onPause() {
        super.onPause()
        cameraService.stopPreview()
        imuService.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        closeCamera()
        cameraService.release()
    }
}
