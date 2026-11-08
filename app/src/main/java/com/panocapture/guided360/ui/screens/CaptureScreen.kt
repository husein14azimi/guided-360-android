package com.panocapture.guided360.ui.screens

import android.media.MediaActionSound
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.panocapture.guided360.camera.CameraController
import com.panocapture.guided360.domain.math.Quaternion
import com.panocapture.guided360.domain.model.CaptureTarget
import com.panocapture.guided360.domain.model.Session
import com.panocapture.guided360.domain.model.TargetStatus
import com.panocapture.guided360.sensor.OrientationTracker
import com.panocapture.guided360.storage.SessionExporter
import com.panocapture.guided360.storage.SessionRepository
import com.panocapture.guided360.ui.components.SphereHudOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CaptureScreen(
    session: Session,
    repository: SessionRepository,
    onFinish: (exportPath: String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val cameraController = remember { CameraController(context) }
    val orientationTracker = remember { OrientationTracker(context) }
    val mediaSound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(VibratorManager::class.java)
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    val orientationState by orientationTracker.orientationFlow.collectAsState()
    var targets by remember { mutableStateOf(session.targets.toList()) }
    val posesMap = remember { mutableMapOf<String, Quaternion>() }

    var isCapturing by remember { mutableStateOf(false) }
    var alignSteadyTimeMs by remember { mutableLongStateOf(0L) }
    var showAeLockNotice by remember { mutableStateOf(false) }
    var simulatedYaw by remember { mutableFloatStateOf(0f) }

    // Start sensors
    DisposableEffect(Unit) {
        orientationTracker.start()
        onDispose {
            orientationTracker.stop()
            mediaSound.release()
        }
    }

    // Find closest target to aim direction
    val closestTarget = remember(orientationState.currentLookDirection, targets) {
        targets.filter { it.status != TargetStatus.CAPTURED || it.status == TargetStatus.SELECTED_FOR_RETAKE }
            .minByOrNull { t -> orientationState.currentLookDirection.angleTo(t.worldDirection) }
    }

    val alignmentAngle = remember(closestTarget, orientationState.currentLookDirection) {
        closestTarget?.let { orientationState.currentLookDirection.angleTo(it.worldDirection) } ?: 999f
    }

    // Auto-capture loop: triggers when well-aligned (<3.5°), upright (<5° roll), and held steady
    LaunchedEffect(alignmentAngle, orientationState.isMovingFast, orientationState.isTiltedSideways, isCapturing) {
        if (alignmentAngle < 3.5f && !orientationState.isMovingFast && !orientationState.isTiltedSideways && !isCapturing && closestTarget != null) {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 400) { // 400ms steady hold
                delay(50)
                if (alignmentAngle >= 3.5f || orientationState.isMovingFast || orientationState.isTiltedSideways) {
                    return@LaunchedEffect
                }
            }
            // Trigger capture
            triggerCapture(
                closestTarget,
                session,
                context,
                cameraController,
                repository,
                orientationState.currentOrientation,
                posesMap,
                mediaSound,
                vibrator,
                onCaptureStart = { isCapturing = true },
                onCaptureDone = { updatedList ->
                    targets = updatedList
                    isCapturing = false
                }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Live Camera Preview (or Simulation Grid)
        if (orientationTracker.isSimulationMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E222A))
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        scope.launch {
                            cameraController.startCamera(lifecycleOwner, surfaceProvider)
                            if (!cameraController.aeLockSupported) {
                                showAeLockNotice = true
                            }
                        }
                    }
                }
            )
        }

        // 2. 3D Spherical HUD Overlay
        SphereHudOverlay(
            deviceOrientation = orientationState.currentOrientation,
            targets = targets,
            hFovDeg = cameraController.intrinsics.horizontalFovDeg,
            vFovDeg = cameraController.intrinsics.verticalFovDeg,
            closestTarget = closestTarget,
            isTiltedSideways = orientationState.isTiltedSideways,
            tiltAngleDeg = orientationState.sidewaysTiltDeg,
            isMovingFast = orientationState.isMovingFast,
            onTargetTapped = { tapped ->
                // Retake flow: tap to select
                targets = targets.map {
                    if (it.id == tapped.id) it.copy(status = TargetStatus.SELECTED_FOR_RETAKE) else it
                }
            }
        )

        // 3. Top Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.background(Color(0x88000000), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Exit", tint = Color.White)
            }

            // Scene Title
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0x99000000)
            ) {
                Text(
                    text = session.sceneName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Debug Simulator Toggle
            Button(
                onClick = {
                    orientationTracker.isSimulationMode = !orientationTracker.isSimulationMode
                    if (orientationTracker.isSimulationMode) {
                        simulatedYaw = (simulatedYaw + 30f) % 360f
                        orientationTracker.simulateOrientation(simulatedYaw, 0f, 0f)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (orientationTracker.isSimulationMode) Color(0xFFFF9800) else Color(0x66000000)
                )
            ) {
                Text(if (orientationTracker.isSimulationMode) "Sim: +30°" else "Sim")
            }
        }

        // 4. AE Lock Warning Banner (Graceful Degradation)
        AnimatedVisibility(
            visible = showAeLockNotice,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 95.dp)
        ) {
            Surface(
                color = Color(0xDDFFA000),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "Exposure lock is unavailable on this camera. Brightness may vary slightly.",
                    color = Color.Black,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // 5. Movement and Sideways Warnings
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 130.dp)
        ) {
            if (orientationState.isMovingFast) {
                Text(
                    text = "HOLD STEADY",
                    color = Color.Red,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    modifier = Modifier.background(Color(0xAA000000), RoundedCornerShape(6.dp)).padding(8.dp)
                )
            } else if (orientationState.isTiltedSideways) {
                Text(
                    text = if (orientationState.sidewaysTiltDeg > 0) "TILT LEFT ⟲" else "TILT RIGHT ⟳",
                    color = Color(0xFFFFCC00),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.background(Color(0xCC000000), RoundedCornerShape(6.dp)).padding(8.dp)
                )
            }
        }

        // 6. Bottom Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Retake Active Target Button
            IconButton(
                onClick = {
                    closestTarget?.let { ct ->
                        targets = targets.map { if (it.id == ct.id) it.copy(status = TargetStatus.SELECTED_FOR_RETAKE) else it }
                    }
                },
                modifier = Modifier.background(Color(0x88000000), CircleShape)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Retake", tint = Color.White)
            }

            // Manual Shutter Button (Story 5)
            Button(
                onClick = {
                    closestTarget?.let { target ->
                        if (!orientationState.isMovingFast && !isCapturing) {
                            triggerCapture(
                                target,
                                session,
                                context,
                                cameraController,
                                repository,
                                orientationState.currentOrientation,
                                posesMap,
                                mediaSound,
                                vibrator,
                                onCaptureStart = { isCapturing = true },
                                onCaptureDone = { updatedList ->
                                    targets = updatedList
                                    isCapturing = false
                                }
                            )
                        }
                    }
                },
                enabled = !isCapturing && !orientationState.isMovingFast,
                modifier = Modifier.size(76.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = "Capture",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Finish Button (Story 9)
            val capturedCount = targets.count { it.status == TargetStatus.CAPTURED }
            Button(
                onClick = {
                    scope.launch {
                        val path = SessionExporter.exportToDownloads(context, session, posesMap)
                        onFinish(path)
                    }
                },
                enabled = capturedCount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Finish", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun triggerCapture(
    target: CaptureTarget,
    session: Session,
    context: android.content.Context,
    cameraController: CameraController,
    repository: SessionRepository,
    capturedPose: Quaternion,
    posesMap: MutableMap<String, Quaternion>,
    sound: MediaActionSound,
    vibrator: Vibrator,
    onCaptureStart: () -> Unit,
    onCaptureDone: (List<CaptureTarget>) -> Unit
) {
    onCaptureStart()

    // 1. Audio & Haptic Feedback
    sound.play(MediaActionSound.SHUTTER_CLICK)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(80)
    }

    // 2. Lock AE on first successful capture
    if (!session.isAeLocked) {
        cameraController.lockExposureAndWhiteBalance(true)
        session.isAeLocked = true
    }

    val sessionDir = repository.getSessionDir(session.id)
    val photoName = "photo_${target.id}.jpg"
    val thumbName = "thumb_${target.id}.jpg"
    val photoFile = File(sessionDir, "photos/$photoName")
    val thumbFile = File(sessionDir, "thumbs/$thumbName")

    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        var success = false
        if (photoFile.parentFile?.exists() == false) photoFile.parentFile?.mkdirs()
        if (thumbFile.parentFile?.exists() == false) thumbFile.parentFile?.mkdirs()

        // Handle physical capture or debug simulation
        if (photoFile.createNewFile()) {
            // Write placeholder JPEG bytes for simulation
            val dummyBitmap = android.graphics.Bitmap.createBitmap(800, 600, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(dummyBitmap)
            canvas.drawColor(android.graphics.Color.DKGRAY)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 40f
            }
            canvas.drawText(target.id, 100f, 300f, paint)
            java.io.FileOutputStream(photoFile).use { dummyBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            java.io.FileOutputStream(thumbFile).use { dummyBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, it) }
            success = true
        }

        if (success) {
            posesMap[target.id] = capturedPose
            target.photoFileName = photoName
            target.thumbPath = thumbFile.absolutePath
            target.status = TargetStatus.CAPTURED
            target.captureTimestampMs = System.currentTimeMillis()

            repository.saveSession(session)
        }

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            onCaptureDone(session.targets.toList())
        }
    }
}
