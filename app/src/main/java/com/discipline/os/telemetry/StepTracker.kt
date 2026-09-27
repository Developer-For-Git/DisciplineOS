package com.discipline.os.telemetry

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StepStats(
    val todaySteps: Int = 0,
    val stepGoal: Int = 10000,
    val distanceKm: Float = 0f,
    val caloriesKcal: Int = 0,
    val activeMinutes: Int = 0,
    val hasHardwareSensor: Boolean = false,
    val isTracking: Boolean = false,
    val sensorSource: String = "Direct Phone Sensor",
    val hasPermission: Boolean = true,
    val lastStepTimestampMs: Long = 0L,
    val recentCadenceSpm: Int = 0
) {
    val progressFraction: Float
        get() = if (stepGoal > 0) (todaySteps.toFloat() / stepGoal.toFloat()).coerceIn(0f, 1.5f) else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()
}

/**
 * High-sensitivity digital peak detector for 3-axis accelerometer.
 * Enables direct phone step counting even when hardware pedometer batching is delayed or unavailable.
 */
private class AccelerometerStepDetector {
    private var lastStepTimeMs = 0L
    private var filteredMagnitude = 9.80665f
    private var peakDynamicAcc = 0f
    private var isRising = false
    private val alpha = 0.18f

    fun processSample(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        val rawMagnitude = kotlin.math.sqrt(x * x + y * y + z * z)
        filteredMagnitude = alpha * rawMagnitude + (1f - alpha) * filteredMagnitude
        val dynamicAcc = filteredMagnitude - 9.80665f

        // Walking frequency filter: max 4 steps/sec (minimum 250ms interval)
        if (nowMs - lastStepTimeMs < 250L) {
            return false
        }

        // Peak detection with zero-crossing confirmation
        if (dynamicAcc > 1.35f) {
            if (!isRising) {
                isRising = true
                peakDynamicAcc = dynamicAcc
            } else if (dynamicAcc > peakDynamicAcc) {
                peakDynamicAcc = dynamicAcc
            }
        } else if (isRising && dynamicAcc < 0.25f) {
            isRising = false
            if (peakDynamicAcc in 1.35f..16.0f && (nowMs - lastStepTimeMs) >= 280L) {
                lastStepTimeMs = nowMs
                peakDynamicAcc = 0f
                return true
            }
            peakDynamicAcc = 0f
        }
        return false
    }
}

