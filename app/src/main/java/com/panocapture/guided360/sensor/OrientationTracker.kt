package com.panocapture.guided360.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.panocapture.guided360.domain.math.Quaternion
import com.panocapture.guided360.domain.math.Vector3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sqrt

data class OrientationState(
    val currentOrientation: Quaternion = Quaternion.IDENTITY,
    val currentLookDirection: Vector3 = Vector3(0f, 1f, 0f),
    val sidewaysTiltDeg: Float = 0f,
    val isTiltedSideways: Boolean = false,
    val isMovingFast: Boolean = false,
    val angularSpeedRadPerSec: Float = 0f,
    val sensorTimestampNs: Long = 0L
)

class OrientationTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _orientationFlow = MutableStateFlow(OrientationState())
    val orientationFlow: StateFlow<OrientationState> = _orientationFlow

    private var latestOrientation = Quaternion.IDENTITY
    private var latestTimestampNs = 0L
    private var currentAngularSpeed = 0f

    var isSimulationMode = false

    fun start() {
        if (isSimulationMode) return
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            latestOrientation = Quaternion.fromRotationVector(event.values)
            latestTimestampNs = event.timestamp

            // World forward look vector = rotate camera optical axis (-Z) into world coordinates
            val lookDir = latestOrientation.rotate(Vector3.FORWARD_CAM).normalized()

            // Calculate sideways roll tilt
            val (_, _, rollDeg) = latestOrientation.toEulerAnglesDegrees()
            val isTilted = abs(rollDeg) > 5.0f

            _orientationFlow.value = _orientationFlow.value.copy(
                currentOrientation = latestOrientation,
                currentLookDirection = lookDir,
                sidewaysTiltDeg = rollDeg,
                isTiltedSideways = isTilted,
                sensorTimestampNs = latestTimestampNs
            )
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val gx = event.values[0]
            val gy = event.values[1]
            val gz = event.values[2]
            currentAngularSpeed = sqrt(gx * gx + gy * gy + gz * gz)
            val isFast = currentAngularSpeed > 0.25f // rad/s threshold (~14 deg/s)

            _orientationFlow.value = _orientationFlow.value.copy(
                angularSpeedRadPerSec = currentAngularSpeed,
                isMovingFast = isFast
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Debug / Simulator method to drive orientation artificially
    fun simulateOrientation(yawDeg: Float, pitchDeg: Float, rollDeg: Float = 0f) {
        val qYaw = Quaternion.fromAxisAngle(Vector3(0f, 0f, 1f), yawDeg)
        val qPitch = Quaternion.fromAxisAngle(Vector3(1f, 0f, 0f), pitchDeg)
        val qRoll = Quaternion.fromAxisAngle(Vector3(0f, 1f, 0f), rollDeg)
        val combined = qYaw.multiply(qPitch).multiply(qRoll)

        val lookDir = combined.rotate(Vector3.FORWARD_CAM).normalized()
        _orientationFlow.value = OrientationState(
            currentOrientation = combined,
            currentLookDirection = lookDir,
            sidewaysTiltDeg = rollDeg,
            isTiltedSideways = abs(rollDeg) > 5.0f,
            isMovingFast = false,
            angularSpeedRadPerSec = 0f,
            sensorTimestampNs = System.nanoTime()
        )
    }
}
