package com.liuli.browser.ui.liquid

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.reduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 滑块的拖拽状态。
 *
 * 这些标记只在手势回调与协程里读写，不参与组合：
 * 用普通字段而不是 Compose 状态，拖动时就不会每帧触发一次重组。
 */
private class SliderDragState {
    /** 本次手势是否真的拖动过。 */
    var dragging: Boolean = false

    /** 本次手势累计的水平位移：越过阈值才算拖动。 */
    var accum: Float = 0f

    /** 是否正在越界（橡皮筋状态）：跨过边界时给一次"咔哒"。 */
    var overshooting: Boolean = false

    /** 最近一次给过轻触反馈的档位。 */
    var hapticStep: Int = Int.MIN_VALUE

    /** 落位动画是否正在跑。 */
    var settling: Boolean = false

    /**
     * 落位动画的代号。
     *
     * 每次落位都会换一个代号：被掐掉的旧动画收尾时，
     * 只有代号还是当前这个的人才有权把 settling 放下。
     * 否则旧动画的收尾会把新动画的 settling 抹掉，
     * 外部赋值就会在动画进行到一半时插进来，拇指跟着抖。
     */
    var settleToken: Int = 0

    /** 落位动画句柄：新的手势开始前要先把它掐掉。 */
    var settleJob: Job? = null

    /**
     * 落位用的弹簧规格，每次重组刷新。
     *
     * 放在这里而不是直接捕获：DampedDragAnimation 是长期存活的 remember 对象，
     * 它回调里若抓着第一次组合时的规格，系统"减少动态效果"切换后
     * 滑块还会用旧的弹簧曲线。
     */
    var settleSpec: AnimationSpec<Float> = MotionSpec.glideSpring(false)
}

/**
 * Port of the catalog's LiquidSlider. The glass thumb is rendered from the
 * combined page/settings backdrop, so it refracts the content underneath.
 */
