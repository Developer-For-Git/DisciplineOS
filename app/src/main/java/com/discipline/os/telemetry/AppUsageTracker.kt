package com.discipline.os.telemetry

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Calendar

enum class AppCategory {
    PRODUCTIVE,
    DISTRACTION,
    UTILITY
}

data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val totalTimeForegroundMs: Long,
    val category: AppCategory,
    val percentageOfTotal: Float
) {
    val formattedTime: String
        get() {
            val totalSeconds = totalTimeForegroundMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return if (hours > 0) {
                "${hours}h ${minutes}m"
            } else if (minutes > 0) {
                "${minutes}m"
            } else {
                "< 1m"
            }
        }
}

data class ScreenTimeStats(
    val hasPermission: Boolean = false,
    val totalScreenTimeMs: Long = 0L,
    val productiveTimeMs: Long = 0L,
    val distractionTimeMs: Long = 0L,
    val utilityTimeMs: Long = 0L,
    val topApps: List<AppUsageItem> = emptyList()
) {
    val productivePercentage: Float
        get() = if (totalScreenTimeMs > 0) {
            ((productiveTimeMs.toFloat() / totalScreenTimeMs.toFloat()) * 100f).coerceIn(0f, 100f)
        } else 100f

    val formattedTotalTime: String
        get() {
            val totalSeconds = totalScreenTimeMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }

    val formattedProductiveTime: String
        get() {
            val totalSeconds = productiveTimeMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }

    val formattedDistractionTime: String
        get() {
            val totalSeconds = distractionTimeMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
}

object AppUsageTracker {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun getTodayUsageStats(context: Context): ScreenTimeStats {
        if (!hasUsageStatsPermission(context)) {
            return ScreenTimeStats(hasPermission = false)
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return ScreenTimeStats(hasPermission = false)

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = cal.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: emptyList()

        val pm = context.packageManager
        val aggregated = mutableMapOf<String, Long>()

        for (u in usageList) {
            if (u.totalTimeInForeground > 0) {
                val current = aggregated.getOrDefault(u.packageName, 0L)
                aggregated[u.packageName] = current + u.totalTimeInForeground
            }
        }

        var totalMs = 0L
        var productiveMs = 0L
        var distractionMs = 0L
        var utilityMs = 0L

        val appItems = mutableListOf<AppUsageItem>()

        for ((pkg, timeMs) in aggregated) {
            // Ignore system launchers or apps with negligible usage (< 15 seconds)
            if (timeMs < 15000L || isIgnoredSystemPackage(pkg)) continue

            totalMs += timeMs
            val category = classifyPackage(pkg)
            when (category) {
                AppCategory.PRODUCTIVE -> productiveMs += timeMs
                AppCategory.DISTRACTION -> distractionMs += timeMs
                AppCategory.UTILITY -> utilityMs += timeMs
            }

            val appName = try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                pkg.substringAfterLast('.')
            }

            appItems.add(
                AppUsageItem(
                    packageName = pkg,
                    appName = appName,
                    totalTimeForegroundMs = timeMs,
                    category = category,
                    percentageOfTotal = 0f
                )
            )
        }

        val sorted = appItems.sortedByDescending { it.totalTimeForegroundMs }
        val finalizedList = sorted.map { item ->
            val pct = if (totalMs > 0) (item.totalTimeForegroundMs.toFloat() / totalMs.toFloat()) * 100f else 0f
            item.copy(percentageOfTotal = pct)
        }

        return ScreenTimeStats(
            hasPermission = true,
            totalScreenTimeMs = totalMs,
            productiveTimeMs = productiveMs,
            distractionTimeMs = distractionMs,
            utilityTimeMs = utilityMs,
            topApps = finalizedList
        )
    }

    private fun isIgnoredSystemPackage(pkg: String): Boolean {
        return pkg in listOf(
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.oppo.launcher",
            "com.vivo.upslide",
            "com.bbk.launcher2"
        )
    }

    private fun classifyPackage(pkg: String): AppCategory {
        val lower = pkg.lowercase()

        // Known Distraction / Social / Entertainment
        if (lower.contains("youtube") || lower.contains("instagram") ||
            lower.contains("tiktok") || lower.contains("facebook") ||
            lower.contains("twitter") || lower.contains("reddit") ||
            lower.contains("netflix") || lower.contains("primevideo") ||
            lower.contains("snapchat") || lower.contains("pinterest") ||
            lower.contains("twitch") || lower.contains("game") ||
            lower.contains("roblox") || lower.contains("pubg") ||
            lower.contains("freefire") || lower.contains("bilibili")
        ) {
            return AppCategory.DISTRACTION
        }

        // Known Productive / Development / Learning / Focus
        if (lower.contains("discipline") || lower.contains("termux") ||
            lower.contains("aide") || lower.contains("vscode") ||
            lower.contains("github") || lower.contains("notion") ||
            lower.contains("obsidian") || lower.contains("anki") ||
            lower.contains("duolingo") || lower.contains("slack") ||
            lower.contains("leetcode") || lower.contains("hackerrank") ||
            lower.contains("w3schools") || lower.contains("cplusplus") ||
            lower.contains("chrome") || lower.contains("firefox") ||
            lower.contains("brave") || lower.contains("pdf") ||
            lower.contains("reader") || lower.contains("books") ||
            lower.contains("docs") || lower.contains("drive") ||
            lower.contains("keep")
        ) {
            return AppCategory.PRODUCTIVE
        }

        return AppCategory.UTILITY
    }
}
