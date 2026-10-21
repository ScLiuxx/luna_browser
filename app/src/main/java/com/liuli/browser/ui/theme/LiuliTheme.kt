package com.liuli.browser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class DarkModeOption(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色")
}

@Immutable
data class AccentPalette(
    val label: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
)

val AccentPalettes = listOf(
    AccentPalette("星蓝", Color(0xFF0A84FF), Color(0xFF5B5BF5), Color(0xFF00C6FF)),
    AccentPalette("青碧", Color(0xFF00A88F), Color(0xFF00C2A8), Color(0xFF7DE2C3)),
    AccentPalette("暮紫", Color(0xFF8B5CF6), Color(0xFFB06AB3), Color(0xFF5B8CFF)),
    AccentPalette("琥珀", Color(0xFFF08A24), Color(0xFFF2B33D), Color(0xFFE85D75)),
    AccentPalette("珊瑚", Color(0xFFEA5A6B), Color(0xFFF08A7A), Color(0xFFB06AB3))
)

@Immutable
data class LiuliColors(
    val isDark: Boolean,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentSecondary: Color,
    val accentTertiary: Color,
    val glassTint: Color,
    val glassTintStrong: Color,
    val glassBorder: Color,
    val dockTint: Color,
    val pageBackground: Color,
    val scrim: Color,
    val danger: Color,
    val success: Color
)

private val LightColors = LiuliColors(
    isDark = false,
    textPrimary = Color(0xFF101014),
    textSecondary = Color(0xFF4A4F5C),
    textTertiary = Color(0xFF7A8090),
    accent = AccentPalettes[0].primary,
    accentSecondary = AccentPalettes[0].secondary,
    accentTertiary = AccentPalettes[0].tertiary,
    glassTint = Color.White.copy(alpha = 0.46f),
    glassTintStrong = Color.White.copy(alpha = 0.74f),
    glassBorder = Color.White.copy(alpha = 0.62f),
    dockTint = Color.White.copy(alpha = 0.50f),
    pageBackground = Color(0xFFEEF3FF),
    scrim = Color(0xFF101014).copy(alpha = 0.06f),
    danger = Color(0xFFE0413C),
    success = Color(0xFF1B9E63)
)

private val DarkColors = LiuliColors(
    isDark = true,
    textPrimary = Color(0xFFF7F8FA),
    textSecondary = Color(0xFFB9BEcC),
    textTertiary = Color(0xFF8A90A0),
    accent = AccentPalettes[0].primary,
    accentSecondary = AccentPalettes[0].secondary,
    accentTertiary = AccentPalettes[0].tertiary,
    glassTint = Color(0xFF10141E).copy(alpha = 0.46f),
    glassTintStrong = Color(0xFF141926).copy(alpha = 0.76f),
    glassBorder = Color.White.copy(alpha = 0.18f),
    dockTint = Color(0xFF10141E).copy(alpha = 0.52f),
    pageBackground = Color(0xFF0B1020),
    scrim = Color.Black.copy(alpha = 0.22f),
    danger = Color(0xFFFF6B66),
    success = Color(0xFF4CD79A)
)

val LocalLiuliColors = staticCompositionLocalOf { LightColors }

@Composable
fun LiuliTheme(
    darkMode: DarkModeOption = DarkModeOption.System,
    accentIndex: Int = 0,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (darkMode) {
        DarkModeOption.System -> systemDark
        DarkModeOption.Light -> false
        DarkModeOption.Dark -> true
    }
    val base = if (isDark) DarkColors else LightColors
    val accent = AccentPalettes[accentIndex.coerceIn(0, AccentPalettes.lastIndex)]
    val colors = base.copy(
        accent = accent.primary,
        accentSecondary = accent.secondary,
        accentTertiary = accent.tertiary
    )
    CompositionLocalProvider(LocalLiuliColors provides colors) {
        content()
    }
}

object Dimens {
    val screenPadding: Dp = 16.dp
    val tabletPadding: Dp = 24.dp
    val cardRadius: Dp = 26.dp
    val barRadius: Dp = 28.dp
    val dockHeight: Dp = 66.dp
    val topBarHeight: Dp = 56.dp
    val railWidth: Dp = 86.dp
    val minTouch: Dp = 48.dp
}
