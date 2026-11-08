package com.panocapture.guided360.domain.model

import com.panocapture.guided360.domain.math.Vector3

enum class RingType { EYE_LEVEL, UPPER, LOWER, CEILING }
enum class TargetStatus { PENDING, ACTIVE_AIM, CAPTURED, SELECTED_FOR_RETAKE }

data class CaptureTarget(
    val id: String,
    val ringType: RingType,
    val targetYawDeg: Float,
    val targetPitchDeg: Float,
    val worldDirection: Vector3,
    var status: TargetStatus = TargetStatus.PENDING,
    var photoFileName: String? = null,
    var thumbPath: String? = null,
    var captureTimestampMs: Long? = null
)