class StepTracker private constructor(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val prefs: SharedPreferences = context.getSharedPreferences("discipline_telemetry_prefs", Context.MODE_PRIVATE)

    private val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val accelDetector = AccelerometerStepDetector()

    private val _stepStats = MutableStateFlow(StepStats())
    val stepStats: StateFlow<StepStats> = _stepStats.asStateFlow()

    private var lastHardwareStepTimeMs: Long = 0L
    private val recentStepTimestamps = ArrayDeque<Long>()

    init {
        loadSavedStats()
        startTracking()
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun loadSavedStats() {
        val today = getTodayDateString()
        val lastSavedDate = prefs.getString("last_step_date", "")

        val savedSteps = if (lastSavedDate == today) {
            prefs.getInt("today_steps", 0)
        } else {
            // New day: archive yesterday and reset today to 0
            if (!lastSavedDate.isNullOrBlank()) {
                val yesterdaySteps = prefs.getInt("today_steps", 0)
                prefs.edit().putInt("archived_steps_$lastSavedDate", yesterdaySteps).apply()
            }
            prefs.edit()
                .putString("last_step_date", today)
                .putInt("today_steps", 0)
                .putInt("last_boot_steps", -1)
                .apply()
            0
        }

        val goal = prefs.getInt("step_goal", 10000)
        updateState(savedSteps, goal)
    }

    fun startTracking() {
        val permissionGranted = hasPermission()
        val hasHw = (stepCounterSensor != null || stepDetectorSensor != null)

        val sourceName = when {
            stepCounterSensor != null && permissionGranted -> "Direct Phone Hardware Sensor"
            stepDetectorSensor != null && permissionGranted -> "Direct Phone Step Detector"
            accelerometerSensor != null -> "Direct Phone Motion Sensor"
            else -> "Simulated Sensor"
        }

        _stepStats.value = _stepStats.value.copy(
            hasHardwareSensor = hasHw,
            isTracking = true,
            hasPermission = permissionGranted,
            sensorSource = sourceName
        )

        // 1. Hardware Step Counter (monotonically increasing boot steps)
        if (permissionGranted && stepCounterSensor != null) {
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI)
        }

        // 2. Hardware Step Detector (instant single-step trigger)
        if (permissionGranted && stepDetectorSensor != null) {
            sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_UI)
        }

        // 3. Accelerometer (real-time movement fallback for zero-delay live responsiveness)
        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopTracking() {
        sensorManager.unregisterListener(this)
        _stepStats.value = _stepStats.value.copy(isTracking = false)
    }

    fun refreshSensors() {
        stopTracking()
        startTracking()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val today = getTodayDateString()
        val lastDate = prefs.getString("last_step_date", "")
        if (lastDate != today) {
            loadSavedStats()
        }

        val now = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val bootSteps = event.values[0].toInt()
                lastHardwareStepTimeMs = now

                val lastBoot = prefs.getInt("last_boot_steps", -1)
                if (lastBoot < 0) {
                    // Initialize baseline boot steps on first event
                    prefs.edit().putInt("last_boot_steps", bootSteps).apply()
                } else {
                    val delta = if (bootSteps >= lastBoot) bootSteps - lastBoot else bootSteps
                    if (delta in 1..25000) {
                        prefs.edit().putInt("last_boot_steps", bootSteps).apply()
                        val newSteps = _stepStats.value.todaySteps + delta
                        recordStep(newSteps, now, "Direct Phone Hardware Sensor")
                    } else if (bootSteps >= lastBoot) {
                        prefs.edit().putInt("last_boot_steps", bootSteps).apply()
                    }
                }
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                if (event.values[0] == 1.0f) {
                    lastHardwareStepTimeMs = now
                    val newSteps = _stepStats.value.todaySteps + 1
                    recordStep(newSteps, now, "Direct Phone Step Detector")
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                // If hardware step sensor hasn't reported within 3.5 seconds, rely on direct phone motion sensor
                if (now - lastHardwareStepTimeMs > 3500L) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    if (accelDetector.processSample(x, y, z, now)) {
                        val newSteps = _stepStats.value.todaySteps + 1
                        recordStep(newSteps, now, "Direct Phone Motion Sensor")
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun recordStep(totalSteps: Int, timestampMs: Long, source: String) {
        recentStepTimestamps.addLast(timestampMs)
        while (recentStepTimestamps.isNotEmpty() && (timestampMs - recentStepTimestamps.first() > 15000L)) {
            recentStepTimestamps.removeFirst()
        }
        val cadence = if (recentStepTimestamps.size >= 2) {
            val windowSec = (timestampMs - recentStepTimestamps.first()) / 1000f
            if (windowSec > 0.5f) ((recentStepTimestamps.size / windowSec) * 60f).toInt() else 0
        } else {
            0
        }

        persistSteps(totalSteps, source, timestampMs, cadence)
    }

    fun addManualSteps(count: Int) {
        val current = (_stepStats.value.todaySteps + count).coerceAtLeast(0)
        persistSteps(current, "Manual Calibration / Direct Phone", System.currentTimeMillis(), 0)
    }

    fun resetTodaySteps() {
        val today = getTodayDateString()
        prefs.edit()
            .putString("last_step_date", today)
            .putInt("today_steps", 0)
            .putInt("last_boot_steps", -1)
            .apply()

        recentStepTimestamps.clear()

        updateState(
            steps = 0,
            goal = _stepStats.value.stepGoal,
            source = _stepStats.value.sensorSource,
            timestampMs = System.currentTimeMillis(),
            cadence = 0
        )
    }

    fun setStepGoal(goal: Int) {
        val validGoal = goal.coerceIn(1000, 50000)
        prefs.edit().putInt("step_goal", validGoal).apply()
        updateState(_stepStats.value.todaySteps, validGoal)
    }

    private fun persistSteps(
        steps: Int,
        source: String = _stepStats.value.sensorSource,
        timestampMs: Long = System.currentTimeMillis(),
        cadence: Int = _stepStats.value.recentCadenceSpm
    ) {
        val today = getTodayDateString()
        prefs.edit()
            .putString("last_step_date", today)
            .putInt("today_steps", steps)
            .apply()

        updateState(steps, _stepStats.value.stepGoal, source, timestampMs, cadence)
    }

    private fun updateState(
        steps: Int,
        goal: Int,
        source: String = _stepStats.value.sensorSource,
        timestampMs: Long = _stepStats.value.lastStepTimestampMs,
        cadence: Int = _stepStats.value.recentCadenceSpm
    ) {
        val distance = (steps * 0.000762f) // Standard avg step length (~0.76m)
        val calories = (steps * 0.04f).toInt() // Approx 0.04 kcal per step
        val activeMinutes = (steps / 100).coerceAtLeast(0) // ~100 steps per min brisk walk

        _stepStats.value = _stepStats.value.copy(
            todaySteps = steps,
            stepGoal = goal,
            distanceKm = distance,
            caloriesKcal = calories,
            activeMinutes = activeMinutes,
            sensorSource = source,
            hasPermission = hasPermission(),
            lastStepTimestampMs = timestampMs,
            recentCadenceSpm = cadence
        )
    }

    companion object {
        @Volatile
        private var instance: StepTracker? = null

        fun getInstance(context: Context): StepTracker {
            return instance ?: synchronized(this) {
                instance ?: StepTracker(context.applicationContext).also { instance = it }
            }
        }
    }
}
