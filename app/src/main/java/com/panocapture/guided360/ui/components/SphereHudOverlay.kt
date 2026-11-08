package com.panocapture.guided360.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.panocapture.guided360.domain.math.Quaternion
import com.panocapture.guided360.domain.math.Vector3
import com.panocapture.guided360.domain.model.CaptureTarget
import com.panocapture.guided360.domain.model.TargetStatus
import kotlin.math.*

@Composable
fun SphereHudOverlay(
    modifier: Modifier = Modifier,
    deviceOrientation: Quaternion,
    targets: List<CaptureTarget>,
    hFovDeg: Float,
    vFovDeg: Float,
    closestTarget: CaptureTarget?,
    isTiltedSideways: Boolean,
    tiltAngleDeg: Float,
    isMovingFast: Boolean,
    onTargetTapped: (CaptureTarget) -> Unit
) {
    // Cache for projected screen positions to handle tap interactions
    val projectedMap = remember { mutableMapOf<String, Offset>() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(targets) {
                detectTapGestures { tapOffset ->
                    val clicked = projectedMap.entries.firstOrNull { (_, offset) ->
                        (offset - tapOffset).getDistance() < 70f
                    }
                    clicked?.let { entry ->
                        targets.firstOrNull { it.id == entry.key }?.let { onTargetTapped(it) }
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        val hTan = tan(Math.toRadians(hFovDeg / 2.0)).toFloat()
        val vTan = tan(Math.toRadians(vFovDeg / 2.0)).toFloat()

        // Inverse orientation rotates world directions into current camera space
        val qInv = deviceOrientation.conjugate()
        projectedMap.clear()

        // 1. Draw Target Markers on Sphere
        for (target in targets) {
            val camDir = qInv.rotate(target.worldDirection)

            // Behind phone plane
            if (camDir.z >= -0.05f) continue

            // Perspective division
            val xNorm = camDir.x / (-camDir.z * hTan)
            val yNorm = camDir.y / (-camDir.z * vTan)

            val screenX = cx + xNorm * cx
            val screenY = cy - yNorm * cy

            if (screenX in -100f..(w + 100f) && screenY in -100f..(h + 100f)) {
                val center = Offset(screenX, screenY)
                projectedMap[target.id] = center
                val isClosest = target.id == closestTarget?.id

                drawTargetBubble(
                    target = target,
                    center = center,
                    isClosest = isClosest
                )
            }
        }

        // 2. Draw Center Reticle (Aiming Ring)
        drawCenterAimReticle(cx, cy, isMovingFast, isTiltedSideways)

        // 3. Draw Guidance Arrow if closest target is off-screen
        closestTarget?.let { target ->
            val camDir = qInv.rotate(target.worldDirection)
            if (camDir.z >= -0.05f || !projectedMap.containsKey(target.id)) {
                drawOffScreenChevron(cx, cy, camDir, w, h)
            }
        }

        // 4. Sideways Tilt Warning Indicator
        if (isTiltedSideways) {
            drawTiltWarning(cx, cy, tiltAngleDeg)
        }
    }
}

private fun DrawScope.drawTargetBubble(
    target: CaptureTarget,
    center: Offset,
    isClosest: Boolean
) {
    val radius = if (isClosest) 42f else 32f

    when (target.status) {
        TargetStatus.CAPTURED -> {
            // Draw thumbnail if available
            val thumb = target.thumbPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
            if (thumb != null) {
                drawImage(
                    image = thumb,
                    dstOffset = androidx.compose.ui.unit.IntOffset((center.x - radius).toInt(), (center.y - radius).toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize((radius * 2).toInt(), (radius * 2).toInt())
                )
                drawCircle(
                    color = Color.Green,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 4f)
                )
            } else {
                drawCircle(color = Color(0xAA4CAF50), radius = radius, center = center)
            }
        }
        TargetStatus.SELECTED_FOR_RETAKE -> {
            drawCircle(color = Color(0x66FF9800), radius = radius + 6f, center = center)
            drawCircle(color = Color.Yellow, radius = radius, center = center, style = Stroke(width = 6f))
        }
        TargetStatus.ACTIVE_AIM -> {
            drawCircle(color = Color(0x442196F3), radius = radius + 10f, center = center)
            drawCircle(color = Color.Cyan, radius = radius, center = center, style = Stroke(width = 5f))
        }
        TargetStatus.PENDING -> {
            val color = if (isClosest) Color.White else Color(0x88DDDDDD)
            drawCircle(color = Color(0x33000000), radius = radius, center = center)
            drawCircle(color = color, radius = radius, center = center, style = Stroke(width = if (isClosest) 5f else 3f))
            drawCircle(color = color, radius = 6f, center = center)
        }
    }
}

private fun DrawScope.drawCenterAimReticle(cx: Float, cy: Float, isMovingFast: Boolean, isTilted: Boolean) {
    val reticleColor = when {
        isMovingFast -> Color.Red
        isTilted -> Color(0xFFFF9800)
        else -> Color.White
    }

    drawCircle(
        color = reticleColor,
        radius = 28f,
        center = Offset(cx, cy),
        style = Stroke(width = 4f)
    )
    drawCircle(
        color = reticleColor,
        radius = 4f,
        center = Offset(cx, cy)
    )

    // Crosshairs
    drawLine(reticleColor, Offset(cx - 40f, cy), Offset(cx - 32f, cy), strokeWidth = 3f)
    drawLine(reticleColor, Offset(cx + 32f, cy), Offset(cx + 40f, cy), strokeWidth = 3f)
    drawLine(reticleColor, Offset(cx, cy - 40f), Offset(cx, cy - 32f), strokeWidth = 3f)
    drawLine(reticleColor, Offset(cx, cy + 32f), Offset(cx, cy + 40f), strokeWidth = 3f)
}

private fun DrawScope.drawOffScreenChevron(cx: Float, cy: Float, camDir: Vector3, w: Float, h: Float) {
    val angle = atan2(camDir.y, camDir.x)
    val margin = 80f
    val posX = (cx + cos(angle) * (cx - margin)).coerceIn(margin, w - margin)
    val posY = (cy - sin(angle) * (cy - margin)).coerceIn(margin, h - margin)

    val path = Path().apply {
        moveTo(posX + cos(angle) * 30f, posY - sin(angle) * 30f)
        lineTo(posX + cos(angle + 2.5f) * 22f, posY - sin(angle + 2.5f) * 22f)
        lineTo(posX + cos(angle - 2.5f) * 22f, posY - sin(angle - 2.5f) * 22f)
        close()
    }
    drawPath(path, color = Color(0xFF00E5FF))
}

private fun DrawScope.drawTiltWarning(cx: Float, cy: Float, tiltAngleDeg: Float) {
    val bannerY = cy + 120f
    drawRoundRect(
        color = Color(0xCCB00020),
        topLeft = Offset(cx - 180f, bannerY - 30f),
        size = androidx.compose.ui.geometry.Size(360f, 60f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
    )
}
