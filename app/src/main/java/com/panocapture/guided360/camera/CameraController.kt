package com.panocapture.guided360.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.panocapture.guided360.domain.model.CameraIntrinsics
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCancellableCoroutine
import kotlin.math.atan

class CameraController(private val context: Context) {

    private var imageCapture: ImageCapture? = null
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null

    var intrinsics: CameraIntrinsics = CameraIntrinsics()
        private set

    var aeLockSupported: Boolean = false
        private set

    @OptIn(ExperimentalCamera2Interop::class)
    suspend fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ): Boolean = suspendCancellableCoroutine { cont ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(surfaceProvider)
                }

                val captureBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(android.view.Surface.ROTATION_0)

                imageCapture = captureBuilder.build()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo

                queryIntrinsics(cameraInfo!!)
                cont.resume(true)
            } catch (e: Exception) {
                Log.e("CameraController", "Camera bind failed", e)
                cont.resume(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun queryIntrinsics(camInfo: CameraInfo) {
        val c2Info = Camera2CameraInfo.from(camInfo)
        val focalLengths = c2Info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val sensorSize = c2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val aeModes = c2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)

        aeLockSupported = c2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true

        val fl = focalLengths?.firstOrNull() ?: 4.5f
        val sw = sensorSize?.width ?: 5.6f
        val sh = sensorSize?.height ?: 4.2f

        // Phone in portrait: sensor width maps to vertical FOV, sensor height to horizontal FOV
        val hFov = Math.toDegrees(2.0 * atan((sh / (2.0 * fl)).toDouble())).toFloat()
        val vFov = Math.toDegrees(2.0 * atan((sw / (2.0 * fl)).toDouble())).toFloat()

        intrinsics = CameraIntrinsics(
            horizontalFovDeg = hFov.coerceIn(40f, 85f),
            verticalFovDeg = vFov.coerceIn(50f, 95f),
            focalLengthMm = fl,
            sensorWidthMm = sw,
            sensorHeightMm = sh
        )
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun lockExposureAndWhiteBalance(lock: Boolean) {
        val cControl = cameraControl ?: return
        try {
            val c2Control = androidx.camera.camera2.interop.Camera2CameraControl.from(cControl)
            val captureRequestOptions = androidx.camera.camera2.interop.CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, lock)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, lock)
                .build()
            c2Control.setCaptureRequestOptions(captureRequestOptions)
        } catch (e: Exception) {
            Log.w("CameraController", "AE/AWB lock unsupported or failed: ${e.message}")
        }
    }

    suspend fun takePhoto(outputFile: File, thumbFile: File): Boolean = suspendCancellableCoroutine { cont ->
        val capture = imageCapture
        if (capture == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    generateThumbnail(outputFile, thumbFile)
                    cont.resume(true)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraController", "Photo save failed", exception)
                    cont.resume(false)
                }
            }
        )
    }

    private fun generateThumbnail(fullImageFile: File, thumbFile: File) {
        try {
            val options = BitmapFactory.Options().apply { inSampleSize = 8 }
            val bitmap = BitmapFactory.decodeFile(fullImageFile.absolutePath, options) ?: return
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
        } catch (e: Exception) {
            Log.e("CameraController", "Thumbnail error", e)
        }
    }
}
