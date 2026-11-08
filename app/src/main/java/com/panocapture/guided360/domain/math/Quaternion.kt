package com.panocapture.guided360.domain.math

import kotlin.math.*

/**
 * Hamilton Quaternion representation [w, x, y, z].
 */
data class Quaternion(val w: Float, val x: Float, val y: Float, val z: Float) {

    fun conjugate(): Quaternion = Quaternion(w, -x, -y, -z)

    fun multiply(q: Quaternion): Quaternion = Quaternion(
        w * q.w - x * q.x - y * q.y - z * q.z,
        w * q.x + x * q.w + y * q.z - z * q.y,
        w * q.y - x * q.z + y * q.w + z * q.x,
        w * q.z + x * q.y - y * q.x + z * q.w
    )

    /**
     * Rotates vector v by this quaternion: v' = q * v * q^-1
     */
    fun rotate(v: Vector3): Vector3 {
        val qv = Quaternion(0f, v.x, v.y, v.z)
        val res = this.multiply(qv).multiply(this.conjugate())
        return Vector3(res.x, res.y, res.z)
    }

    /**
     * Converts to Euler angles in degrees (Yaw = Z-axis, Pitch = X-axis, Roll = Y-axis).
     */
    fun toEulerAnglesDegrees(): Triple<Float, Float, Float> {
        // Roll (x-axis rotation)
        val sinrCosp = 2f * (w * x + y * z)
        val cosrCosp = 1f - 2f * (x * x + y * y)
        val roll = atan2(sinrCosp, cosrCosp)

        // Pitch (y-axis rotation)
        val sinp = 2f * (w * y - z * x)
        val pitch = if (abs(sinp) >= 1f) {
            (Math.PI / 2.0).withSign(sinp.toDouble()).toFloat()
        } else {
            asin(sinp)
        }

        // Yaw (z-axis rotation)
        val sinyCosp = 2f * (w * z + x * y)
        val cosyCosp = 1f - 2f * (y * y + z * z)
        val yaw = atan2(sinyCosp, cosyCosp)

        return Triple(
            Math.toDegrees(yaw.toDouble()).toFloat(),
            Math.toDegrees(pitch.toDouble()).toFloat(),
            Math.toDegrees(roll.toDouble()).toFloat()
        )
    }

    companion object {
        val IDENTITY = Quaternion(1f, 0f, 0f, 0f)

        /**
         * Construct from Android's 4-element rotation_vector [x, y, z, w].
         */
        fun fromRotationVector(vec: FloatArray): Quaternion {
            return if (vec.size >= 4) {
                Quaternion(vec[3], vec[0], vec[1], vec[2])
            } else {
                // If 3 elements, reconstruct w
                val x = vec[0]
                val y = vec[1]
                val z = vec[2]
                val w = sqrt(max(0.0, 1.0 - (x * x + y * y + z * z).toDouble())).toFloat()
                Quaternion(w, x, y, z)
            }
        }

        fun fromAxisAngle(axis: Vector3, angleDegrees: Float): Quaternion {
            val rad = Math.toRadians(angleDegrees.toDouble() / 2.0)
            val sinA = sin(rad).toFloat()
            val normAxis = axis.normalized()
            return Quaternion(
                cos(rad).toFloat(),
                normAxis.x * sinA,
                normAxis.y * sinA,
                normAxis.z * sinA
            )
        }
    }
}
