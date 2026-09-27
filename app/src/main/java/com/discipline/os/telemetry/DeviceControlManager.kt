package com.discipline.os.telemetry

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UnifiedTelemetry(
    val steps: StepStats = StepStats(),
    val screenTime: ScreenTimeStats = ScreenTimeStats(),
    val network: NetworkDataStats = NetworkDataStats(),
    val disciplineScore: Int = 100,
    val focusStatus: String = "Apex Focus"
)

class DeviceControlManager private constructor(private val context: Context) {

    private val stepTracker = StepTracker.getInstance(context)
    private val _telemetry = MutableStateFlow(UnifiedTelemetry())
    val telemetry: StateFlow<UnifiedTelemetry> = _telemetry.asStateFlow()

    fun refreshTelemetry(protocolCompletionRate: Float = 0f) {
        val currentSteps = stepTracker.stepStats.value
        val currentScreen = AppUsageTracker.getTodayUsageStats(context)
        val currentNetwork = NetworkUsageTracker.getNetworkStats(context)

        // Calculate Discipline Score (0 to 100)
        // 1. Task Execution (40%)
        val taskPoints = (protocolCompletionRate.coerceIn(0f, 1f) * 40f)

        // 2. Physical Walking Activity (30%)
        val stepRatio = if (currentSteps.stepGoal > 0) {
            (currentSteps.todaySteps.toFloat() / currentSteps.stepGoal.toFloat()).coerceIn(0f, 1f)
        } else 0f
        val stepPoints = stepRatio * 30f

        // 3. Screen Time Discipline (30%)
        val screenDisciplineRatio = if (currentScreen.hasPermission && currentScreen.totalScreenTimeMs > 0) {
            (currentScreen.productiveTimeMs.toFloat() / currentScreen.totalScreenTimeMs.toFloat()).coerceIn(0f, 1f)
        } else {
            1.0f // Default if not tracking or 0 screen time
        }
        val screenPoints = screenDisciplineRatio * 30f

        val totalScore = (taskPoints + stepPoints + screenPoints).toInt().coerceIn(0, 100)

        val status = when {
            totalScore >= 85 -> "Apex Focus ⚡"
            totalScore >= 70 -> "High Discipline 🛡️"
            totalScore >= 50 -> "Balanced Warrior ⚔️"
            totalScore >= 35 -> "Focus Drifting ⚠️"
            else -> "Action Required 🚨"
        }

        _telemetry.value = UnifiedTelemetry(
            steps = currentSteps,
            screenTime = currentScreen,
            network = currentNetwork,
            disciplineScore = totalScore,
            focusStatus = status
        )
    }

    companion object {
        @Volatile
        private var instance: DeviceControlManager? = null

        fun getInstance(context: Context): DeviceControlManager {
            return instance ?: synchronized(this) {
                instance ?: DeviceControlManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
