package com.panocapture.guided360

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.panocapture.guided360.domain.model.CameraIntrinsics
import com.panocapture.guided360.domain.model.Session
import com.panocapture.guided360.storage.SessionRepository
import com.panocapture.guided360.ui.screens.CaptureScreen
import com.panocapture.guided360.ui.screens.SessionListScreen
import com.panocapture.guided360.ui.theme.Guided360Theme

class MainActivity : ComponentActivity() {

    private lateinit var sessionRepository: SessionRepository

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Camera permission is required to capture scenes", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionRepository = SessionRepository(applicationContext)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            Guided360Theme {
                var activeSession by remember { mutableStateOf<Session?>(null) }

                if (activeSession != null) {
                    CaptureScreen(
                        session = activeSession!!,
                        repository = sessionRepository,
                        onFinish = { exportPath ->
                            Toast.makeText(this, "Exported to $exportPath", Toast.LENGTH_LONG).show()
                            activeSession = null
                        },
                        onCancel = {
                            activeSession = null
                        }
                    )
                } else {
                    SessionListScreen(
                        repository = sessionRepository,
                        onStartNewSession = { name ->
                            val newSession = sessionRepository.createSession(name, CameraIntrinsics())
                            activeSession = newSession
                        },
                        onResumeSession = { resumed ->
                            activeSession = resumed
                        }
                    )
                }
            }
        }
    }
}
