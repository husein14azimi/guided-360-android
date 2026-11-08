package com.panocapture.guided360

import com.panocapture.guided360.domain.math.Quaternion
import com.panocapture.guided360.domain.math.Vector3
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class Math3DTest {

    @Test
    fun testVectorDotAndAngle() {
        val v1 = Vector3(1f, 0f, 0f)
        val v2 = Vector3(0f, 1f, 0f)
        assertEquals(0f, v1.dot(v2), 1e-5f)
        assertEquals(90f, v1.angleTo(v2), 1e-4f)
    }

    @Test
    fun testQuaternionRotation() {
        // Rotate [0, 0, -1] around Y by 90 degrees -> should point to [-1, 0, 0]
        val q = Quaternion.fromAxisAngle(Vector3(0f, 1f, 0f), 90f)
        val rotated = q.rotate(Vector3.FORWARD_CAM)

        assertEquals(-1f, rotated.x, 1e-4f)
        assertEquals(0f, rotated.y, 1e-4f)
        assertEquals(0f, rotated.z, 1e-4f)
    }

    @Test
    fun testEulerConversionEquivalence() {
        val q = Quaternion.fromAxisAngle(Vector3(0f, 0f, 1f), 45f)
        val (yaw, pitch, roll) = q.toEulerAnglesDegrees()
        assertEquals(45f, yaw, 0.5f)
        assertEquals(0f, pitch, 0.5f)
        assertEquals(0f, roll, 0.5f)
    }
}
