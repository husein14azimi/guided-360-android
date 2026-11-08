package com.panocapture.guided360.domain.math

import com.panocapture.guided360.domain.model.CaptureTarget
import com.panocapture.guided360.domain.model.RingType
import kotlin.math.*

object TargetGridCalculator {

    const val TARGET_OVERLAP = 0.28f // 28% overlap

    /**
     * Generates spherical targets calculated dynamically from the phone camera FOV.
     * Horizontal and Vertical FOVs are in degrees for portrait orientation.
     */
    fun calculateTargets(hFovDeg: Float, vFovDeg: Float): List<CaptureTarget> {
        val targets = mutableListOf<CaptureTarget>()
        val effectiveVStep = vFovDeg * (1f - TARGET_OVERLAP)
        val effectiveHStep = hFovDeg * (1f - TARGET_OVERLAP)

        // 1. Eye-level Ring (Pitch = 0°)
        val eyeCount = max(6, ceil(360f / effectiveHStep).toInt())
        val eyeHStep = 360f / eyeCount
        for (i in 0 until eyeCount) {
            val yaw = i * eyeHStep
            targets.add(createTarget("eye_$i", RingType.EYE_LEVEL, yawDeg = yaw, pitchDeg = 0f))
        }

        // 2. Upper Ring (Pitch = +effectiveVStep)
        val upperPitch = effectiveVStep.coerceAtMost(55f)
        val upperCircumferenceFraction = cos(Math.toRadians(upperPitch.toDouble())).toFloat()
        val upperCount = max(4, ceil((360f * upperCircumferenceFraction) / effectiveHStep).toInt())
        val upperHStep = 360f / upperCount
        for (i in 0 until upperCount) {
            val yaw = i * upperHStep
            targets.add(createTarget("upper_$i", RingType.UPPER, yawDeg = yaw, pitchDeg = upperPitch))
        }

        // 3. Lower Ring (Pitch = -effectiveVStep)
        val lowerPitch = -effectiveVStep.coerceAtMost(55f)
        val lowerCircumferenceFraction = cos(Math.toRadians(abs(lowerPitch.toDouble()))).toFloat()
        val lowerCount = max(4, ceil((360f * lowerCircumferenceFraction) / effectiveHStep).toInt())
        val lowerHStep = 360f / lowerCount
        for (i in 0 until lowerCount) {
            val yaw = i * lowerHStep
            targets.add(createTarget("lower_$i", RingType.LOWER, yawDeg = yaw, pitchDeg = lowerPitch))
        }

        // 4. Zenith (Ceiling: Pitch = +90°)
        targets.add(createTarget("zenith", RingType.CEILING, yawDeg = 0f, pitchDeg = 90f))

        // Floor / Nadir is explicitly excluded per product requirements
        return targets
    }

    private fun createTarget(id: String, ring: RingType, yawDeg: Float, pitchDeg: Float): CaptureTarget {
        val yawRad = Math.toRadians(yawDeg.toDouble())
        val pitchRad = Math.toRadians(pitchDeg.toDouble())

        // Right-Handed World Frame: +X = East, +Y = North, +Z = Zenith
        val x = (cos(pitchRad) * sin(yawRad)).toFloat()
        val y = (cos(pitchRad) * cos(yawRad)).toFloat()
        val z = sin(pitchRad).toFloat()

        return CaptureTarget(
            id = id,
            ringType = ring,
            targetYawDeg = yawDeg,
            targetPitchDeg = pitchDeg,
            worldDirection = Vector3(x, y, z).normalized()
        )
    }
}
