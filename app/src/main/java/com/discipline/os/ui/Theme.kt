package com.discipline.os.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * DisciplineOS Adaptive Design System
 * TripGlide / Apple-Tier Modern UI with Full Dark & Light Mode Support
 */
data class AppThemeColors(
    val isDark: Boolean,
    val canvasBg: Color,
    val cardBg: Color,
    val cardElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val borderSubtle: Color,
    val borderDivider: Color,
    val primaryActionBg: Color,
    val primaryActionFg: Color,
    val secondaryActionBg: Color,
    val secondaryActionFg: Color,
    val dockBg: Color,
    val dockBorder: Color,
    val dockActiveCircle: Color,
    val dockActiveIcon: Color,
    val dockInactiveIcon: Color,
    val accentFlame: Color,
    val accentFlameSoft: Color,
    val successGreen: Color,
    val successGreenSoft: Color,
    val accentCyan: Color,
    val accentCyanSoft: Color
)

// Dark Palette (Apple / Obsidian / TripGlide Dark) - Default for Eye Strain Relief
val DarkThemePalette = AppThemeColors(
    isDark = true,
    canvasBg = Color(0xFF090D12),           // Deep OLED-friendly Canvas (#090d12)
    cardBg = Color(0xFF141922),             // Elevated Card Slate (#141922)
    cardElevated = Color(0xFF1C222E),       // Secondary elevated surface
    textPrimary = Color(0xFFF0F6FC),        // Pure crisp white text
    textSecondary = Color(0xFF94A3B8),      // Smooth slate silver secondary text
    textMuted = Color(0xFF64748B),          // Muted captions & placeholders
    borderSubtle = Color(0xFF262E3B),       // Clean hairline borders
    borderDivider = Color(0xFF1E2530),      // Divider lines
    primaryActionBg = Color(0xFFF0F6FC),    // High-contrast primary action pill
    primaryActionFg = Color(0xFF090D12),    // Dark text inside primary action
    secondaryActionBg = Color(0xFF1E2532),  // Secondary button surface
    secondaryActionFg = Color(0xFFF0F6FC),  // Secondary button text/icon
    dockBg = Color(0xFF141922),             // Floating stadium dock
    dockBorder = Color(0xFF2A3342),         // Dock outline
    dockActiveCircle = Color(0xFFFFFFFF),   // Active tab white circle
    dockActiveIcon = Color(0xFF090D12),     // Active tab icon
    dockInactiveIcon = Color(0xFF8B949E),   // Inactive tab icon
    accentFlame = Color(0xFFFF6B35),        // Flame Orange
    accentFlameSoft = Color(0xFF331A12),    // Dark flame tint
    successGreen = Color(0xFF10B981),       // Emerald Green
    successGreenSoft = Color(0xFF0F3020),   // Dark emerald tint
    accentCyan = Color(0xFF38BDF8),         // Sky Cyan
    accentCyanSoft = Color(0xFF0E2A3A)      // Dark cyan tint
)

// Light Palette (TripGlide / Apple-Tier Modern Spec)
val LightThemePalette = AppThemeColors(
    isDark = false,
    canvasBg = Color(0xFFF5F6F7),           // Soft pearl off-white (#f5f6f7)
    cardBg = Color(0xFFFFFFFF),             // Crisp pure white cards (#ffffff)
    cardElevated = Color(0xFFF8F9FA),       // Subtle elevated surface
    textPrimary = Color(0xFF212529),        // Dark charcoal text
    textSecondary = Color(0xFF6C757D),      // Medium slate gray
    textMuted = Color(0xFFADB5BD),          // Soft gray
    borderSubtle = Color(0xFFE9ECEF),       // Hairline border
    borderDivider = Color(0xFFDEE2E6),      // Divider lines
    primaryActionBg = Color(0xFF212529),    // Dark charcoal action pill
    primaryActionFg = Color(0xFFFFFFFF),    // Pure white text
    secondaryActionBg = Color(0xFFF0F2F5),  // Light gray button
    secondaryActionFg = Color(0xFF212529),  // Dark icon
    dockBg = Color(0xFF212529),             // Dark charcoal dock
    dockBorder = Color(0xFF212529),
    dockActiveCircle = Color(0xFFFFFFFF),
    dockActiveIcon = Color(0xFF212529),
    dockInactiveIcon = Color(0xCCFFFFFF),
    accentFlame = Color(0xFFFF5722),
    accentFlameSoft = Color(0xFFFFF0EB),
    successGreen = Color(0xFF10B981),
    successGreenSoft = Color(0xFFE6F4EA),
    accentCyan = Color(0xFF0284C7),
    accentCyanSoft = Color(0xFFE0F2FE)
)

val LocalAppColors = staticCompositionLocalOf { DarkThemePalette }

object AppTheme {
    val colors: AppThemeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

// Convenient Dynamic Color Accessors
val CanvasBg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.canvasBg
val CardWhite: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.cardBg
val CardElevated: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.cardElevated
val BorderSubtle: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.borderSubtle
val BorderDivider: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.borderDivider
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textSecondary
val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textMuted
val TextDark: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary
val PrimaryActionBg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.primaryActionBg
val PrimaryActionFg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.primaryActionFg
val SecondaryActionBg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.secondaryActionBg
val SecondaryActionFg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.secondaryActionFg
val AccentFlame: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentFlame
val AccentFlameSoft: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentFlameSoft
val SuccessGreen: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.successGreen
val SuccessGreenSoft: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.successGreenSoft
val AccentCyan: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentCyan
val AccentCyanSoft: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentCyanSoft

// Backward Compatibility Static Constants
val DarkCharcoal: Color = Color(0xFF212529)

@Composable
fun DisciplineTheme(
    isDarkMode: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (isDarkMode) DarkThemePalette else LightThemePalette
    val materialScheme = if (isDarkMode) {
        darkColorScheme(
            primary = colors.primaryActionBg,
            onPrimary = colors.primaryActionFg,
            background = colors.canvasBg,
            onBackground = colors.textPrimary,
            surface = colors.cardBg,
            onSurface = colors.textPrimary
        )
    } else {
        lightColorScheme(
            primary = colors.primaryActionBg,
            onPrimary = colors.primaryActionFg,
            background = colors.canvasBg,
            onBackground = colors.textPrimary,
            surface = colors.cardBg,
            onSurface = colors.textPrimary
        )
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = Typography(),
            content = content
        )
    }
}
