package com.discipline.os.alarm

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object VibrationHelper {
    private const val PREFS_NAME = "discipline_prefs"
    private const val KEY_VIBRATION_ENABLED = "is_vibration_enabled"

    // Ultra-rapid high-potential vibration pattern:
    // Timing: 0ms delay, 70ms on, 30ms off, 70ms on, 30ms off, 120ms on, 40ms off, 80ms on
    val RAPID_PULSE_TIMINGS = longArrayOf(0, 70, 30, 70, 30, 120, 40, 80, 40, 150)
    // Amplitudes: Maximum motor power (255)
    val RAPID_PULSE_AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255)

    fun isVibrationEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
        if (!enabled) {
            stopVibration(context)
        }
    }

    fun triggerRapidVibration(context: Context, repeat: Boolean = false) {
        if (!isVibrationEnabled(context)) return

        val vibrator = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val repeatIndex = if (repeat) 0 else -1
            val effect = VibrationEffect.createWaveform(RAPID_PULSE_TIMINGS, RAPID_PULSE_AMPLITUDES, repeatIndex)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(RAPID_PULSE_TIMINGS, if (repeat) 0 else -1)
        }
    }

    fun stopVibration(context: Context) {
        val vibrator = getVibrator(context) ?: return
        vibrator.cancel()
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
