package com.liuli.browser.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.liuli.browser.ui.liquid.GlassShape
import com.liuli.browser.ui.liquid.pressScale
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.Radius
import com.liuli.browser.ui.theme.Space
import com.liuli.browser.ui.theme.Type
import com.liuli.browser.ui.theme.reduceMotion
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * 锚点：把触发元素的位置登记进来，浮层据此定位。
 *
 * 记的是**窗口坐标**而不是父容器坐标：浮层挂在窗口根上，
 * 触发元素却可能埋在好几层布局里，只有都换算到窗口坐标才对得上。
 */
@Stable
class GlassPopupAnchor {

    /**
     * 触发元素在窗口里的位置；还没量到时为 null。
     */
    var boundsInWindow: Rect? by mutableStateOf<Rect?>(null)
        internal set
}

/**
 * 挂在触发元素上，登记它的窗口坐标。
 *
 * 元素滚动或改变大小时坐标会跟着变，这里每次重新测量都更新一遍，
 * 浮层因此始终贴着触发它的那个位置。
 */
fun Modifier.glassPopupAnchor(anchor: GlassPopupAnchor): Modifier =
    this.onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInWindow()
        if (anchor.boundsInWindow != rect) {
            anchor.boundsInWindow = rect
        }
    }

/**
 * 锚定型玻璃浮层。
 *
 * 与 [LiquidDialog] 的区别只在"从哪儿冒出来"：对话框从屏幕中央升起，
 * 这里从触发它的那个按钮处**长出来**——缩放的原点落在锚点上，
 * 面板看上去是从按钮里挤出来的，而不是凭空出现在屏幕中间。
 *
 * 为什么不用 `androidx.compose.ui.window.Popup`：Popup 与 Dialog 都是独立窗口，
 * 而 Backdrop 库的玻璃折射要采样**同一个 Activity 窗口**里录制的 LayerBackdrop；
 * 独立窗口里什么也采不到，玻璃会退化成一块死板的半透明色块。
 * 因此这里老老实实用 [Box] 铺满父容器，把面板绝对定位画在同一窗口里。
 *
 * 调用方要把本组件放在与主 UI 同一个窗口的根布局里（与触发元素同一层），
 * 否则遮罩与面板会画在别人的层上面。
 *
 * @param anchor 触发元素，通过 [glassPopupAnchor] 登记它的窗口坐标。
 *   坐标没登记上之前浮层不会展开：不知道该从哪儿长出来，展开也只是凭空一坨。
 * @param visible 是否展开。
 * @param onDismissRequest 点浮层外部或按返回键时回调。
 * @param backdrop 采样的图层。必须传**调用方所在窗口**的那份 LayerBackdrop
 *   （例如 `LiuliApp` 里 `rememberLayerBackdrop()` 出来、传给各页面的 `auroraBackdrop`）。
 *   这里不自己 `rememberLayerBackdrop()`：自建的那份只能录到浮层自己画的东西，
 *   采样到的会是浮层自己，折射毫无意义。
 * @param minWidth 菜单最小宽度，内容比它窄时面板仍留到这个宽度。
 * @param maxWidth 菜单最大宽度，内容比它宽时文字省略，不把面板撑破。
 */
