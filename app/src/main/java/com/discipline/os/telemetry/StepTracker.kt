package com.discipline.os.telemetry

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
    val isTracking: Boolean = false
) {
    val progressFraction: Float
        get() = if (stepGoal > 0) (todaySteps.toFloat() / stepGoal.toFloat()).coerceIn(0f, 1.5f) else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()
}

class StepTracker private constructor(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val prefs: SharedPreferences = context.getSharedPreferences("discipline_telemetry_prefs", Context.MODE_PRIVATE)
    private val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val _stepStats = MutableStateFlow(StepStats())
    val stepStats: StateFlow<StepStats> = _stepStats.asStateFlow()

    private var initialHardwareSteps: Int = -1

    init {
        loadSavedStats()
        startTracking()
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
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
                .putInt("baseline_hardware_steps", -1)
                .apply()
            0
        }

        val goal = prefs.getInt("step_goal", 10000)
        updateState(savedSteps, goal)
    }

    fun startTracking() {
        val hasSensor = (stepCounterSensor != null || stepDetectorSensor != null)
        _stepStats.value = _stepStats.value.copy(
            hasHardwareSensor = hasSensor,
            isTracking = true
        )

        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        if (stepCounterSensor == null && stepDetectorSensor != null) {
            sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopTracking() {
        sensorManager.unregisterListener(this)
        _stepStats.value = _stepStats.value.copy(isTracking = false)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val today = getTodayDateString()
        val lastDate = prefs.getString("last_step_date", "")
        if (lastDate != today) {
            loadSavedStats()
        }

        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val totalBootSteps = event.values[0].toInt()
                var baseline = prefs.getInt("baseline_hardware_steps", -1)

                if (baseline < 0 || baseline > totalBootSteps) {
                    // Calibrate baseline
                    baseline = totalBootSteps - _stepStats.value.todaySteps.coerceAtLeast(0)
                    prefs.edit().putInt("baseline_hardware_steps", baseline).apply()
                }

                val currentToday = (totalBootSteps - baseline).coerceAtLeast(0)
                persistSteps(currentToday)
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                if (event.values[0] == 1.0f) {
                    val current = _stepStats.value.todaySteps + 1
                    persistSteps(current)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun addManualSteps(count: Int) {
        val current = (_stepStats.value.todaySteps + count).coerceAtLeast(0)
        persistSteps(current)
    }

    fun setStepGoal(goal: Int) {
        val validGoal = goal.coerceIn(1000, 50000)
        prefs.edit().putInt("step_goal", validGoal).apply()
        updateState(_stepStats.value.todaySteps, validGoal)
    }

    private fun persistSteps(steps: Int) {
        val today = getTodayDateString()
        prefs.edit()
            .putString("last_step_date", today)
            .putInt("today_steps", steps)
            .apply()

        updateState(steps, _stepStats.value.stepGoal)
    }

    private fun updateState(steps: Int, goal: Int) {
        val distance = (steps * 0.000762f) // Standard avg step length (~0.76m)
        val calories = (steps * 0.04f).toInt() // Approx 0.04 kcal per step
        val activeMinutes = (steps / 100).coerceAtLeast(0) // ~100 steps per min brisk walk

        _stepStats.value = _stepStats.value.copy(
            todaySteps = steps,
            stepGoal = goal,
            distanceKm = distance,
            caloriesKcal = calories,
            activeMinutes = activeMinutes
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
