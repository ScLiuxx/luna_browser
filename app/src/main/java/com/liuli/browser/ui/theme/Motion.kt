package com.liuli.browser.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 全局动效规格。
 *
 * 项目此前的动画参数散落在各个文件里：同一套"页面级弹性"
 * `spring(0.9f, 380f)` 出现 8 次，tween 时长有 9 种裸数字。
 * 这里把曲线与时长收敛成一份规格，所有动效都从这里取值，
 * 保证整个 App 的手感一致，也便于统一响应"减少动态效果"。
 */
object Motion {

    // ---------------------------------------------------------------------
    // 弹簧曲线
    // ---------------------------------------------------------------------

    /** 页面级弹性：轻微过冲，适合容器出现、面板展开。 */
    val PageSpring: SpringSpec = SpringSpec(dampingRatio = 0.9f, stiffness = 380f)

    /** 共享元素（一镜到底）：不过冲，保证地址栏与快照严丝合缝。 */
    val SharedSpring: SpringSpec = SpringSpec(dampingRatio = 1f, stiffness = 342f)

    /** 液态阻尼：明显过冲，用于玻璃件的甩动与回弹。 */
    val LiquidSpring: SpringSpec = SpringSpec(dampingRatio = 0.55f, stiffness = 300f)

    /** 按压回弹：快起快落，手指松开立刻回位。 */
    val PressSpring: SpringSpec = SpringSpec(dampingRatio = 0.7f, stiffness = 700f)

    /** 通用元素位移：无过冲，避免列表项抖动。 */
    val GlideSpring: SpringSpec = SpringSpec(dampingRatio = 1f, stiffness = 420f)

    // ---------------------------------------------------------------------
    // 时长
    // ---------------------------------------------------------------------

    const val Fast = 120
    const val Quick = 160
    const val Normal = 220
    const val Slow = 320

    // ---------------------------------------------------------------------
    // 缓动
    // ---------------------------------------------------------------------

    /** 进入：快起慢收，元素干净利落地落位。 */
    val Enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 退出：慢起快走，元素干净地离场。 */
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 强调：用于需要被注意到的变化。 */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/**
 * 弹簧规格的轻量包装，便于在需要 `AnimationSpec` 的地方直接使用，
 * 同时保留 dampingRatio / stiffness 以便各处保持一致。
 */
data class SpringSpec(
    val dampingRatio: Float,
    val stiffness: Float
) {
    /** 不带可见阈值的弹簧，适用于 Float 等连续类型。 */
    fun spring(): FiniteAnimationSpec<Float> =
        spring(dampingRatio = dampingRatio, stiffness = stiffness)

    /** 带可见阈值的弹簧，用于 IntOffset / Rect / Dp 等类型。 */
    fun <T> spring(visibilityThreshold: T): FiniteAnimationSpec<T> =
        spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness,
            visibilityThreshold = visibilityThreshold
        )
}

/**
 * 是否响应系统的"减少动态效果"。
 *
 * 此前只有极光背景与共享元素两处生效，其余动效全部无视该设置。
 * 这里做成全局 CompositionLocal，让任何动效都能查询并降级。
 */
val LocalReduceMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** 当前是否开启了"减少动态效果"。 */
val reduceMotion: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalReduceMotion.current

/**
 * 统一获取动效规格。
 *
 * 每个规格都有两套入口：
 * - 带 `reduced: Boolean` 的非 Composable 版本，用于 `AnimatedContent.transitionSpec`
 *   这类不是 Composable 的回调；
 * - 不带参数的 Composable 版本，自动读取全局开关，用于普通组件里。
 */
object MotionSpec {

    // ---------------------------------------------------------------------
    // 非 Composable 版本
    // ---------------------------------------------------------------------

    /** 页面级弹性动画（Float）。 */
    fun pageSpring(reduced: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) tween(Motion.Fast) else Motion.PageSpring.spring()

    /** 页面级弹性动画（带可见阈值，如 IntOffset / Rect / Dp）。 */
    fun <T> pageSpring(reduced: Boolean, visibilityThreshold: T): FiniteAnimationSpec<T> =
        if (reduced) {
            tween(Motion.Fast)
        } else {
            Motion.PageSpring.spring(visibilityThreshold)
        }

    /** 共享元素动画。 */
    fun <T> sharedSpring(reduced: Boolean, visibilityThreshold: T): FiniteAnimationSpec<T> =
        if (reduced) {
            tween(Motion.Fast)
        } else {
            Motion.SharedSpring.spring(visibilityThreshold)
        }

