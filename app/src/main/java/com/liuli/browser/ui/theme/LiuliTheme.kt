package com.liuli.browser.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
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

/**
 * 主题调色板。
 *
 * 深浅两套主题之间切换时，颜色**不是硬切**的，而是一起淡过去。
 * Luna 的阅读模式做的是同一件事：
 * `body { transition: color 500ms ease }`（`defpackage/vzc.java:385`）——
 * 换主题时前景与背景同时过渡，眼睛不用重新适应。
 *
 * 硬切的问题是它"啪"一下把整屏换掉了：
 * 深色主题下白字变黑字的那一瞬间，对比度与亮度同时跳变，
 * 正在读的那一行会闪一下。一起淡过去就没有这个闪。
 *
 * 逐个颜色动画而不是整屏交叉淡化：后者会让整棵界面树重建，
 * 几百个组件同时换掉背景与文字色；前者只改颜色值，
 * 布局完全不动，开销小得多。
 */
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
) {
    /**
     * 危险色之上的文字颜色。
     *
     * 危险色在深浅两套主题里明暗相反：浅色主题是深红、深色主题是亮红，
     * 压在危险色上的文字必须跟着反过来选，否则深色主题下白字糊在亮红上。
     * 三处确认框此前各自写死这个色值，收成一处。
     */
    val onDanger: Color get() = if (isDark) Color(0xFF2B0B0B) else Color.White
}

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

/**
 * 调色板。
 *
 * 用 `compositionLocalOf` 而不是 `staticCompositionLocalOf`：
 * 后者只在**值变化时**让读取者重组，而主题过渡时调色板是**逐帧变化**的
 * （淡过去的每一帧都是一个新值）。用 static 的话，读取者拿到的
 * 永远是最后一次提供的值，中间那几百毫秒的过渡根本看不见。
 *
 * `compositionLocalOf` 会记录读取位置，值一变就让所有读取者重组——
 * 这正是主题过渡需要的。
 */
val LocalLiuliColors = compositionLocalOf { LightColors }

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
    val target = base.copy(
        accent = accent.primary,
        accentSecondary = accent.secondary,
        accentTertiary = accent.tertiary
    )
    // 颜色一起淡过去，而不是硬切（见 LiuliColors 的注释）。
    //
    // 逐个字段动画：每个字段是一条独立的补间，
    // 但它们的规格与时长相同，所以整屏看上去是同步的一次过渡。
    //
    // isDark 是布尔量，不能动画——它是"现在算深色还是浅色"的判断依据，
    // 半途改变会让 onDanger 之类的派生值在过渡中途翻一次。
    // 它在过渡开始时就切到目标值，其余颜色跟过去。
    val spec = MotionSpec.color(reduceMotion, Motion.ThemeFade)
    val colors = LiuliColors(
        isDark = isDark,
        textPrimary = animateColor(target.textPrimary, spec),
        textSecondary = animateColor(target.textSecondary, spec),
        textTertiary = animateColor(target.textTertiary, spec),
        accent = animateColor(target.accent, spec),
        accentSecondary = animateColor(target.accentSecondary, spec),
        accentTertiary = animateColor(target.accentTertiary, spec),
        glassTint = animateColor(target.glassTint, spec),
        glassTintStrong = animateColor(target.glassTintStrong, spec),
        glassBorder = animateColor(target.glassBorder, spec),
        dockTint = animateColor(target.dockTint, spec),
        pageBackground = animateColor(target.pageBackground, spec),
        scrim = animateColor(target.scrim, spec),
        danger = animateColor(target.danger, spec),
        success = animateColor(target.success, spec)
    )
    CompositionLocalProvider(LocalLiuliColors provides colors) {
        content()
    }
}

/**
 * 主题过渡时单个颜色的动画。
 *
 * 用 `animateColorAsState` 而不是自己插值：它会在颜色再次变化时
 * 从**当前**颜色继续走，不会跳回起点。
 */
@Composable
private fun animateColor(target: Color, spec: AnimationSpec<Color>): Color {
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = spec,
        label = "theme-color"
    )
    return animated
}

object Dimens {
    val screenPadding: Dp = 16.dp
    val tabletPadding: Dp = 24.dp
    val cardRadius: Dp = 26.dp
    val barRadius: Dp = 28.dp
    val dockHeight: Dp = 66.dp
    val topBarHeight: Dp = 56.dp

    /**
     * 单栏页面的内容宽度上限。
     *
     * 此前首页 880dp、设置页 860dp、书签页 1000dp、标签页 1000/1160dp，
     * 四个同类页面四个值：在平板上左右切换时内容宽度会跳。
     * 这里收成一档，超出就居中留白，不再各页自己写数字。
     */
    val pageContentWidth: Dp = 1000.dp

    /**
     * 对话框面板的宽度上限。
     *
     * 确认框、错误卡、隐私护盾面板此前都写 380dp，隐私说明写 520dp。
     * 前者一起收进令牌，后者是整篇文档，单独留档。
     */
    val dialogWidth: Dp = 380.dp
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
    /**
     * 页面大标题：设置页、书签页、标签页、隐私门。
     *
     * 此前这里是 23sp，而真正当页面标题用的三页各自在 display 之上
     * 又写了一遍 26sp：同一个角色有两种字号。
     * 这一档改成 26sp，由令牌自己定，手机上的四页完全一致。
     */
    val display: TextStyle = TextStyle(
        fontSize = 26.sp,
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
