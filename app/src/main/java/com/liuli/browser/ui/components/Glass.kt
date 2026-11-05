package com.liuli.browser.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangularShape
import com.liuli.browser.ui.liquid.GlassEdgeStyle
import com.liuli.browser.ui.liquid.InteractiveHighlight
import com.liuli.browser.ui.liquid.LiquidIconButton
import com.liuli.browser.ui.liquid.LiquidTextAction
import com.liuli.browser.ui.liquid.pressScale
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 液态玻璃容器。
 *
 * 默认它只是展示容器：背景模糊 + 静态高光，按下时完全静止。
 * 想要"被按到"的实感，有两种开启方式：
 * - 传入 [onClick]：整块玻璃变成可点击卡片，按下缩小回弹，
 *   同时玻璃层跟随手指轻微形变、指下泛起高光；
 * - 只把 [interactive] 置为 true：保留卡片内部子组件自己的点击，
 *   仅让玻璃层跟随手指（适合卡片里已经放了按钮的场景）。
 */
@Composable
fun GlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.cardRadius),
    tint: Color? = null,
    blurRadius: Dp = 18.dp,
    lensHeight: Dp = 16.dp,
    lensAmount: Dp = 24.dp,
    shadow: Boolean = true,
    highlight: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** 是否描出边缘。玻璃件没有轮廓会整个融进背景，深色下尤其明显。 */
    edgeStroke: Boolean = true,
    /**
     * 边缘光的样式。
     *
     * 玻璃的边不该是一圈等宽的线：等宽描边看上去像贴了一条边，
     * 而玻璃是有厚度的，边缘应当被光照出方向。
     * 这里用对向双光源——一侧主光、一侧弱副光——
     * 玻璃于是有了"哪边朝光"的知觉。
     */
    edgeStyle: GlassEdgeStyle = GlassEdgeStyle(),
    /** 是否画内阴影。有内阴影的玻璃才像一块有厚度的板，而不是一层贴图。 */
    innerShadow: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val colors = LocalLiuliColors.current
    val requestedTint = tint ?: colors.glassTint
    val effectiveTint = if (isRenderEffectSupported()) {
        requestedTint
    } else {
        requestedTint.copy(alpha = max(requestedTint.alpha, if (colors.isDark) 0.90f else 0.88f))
    }
    // 折射着色器只认圆角形状，喂给它别的形状会直接抛异常。
    // 玻璃件统一用圆角形状，这里再兜一次底，免得调用方踩到崩溃。
    val lensEnabled = lensHeight > 0.dp && lensAmount > 0.dp && shape.supportsLens()

    // 有 onClick 定开启液态跟随，interactive 单独开启则只跟随不接管点击
    val liquidEnabled = interactive || onClick != null
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val interactionSource = remember { MutableInteractionSource() }
    // 按压回弹放在最外层，整块玻璃（含内容）一起缩小
    val pressModifier = if (onClick != null) {
        Modifier.pressScale(pressedScale = 0.96f, interactionSource = interactionSource)
    } else {
        Modifier
    }
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick
        )
    } else {
        Modifier
    }
    // 高光与手势放在 clickable 之后，保证手指移动时仍能跟到玻璃层
    val liquidModifier = if (liquidEnabled) {
        Modifier
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
    } else {
        Modifier
    }

    Box(
        modifier
            .then(pressModifier)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    if (isRenderEffectSupported()) {
                        // 模糊不能无限盖过玻璃本身的大小：模糊会把背后的图层向外撑开，
                        // 小玻璃上就会露出一圈被拉散的边，也白费一份显存。
                        // 这里只做兜底，正常尺寸的玻璃件不受影响。
                        val blurPx = blurRadius.toPx().coerceAtMost(size.minDimension * 0.35f)
                        if (blurPx > 0f) blur(blurPx)
                        if (lensEnabled) {
                            lens(
                                refractionHeight = lensHeight.toPx(),
                                refractionAmount = lensAmount.toPx(),
                                chromaticAberration = true
                            )
                        }
                    }
                },
                layerBlock = if (liquidEnabled) {
                    {
                        // 玻璃层跟随手指轻微形变：位移用 tanh 限幅，缩放做各向异性
                        val width = size.width
                        val height = size.height.coerceAtLeast(1f)
                        val maxDimension = size.maxDimension.coerceAtLeast(1f)
                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 3f.dp.toPx() / height, progress)

                        val maxOffset = size.minDimension.coerceAtLeast(1f)
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = 3f.dp.toPx() / height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX = scale + maxDragScale *
                            abs(cos(offsetAngle) * offset.x / maxDimension) *
                            (width / height).fastCoerceAtMost(1f)
                        scaleY = scale + maxDragScale *
                            abs(sin(offsetAngle) * offset.y / maxDimension) *
                            (height / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                highlight = if (highlight) {
                    // 边缘光取代等宽描边：描边每一处都一样亮，
                    // 玻璃看上去是平的；换成对向双光源后，
                    // 朝光的一侧亮、背光的一侧暗，边缘才有厚度和方向。
                    { edgeStyle.toHighlight(alpha = if (colors.isDark) 0.72f else 0.9f) }
                } else {
                    null
                },
                innerShadow = if (innerShadow) {
                    {
                        // 内阴影压在下沿：光从上方来，厚玻璃的下沿会自己遮掉一点光，
                        // 有它面板才像一块有厚度的板，而不是一张贴图。
                        InnerShadow(
                            radius = 16.dp,
                            offset = DpOffset(0.dp, -16.dp),
                            color = Color.Black.copy(alpha = if (colors.isDark) 0.26f else 0.10f)
                        )
                    }
                } else {
                    null
                },
                shadow = if (shadow) {
                    {
                        Shadow(
                            radius = 20.dp,
                            color = Color.Black.copy(alpha = if (colors.isDark) 0.26f else 0.10f)
                        )
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    drawRect(effectiveTint)
                    // 边缘描边：没有它，深色背景上的玻璃会整个"融"进去，
                    // 轮廓消失，只剩一团模糊的色块。
                    // 描边必须跟着形状走：玻璃是圆角矩形、边框却是直角矩形的话，
                    // 四个角会露出不属于玻璃的直角。
                    if (edgeStroke) {
                        drawShapeOutline(shape, colors.glassBorder, 1.dp.toPx())
                    }
                }
            )
            .then(clickModifier)
            .then(liquidModifier)
            .padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * Kept as a thin adapter so the whole app uses the catalog's InteractiveHighlight
 * and Backdrop lens pipeline.
 *
 * 禁用态由底层的 Liquid 组件统一表达（图标转灰、底座退淡、不铺主色），
 * 这里不再额外压一层透明度：两处各管一半，
 * 同一屏里的图标键与胶囊键会灰得不一样。
 *
 * 触感反馈也由底层的 Liquid 组件给出：按钮被点本来就该有触感，
 * 不必每个调用处手动补一遍。调用处若已经自己补过（破坏性操作想给更重的一下），
 * 把 [haptic] 关掉，否则同一次点击会震两下。
 */
@Composable
fun GlassIconButton(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.minTouch,
    glyphSize: Dp = 22.dp,
    accent: Boolean = false,
    /** 破坏性操作：图标染上危险色。 */
    danger: Boolean = false,
    enabled: Boolean = true,
    /** 是否由组件自己给一次轻触反馈。 */
    haptic: Boolean = true
) {
    LiquidIconButton(
        glyph = glyph,
        description = contentDescription,
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        size = maxOf(size, Dimens.minTouch),
        glyphSize = glyphSize,
        selected = accent,
        danger = danger,
        enabled = enabled,
        haptic = haptic
    )
}

@Composable
fun GlassPillButton(
    label: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    accent: Boolean = false,
    /** 破坏性操作：文字与图标都染上危险色。 */
    danger: Boolean = false,
    enabled: Boolean = true,
    /** 是否由组件自己给一次轻触反馈。 */
    haptic: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp)
) {
    LiquidTextAction(
        label = label,
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        accent = accent,
        danger = danger,
        glyph = glyph,
        enabled = enabled,
        haptic = haptic,
        contentPadding = contentPadding
    )
}

/**
 * 沿着形状轮廓描边。
 *
 * 用矩形描边时，玻璃本身是圆角矩形、边框却是直角矩形，
 * 四个角上会露出不属于玻璃的直角，一眼就穿帮。
 */
private fun DrawScope.drawShapeOutline(shape: Shape, color: Color, width: Float) {
    val outline = shape.createOutline(size, layoutDirection, this)
    when (outline) {
        is Outline.Rounded -> drawPath(
            // RoundRect 在 1.12 里没有公开 topLeft/size/cornerRadius，
            // 直接用它拼路径最省事，也能保留四个角各自不同的圆角。
            path = Path().apply { addRoundRect(outline.roundRect) },
            color = color,
            style = Stroke(width = width)
        )
        is Outline.Generic -> drawPath(
            path = outline.path,
            color = color,
            style = Stroke(width = width)
        )
        else -> drawRect(
            color = color,
            style = Stroke(width = width)
        )
    }
}

/**
 * 折射着色器只认三类形状：库自带的 RoundedRectangularShape、
 * 绝对圆角与普通圆角（CornerBasedShape，CircleShape 也是这一类）。
 * 喂给它别的形状会抛 UnsupportedOperationException，
 * 玻璃件在加折射前先问一句：形状不支持就只留模糊与高光，
 * 总好过整个面板崩掉。
 */
private fun Shape.supportsLens(): Boolean =
    this is RoundedRectangularShape || this is CornerBasedShape

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    val colors = LocalLiuliColors.current
    Canvas(modifier.padding(horizontal = 8.dp).size(1.dp, 22.dp)) {
        drawRect(colors.textPrimary.copy(alpha = 0.12f))
    }
}
