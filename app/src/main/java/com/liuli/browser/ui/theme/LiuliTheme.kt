package com.liuli.browser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/**
 * 圆角阶梯。
 *
 * 此前全 App 用了 17 种不同的圆角半径（6dp 到 34dp 都有），
 * 同样的卡片在不同页面圆角不同，玻璃的折射高光走向也随之变化。
 * 这里收敛成 6 档，按"容器越大、圆角越圆"配对，跨页面看过去才像一套东西。
 */
object Radius {
    /** 极小元素：徽标、内嵌图标底座。 */
    val xs: Dp = 8.dp

    /** 小元素：小按钮、输入框、列表行。 */
    val sm: Dp = 14.dp

    /** 中等：次级卡片、浮层内块。 */
    val md: Dp = 20.dp

    /** 大：主卡片。 */
    val lg: Dp = 26.dp

    /** 特大：面板、对话框、地址栏。 */
    val xl: Dp = 30.dp

    /** 胶囊：标签页、色板开关、角标。 */
    val pill: Dp = 50.dp
}

/**
 * 间距阶梯。
 *
 * 8 的倍数为主，4 用于元素内部的微调，12 与 20 用于分段。
 * 全 App 的留白节奏由此统一，避免"这块 8dp、那块 10dp"的随意感。
 */
object Space {
    /** 元素内部的极小留白。 */
    val xxs: Dp = 2.dp

    /** 图标与文字之间。 */
    val xs: Dp = 4.dp

    /** 紧凑控件内部。 */
    val sm: Dp = 8.dp

    /** 控件之间。 */
    val md: Dp = 12.dp

    /** 段落之间。 */
    val lg: Dp = 16.dp

    /** 分段之间。 */
    val xl: Dp = 24.dp

    /** 大块分隔。 */
    val xxl: Dp = 32.dp
}

/**
 * 排版层级。
 *
 * 此前字号散落 14 档（7sp 到 23sp），正文与标签常常差 1sp，
 * 层级关系读不出来。这里定义 8 档语义化字号，每档绑定字重，
 * 跨页面看过去，标题就是标题、说明就是说明。
 */
object Type {
    /** 页面大标题：设置页、隐私门。 */
    val display: TextStyle = TextStyle(
        fontSize = 23.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp
    )

    /** 区块标题：卡片头、对话框标题。 */
    val title: TextStyle = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    )

    /** 小节标题：设置页分区名。 */
    val sectionTitle: TextStyle = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp
    )

    /** 正文：列表主文案、输入内容。 */
    val body: TextStyle = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    )

    /** 次要正文：说明句、列表副标题。 */
    val bodySmall: TextStyle = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 17.sp
    )

    /** 标签：徽标文字、状态标签。 */
    val label: TextStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    )

    /** 极小标签：角标数字、元信息。 */
    val caption: TextStyle = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 14.sp
    )

    /** 最小：角标计数。 */
    val micro: TextStyle = TextStyle(
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium
    )
}
