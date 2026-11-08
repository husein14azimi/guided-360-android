package com.panocapture.guided360.domain.math

import kotlin.math.acos
import kotlin.math.sqrt

data class Vector3(val x: Float, val y: Float, val z: Float) {
    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(): Vector3 {
        val len = length()
        return if (len > 1e-6f) Vector3(x / len, y / len, z / len) else Vector3(0f, 0f, 0f)
    }

    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3): Vector3 = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun angleTo(other: Vector3): Float {
        val d = (this.dot(other) / (this.length() * other.length())).coerceIn(-1.0f, 1.0f)
        return Math.toDegrees(acos(d.toDouble())).toFloat()
    }

    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
        val FORWARD_CAM = Vector3(0f, 0f, -1f) // looking down negative Z in phone camera frame
        val UP_CAM = Vector3(0f, 1f, 0f)
    }
}
