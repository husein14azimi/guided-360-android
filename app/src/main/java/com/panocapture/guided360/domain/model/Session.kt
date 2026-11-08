package com.panocapture.guided360.domain.model

data class CameraIntrinsics(
    val horizontalFovDeg: Float = 58f,
    val verticalFovDeg: Float = 72f,
    val focalLengthMm: Float = 4.5f,
    val lensFacing: String = "BACK",
    val sensorWidthMm: Float = 5.6f,
    val sensorHeightMm: Float = 4.2f,
    val imageWidth: Int = 4000,
    val imageHeight: Int = 3000
)

data class Session(
    val id: String,
    val sceneName: String,
    val createdAtEpochMs: Long,
    val targets: MutableList<CaptureTarget>,
    var intrinsics: CameraIntrinsics = CameraIntrinsics(),
    var isAeLocked: Boolean = false
)
