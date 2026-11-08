package com.panocapture.guided360

import com.panocapture.guided360.domain.math.TargetGridCalculator
import com.panocapture.guided360.domain.model.RingType
import org.junit.Assert.*
import org.junit.Test

class TargetGridCalculatorTest {

    @Test
    fun testTargetsGeneratedWithExpectedOverlap() {
        val hFov = 58.0f
        val vFov = 72.0f

        val targets = TargetGridCalculator.calculateTargets(hFov, vFov)

        // Must have Eye Level, Upper, Lower, and Zenith
        assertTrue(targets.any { it.ringType == RingType.EYE_LEVEL })
        assertTrue(targets.any { it.ringType == RingType.UPPER })
        assertTrue(targets.any { it.ringType == RingType.LOWER })
        assertTrue(targets.any { it.ringType == RingType.CEILING })

        // No Nadir/Floor targets per specifications
        assertFalse(targets.any { it.targetPitchDeg <= -85f })

        // Check horizontal ring spacing conforms to ~28% overlap
        val eyeTargets = targets.filter { it.ringType == RingType.EYE_LEVEL }
        val step = 360.0f / eyeTargets.size
        val effectiveStep = hFov * (1f - TargetGridCalculator.TARGET_OVERLAP)
        assertTrue("Step size must ensure sufficient overlap", step <= effectiveStep + 1.0f)
    }

    @Test
    fun testCeilingTargetPresentAndSingle() {
        val targets = TargetGridCalculator.calculateTargets(60f, 70f)
        val zenith = targets.filter { it.ringType == RingType.CEILING }
        assertEquals("There should be exactly one zenith target", 1, zenith.size)
        assertEquals(90f, zenith[0].targetPitchDeg, 0.01f)
    }
}
