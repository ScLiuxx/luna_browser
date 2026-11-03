package com.liuli.browser.ui.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.snapshotFlow
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

@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    // 自身管理 interactionSource，让按下时图标有轻微缩放反馈。
    val interaction = remember { MutableInteractionSource() }
    val base = modifier
        .clip(Capsule())
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
            val scale = scale()
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

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8.dp.toPx()) / items.size.coerceAtLeast(1)
        }
        val lastIndex = (items.size - 1).coerceAtLeast(0)
        val maxValue = lastIndex.toFloat()
        // 越界的余量：换算成标签单位，值域两端各留一点。
        val overscrollValue = with(density) {
            if (tabWidth > 0f) OVERSCROLL_DP.dp.toPx() / tabWidth else 0f
        }

        val offsetAnimation = remember { Animatable(0f) }
        // constraints.maxWidth 参与 key：旋转屏幕或折叠屏展开后宽度会变，
        // 派生状态若还抓着旧宽度，底栏的偏移量就按旧尺寸算了。
        val panelOffset by remember(density, constraints.maxWidth) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
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
        // 最近一次给过轻触反馈的标签序号：
        // 跨过分界才震一下，同一个标签内来回蹭不会连着震。
        val hapticIndexState = remember { mutableIntStateOf(selectedIndex) }

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
                    dragPositionState.floatValue = value
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
                    animateToValue(targetIndex.toFloat(), flingVelocity)
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, glideSpec)
                    }
                    // 落回原来的标签就不必通知外部，避免无谓的跳转。
                    if (changed) {
                        onSelected(targetIndex)
                    }
                },
                onDrag = { _, dragAmount ->
                    // 液滴必须 1:1 跟手：弹簧会让它落后于手指半拍，
                    // 拖起来"粘滞"，液态感全无。这里直接落位，
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
                    snapValue(next)
                    // 甩动速度：液滴沿运动方向的拉伸要用它。
                    reportDragVelocity(pointerVelocity / tabWidth * if (isLtr) 1f else -1f)
                    // 跨过标签分界时给一次轻触：拖动过程中能感知到自己滑过了几个。
                    val crossed = raw.fastRoundToInt().fastCoerceIn(0, lastIndex)
                    if (crossed != hapticIndexState.value) {
                        hapticIndexState.value = crossed
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(selectedIndex, dampedDragAnimation) {
            currentIndexState.value = selectedIndex
            dragPositionState.floatValue = selectedIndex.toFloat()
            hapticIndexState.value = selectedIndex
            // 已经在位就不必再弹一次，否则每次重组液滴都会"闪"一下。
            if (dampedDragAnimation.targetValue != selectedIndex.toFloat()) {
                dampedDragAnimation.animateToValue(selectedIndex.toFloat())
            }
        }

        // 以 dampedDragAnimation 为 key：position 回调读的是闭包里的
        // dampedDragAnimation 与 tabWidth，动画对象换了（改宽度或改减弱动效）
        // 而高光还抓着旧对象，它就会停在旧位置不动。
        val interactiveHighlight = remember(dampedDragAnimation) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
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
                dampedDragAnimation.animateToValue(index.toFloat())
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
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
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
                        val stretch = (abs(dampedDragAnimation.velocity) / 10f * 0.75f)
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
                .fillMaxWidth(1f / items.size)
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
        LiquidBottomTab(
            onClick = { onSelected(index) },
            interactive = interactive
        ) {
            Box(contentAlignment = Alignment.Center) {
                GlyphIcon(
                    glyph = item.glyph,
                    tint = iconTint,
                    size = 22.dp
                )
                if (item.glyph == Glyph.Tabs) {
                    // 角标常驻在组合里，靠缩放控制显隐：
                    // 新增标签时从 0 弹到 1，给一个轻微的弹性提示。
                    val badgeScale by animateFloatAsState(
                        targetValue = if (tabCount > 0) 1f else 0f,
                        animationSpec = MotionSpec.pageSpring(),
                        label = "dock-badge-scale"
                    )
                    Box(
                        Modifier
                            .graphicsLayer {
                                translationX = 10.dp.toPx()
                                translationY = (-7).dp.toPx()
                                scaleX = badgeScale
                                scaleY = badgeScale
                            }
                            .size(12.dp)
                            .clip(Capsule())
                            .background(colors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        // 数字常驻：清零时容器缩到 0，文字跟着消失，
                        // 不用硬切，角标不会"啪"地闪一下。
                        BasicText(
                            text = tabCount.coerceAtMost(99).toString(),
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 7.sp,
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
                    fontSize = 9.sp,
                    fontWeight = if (labelWeight > 0.5f) FontWeight.SemiBold else FontWeight.Normal
                ),
                maxLines = 1
            )
        }
    }
}
