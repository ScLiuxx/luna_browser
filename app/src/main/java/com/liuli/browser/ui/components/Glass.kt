package com.liuli.browser.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
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
import com.kyant.backdrop.shadow.Shadow
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
    content: @Composable BoxScope.() -> Unit = {}
) {
    val colors = LocalLiuliColors.current
    val requestedTint = tint ?: colors.glassTint
    val effectiveTint = if (isRenderEffectSupported()) {
        requestedTint
    } else {
        requestedTint.copy(alpha = max(requestedTint.alpha, if (colors.isDark) 0.90f else 0.88f))
    }

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
                    if (blurRadius > 0.dp && isRenderEffectSupported()) blur(blurRadius.toPx())
                    if (lensHeight > 0.dp && isRenderEffectSupported()) {
                        lens(
                            refractionHeight = lensHeight.toPx(),
                            refractionAmount = lensAmount.toPx(),
                            chromaticAberration = true
                        )
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
                    { Highlight.Default.copy(alpha = if (colors.isDark) 0.72f else 0.9f) }
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
                onDrawSurface = { drawRect(effectiveTint) }
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
    enabled: Boolean = true
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
        enabled = enabled
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
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp)
) {
    LiquidTextAction(
        label = label,
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        accent = accent,
        glyph = glyph,
        enabled = enabled,
        contentPadding = contentPadding
    )
}

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    val colors = LocalLiuliColors.current
    Canvas(modifier.padding(horizontal = 8.dp).size(1.dp, 22.dp)) {
        drawRect(colors.textPrimary.copy(alpha = 0.12f))
    }
}
