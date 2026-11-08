package com.panocapture.guided360.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.panocapture.guided360.domain.math.Quaternion
import com.panocapture.guided360.domain.model.CaptureTarget
import com.panocapture.guided360.domain.model.Session
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object SessionExporter {

    fun exportToDownloads(context: Context, session: Session, posesMap: Map<String, Quaternion>): String {
        val safeName = session.sceneName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val folderName = "Pano_${safeName}_${session.createdAtEpochMs}"

        val sessionDir = File(context.filesDir, "sessions/${session.id}")
        val photosDir = File(sessionDir, "photos")

        val posesJsonStr = generatePosesJson(session, posesMap)
        val readmeStr = generateReadme()

        val capturedTargets = session.targets.filter { it.photoFileName != null }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver

            // 1. Write poses.json
            writeFileToDownloads(resolver, folderName, "poses.json", "application/json") { out ->
                out.write(posesJsonStr.toByteArray())
            }

            // 2. Write README.txt
            writeFileToDownloads(resolver, folderName, "README.txt", "text/plain") { out ->
                out.write(readmeStr.toByteArray())
            }

            // 3. Write photos
            for (t in capturedTargets) {
                val fName = t.photoFileName ?: continue
                val srcFile = File(photosDir, fName)
                if (srcFile.exists()) {
                    writeFileToDownloads(resolver, folderName, fName, "image/jpeg") { out ->
                        srcFile.inputStream().use { it.copyTo(out) }
                    }
                }
            }
        } else {
            // Legacy Storage
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadDir, "PanoCapture/$folderName").apply { mkdirs() }

            File(targetDir, "poses.json").writeText(posesJsonStr)
            File(targetDir, "README.txt").writeText(readmeStr)

            for (t in capturedTargets) {
                val fName = t.photoFileName ?: continue
                val srcFile = File(photosDir, fName)
                if (srcFile.exists()) {
                    srcFile.copyTo(File(targetDir, fName), overwrite = true)
                }
            }
        }

        return "Download/PanoCapture/$folderName"
    }

    private fun writeFileToDownloads(
        resolver: android.content.ContentResolver,
        subFolder: String,
        displayName: String,
        mimeType: String,
        writeBlock: (OutputStream) -> Unit
    ) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PanoCapture/$subFolder")
            }
        }
        val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            resolver.openOutputStream(it)?.use { out -> writeBlock(out) }
        }
    }

    private fun generatePosesJson(session: Session, posesMap: Map<String, Quaternion>): String {
        val root = JSONObject()
        root.put("schema_version", "1.0.0")
        root.put("scene_name", session.sceneName)
        root.put("session_id", session.id)
        root.put("created_at_epoch_ms", session.createdAtEpochMs)

        val cameraObj = JSONObject().apply {
            put("lens_facing", session.intrinsics.lensFacing)
            put("horizontal_fov_degrees", session.intrinsics.horizontalFovDeg)
            put("vertical_fov_degrees", session.intrinsics.verticalFovDeg)
            put("focal_length_mm", session.intrinsics.focalLengthMm)
            put("sensor_width_mm", session.intrinsics.sensorWidthMm)
            put("sensor_height_mm", session.intrinsics.sensorHeightMm)
            put("image_width", session.intrinsics.imageWidth)
            put("image_height", session.intrinsics.imageHeight)
            put("ae_locked", session.isAeLocked)
        }
        root.put("camera", cameraObj)

        val photosArray = JSONArray()
        var index = 1
        for (target in session.targets) {
            val fileName = target.photoFileName ?: continue
            val q = posesMap[target.id] ?: Quaternion.IDENTITY
            val (yaw, pitch, roll) = q.toEulerAnglesDegrees()

            val pObj = JSONObject().apply {
                put("index", index++)
                put("filename", fileName)
                put("target_id", target.id)
                put("ring", target.ringType.name)
                put("capture_timestamp_ms", target.captureTimestampMs ?: session.createdAtEpochMs)
                put("quaternion_wxyz", JSONArray(listOf(q.w, q.x, q.y, q.z)))
                put("angles_degrees", JSONObject().apply {
                    put("yaw", yaw)
                    put("pitch", pitch)
                    put("roll", roll)
                })
            }
            photosArray.put(pObj)
        }
        root.put("photos", photosArray)

        return root.toString(2)
    }

    private fun generateReadme(): String {
        return """
        ================================================================================
        PanoCapture 360 - Scene Orientation & Metadata Specification
        ================================================================================

        1. COORDINATE REFERENCE FRAMES:
           - World Coordinate Frame: Right-handed Cartesian reference frame.
             * +X points East (horizontal)
             * +Y points North (horizontal)
             * +Z points Zenith (straight up toward the ceiling)
           - Camera Coordinate Frame (Portrait mode, looking through rear camera):
             * +X_cam points to the right of the phone display.
             * +Y_cam points to the top edge of the phone.
             * -Z_cam points along the optical axis, outward from the rear lens.

        2. CAMERA POSE REPRESENTATION:
           - "quaternion_wxyz": [w, x, y, z] is a normalized Hamilton quaternion representing
             the 3D rotation from Camera Coordinates to World Coordinates:
             v_world = Q * v_camera * Q_conjugate
           - "angles_degrees":
             * yaw: Azimuth angle [0..360 deg) clockwise from North (+Y).
             * pitch: Elevation angle [-90..+90 deg] above horizon (+90 deg is straight up).
             * roll: Sideways tilt [-180..+180 deg] around the camera's optical axis.

        3. INTEGRATION WITH PANORAMA STITCHERS:
           - PTGui / Hugin / Panotools:
             Map yaw -> Yaw, pitch -> Pitch, roll -> Roll.
             The FOV in camera intrinsics corresponds to portrait orientation.
           - OpenSfM / COLMAP:
             The camera rotation matrix R = QuaternionToMatrix(quaternion_wxyz).
        ================================================================================
        """.trimIndent()
    }
}
