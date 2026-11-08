package com.panocapture.guided360.storage

import android.content.Context
import com.panocapture.guided360.domain.math.TargetGridCalculator
import com.panocapture.guided360.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SessionRepository(private val context: Context) {

    private val baseDir: File = File(context.filesDir, "sessions").apply { mkdirs() }

    fun createSession(sceneName: String, intrinsics: CameraIntrinsics): Session {
        val sessionId = "session_${System.currentTimeMillis()}"
        val sessionDir = File(baseDir, sessionId).apply { mkdirs() }
        File(sessionDir, "photos").mkdirs()
        File(sessionDir, "thumbs").mkdirs()

        val targets = TargetGridCalculator.calculateTargets(
            intrinsics.horizontalFovDeg,
            intrinsics.verticalFovDeg
        ).toMutableList()

        val session = Session(
            id = sessionId,
            sceneName = sceneName,
            createdAtEpochMs = System.currentTimeMillis(),
            targets = targets,
            intrinsics = intrinsics
        )
        saveSession(session)
        return session
    }

    fun saveSession(session: Session) {
        val sessionDir = File(baseDir, session.id)
        val jsonFile = File(sessionDir, "session.json")
        val json = JSONObject().apply {
            put("id", session.id)
            put("sceneName", session.sceneName)
            put("createdAtEpochMs", session.createdAtEpochMs)
            put("isAeLocked", session.isAeLocked)

            val targetsArray = JSONArray()
            for (t in session.targets) {
                targetsArray.put(JSONObject().apply {
                    put("id", t.id)
                    put("ringType", t.ringType.name)
                    put("yawDeg", t.targetYawDeg)
                    put("pitchDeg", t.targetPitchDeg)
                    put("status", t.status.name)
                    put("photoFileName", t.photoFileName ?: JSONObject.NULL)
                    put("thumbPath", t.thumbPath ?: JSONObject.NULL)
                    put("timestampMs", t.captureTimestampMs ?: 0L)
                })
            }
            put("targets", targetsArray)
        }
        jsonFile.writeText(json.toString(2))
    }

    fun loadAllSessions(): List<Session> {
        val list = mutableListOf<Session>()
        val dirs = baseDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        for (d in dirs) {
            val jsonFile = File(d, "session.json")
            if (jsonFile.exists()) {
                try {
                    val root = JSONObject(jsonFile.readText())
                    val targetsList = mutableListOf<CaptureTarget>()
                    val tArray = root.getJSONArray("targets")
                    for (i in 0 until tArray.length()) {
                        val obj = tArray.getJSONObject(i)
                        targetsList.add(
                            CaptureTarget(
                                id = obj.getString("id"),
                                ringType = RingType.valueOf(obj.getString("ringType")),
                                targetYawDeg = obj.getDouble("yawDeg").toFloat(),
                                targetPitchDeg = obj.getDouble("pitchDeg").toFloat(),
                                worldDirection = TargetGridCalculator.calculateTargets(58f, 72f)
                                    .firstOrNull { it.id == obj.getString("id") }?.worldDirection
                                    ?: com.panocapture.guided360.domain.math.Vector3(0f, 1f, 0f),
                                status = TargetStatus.valueOf(obj.getString("status")),
                                photoFileName = if (obj.isNull("photoFileName")) null else obj.getString("photoFileName"),
                                thumbPath = if (obj.isNull("thumbPath")) null else obj.getString("thumbPath"),
                                captureTimestampMs = obj.optLong("timestampMs", 0L)
                            )
                        )
                    }
                    list.add(
                        Session(
                            id = root.getString("id"),
                            sceneName = root.getString("sceneName"),
                            createdAtEpochMs = root.getLong("createdAtEpochMs"),
                            targets = targetsList,
                            isAeLocked = root.optBoolean("isAeLocked", false)
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return list.sortedByDescending { it.createdAtEpochMs }
    }

    fun deleteSession(sessionId: String) {
        val dir = File(baseDir, sessionId)
        if (dir.exists()) dir.deleteRecursively()
    }

    fun getSessionDir(sessionId: String): File = File(baseDir, sessionId)
}
