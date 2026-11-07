package com.liuli.browser.ui.liquid

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
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

/**
 * 开关的交互状态。
 *
 * 拖拽位置、落位动画这些都要活过重组，而 DampedDragAnimation 是长期存活的
 * remember 对象，它回调里捕获的每一个值都得是最新的。
 * 把它们收进一个稳定的持有者，回调里只碰这个持有者，
 * 就不会再出现"点开之后就再也关不上"这类读到旧值的问题。
 */
private class ToggleInteractionState {
    /** 落位动画。新的手势或新的状态变化开始前必须先把它掐掉。 */
    var settleJob: Job? = null

    /** 落位用的弹簧规格，每次重组刷新（"减少动态效果"会换成快而稳的补间）。 */
    var settleSpec: AnimationSpec<Float> = MotionSpec.liquidSpring(false)

    /** 触感反馈器，每次重组刷新。 */
    var haptics: HapticFeedback? = null

    /** 是否响应"减少动态效果"，每次重组刷新。 */
    var reduced: Boolean = false

    /** 最近一次落位目标：用来分辨"这次状态变化是我发起的"还是"外面改的"。 */
    var lastTarget: Float = 0f

    /** 本次手势是否已经越过拖拽阈值。 */
    var dragging: Boolean = false

    /** 本次手势累计的水平位移。 */
    var accum: Float = 0f

    /** 拇指是否已经越过中线：越线时给一下"咔哒"的触感。 */
    var crossed: Boolean = false
}

/**
 * Port of the catalog's LiquidToggle: the thumb behaves like a droplet,
 * stretching while dragged and snapping with a damped spring.
 */
