package com.liuli.browser.ui.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.GlyphIcon
import com.liuli.browser.ui.components.NavItem
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.reduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

internal val LocalLiquidBottomTabScale = staticCompositionLocalOf { { 1f } }

/**
 * 甩动预测时长（秒）。
 * 松手时按这段时间外推手指位移来决定液滴落点：
 * 轻推只补齐最后一点位移，狠甩能跨过一格。
 */
private const val FLING_PROJECTION_SECONDS = 0.1f

/** 甩动速度上限（标签/秒）：防止一次猛甩把液滴甩出屏幕。 */
private const val MAX_FLING_VELOCITY = 4f

/**
 * 液滴能被拽出边缘的最大距离（dp）。
 * 用固定距离而不是标签宽度的比例：平板上标签更宽，
 * 按比例算会甩出去一大截。
 */
private const val OVERSCROLL_DP = 12f

/**
 * 底栏的交互状态。
 *
 * 落位动画这些要活过重组，而 DampedDragAnimation 是长期存活的 remember 对象，
 * 它回调里读到的必须是最新值。收进一个稳定的持有者就不用每次重组都重建它。
 */
private class DockSettleState {
    /** 落位动画句柄：新的手势开始前要先把它掐掉。 */
    var job: Job? = null

    /**
     * 当前落位的目标标签。
     *
     * 用来分辨"这次落位是自己发起的"还是"外面改了选中项"：
     * 甩动时液滴正带着惯性飞向新标签，此刻它还没到，
     * 拿位置当判据会把这次落位重做一遍，惯性就没了。
     */
    var target: Float = Float.NaN

    /**
     * 落位用的弹簧规格，每次重组刷新。
     *
     * 放在这里而不是直接捕获：动画对象换新时（改宽度或改减弱动效），
     * 若还抓着第一次组合的规格，液滴会用旧的曲线落位。
     */
    var spec: AnimationSpec<Float> = MotionSpec.glideSpring(false)
}