    /** 液态阻尼动画。 */
    fun liquidSpring(reduced: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) tween(Motion.Fast) else Motion.LiquidSpring.spring()

    /** 按压回弹动画。 */
    fun pressSpring(reduced: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) tween(Motion.Fast) else Motion.PressSpring.spring()

    /** 通用位移动画。 */
    fun glideSpring(reduced: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) tween(Motion.Fast) else Motion.GlideSpring.spring()

    /** 进入动画（用于 fadeIn / scaleIn 等 Float 规格的转场）。 */
    fun enter(reduced: Boolean, durationMillis: Int = Motion.Normal): FiniteAnimationSpec<Float> =
        tween(durationMillis, easing = Motion.Enter)

    /** 退出动画（用于 fadeOut / scaleOut 等 Float 规格的转场）。 */
    fun exit(reduced: Boolean, durationMillis: Int = Motion.Quick): FiniteAnimationSpec<Float> =
        tween(durationMillis, easing = Motion.Exit)

    /** 数值补间动画。 */
    fun value(reduced: Boolean, durationMillis: Int = Motion.Normal): FiniteAnimationSpec<Float> =
        tween(durationMillis, easing = Motion.Enter)

    /** 颜色过渡（用于 animateColorAsState）。 */
    fun color(reduced: Boolean, durationMillis: Int = Motion.Quick): FiniteAnimationSpec<Color> =
        tween(durationMillis, easing = Motion.Enter)

    // ---------------------------------------------------------------------
    // Composable 版本
    // ---------------------------------------------------------------------

    /** 页面级弹性动画（Float）。 */
    @Composable
    fun pageSpring(): FiniteAnimationSpec<Float> = pageSpring(reduceMotion)

    /** 页面级弹性动画（带可见阈值）。 */
    @Composable
    fun <T> pageSpring(visibilityThreshold: T): FiniteAnimationSpec<T> =
        pageSpring(reduceMotion, visibilityThreshold)

    /** 共享元素动画。 */
    @Composable
    fun <T> sharedSpring(visibilityThreshold: T): FiniteAnimationSpec<T> =
        sharedSpring(reduceMotion, visibilityThreshold)

    /** 液态阻尼动画。 */
    @Composable
    fun liquidSpring(): FiniteAnimationSpec<Float> = liquidSpring(reduceMotion)

    /** 按压回弹动画。 */
    @Composable
    fun pressSpring(): FiniteAnimationSpec<Float> = pressSpring(reduceMotion)

    /** 通用位移动画。 */
    @Composable
    fun glideSpring(): FiniteAnimationSpec<Float> = glideSpring(reduceMotion)

    /** 进入动画（用于 fadeIn / scaleIn 等 Float 规格的转场）。 */
    @Composable
    fun enter(durationMillis: Int = Motion.Normal): FiniteAnimationSpec<Float> =
        enter(reduceMotion, durationMillis)

    /** 退出动画（用于 fadeOut / scaleOut 等 Float 规格的转场）。 */
    @Composable
    fun exit(durationMillis: Int = Motion.Quick): FiniteAnimationSpec<Float> =
        exit(reduceMotion, durationMillis)

    /** 数值补间动画。 */
    @Composable
    fun value(durationMillis: Int = Motion.Normal): FiniteAnimationSpec<Float> =
        value(reduceMotion, durationMillis)

    /** 颜色过渡（用于 animateColorAsState）。 */
    @Composable
    fun color(durationMillis: Int = Motion.Quick): FiniteAnimationSpec<Color> =
        color(reduceMotion, durationMillis)

    /**
     * 列表逐项入场时的延迟：让项目依次浮现，而不是整片同时出现。
     * "减少动态效果"开启时不加延迟，也不做位移动画。
     */
    @Composable
    fun staggerDelay(index: Int, stepMillis: Int = 28): Int =
        if (reduceMotion) 0 else (index.coerceAtMost(8) * stepMillis)

    /** 记住一个与 reduceMotion 绑定的动画规格。 */
    @Composable
    fun <T> rememberSpring(
        dampingRatio: Float,
        stiffness: Float,
        visibilityThreshold: T
    ): FiniteAnimationSpec<T> {
        val reduced = reduceMotion
        return remember(dampingRatio, stiffness, visibilityThreshold, reduced) {
            if (reduced) {
                tween(Motion.Fast)
            } else {
                spring(
                    dampingRatio = dampingRatio,
                    stiffness = stiffness,
                    visibilityThreshold = visibilityThreshold
                )
            }
        }
    }
}
