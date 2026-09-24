package com.discipline.os.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeHelper {

    /**
     * Converts any raw time input into 24-hour "HH:mm" for database consistency and AlarmScheduler.
     * Supports:
     * - Shorthand numbers: "527" -> "17:27", "827" -> "20:27", "10" -> "22:00", "730" -> "07:30"
     * - Named presets: "bedtime" -> "22:00", "evening" -> "17:27", "morning" -> "07:30"
     * - 12-hour AM/PM: "5:27 PM" -> "17:27", "8:27 pm" -> "20:27", "10:00 PM" -> "22:00", "7:30 AM" -> "07:30"
     * - 24-hour: "17:27" -> "17:27", "22:00" -> "22:00"
     */
    fun normalizeTo24Hour(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ""

        val lower = trimmed.lowercase(Locale.US)
        if (lower.contains("bedtime") || lower.contains("sleep")) {
            return "22:00"
        }
        if (lower.contains("morning")) {
            return "07:30"
        }
        if (lower.contains("evening")) {
            return "17:27"
        }
        if (lower.contains("night")) {
            return "20:27"
        }

        // Try standard 12-hour AM/PM formats e.g. "5:27 PM", "05:27 PM", "5:27pm", "5 PM", "10 PM"
        val formats12 = listOf(
            SimpleDateFormat("h:mm a", Locale.US),
            SimpleDateFormat("h:mma", Locale.US),
            SimpleDateFormat("hh:mm a", Locale.US),
            SimpleDateFormat("hh:mma", Locale.US),
            SimpleDateFormat("h a", Locale.US),
            SimpleDateFormat("ha", Locale.US),
            SimpleDateFormat("h.mm a", Locale.US)
        )
        for (sdf in formats12) {
            try {
                sdf.isLenient = false
                val date = sdf.parse(trimmed)
                if (date != null) {
                    val cal = Calendar.getInstance().apply { time = date }
                    return String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                }
            } catch (_: Exception) {}
        }

        // Check if input is standard 24-hour "HH:mm" or "H:mm" e.g. "17:27", "7:30"
        if (trimmed.contains(":")) {
            val parts = trimmed.split(":")
            if (parts.size == 2) {
                val h = parts[0].trim().toIntOrNull()
                val m = parts[1].trim().take(2).toIntOrNull()
                if (h != null && m != null && h in 0..23 && m in 0..59) {
                    return String.format(Locale.US, "%02d:%02d", h, m)
                }
            }
        }

        // Check if raw numbers like "527" (5:27 PM = 17:27), "827" (8:27 PM = 20:27), "10" (10:00 PM = 22:00)
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.length in 1..4) {
            when (digitsOnly.length) {
                1, 2 -> {
                    val hour = digitsOnly.toInt()
                    if (hour in 1..23) {
                        val normalizedHour = when {
                            hour in 1..6 -> hour + 12   // 1 PM .. 6 PM
                            hour in 8..11 -> hour + 12  // 8 PM .. 11 PM (e.g. 10 -> 22:00 Bedtime)
                            else -> hour
                        }
                        return String.format(Locale.US, "%02d:00", normalizedHour)
                    }
                }
                3 -> {
                    // e.g. "527" -> 5:27 PM (17:27), "827" -> 8:27 PM (20:27), "730" -> 07:30 AM
                    val h = digitsOnly.substring(0, 1).toInt()
                    val m = digitsOnly.substring(1).toInt()
                    if (m in 0..59) {
                        val hour24 = if (h in 1..6 || h in 8..9) h + 12 else h
                        return String.format(Locale.US, "%02d:%02d", hour24, m)
                    }
                }
                4 -> {
                    val h = digitsOnly.substring(0, 2).toInt()
                    val m = digitsOnly.substring(2).toInt()
                    if (h in 0..23 && m in 0..59) {
                        return String.format(Locale.US, "%02d:%02d", h, m)
                    }
                }
            }
        }

        return trimmed
    }

    /**
     * Converts a 24-hour "HH:mm" time string or raw user input into human-friendly 12-hour AM/PM format.
     * Examples:
     * - "17:27" -> "5:27 PM"
     * - "20:27" -> "8:27 PM"
     * - "22:00" -> "10:00 PM"
     * - "07:30" -> "7:30 AM"
     */
    fun formatDisplayTime(timeStr: String, is24Hour: Boolean = false): String {
        if (timeStr.isBlank()) return ""
        val normalized = normalizeTo24Hour(timeStr)
        val parts = normalized.split(":")
        if (parts.size != 2) return timeStr

        val hour = parts[0].toIntOrNull() ?: return timeStr
        val minute = parts[1].toIntOrNull() ?: return timeStr

        if (is24Hour) {
            return String.format(Locale.US, "%02d:%02d", hour, minute)
        }

        val amPm = if (hour >= 12) "PM" else "AM"
        val hour12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }

        return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
    }

    /**
     * Converts epoch milliseconds to a 12-hour AM/PM string, e.g. "5:27 PM".
     */
    fun formatEpochToTime(epochMs: Long, is24Hour: Boolean = false): String {
        if (epochMs <= 0) return ""
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.US).format(Date(epochMs))
    }

    /**
     * Converts a scheduled time string into total minutes from 00:00 midnight (0..1439).
     * Used for strict chronological sorting across the application.
     * Returns 9999 for unscheduled tasks so they appear at the bottom.
     */
    fun getMinutesFromMidnight(timeStr: String): Int {
        if (timeStr.isBlank()) return 9999
        val normalized = normalizeTo24Hour(timeStr)
        val parts = normalized.split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: return 9999
            val m = parts[1].toIntOrNull() ?: 0
            return (h.coerceIn(0, 23) * 60) + m.coerceIn(0, 59)
        }
        return 9999
    }

    /**
     * Returns current local time in total minutes from midnight (0..1439).
     */
    fun getCurrentMinutesFromMidnight(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /**
     * Returns a contextual tag e.g. "Morning Kickoff", "College & Lab", "Evening Focus", "Bedtime".
     */
    fun getPeriodTag(timeStr: String): String {
        if (timeStr.isBlank()) return "All-Day"
        val normalized = normalizeTo24Hour(timeStr)
        val parts = normalized.split(":")
        if (parts.size != 2) return ""
        val hour = parts[0].toIntOrNull() ?: return ""
        return when (hour) {
            in 21..23, in 0..4 -> "Bedtime"
            in 17..20 -> "Evening"
            in 12..16 -> "Afternoon"
            in 8..11 -> "College"
            in 5..7 -> "Morning"
            else -> ""
        }
    }

    /**
     * Returns a contextual emoji icon for the time of day and schedule phase.
     */
    fun getPeriodEmoji(timeStr: String): String {
        if (timeStr.isBlank()) return "🛡️"
        val normalized = normalizeTo24Hour(timeStr)
        val parts = normalized.split(":")
        if (parts.size != 2) return "🛡️"
        val hour = parts[0].toIntOrNull() ?: return "🛡️"
        return when (hour) {
            in 21..23, in 0..4 -> "🌙"
            in 17..20 -> "🌇"
            in 12..16 -> "☀️"
            in 8..11 -> "🎓"
            in 5..7 -> "🌅"
            else -> "⏰"
        }
    }

    /**
     * Returns the live application time formatted for the top status bar / header.
     * e.g. "05:27 PM" or "17:27"
     */
    fun getCurrentLiveTime(is24Hour: Boolean = false): String {
        val pattern = if (is24Hour) "HH:mm" else "hh:mm a"
        return SimpleDateFormat(pattern, Locale.US).format(Date())
    }
}