@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    isSelected: Boolean = false,
    /** 读屏时念出来的名字：底栏是手机上唯一的主导航，必须说得出这是哪一项。 */
    a11yLabel: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    // 自身管理 interactionSource，让按下时图标有轻微缩放反馈。
    val interaction = remember { MutableInteractionSource() }
    val base = modifier
        .clip(Capsule())
        .semantics {
            if (a11yLabel.isNotEmpty()) contentDescription = a11yLabel
            if (isSelected) {
                // 当前项要能被读屏直接听出来，光靠图标变个颜色是不够的。
                selected = true
                stateDescription = "已选中"
            }
        }
        .then(
            if (interactive) {
                Modifier
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Tab,
                        onClick = onClick
                    )
                    .pressScale(pressedScale = 0.88f, interactionSource = interaction)
            } else {
                Modifier
            }
        )
        .fillMaxHeight()
        .weight(1f)
        .graphicsLayer {
            // 只放大选中的那一项：整排一起放大，图标会挤成一团，
            // 用户也看不出自己到底盯着哪个。
            val scale = if (isSelected) scale() else 1f
            scaleX = scale
            scaleY = scale
        }
    Column(
        base,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/**
 * AndroidLiquidGlass-style bottom tabs: the selected pill is a liquid glass
 * droplet that stretches while dragged and settles on the chosen tab.
 */
@Composable
fun LiquidDock(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tabCount: Int = 0
) {
    val colors = LocalLiuliColors.current
    val accentColor = colors.accent
    val containerColor = colors.dockTint
    val tabsBackdrop = rememberLayerBackdrop()
    // 减少动态效果：液滴不再过冲，回位更快。
    val reduced = reduceMotion
    val glideSpec = MotionSpec.glideSpring()
    // 液滴落位用带过冲的弹簧：甩到位时液滴会轻轻冲过头再稳下来，
    // 这一下过冲就是液态的回弹。"减少动态效果"时换成快而稳的补间。
    val settleSpec = MotionSpec.rememberSpring(
        dampingRatio = 0.62f,
        stiffness = 700f,
        visibilityThreshold = 0.001f
    )

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        // 项数下限兜底：液滴宽度按项数均分，除数为 0 会让布局直接崩掉。
        val itemCount = items.size.coerceAtLeast(1)
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8.dp.toPx()) / itemCount
        }
        val lastIndex = (items.size - 1).coerceAtLeast(0)
        val maxValue = lastIndex.toFloat()
        // 越界的余量：换算成标签单位，值域两端各留一点。
        val overscrollValue = with(density) {
            if (tabWidth > 0f) OVERSCROLL_DP.dp.toPx() / tabWidth else 0f
        }

        // 底栏整体被拖动时的横向位移。
        // 用普通状态而不是 Animatable：拖拽期间每个指针事件都要写一次，
        // 起协程去同步动画对象会让底栏慢半拍，液滴与底栏会错位。
        var panelDrag by remember { mutableFloatStateOf(0f) }
        // constraints.maxWidth 参与 key：旋转屏幕或折叠屏展开后宽度会变，
        // 派生状态若还抓着旧宽度，底栏的偏移量就按旧尺寸算了。
        val panelOffset by remember(density, constraints.maxWidth) {
            derivedStateOf {
                val fraction = (panelDrag / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        // 切换标签时给一次轻触反馈，与液滴甩动同步。
        val haptics = LocalHapticFeedback.current
        // 当前选中项用稳定的状态对象持有：
        // 若跟着 selectedIndex 重建，remember 里捕获的回调会一直读到旧值。
        val currentIndexState = remember { mutableIntStateOf(selectedIndex) }
        val currentIndex = currentIndexState.value
        // 手指真正走过的位置（标签单位）。
        // 必须自己累加：边界处 value 会被夹住，靠它累加液滴到头就"卡死"了。
        val dragPositionState = remember { mutableFloatStateOf(selectedIndex.toFloat()) }
        // 液滴的可见位置（标签单位）。
        // 拖拽期间手指写到哪就是哪：1:1 跟手，液滴不会落后半拍。
        // 之前靠 DampedDragAnimation 的 Animatable 承载位置，
        // 每个指针事件都要起一个协程去同步，事件一密就明显拖后腿。
        val dropletPos = remember { mutableFloatStateOf(selectedIndex.toFloat()) }
        // 液滴自身的速度（标签/秒），用来做甩动时的拉伸形变。
        // 必须单独记：液滴位置已经不再由 DampedDragAnimation 承载，
        // 它的 velocity 字段从此不再被驱动，读它只会拿到 0，
        // 形变就整个消失了。
        val dropletVelocity = remember { mutableFloatStateOf(0f) }
        // 落位动画的持有者：只在手势回调里读写，不参与组合，
        // 拖动时不会因为写它而触发重组。
        val settle = remember { DockSettleState() }
        settle.spec = settleSpec
        // 最近一次给过轻触反馈的标签序号：
        // 跨过分界才震一下，同一个标签内来回蹭不会连着震。
        val hapticIndexState = remember { mutableIntStateOf(selectedIndex) }

        // 液滴落位：带着甩动的惯性走到目标，途中允许轻微过冲。
        // 甩动的方向感来自这里：初速度是手指给的，不是凭空加的。
        fun settleDroplet(target: Float, velocity: Float = 0f) {
            settle.job?.cancel()
            settle.target = target
            if (reduced) {
                dropletPos.floatValue = target
                dropletVelocity.floatValue = 0f
                return
            }
            val from = dropletPos.floatValue
            settle.job = animationScope.launch {
                animate(
                    initialValue = from,
                    targetValue = target,
                    initialVelocity = velocity,
                    animationSpec = settle.spec
                ) { value, currentVelocity ->
                    dropletPos.floatValue = value
                    dropletVelocity.floatValue = currentVelocity
                }
            }
        }

        // reduced 与 tabWidth 参与 key：系统"减少动态效果"开关变了要重建，
        // 否则液滴还会用旧的弹簧曲线；tabWidth 变了（旋转或折叠屏展开）
        // 也要重建，否则甩动换算还按旧宽度算位移，落点会偏。
        val dampedDragAnimation = remember(animationScope, reduced, tabWidth) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.toFloat(),
                // 值域两端各留一点余量：越界时液滴能被拽出去一点，
                // 松手再弹回来，而不是硬邦邦地卡在边界上。
                valueRange = -overscrollValue..(maxValue + overscrollValue),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                // 液滴甩到目标位置时允许轻微过冲，而不是直落到位。
                valueDampingRatio = 0.62f,
                valueStiffness = 700f,
                reducedMotion = reduced,
                onDragStarted = {
                    // 抓住液滴时从它当前所在的位置接着走，避免跳一下。
                    dragPositionState.floatValue = dropletPos.floatValue
                    // 抓住的瞬间，之前的落位动画必须让位：
                    // 否则液滴还会继续往旧目标上飞，手指与液滴会打架。
                    settle.job?.cancel()
                    settle.job = null
                },
                onDragStopped = {
                    // 甩动速度换算成"标签/秒"，用它预测落点：
                    // 轻推补齐最后一点位移，狠甩能跨过一格。
                    val flingVelocity = (pointerVelocity / tabWidth * if (isLtr) 1f else -1f)
                        .fastCoerceIn(-MAX_FLING_VELOCITY, MAX_FLING_VELOCITY)
                    val projected =
                        dragPositionState.floatValue + flingVelocity * FLING_PROJECTION_SECONDS
                    val targetIndex = projected.fastRoundToInt().fastCoerceIn(0, lastIndex)
                    val changed = targetIndex != currentIndexState.value
                    currentIndexState.value = targetIndex
                    dragPositionState.floatValue = targetIndex.toFloat()
                    // 带着甩动的惯性落位：弹簧从当前速度继续走，
                    // 到位时自然过冲一下再稳住。
                    settleDroplet(targetIndex.toFloat(), flingVelocity)
                    // 底栏的横向偏移弹回中位：松手后它该自己回正，
                    // 否则底栏会一直歪在一边。
                    // 起点必须在启动协程前取好：协程真正跑起来时
                    // panelDrag 可能已经被改过，取到的起点就不对了。
                    val panelStart = panelDrag
                    animationScope.launch {
                        animate(
                            initialValue = panelStart,
                            targetValue = 0f,
                            animationSpec = glideSpec
                        ) { value, _ -> panelDrag = value }
                    }
                    // 落回原来的标签就不必通知外部，避免无谓的跳转。
                    if (changed) {
                        onSelected(targetIndex)
                    }
                },
                onDrag = { _, dragAmount ->
                    // 液滴必须 1:1 跟手：这里直接写状态，
                    // 松手后再由 onDragStopped 的过冲弹簧接管。
                    val raw =
                        dragPositionState.floatValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f
                    dragPositionState.floatValue = raw
                    // 越界时给阻尼：拖到头还能继续拽，但明显变沉，
                    // 告诉用户"到边了"，而不是硬邦邦地卡住。
                    val next = when {
                        raw < 0f -> raw * 0.35f
                        raw > maxValue -> maxValue + (raw - maxValue) * 0.35f
                        else -> raw
                    }.fastCoerceIn(-overscrollValue, maxValue + overscrollValue)
                    dropletPos.floatValue = next
                    // 甩动速度：液滴沿运动方向的拉伸要用它。
                    // 拖拽期间用手指速度，松手后由弹簧自己的速度接手，
                    // 两段连起来，液滴从头到尾都是"活的"。
                    val fingerVelocity = pointerVelocity / tabWidth * if (isLtr) 1f else -1f
                    dropletVelocity.floatValue = fingerVelocity
                    reportDragVelocity(fingerVelocity)
                    // 跨过标签分界时给一次轻触：拖动过程中能感知到自己滑过了几个。
                    val crossed = raw.fastRoundToInt().fastCoerceIn(0, lastIndex)
                    if (crossed != hapticIndexState.value) {
                        hapticIndexState.value = crossed
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    // 底栏的横向偏移直接写状态：1:1 跟手。
                    // 之前每个指针事件都起一个协程去 snapTo，
                    // 上百个协程互相打断，底栏会明显落后于液滴。
                    panelDrag += dragAmount.x
                }
            )
        }
        LaunchedEffect(selectedIndex, settleSpec) {
            currentIndexState.value = selectedIndex
            dragPositionState.floatValue = selectedIndex.toFloat()
            hapticIndexState.value = selectedIndex
            // 判据是"这次落位是不是我们自己发起的"，而不是"液滴到没到目标"：
            // 甩动时液滴带着惯性飞向新标签，中途它当然还没到，
            // 若拿位置当判据，这里就会再落一次位，
            // 而且是不带初速度的那一次——甩动的惯性被抹掉了。
            if (settle.target != selectedIndex.toFloat()) {
                settleDroplet(selectedIndex.toFloat())
            }
        }

        // 高光跟着液滴走：按下的那点高光要落在液滴上，
        // 否则高光与液滴会分家，看着像两块各动各的玻璃。
        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dropletPos.floatValue + 0.5f) * tabWidth + panelOffset
                        else size.width - (dropletPos.floatValue + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(Dimens.dockHeight)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockItems(items, currentIndex, tabCount, onSelected = { index ->
                if (index != currentIndex) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                currentIndexState.value = index
                dragPositionState.floatValue = index.toFloat()
                hapticIndexState.value = index
                // 切标签时液滴从当前位置滑过去：
                // 方向感来自它真的在"走"这一段路，而不是瞬移。
                settleDroplet(index.toFloat())
                onSelected(index)
            })
        }

        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                // 选中标签的放大用弹性曲线，和液滴的甩动保持一致的手感。
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(24.dp.toPx() * progress, 24.dp.toPx() * progress)
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(Dimens.dockHeight - 8.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockItems(items, currentIndex, tabCount, interactive = false, onSelected = {})
            }
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    // 液滴位置读的是直接写的状态：拖拽时 1:1 跟手，
                    // 落位时由弹簧带过去。
                    translationX =
                        if (isLtr) dropletPos.floatValue * tabWidth + panelOffset
                        else size.width - (dropletPos.floatValue + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 8.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        // 甩动时沿运动方向把液滴"抻长"：被拖着走时形变，
                        // 停手后弹回原状。取绝对值，左右两个方向的手感才对称。
                        val stretch = (abs(dropletVelocity.floatValue) / 10f * 0.75f)
                            .fastCoerceIn(0f, 0.2f)
                        scaleX *= 1f + stretch
                        scaleY *= 1f - stretch * 0.34f
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (colors.isDark) Color.White.copy(0.1f) else Color.Black.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(Dimens.dockHeight - 8.dp)
                .fillMaxWidth(1f / itemCount)
        )
    }
}

