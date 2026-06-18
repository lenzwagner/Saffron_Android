package com.zephron.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Holds the two user-configurable accent colors.
 * Provided app-wide via [LocalAppColors] so any composable can read them.
 */
data class AppColors(
    val accent: Color = Color(0xFFFF6B35),    // orange – buttons, badges, borders
    val secondary: Color = Color(0xFF34693A)  // green  – vegetarian carousel label
) {
    /** Slightly lighter variant used as the gradient top. */
    val gradientTop: Color get() = accent.lighten(0.14f)
    /** Slightly darker variant used as the gradient bottom. */
    val gradientBottom: Color get() = accent.darken(0.12f)
    /** Two-stop list ready for Brush.verticalGradient / linearGradient. */
    val gradient: List<Color> get() = listOf(gradientTop, gradientBottom)
}

fun Color.lighten(fraction: Float): Color = Color(
    red   = (red   + (1f - red)   * fraction).coerceIn(0f, 1f),
    green = (green + (1f - green) * fraction).coerceIn(0f, 1f),
    blue  = (blue  + (1f - blue)  * fraction).coerceIn(0f, 1f),
    alpha = alpha
)

fun Color.darken(fraction: Float): Color = Color(
    red   = (red   * (1f - fraction)).coerceIn(0f, 1f),
    green = (green * (1f - fraction)).coerceIn(0f, 1f),
    blue  = (blue  * (1f - fraction)).coerceIn(0f, 1f),
    alpha = alpha
)

/** Preset palette for the accent (orange) picker. */
val ACCENT_PALETTE = listOf(
    Color(0xFFFF6B35), // Zephron Orange (default)
    Color(0xFFE53935), // Rot
    Color(0xFFD81B60), // Pink
    Color(0xFF8E24AA), // Lila
    Color(0xFF3949AB), // Indigo
    Color(0xFF1E88E5), // Blau
    Color(0xFF039BE5), // Hellblau
    Color(0xFF00897B), // Türkis
    Color(0xFF43A047), // Grün
    Color(0xFFF4511E), // Tiefes Orange
    Color(0xFFFF8F00), // Bernstein
    Color(0xFF6D4C41), // Braun
)

/** Preset palette for the secondary (green) picker. */
val SECONDARY_PALETTE = listOf(
    Color(0xFF34693A), // Zephron Grün (default)
    Color(0xFF2E7D32), // Dunkelgrün
    Color(0xFF558B2F), // Hellgrün
    Color(0xFF00838F), // Cyan
    Color(0xFF0288D1), // Blau
    Color(0xFF5E35B1), // Dunkelviolett
    Color(0xFF00897B), // Smaragd
    Color(0xFF546E7A), // Blaugrau
    Color(0xFF7D5700), // Amber dunkel
    Color(0xFFFF6B35), // Orange (wie Akzent)
    Color(0xFFD81B60), // Pink
    Color(0xFF6D4C41), // Braun
)

val LocalAppColors = staticCompositionLocalOf { AppColors() }