@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    contentDescription: String = "开关"
) {
    val colors = LocalLiuliColors.current
    val accentColor = colors.success
    // 轨道底色跟其它玻璃件同一量级：深色 0.36、浅色 0.24。
    // 此前浅色只有 0.20，"关"着的轨道几乎融进页面背景，
    // 用户根本看不出这是一枚能拨的开关；深色的灰也偏暗，压不住底色。
    val trackColor = if (colors.isDark) {
        Color(0xFF8A8A94).copy(alpha = 0.36f)
    } else {
        Color(0xFF787878).copy(alpha = 0.24f)
    }
    // 拇指边缘的一圈暗线：白拇指压在灰轨道上，两者的轮廓会糊在一起，
    // 浅色主题尤其明显。有了这道细线，拇指才"浮"在轨道上而不是嵌进去。
    val thumbRim = Color.Black.copy(alpha = if (colors.isDark) 0.22f else 0.12f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    // 拇指可走的总距离：轨道 64dp - 拇指 40dp - 两侧留白 4dp = 20dp。
    // 手指在这 20dp 上移动多少，拇指就移动多少，1:1 不打滑。
    val dragWidth = with(density) { 20f.dp.toPx() }
    val trackPadding = with(density) { 2f.dp.toPx() }
    // 越过这个位移才算"拖动"，否则轻点会被手抖当成拖动，开关就点不动了。
    val dragSlop = with(density) { 3f.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    // "减弱动态效果"时不再用弹簧回弹，拇指直接落位。
    // 用 rememberUpdatedState 包裹：开关本身可以热切换，回调里要读到最新值。
    val reduced = rememberUpdatedState(reduceMotion)
    // 记的是布尔值而不是回调本身：结构相等让"值没变"不产生写入，
    // 下面的收集器也就不会被无谓地重启。
    // 之前这里直接闭包捕获 selected，而 DampedDragAnimation 是长期存活的
    // remember 对象，捕获到的是第一次组合时的旧值：
    // 开关点开之后就再也关不上了。
    val isOn = rememberUpdatedState(selected())
    // 同理，回调本身也可能是每次重组都新建的 lambda：
    // 长期存活的动画对象必须拿到最新的那一个。
    val onSelectState = rememberUpdatedState(onSelect)
    val haptics = LocalHapticFeedback.current

    // 拇指的可见位置。
    // 拖动期间手指写到哪就是哪：一个事件都不落后，拨起来是跟手的。
    // 早先这里每帧都起协程去同步 Animatable，拇指会慢半拍，液态感全丢。
    val thumbState = remember { mutableFloatStateOf(if (selected()) 1f else 0f) }
    // lastTarget 只在创建时定一次：它是"逻辑状态"而不是"拇指当前位置"，
    // 若每次重组都拿当前位置覆盖它，动画播到一半的 0.5 会被当成新目标，
    // 外部状态收集器就会重复驱动一次动画，拇指跟着抖。
    val interaction = remember {
        ToggleInteractionState().apply { lastTarget = thumbState.floatValue }
    }
    interaction.haptics = haptics
    interaction.reduced = reduced.value
    // 落位用带过冲的弹簧：拨到位时拇指会轻轻冲过头再稳起来，
    // 这一下过冲就是"啪"的液态回弹。
    // "减少动态效果"时 MotionSpec 会换成快而稳的补间，弹簧不再过冲。
    val settleSpec = MotionSpec.rememberSpring(
        dampingRatio = 0.62f,
        stiffness = 680f,
        visibilityThreshold = 0.001f
    )
    interaction.settleSpec = settleSpec

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    // 落到某个位置。changed 表示"这次是不是真的换了状态"：
    // 拨回原处不算换了状态，不该通知外面，也不该震动。
    fun commitTo(target: Float) {
        val changed = target != interaction.lastTarget
        interaction.lastTarget = target
        if (changed) {
            onSelectState.value(target == 1f)
            interaction.haptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        // 落位动画：一次只允许有一个在跑，新的开始前先把旧的掐掉。
        interaction.settleJob?.cancel()
        if (interaction.reduced) {
            // 减弱动效：直接落位，不做弹簧回弹。
            thumbState.floatValue = target
            return
        }
        val from = thumbState.floatValue
        interaction.settleJob = animationScope.launch {
            animate(
                initialValue = from,
                targetValue = target,
                animationSpec = interaction.settleSpec
            ) { value, _ -> thumbState.floatValue = value }
        }
    }

    // 只用它的按压形变与甩动速度；位置由 thumbState 自己管。
    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialScale = 1f,
            // 按下时拇指鼓成一颗液滴：1.25 倍刚好看得见形变，
            // 又不会胀到比轨道还高（1.5 倍会顶出轨道，看着像穿模）。
            pressedScale = 1.25f,
            onDragStarted = {
                // 抓住液滴时，任何还在跑的落位动画都得让位：
                // 否则弹簧会继续把拇指往旧目标上拽，手指与拇指就打架了。
                interaction.settleJob?.cancel()
                interaction.settleJob = null
                interaction.dragging = false
                interaction.accum = 0f
                interaction.crossed = thumbState.floatValue >= 0.5f
            },
            onDragStopped = {
                if (interaction.dragging) {
                    // 拨动到位：按拇指停下的位置决定开合。
                    commitTo(if (thumbState.floatValue >= 0.5f) 1f else 0f)
                } else {
                    // 轻点：直接翻转。
                    commitTo(if (isOn.value) 0f else 1f)
                }
            },
            onDrag = { _, dragAmount ->
                interaction.accum += dragAmount.x
                if (!interaction.dragging && abs(interaction.accum) > dragSlop) {
                    interaction.dragging = true
                }
                if (interaction.dragging) {
                    val delta = dragAmount.x / dragWidth * if (isLtr) 1f else -1f
                    val raw = thumbState.floatValue + delta
                    // 越界时给阻尼：拨到头还能继续拽，但明显变沉，
                    // 告诉用户"到头了"，而不是硬邦邦地卡住。
                    // 这里不能把结果夹回 0..1：夹掉之后橡皮筋就废了，
                    // 拇指会硬停在边界上，用户感觉不到"到头了"。
                    thumbState.floatValue = when {
                        raw < 0f -> raw * 0.35f
                        raw > 1f -> 1f + (raw - 1f) * 0.35f
                        else -> raw
                    }
                    // 越过中线给一下"咔哒"：手指一路拨过去时，
                    // 能听出离到位还差多远。
                    val crossed = thumbState.floatValue >= 0.5f
                    if (crossed != interaction.crossed) {
                        interaction.crossed = crossed
                        interaction.haptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }
        )
    }

    // 外部开关状态变化时把拇指送到位。
    // 之前用 selected 这个 lambda 当 key：它每次重组都是新实例，
    // 收集器因此不停重启，状态刚变就被下一个实例顶掉。
    // 改成监听 isOn 这个布尔值，只有开关真的翻转时才动。
    LaunchedEffect(settleSpec) {
        snapshotFlow { isOn.value }.collectLatest { isSelected ->
            val target = if (isSelected) 1f else 0f
            // 自己刚拨出来的状态不用再动一次：路径重复会让拇指抖一下。
            if (target != interaction.lastTarget) {
                interaction.lastTarget = target
                val from = thumbState.floatValue
                interaction.settleJob?.cancel()
                if (reduced.value) {
                    thumbState.floatValue = target
                } else {
                    interaction.settleJob = animationScope.launch {
                        animate(
                            initialValue = from,
                            targetValue = target,
                            animationSpec = settleSpec
                        ) { value, _ -> thumbState.floatValue = value }
                    }
                }
            }
        }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind {
                    // 轨道整体从灰过渡到强调色：颜色跟着拇指走，
                    // 于是"拨了多少"本身就是一种进度反馈。
                    val progress = thumbState.floatValue.fastCoerceIn(0f, 1f)
                    drawRect(lerpColor(trackColor, accentColor, progress))
                }
                .size(64.dp, 28.dp)
        )

        Box(
            Modifier
                .graphicsLayer {
                    // 拇指位置允许越界一点点：橡皮筋的阻尼要看得见，
                    // 否则用户只会觉得"推不动"。
                    // 但也不能让它跑出轨道：夹在一个很小的余量里。
                    val fraction = thumbState.floatValue.fastCoerceIn(-0.12f, 1.12f)
                    translationX = if (isLtr) {
                        lerp(trackPadding, trackPadding + dragWidth, fraction)
                    } else {
                        lerp(-trackPadding, -(trackPadding + dragWidth), fraction)
                    }
                }
                .semantics {
                    role = Role.Switch
                    // 开关状态由 toggleableState 表达：读屏会念成"已开启/已关闭"。
                    // 不要再补一条 stateDescription，两份状态会被念两遍。
                    toggleableState = ToggleableState(selected())
                    this.contentDescription = contentDescription
                    // 读屏用户双击这里也该能拨动开关：
                    // 只有 toggleableState 没有动作，TalkBack 念得出状态却拨不动。
                    // 走同一条 commitTo 路径：读屏切换与手指切换的手感、动画完全一致。
                    onClick {
                        commitTo(if (selected()) 0f else 1f)
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
                            val scaleX = lerp(2f / 3f, 0.75f, progress)
                            val scaleY = lerp(0f, 0.75f, progress)
                            scale(scaleX, scaleY) { drawBackdrop() }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8.dp.toPx() * (1f - progress))
                        lens(
                            5.dp.toPx() * progress,
                            10.dp.toPx() * progress,
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
                        // 拇指的投影要压得住：没有它，白拇指和灰轨道
                        // 在浅色主题下会糊成同一块，分不出谁是谁。
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
                        // "减少动态效果"时不拉伸：拇指鼓起来是一次明显的位移，
                        // 开了这个开关的用户不该被它晃到。
                        if (!reduced.value) {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            // 甩动时拇指沿运动方向拉长：
                            // 用手指速度换算成"轨道长度/秒"，量纲才对得上。
                            val velocity = (dampedDragAnimation.pointerVelocity / dragWidth) / 50f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        }
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                        // 边缘暗线：把拇指的轮廓描出来，
                        // 浅色主题下白拇指压浅灰轨道，否则两者边界是糊的。
                        val inset = 0.5.dp.toPx()
                        val rimHeight = size.height - inset * 2f
                        drawRoundRect(
                            color = thumbRim,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - inset * 2f, rimHeight),
                            cornerRadius = CornerRadius(rimHeight / 2f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                )
                .size(40.dp, 24.dp)
        )
    }
}
