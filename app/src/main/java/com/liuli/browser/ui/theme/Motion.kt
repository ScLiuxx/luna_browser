package com.liuli.browser.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
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

    /**
     * 浮层展开：轻微过冲，让面板"长"出来。
     *
     * 取自 Luna 的实际数值（`defpackage/ry4.java:891`）。
     * 它用"周期"而不是刚度来描述弹簧：周期 0.4 秒、阻尼 0.8，
     * 换算成刚度约 247。过冲只有一点点，手感干净。
     */
    val PopupEnterSpring: SpringSpec = SpringSpec(dampingRatio = 0.8f, stiffness = 246.7f)

    /**
     * 浮层收起：同一套阻尼，但周期缩到 0.28 秒（刚度约 504）。
     *
     * **退场必须比进场快**，这是硬编码在 Luna 里的不对称：
     * 面板缩回锚点时用户的注意力已经移开，让它慢慢退会拖沓。
     *
     * 用同一套阻尼是为了让进出的手感连续——
     * 换一套曲线的话，展开与收起看上去像两个不同的组件。
     */
    val PopupExitSpring: SpringSpec = SpringSpec(dampingRatio = 0.8f, stiffness = 503.6f)

    // ---------------------------------------------------------------------
    // 时长
    // ---------------------------------------------------------------------

    const val Fast = 120
    const val Quick = 160
    const val Normal = 220
    const val Slow = 320

    // ---------------------------------------------------------------------
    // 页面转场
    // ---------------------------------------------------------------------

    /**
     * 转场进场时长。
     *
     * 取自 Luna 的实际数值（`p000/mt7.java:155`）。
     * 它比本项目原先用的 [Normal] 略短：190ms 已经足够看清"新页面从哪来"，
     * 再长就变成"等动画"了。
     */
    const val ScreenEnter = 190

    /**
     * 转场退场时长。
     *
     * 取自 Luna 的实际数值（`p000/mt7.java:161`）。
     *
     * 退场只有进场的一半，这个**不对称**是关键：
     * 用户的注意力已经跟着新页面走了，旧页面就该立刻让路；
     * 两边等长时，旧页面会赖在屏幕上不走，转场显得拖沓。
     */
    const val ScreenExit = 95

    /**
     * 主题过渡时长。
     *
     * 换主题时整屏颜色一起淡过去要多久。
     *
     * 取 260ms：比转场的 190ms 长一点（换主题比换页面更"重"，
     * 需要更长的过渡让眼睛跟上），又比 Luna 阅读模式的 500ms 短——
     * 那是网页里的 body 文字，本项目是整个 App 的界面，
     * 500ms 会让人觉得"卡了一下"。
     */
    const val ThemeFade = 260

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

    /**
     * 浮层展开动画。
     *
     * 退场另有 [popupExitSpring]，它比进场快一半——
     * 面板缩回锚点时用户注意力已经移开，慢慢退会拖沓。
     */
    fun popupEnterSpring(reduced: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) tween(Motion.Fast) else Motion.PopupEnterSpring.spring()

    /** 浮层收起动画。刻意比进场快。 */
    fun popupExitSpring(reduced: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) tween(Motion.Fast) else Motion.PopupExitSpring.spring()

    /**
     * 补间时长。
     *
     * "减少动态效果"开启时一律用最短档：补间规格此前完全无视这个开关，
     * 只有弹簧版本会降级，于是"减少动态效果"对绝大多数转场等于没开。
     * 时长压到 Fast 之后，位移与缩放仍在，但快到几乎察觉不到。
     */
    private fun duration(reduced: Boolean, durationMillis: Int): Int =
        if (reduced) Motion.Fast else durationMillis

    /** 进入动画（用于 fadeIn / scaleIn 等 Float 规格的转场）。 */
    fun enter(reduced: Boolean, durationMillis: Int = Motion.Normal): FiniteAnimationSpec<Float> =
        tween(duration(reduced, durationMillis), easing = Motion.Enter)

    /** 退出动画（用于 fadeOut / scaleOut 等 Float 规格的转场）。 */
    fun exit(reduced: Boolean, durationMillis: Int = Motion.Quick): FiniteAnimationSpec<Float> =
        tween(duration(reduced, durationMillis), easing = Motion.Exit)

    /**
     * 页面级转场的进场淡入。
     *
     * 与 [exit] 配套使用：进场 190ms、退场 95ms。
     * 两边等长时旧页面会在新页面到位后还赖着不走，转场显得拖。
     */
    fun screenEnter(reduced: Boolean): FiniteAnimationSpec<Float> =
        tween(duration(reduced, Motion.ScreenEnter), easing = Motion.Enter)

    /** 页面级转场的退场淡出。刻意比进场短一半。 */
    fun screenExit(reduced: Boolean): FiniteAnimationSpec<Float> =
        tween(duration(reduced, Motion.ScreenExit), easing = Motion.Exit)

    /** 数值补间动画。 */
    fun value(reduced: Boolean, durationMillis: Int = Motion.Normal): FiniteAnimationSpec<Float> =
        tween(duration(reduced, durationMillis), easing = Motion.Enter)

    /** 整数补间动画（用于行数、偏移量等 Int 状态）。 */
    fun intValue(reduced: Boolean, durationMillis: Int = Motion.Normal): FiniteAnimationSpec<Int> =
        tween(duration(reduced, durationMillis), easing = Motion.Enter)

    /** 尺寸补间动画（用于 expandVertically 等 IntSize 规格）。 */
    fun sizeValue(reduced: Boolean, durationMillis: Int = Motion.Normal): FiniteAnimationSpec<IntSize> =
        tween(duration(reduced, durationMillis), easing = Motion.Enter)

    /** 颜色过渡（用于 animateColorAsState）。 */
    fun color(reduced: Boolean, durationMillis: Int = Motion.Quick): FiniteAnimationSpec<Color> =
        tween(duration(reduced, durationMillis), easing = Motion.Enter)

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

    /** 浮层展开动画。 */
    @Composable
    fun popupEnterSpring(): FiniteAnimationSpec<Float> = popupEnterSpring(reduceMotion)

    /** 浮层收起动画。 */
    @Composable
    fun popupExitSpring(): FiniteAnimationSpec<Float> = popupExitSpring(reduceMotion)

    /** 进入动画（用于 fadeIn / scaleIn 等 Float 规格的转场）。 */
    @Composable
    fun enter(durationMillis: Int = Motion.Normal): FiniteAnimationSpec<Float> =
        enter(reduceMotion, durationMillis)

    /** 退出动画（用于 fadeOut / scaleOut 等 Float 规格的转场）。 */
    @Composable
    fun exit(durationMillis: Int = Motion.Quick): FiniteAnimationSpec<Float> =
        exit(reduceMotion, durationMillis)

    /** 页面级转场的进场淡入（190ms）。 */
    @Composable
    fun screenEnter(): FiniteAnimationSpec<Float> = screenEnter(reduceMotion)

    /** 页面级转场的退场淡出（95ms）。 */
    @Composable
    fun screenExit(): FiniteAnimationSpec<Float> = screenExit(reduceMotion)

    /** 数值补间动画。 */
    @Composable
    fun value(durationMillis: Int = Motion.Normal): FiniteAnimationSpec<Float> =
        value(reduceMotion, durationMillis)

    /** 整数补间动画（用于行数、偏移量等 Int 状态）。 */
    @Composable
    fun intValue(durationMillis: Int = Motion.Normal): FiniteAnimationSpec<Int> =
        intValue(reduceMotion, durationMillis)

    /** 尺寸补间动画（用于 expandVertically 等 IntSize 规格）。 */
    @Composable
    fun sizeValue(durationMillis: Int = Motion.Normal): FiniteAnimationSpec<IntSize> =
        sizeValue(reduceMotion, durationMillis)

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
