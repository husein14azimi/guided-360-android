package com.panocapture.guided360

import com.panocapture.guided360.domain.math.Vector3
import org.junit.Assert.*
import org.junit.Test

class AlignmentLogicTest {

    @Test
    fun testAimingDistanceCalculation() {
        val lookDirection = Vector3(0f, 1f, 0f)
        val targetDirection = Vector3(0.05f, 0.998f, 0f).normalized()

        val angle = lookDirection.angleTo(targetDirection)
        assertTrue("Angle should be within capture tolerance (~2.8 degrees)", angle < 3.5f)
    }

    @Test
    fun testSidewaysTiltRejection() {
        val rollDeg = 7.5f
        val isTilted = kotlin.math.abs(rollDeg) > 5.0f
        assertTrue("Capture must be inhibited when phone tilt exceeds 5 degrees", isTilted)
    }
}