@Composable
fun GlassPopup(
    anchor: GlassPopupAnchor,
    visible: Boolean,
    onDismissRequest: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape.squircle(Radius.lg),
    minWidth: Dp = 220.dp,
    maxWidth: Dp = 320.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalLiuliColors.current
    val reduced = reduceMotion
    val density = LocalDensity.current

    // 浮层内容区在窗口里的原点：锚点给的是窗口坐标，
    // 这里也要换算成窗口坐标，两者相减才是内容区内的偏移量。
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
    // 面板尺寸：落位（下方放不下要翻上去）与缩放原点都要靠它。
    var panelSize by remember { mutableStateOf<IntSize?>(null) }
    // 退场期间面板仍留在树上，否则关闭的一瞬间面板会直接消失，看不到收尾。
    var showing by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    // 规格统一从 MotionSpec 取：它内部已经按全局开关降级。
    val enterSpec = MotionSpec.pageSpring()
    val exitSpec = MotionSpec.exit()

    LaunchedEffect(visible) {
        if (visible) {
            showing = true
            // 面板要先量出尺寸，才知道该落在哪儿、该从哪儿长出来；
            // 提前起手会先闪在左上角再跳到位。
            //
            // 等两件事都成立：锚点已登记（面板被组合的前提），且尺寸已量到。
            // 只等尺寸的话，锚点迟迟不来时这个协程会永远挂着——
            // 面板压根没被组合过，谁也量不到尺寸。
            // 锚点后来登记上，面板量到尺寸，展开动画自己接上。
            // 协程在 visible 翻回 false 时会被取消，不会一直占着。
            snapshotFlow { anchor.boundsInWindow != null && panelSize != null }.first { it }
            progress.animateTo(1f, animationSpec = enterSpec)
        } else {
            // 退场用补间而不是弹簧：弹簧在消失时会回弹一下，像是不舍得走，
            // 关闭动作要干脆。
            progress.animateTo(0f, animationSpec = exitSpec)
            showing = false
            panelSize = null
        }
    }

    // 返回键此前只认浮层外部的点按：用户按返回毫无反应，只能在屏幕上乱点。
    //
    // 退场期间也要继续吞掉返回键：面板还在淡出时返回键就失效的话，
    // 这一下会穿透到下面的页面，用户明明只想关掉菜单，却把整页也退掉了。
    // 这与 LiquidDialog 的 exiting 守卫是同一件事。
    BackHandler(enabled = visible || showing) {
        if (visible) onDismissRequest()
    }

    // 面板与锚点之间留一道缝：贴死在一起会像按钮自己裂开了。
    val gap = with(density) { Space.sm.toPx() }

    Box(
        modifier
            .fillMaxSize()
    ) {
        // 用 BoxWithConstraints 拿可用区域的尺寸：约束在首帧就是确定的，
        // 而"量出来"的尺寸首帧还是 0，拿它算高度上限会把面板压成一条线。
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val areaWidth = with(density) { maxWidth.toPx() }
            val areaHeight = with(density) { maxHeight.toPx() }
            val anchorRect = anchor.boundsInWindow

            val layout = remember(anchorRect, contentOrigin, panelSize, gap, areaWidth, areaHeight) {
                computePopupLayout(
                    anchorRect = anchorRect,
                    contentOrigin = contentOrigin,
                    areaWidth = areaWidth,
                    areaHeight = areaHeight,
                    panelSize = panelSize,
                    gap = gap
                )
            }

            // 菜单高度上限：屏幕再矮也不能让浮层吃掉整屏。
            val maxPanelHeight = maxHeight * 0.6f

            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        contentOrigin = coordinates.boundsInWindow().topLeft
                    }
            ) {
                // 遮罩取调色板里的 scrim，并且压得很轻：浮层是"贴着按钮长出来"的东西，
                // 盖太厚会让人以为整屏被接管了。
                //
                // 只要锚点就位就挂上，不等面板算出落位：
                // 面板在量到尺寸之前是透明的（展开进度还是 0），
                // 那几帧里若没有遮罩，点击会穿透到下面的页面内容上。
                //
                // 锚点还没就位时不挂：盖住整屏却什么也不显示，用户只会被困住。
                AnimatedVisibility(
                    visible = visible && anchorRect != null,
                    enter = fadeIn(animationSpec = MotionSpec.enter()),
                    exit = fadeOut(animationSpec = exitSpec)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(colors.scrim.copy(alpha = if (colors.isDark) 0.10f else 0.18f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onDismissRequest() }
                    )
                }

                // 面板一展开就要先被量出尺寸，才知道该落在哪儿。
                // 所以它必须在 layout 算出来之前就先组合上：
                // 若等 layout != null 才画，面板就永远量不到自己，
                // panelSize 永远为空，展开动画也就永远不会开始——死锁。
                //
                // 锚点没登记上就不组合：不知道该从哪儿长出来，
                // 展开只是一坨凭空出现的东西。
                // 这时展开进度停在 0，面板不可见；
                // 锚点稍后登记上（触发元素滚进视口之类），它自己就长出来了。
                //
                // 量尺寸那一帧面板是透明的（progress 仍是 0），
                // 用户看不见它，只是不在正确的位置上而已。
                if (showing && anchorRect != null) {
                    // 量尺寸之前没有完整落位，就先贴着锚点放。
                    // 反正那一帧是透明的，位置对不对都看不出来。
                    val placement = layout ?: fallbackPlacement(anchorRect, contentOrigin, gap)
                    GlassSurface(
                        backdrop = backdrop,
                        shape = shape,
                        modifier = Modifier
                            .offset { IntOffset(placement.left.roundToInt(), placement.top.roundToInt()) }
                            .onSizeChanged { panelSize = it }
                            .graphicsLayer {
                                val p = progress.value
                                alpha = p.coerceIn(0f, 1f)
                                if (!reduced) {
                                    // 缩放原点落在锚点与面板相接的那一点：
                                    // 面板看上去是从按钮里长出来的，而不是从自己的中心缩放。
                                    //
                                    // 原点按**实际量到的尺寸**实时算，不写死在落位结果里：
                                    // 量尺寸那一帧的临时落位与最终落位差一道缝，
                                    // 写死的原点会在两帧之间跳一下，面板"弹"出来。
                                    val connectX = (anchorRect.left + anchorRect.right) / 2f
                                    val connectY = if (placement.above) anchorRect.top else anchorRect.bottom
                                    val panelLeft = contentOrigin.x + placement.left
                                    val panelTop = contentOrigin.y + placement.top
                                    transformOrigin = TransformOrigin(
                                        if (size.width > 0f) {
                                            ((connectX - panelLeft) / size.width).coerceIn(0f, 1f)
                                        } else {
                                            0.5f
                                        },
                                        if (size.height > 0f) {
                                            ((connectY - panelTop) / size.height).coerceIn(0f, 1f)
                                        } else {
                                            0f
                                        }
                                    )
                                    val scale = 0.88f + 0.12f * p
                                    scaleX = scale
                                    scaleY = scale
                                    // 位移补上缩放看不出来的那一点点"钻出来"的感觉：
                                    // 面板在下方时先贴着锚点再往下让开，在上方时反过来。
                                    translationY = (1f - p) * gap * (if (placement.above) 1f else -1f)
                                }
                            }
                            // 面板自己吃掉点击：面板没消费点击时命中测试会落到下层遮罩，
                            // 于是"点面板上的空白处也会把浮层关掉"。
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { },
                        contentPadding = PaddingValues(vertical = Space.sm)
                    ) {
                        Column(
                            Modifier
                                // 宽度先按内容取（IntrinsicSize.Max），再夹进 [minWidth, maxWidth]：
                                // 只夹不取，菜单要么永远是最大宽、要么被挤成一条。
                                .widthIn(min = minWidth, max = maxWidth)
                                .width(IntrinsicSize.Max)
                                .heightIn(max = maxPanelHeight)
                                .verticalScroll(rememberScrollState())
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

/**
 * 菜单项：一行图标 + 文案，可选尾部内容（如开关、说明文字）。
 *
 * 禁用态由文字颜色表达：菜单行没有按钮底座，只把字变灰的话，
 * 用户仍会以为它能点。
 */
@Composable
fun GlassPopupItem(
    glyph: Glyph? = null,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = LocalLiuliColors.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val tint = when {
        !enabled -> colors.textTertiary
        danger -> colors.danger
        else -> colors.textPrimary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            // 菜单行按下也要有回弹，否则整块玻璃像是死的。
            .pressScale(pressedScale = 0.96f, interactionSource = interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button
            ) {
                // 菜单项是离散操作，点一下给一次轻触，确认"确实按到了"。
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (glyph != null) {
            GlyphIcon(
                glyph = glyph,
                tint = tint,
                size = 18.dp
            )
            Spacer(Modifier.width(Space.md))
        }
        BasicText(
            text = label,
            style = Type.body.copy(
                color = tint,
                fontWeight = FontWeight.Medium
            ),
            // 超长文案不换行：菜单行的高度是固定的，文案一换行就会顶出去。
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing != null) {
            Spacer(Modifier.width(Space.sm))
            trailing()
        }
    }
}

/**
 * 浮层的落位结果。
 *
 * 缩放原点不在这里给：它要按面板**实际量到的尺寸**算，
 * 而落位算出来时尺寸还不知道。放在绘制时算才准。
 *
 * @param left 面板左边缘相对浮层内容区左边缘的偏移（px）。
 * @param top 面板上边缘相对浮层内容区上边缘的偏移（px）。
 * @param above 面板是否翻到了锚点上方。
 */
private class PopupLayout(
    val left: Float,
    val top: Float,
    val above: Boolean
)

/**
 * 量尺寸之前的临时落位：贴着锚点下方。
 *
 * 面板要先被组合上才量得到自己的尺寸，而落位又依赖尺寸——
 * 这一帧只有等式的一头。临时位置按最终落位的规则给：
 * 同样的锚点、同样的缝隙，这样缩放原点在两帧之间不会跳，
 * 面板不会在展开时"弹"一下。
 */
private fun fallbackPlacement(anchorRect: Rect, contentOrigin: Offset, gap: Float): PopupLayout =
    PopupLayout(
        left = anchorRect.left - contentOrigin.x,
        top = anchorRect.bottom + gap - contentOrigin.y,
        above = false
    )

/**
 * 算面板落在哪儿。
 *
 * 锚点坐标是窗口坐标，浮层内容区的原点也是窗口坐标，两者相减才得到内容区内的偏移；
 * 混用坐标系面板就会整体错位。
 *
 * 锚点还没量到时返回 null：此时没有任何可靠的位置可言，宁可不开。
 */
private fun computePopupLayout(
    anchorRect: Rect?,
    contentOrigin: Offset,
    areaWidth: Float,
    areaHeight: Float,
    panelSize: IntSize?,
    gap: Float
): PopupLayout? {
    if (anchorRect == null || panelSize == null) return null

    val anchorLeft = anchorRect.left - contentOrigin.x
    val anchorRight = anchorRect.right - contentOrigin.x
    val anchorTop = anchorRect.top - contentOrigin.y
    val anchorBottom = anchorRect.bottom - contentOrigin.y

    val panelWidth = panelSize.width.toFloat()
    val panelHeight = panelSize.height.toFloat()

    // 下方放得下就挂在下方；放不下翻到锚点上方，两边都不够就贴着可视区压住。
    val belowTop = anchorBottom + gap
    val aboveTop = anchorTop - gap - panelHeight
    val above = belowTop + panelHeight > areaHeight && aboveTop >= 0f
    val top = when {
        above -> aboveTop
        belowTop + panelHeight > areaHeight -> (areaHeight - panelHeight).coerceAtLeast(0f)
        else -> belowTop
    }.coerceIn(0f, (areaHeight - panelHeight).coerceAtLeast(0f))

    // 水平方向先与锚点左边对齐；右边顶出去了就改成与锚点右边对齐，
    // 最后两边都要夹在可视区内，绝不出屏。
    val left = when {
        anchorLeft + panelWidth > areaWidth -> anchorRight - panelWidth
        else -> anchorLeft
    }.coerceIn(0f, (areaWidth - panelWidth).coerceAtLeast(0f))

    return PopupLayout(left, top, above)
}