@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    contentDescription: String = "滑块"
) {
    val colors = LocalLiuliColors.current
    val accentColor = colors.accent
    // 轨道底色跟开关、玻璃件同一量级：深色 0.36、浅色 0.24。
    // 此前浅色只有 0.20，滑轨几乎看不见，用户不知道那儿可以拖。
    val trackColor = if (colors.isDark) {
        Color(0xFF8A8A94).copy(alpha = 0.36f)
    } else {
        Color(0xFF787878).copy(alpha = 0.24f)
    }
    val trackBackdrop = rememberLayerBackdrop()
    // 跨越整数刻度时给一次轻触反馈：滑动过程中能"数"出档位。
    val haptics = LocalHapticFeedback.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current
    // 越过这个位移才算"拖动"：手势开始时会先来一次位移为零的事件，
    // 不设阈值的话，轻点拇指也会被当成拖动。
    val dragSlop = with(density) { 3f.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    // "减弱动态效果"时滑块直接落位，不再用弹簧回弹。
    // 用 rememberUpdatedState 包裹：开关本身可以热切换，回调里要读到最新值。
    val reduced = rememberUpdatedState(reduceMotion)

    val span = valueRange.endInclusive - valueRange.start
    // 整数量程才画刻度：档位是可以数的滑块才需要刻度，
    // 连续量程画一排标记反而是噪声。
    val showTicks = valueRange.start == valueRange.start.toInt().toFloat() &&
        valueRange.endInclusive == valueRange.endInclusive.toInt().toFloat() &&
        span.toInt() in 1..24
    // 落位用带一点过冲的弹簧：松手后拇指轻轻晃一下才停，手感更像液体。
    // "减少动态效果"时 MotionSpec 会换成快而稳的补间。
    val settleSpec = MotionSpec.rememberSpring(
        dampingRatio = 0.72f,
        stiffness = 700f,
        visibilityThreshold = visibilityThreshold
    )

    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth
        // 拇指可见值：必须是 Compose 状态，绘制层才会在它变化时重绘。
        // 拖动期间手指写到哪就是哪，1:1 跟手：
        // 早先这里每帧都起协程去同步 Animatable，拇指会落后手指半拍。
        val localValue = remember { mutableFloatStateOf(value()) }
        val dragState = remember { SliderDragState() }
        dragState.settleSpec = settleSpec
        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(animationScope = animationScope)
        }

        // 滑到目标值：一次只允许有一个动画在跑，新的开始前先掐掉旧的。
        fun animateTo(target: Float) {
            dragState.settleJob?.cancel()
            if (reduced.value) {
                localValue.floatValue = target
                dragState.settling = false
                return
            }
            val from = localValue.floatValue
            dragState.settling = true
            // 每次落位换一个代号：被掐掉的旧动画收尾时，
            // 只有代号还是当前这个的人才有权把 settling 放下。
            val token = ++dragState.settleToken
            dragState.settleJob = animationScope.launch {
                try {
                    animate(
                        initialValue = from,
                        targetValue = target,
                        animationSpec = dragState.settleSpec
                    ) { v, _ -> localValue.floatValue = v }
                } finally {
                    // 必须在这里收尾：动画被新的手势掐掉时，
                    // 协程会在 animate 里抛取消异常，后面的代码不会执行。
                    // settling 若留在 true，外部赋值就再也推不动拇指了。
                    if (dragState.settleToken == token) {
                        dragState.settling = false
                    }
                }
            }
        }

        // 只用它的按压形变；位置由 localValue 自己管。
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialScale = 1f,
                // 按下时拇指鼓成一颗液滴：1.2 倍刚好看得出形变，
                // 又不会胀到把两边的刻度整个盖住。
                pressedScale = 1.2f,
                onDragStarted = {
                    // 抓住拇指时，任何还在跑的落位动画都得让位，
                    // 否则弹簧会继续把拇指往旧目标上拽，手指与拇指会打架。
                    dragState.settleJob?.cancel()
                    dragState.settleJob = null
                    dragState.dragging = false
                    dragState.accum = 0f
                },
                onDragStopped = {
                    if (dragState.dragging) {
                        // 整数量程松手时对齐到最近的档位：拇指停在刻度上，
                        // 而不是停在两格之间晃。
                        val clamped = localValue.floatValue.coerceIn(valueRange)
                        val snapped = if (showTicks) {
                            clamped.roundToInt().toFloat()
                                .coerceIn(valueRange.start, valueRange.endInclusive)
                        } else {
                            clamped
                        }
                        onValueChange(snapped)
                        animateTo(snapped)
                        dragState.dragging = false
                    }
                },
                onDrag = { _, dragAmount ->
                    // 手势开始时会先来一次位移为零的事件，
                    // 直接标记成"拖动"会让轻点拇指也被当成拖动，松手时拇指会莫名跳一格。
                    dragState.accum += dragAmount.x
                    if (!dragState.dragging && abs(dragState.accum) > dragSlop) {
                        dragState.dragging = true
                    }
                    if (dragState.dragging) {
                        val delta = span * (dragAmount.x / trackWidth)
                        val raw = if (isLtr) {
                            localValue.floatValue + delta
                        } else {
                            localValue.floatValue - delta
                        }
                        // 越界时给阻尼：拖到头还能继续拽，但明显变沉，
                        // 告诉用户"到头了"，而不是硬邦邦地卡住。
                        // 这里不能把结果夹回区间内：夹掉之后橡皮筋就废了，
                        // 拇指会硬停在边界上，用户感觉不到"到头了"。
                        localValue.floatValue = when {
                            raw < valueRange.start ->
                                valueRange.start + (raw - valueRange.start) * 0.35f
                            raw > valueRange.endInclusive ->
                                valueRange.endInclusive + (raw - valueRange.endInclusive) * 0.35f
                            else -> raw
                        }
                        // 越界时震一下：橡皮筋看得见，震动让"到头了"更明确。
                        val over = localValue.floatValue < valueRange.start ||
                            localValue.floatValue > valueRange.endInclusive
                        if (over != dragState.overshooting) {
                            dragState.overshooting = over
                            if (over) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        // 每跨过一个整数档位震一下：滑动时能感知到自己走了几格。
                        val step = localValue.floatValue.roundToInt()
                        if (step != dragState.hapticStep) {
                            dragState.hapticStep = step
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        // 拖动中直接报出去：外部数值与拇指始终一致。
                        onValueChange(localValue.floatValue.coerceIn(valueRange))
                    }
                }
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value() }.collectLatest { current ->
                // 拖动路径自己管位置，这里不能再叠加一次动画，
                // 否则两者互相打断，拇指出现抖动。
                // 落位动画正在跑的时候也不要插手。
                if (!dragState.dragging && !dragState.settling &&
                    abs(current - localValue.floatValue) > 0.001f
                ) {
                    animateTo(current)
                }
            }
        }

        // 轨道：凹槽 + 已灌注的一段 + 刻度，三层叠在一起。
        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(Capsule())
                    .background(trackColor)
                    .pointerInput(animationScope) {
                        detectTapGestures { position ->
                            val delta = span * (position.x / trackWidth)
                            val target = (
                                if (isLtr) valueRange.start + delta
                                else valueRange.endInclusive - delta
                                ).coerceIn(valueRange)
                            onValueChange(target)
                            animateTo(target)
                        }
                    }
                    .height(6.dp)
                    .fillMaxWidth()
            )
            Box(
                Modifier
                    .clip(Capsule())
                    .background(accentColor)
                    .height(6.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        // 灌注长度与拇指中心用同一个进度算：
                        // 色块的边永远落在拇指正中，不会差半格。
                        val progress =
                            (localValue.floatValue.coerceIn(valueRange) - valueRange.start) / span
                        val width = (constraints.maxWidth * progress).fastRoundToInt()
                        layout(width, placeable.height) { placeable.place(0, 0) }
                    }
            )
            if (showTicks) {
                // 刻度画在轨道自身的下半截：轨道只有 6dp 高，
                // 另开一行会撑高整个滑块，把下面的内容挤走。
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            val count = span.toInt()
                            val tickHeight = 2.dp.toPx()
                            val stroke = 1.5.dp.toPx()
                            val passedValue =
                                localValue.floatValue.coerceIn(valueRange) - valueRange.start
                            for (i in 0..count) {
                                val x = size.width * (i.toFloat() / count)
                                val passed = i.toFloat() <= passedValue
                                // 已走过的刻度画白（压在强调色上），
                                // 没走过的画深色（压在灰轨道上）：
                                // 两种底色差别很大，用同一个颜色必然有一段看不见。
                                drawLine(
                                    color = if (passed) {
                                        Color.White.copy(alpha = 0.85f)
                                    } else {
                                        Color.Black.copy(
                                            alpha = if (colors.isDark) 0.32f else 0.22f
                                        )
                                    },
                                    start = Offset(x, size.height - tickHeight),
                                    end = Offset(x, size.height),
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                )
            }
        }

        Box(
            Modifier
                .graphicsLayer {
                    // 拇指中心与灌注长度用同一个进度：色块的边永远在拇指正中。
                    val progress =
                        ((localValue.floatValue.coerceIn(valueRange) - valueRange.start) / span)
                            .fastCoerceIn(0f, 1f)
                    translationX = -size.width / 2f + trackWidth * progress
                }
                .semantics {
                    this.contentDescription = contentDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(value(), valueRange)
                    setProgress { target ->
                        val clamped = target.coerceIn(valueRange)
                        onValueChange(clamped)
                        animateTo(clamped)
                        true
                    }
                }
                .then(dampedDragAnimation.modifier)
                .then(interactiveHighlight.modifier)
                .then(interactiveHighlight.gestureModifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, progress)
                            val scaleY = lerp(0f, 1f, progress)
                            scale(scaleX, scaleY) { drawBackdrop() }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8.dp.toPx() * (1f - progress))
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        // 拇指的投影：没有它，玻璃拇指会跟轨道糊在一起。
                        Shadow(
                            radius = 6.dp,
                            color = Color.Black.copy(alpha = if (colors.isDark) 0.34f else 0.16f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 4.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        // "减少动态效果"时不拉伸：拇指鼓起来是一次明显的位移。
                        if (!reduced.value) {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                        }
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40.dp, 24.dp)
        )
    }
}