@Composable
private fun RowScope.DockItems(
    items: List<NavItem>,
    selectedIndex: Int,
    tabCount: Int,
    interactive: Boolean = true,
    onSelected: (Int) -> Unit
) {
    val colors = LocalLiuliColors.current
    val reduced = reduceMotion
    items.forEachIndexed { index, item ->
        val selected = index == selectedIndex
        // 图标与文字的选中态用颜色插值过渡，而不是瞬时换色。
        val iconTint by animateColorAsState(
            targetValue = if (selected) colors.accent else colors.textSecondary,
            animationSpec = MotionSpec.color(Motion.Quick),
            label = "dock-icon-tint"
        )
        val labelTint by animateColorAsState(
            targetValue = if (selected) colors.accent else colors.textTertiary,
            animationSpec = MotionSpec.color(Motion.Quick),
            label = "dock-label-tint"
        )
        val labelWeight by animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = MotionSpec.value(Motion.Quick),
            label = "dock-label-weight"
        )
        val isTabItem = item.glyph == Glyph.Tabs
        LiquidBottomTab(
            onClick = { onSelected(index) },
            interactive = interactive,
            isSelected = selected,
            // 角标数字并进标签里：单独念一个"3"没有意义，
            // "标签，3 个标签页"才是完整的一句。
            a11yLabel = if (isTabItem && tabCount > 0) {
                "${item.label}，$tabCount 个标签页"
            } else {
                item.label
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                GlyphIcon(
                    glyph = item.glyph,
                    tint = iconTint,
                    size = 22.dp
                )
                if (isTabItem) {
                    // 角标常驻在组合里，靠缩放控制显隐：
                    // 新增标签时从 0 弹到 1，给一个轻微的弹性提示。
                    val badgeScale by animateFloatAsState(
                        targetValue = if (tabCount > 0) 1f else 0f,
                        animationSpec = MotionSpec.pageSpring(),
                        label = "dock-badge-scale"
                    )
                    // 数量变了要有个动静：新增标签不该只是数字悄悄跳一下。
                    // 首次进入不弹：那时数字并没有"变"过。
                    var badgePulse by remember { mutableFloatStateOf(0f) }
                    val lastBadgeCount = remember { mutableIntStateOf(tabCount) }
                    LaunchedEffect(tabCount, reduced) {
                        if (reduced || tabCount <= 0 || lastBadgeCount.intValue == tabCount) {
                            // 归零也要在这里做：上一次脉冲若是在等待中被取消，
                            // 标志会永远停在 1，角标就一直是被撑大的样子。
                            badgePulse = 0f
                            return@LaunchedEffect
                        }
                        lastBadgeCount.intValue = tabCount
                        badgePulse = 1f
                        try {
                            delay(110)
                        } finally {
                            // 清理必须放在 finally：
                            // 等待期间标签数再变，协程会被取消，
                            // 没有 finally 这一次脉冲就再也回不去了。
                            badgePulse = 0f
                        }
                    }
                    val pulse by animateFloatAsState(
                        targetValue = badgePulse,
                        animationSpec = MotionSpec.value(Motion.Quick),
                        label = "dock-badge-pulse"
                    )
                    // 两位数以上角标要加宽，否则数字会被裁掉；
                    // 早先一律显示 99，真实数量反而看不见了。
                    val badgeText = if (tabCount > 99) "99+" else tabCount.toString()
                    val badgeWidth = when {
                        tabCount > 99 -> 22.dp
                        tabCount > 9 -> 17.dp
                        else -> 15.dp
                    }
                    Box(
                        Modifier
                            // 贴着图标的右上角，而不是靠固定偏移硬凑
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-6).dp)
                            .graphicsLayer {
                                // 出现时的弹性缩放与数量变化时的脉冲叠在一起。
                                val pulseScale = badgeScale * (1f + 0.24f * pulse)
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                            .width(badgeWidth)
                            .height(15.dp)
                            .clip(Capsule())
                            .background(colors.accent)
                            // 数字已并进 a11yLabel，这里不要再重复播报一遍。
                            .clearAndSetSemantics {},
                        contentAlignment = Alignment.Center
                    ) {
                        // 数字常驻：清零时容器缩到 0，文字跟着消失，
                        // 不用硬切，角标不会"啪"地闪一下。
                        BasicText(
                            text = badgeText,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = if (tabCount > 9) 7.sp else 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            BasicText(
                text = item.label,
                style = TextStyle(
                    color = labelTint,
                    // 与侧栏导航同字号：底栏是手机上唯一的导航，字太小读不清。
                    fontSize = 10.sp,
                    fontWeight = if (labelWeight > 0.5f) FontWeight.SemiBold else FontWeight.Normal
                ),
                maxLines = 1,
                // 标签文字已由 a11yLabel 播报，这里清掉避免读成两遍。
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
    }
}
