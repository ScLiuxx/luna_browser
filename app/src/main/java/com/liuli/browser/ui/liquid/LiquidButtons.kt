package com.liuli.browser.ui.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.kyant.shapes.Capsule
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.GlyphIcon
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.Type
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Direct port of the AndroidLiquidGlass LiquidButton. The finger-driven
 * InteractiveHighlight bends the glass layer (translation + anisotropic scale),
 * while blur/lens/chromatic aberration are rendered by Backdrop.
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = Capsule(),
    height: Dp = Dimens.minTouch,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    contentDescription: String? = null,
    /**
     * 是否由组件自己给一次轻触反馈。
     *
     * 默认给：按钮被点本来就该有触感，不必每个调用处手动补一遍。
     * 调用处若已经自己补过（破坏性操作想给更重的一下），把它关掉，
     * 否则同一次点击会震两下。
     */
    haptic: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val colors = LocalLiuliColors.current
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    // 按钮本体也参与按压回弹：玻璃件形变之外，整块再缩一点，手感更"实"。
    val interaction = remember { MutableInteractionSource() }
    // 组件自己给一次轻触反馈：按下即震，不用每个调用处手动补一遍。
    // 补在调用处的话，同一个按钮在有的页面震、有的页面不震，
    // 而且调用处一旦忘了补，这枚按钮就哑了。
    val haptics = LocalHapticFeedback.current
    // 触感落在"手指按下"这一刻，而不是等抬手：
    // 玻璃的形变、指下的高光、整块的缩小都是按下就发生的，
    // 触感晚一两百毫秒才到，手感就散了。
    // 落在按下而不是点击，也正好让调用处自己补的那一次（破坏性操作想给更重的确认）
    // 变成"按下 + 确认"两段，而不是同一瞬间连震两下。
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed, haptic, enabled) {
        if (pressed && haptic && enabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    // 禁用时不接手势：按钮推不动，玻璃却还在跟着手指弯、指下还在发亮，
    // 用户会以为它能点。液态跟随只在真正可点时才有。
    val liquidActive = isInteractive && enabled

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    if (isRenderEffectSupported()) {
                        blur(2f.dp.toPx())
                        lens(12f.dp.toPx(), 24f.dp.toPx(), chromaticAberration = true)
                    }
                },
                layerBlock = if (liquidActive) {
                    {
                        val width = size.width
                        val heightPx = size.height
                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = 4f.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX = scale + maxDragScale *
                            abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                            (width / heightPx).fastCoerceAtMost(1f)
                        scaleY = scale + maxDragScale *
                            abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                            (heightPx / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                highlight = {
                    // 高光随按压变亮：玻璃被压下去时反射更强，
                    // 这是"被按到"最直接的视觉线索。
                    val progress = interactiveHighlight.pressProgress
                    Highlight.Default.copy(
                        alpha = (if (colors.isDark) 0.65f else 0.85f) + 0.3f * progress
                    )
                },
                shadow = {
                    Shadow(
                        radius = 18.dp,
                        color = Color.Black.copy(
                            // 禁用态的投影更浅：玻璃件亮着却按不动，
                            // 用户会以为它还能点。压暗之后一眼就看出是哑的。
                            alpha = when {
                                !enabled -> 0f
                                colors.isDark -> 0.28f
                                else -> 0.12f
                            }
                        )
                    )
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                }
            )
            .clip(shape)
            .semantics {
                role = Role.Button
                if (contentDescription != null) this.contentDescription = contentDescription
            }
            .clickable(
                interactionSource = interaction,
                indication = if (isInteractive) null else LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    // 触感已经在按下时给过了（见上面的 collectIsPressedAsState），
                    // 这里不再补一次，否则同一次点击会震两下。
                    onClick()
                }
            )
            .pressScale(pressedScale = 0.95f, interactionSource = interaction)
            .then(
                if (liquidActive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
            .height(height)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Circular variant of LiquidButton, used for toolbar buttons. The glass layer
 * bends toward the finger exactly like the reference catalog buttons.
 */
@Composable
fun LiquidIconButton(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.minTouch,
    glyphSize: Dp = 22.dp,
    selected: Boolean = false,
    /** 破坏性操作：图标染上危险色，和普通键一眼分得开。 */
    danger: Boolean = false,
    enabled: Boolean = true,
    /**
     * 是否由组件自己给一次轻触反馈。
     *
     * 默认给：图标键也是按钮，点它就该有触感。
     * 调用处若已经自己补过（破坏性操作想给更重的一下），把它关掉，
     * 否则同一次点击会震两下。
     */
    haptic: Boolean = true
) {
    val colors = LocalLiuliColors.current
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    // 选中态的颜色用插值过渡，而不是啪地换色：
    // 工具栏按钮被选中时，玻璃底色与图标一起渐变过去。
    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> colors.glassTint.copy(alpha = if (colors.isDark) 0.26f else 0.32f)
            selected -> colors.accent.copy(alpha = if (colors.isDark) 0.30f else 0.20f)
            else -> colors.glassTint.copy(alpha = if (colors.isDark) 0.42f else 0.50f)
        },
        animationSpec = MotionSpec.color(),
        label = "liquid-icon-button-tint"
    )
    val glyphTint by animateColorAsState(
        targetValue = when {
            !enabled -> colors.textTertiary
            selected -> colors.accent
            danger -> colors.danger
            else -> colors.textPrimary
        },
        animationSpec = MotionSpec.color(),
        label = "liquid-icon-button-glyph-tint"
    )
    val interaction = remember { MutableInteractionSource() }
    // 组件自己给一次轻触反馈：与 LiquidButton 保持同一套强度，
    // 同一屏里图标键与胶囊键的手感才对得上。
    val haptics = LocalHapticFeedback.current
    // 触感落在"手指按下"这一刻：图标键的玻璃形变与高光也是按下就发生的，
    // 触感等到抬手才到，手感就散了。
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed, haptic, enabled) {
        if (pressed && haptic && enabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    // 禁用时不接手势：玻璃不该跟着手指弯，也不该在指下发亮。
    val liquidActive = enabled

    Box(
        modifier
            .size(size)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    if (isRenderEffectSupported()) {
                        blur(4f.dp.toPx())
                        lens(10f.dp.toPx(), 18f.dp.toPx(), chromaticAberration = true)
                    }
                },
                layerBlock = if (liquidActive) {
                    {
                        val width = this.size.width
                        val height = this.size.height
                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 5f.dp.toPx() / height, progress)
                        val maxOffset = this.size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)
                        val maxDragScale = 5f.dp.toPx() / height
                        val angle = atan2(offset.y, offset.x)
                        scaleX = scale + maxDragScale *
                            abs(cos(angle) * offset.x / this.size.maxDimension) *
                            (width / height).fastCoerceAtMost(1f)
                        scaleY = scale + maxDragScale *
                            abs(sin(angle) * offset.y / this.size.maxDimension) *
                            (height / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                highlight = {
                    // 同上：按下时高光增强。
                    val progress = interactiveHighlight.pressProgress
                    Highlight.Default.copy(
                        alpha = (if (colors.isDark) 0.6f else 0.8f) + 0.3f * progress
                    )
                },
                shadow = {
                    Shadow(
                        radius = 12.dp,
                        color = Color.Black.copy(
                            // 禁用态的投影更浅：玻璃件亮着却按不动，
                            // 用户会以为它还能点。压暗之后一眼就看出是哑的。
                            alpha = when {
                                !enabled -> 0f
                                colors.isDark -> 0.24f
                                else -> 0.10f
                            }
                        )
                    )
                },
                onDrawSurface = {
                    drawRect(tint)
                }
            )
            .clip(CircleShape)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = description,
                onClick = {
                    // 触感已经在按下时给过了（见上面的 collectIsPressedAsState），
                    // 这里不再补一次，否则同一次点击会震两下。
                    onClick()
                }
            )
            .pressScale(pressedScale = 0.90f, interactionSource = interaction)
            .then(
                if (liquidActive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(
            glyph = glyph,
            tint = glyphTint,
            size = glyphSize
        )
    }
}

@Composable
fun LiquidTextAction(
    label: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    /** 破坏性操作：文字与图标都染上危险色。 */
    danger: Boolean = false,
    glyph: Glyph? = null,
    enabled: Boolean = true,
    /**
     * 是否由组件自己给一次轻触反馈。
     *
     * 默认给；调用处若已经自己补过，把它关掉，否则同一次点击会震两下。
     */
    haptic: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)
) {
    val colors = LocalLiuliColors.current
    // 禁用态在这里一并处理：主色铺底与白字都会把"不可用"这件事藏起来。
    // 撤掉主色、文字转灰，禁用态一眼能认出来。
    val labelTint = when {
        !enabled -> colors.textTertiary
        // 危险 + 主色 = 实心危险按钮：确认破坏性操作的按钮就长这样，
        // 与对话框里的确认键同一套。
        danger && accent -> colors.onDanger
        danger -> colors.danger
        accent -> Color.White
        else -> colors.textPrimary
    }
    // 禁用时不再铺主色，但玻璃底座还在：按钮不能凭空消失。
    val useAccent = accent && enabled
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        tint = if (useAccent) {
            if (danger) colors.danger else colors.accent
        } else {
            Color.Unspecified
        },
        surfaceColor = when {
            useAccent && danger -> colors.danger
            useAccent -> Color.Unspecified
            // 禁用时底座一起退下去：光把字变灰，玻璃还是满亮的，
            // 按钮看上去仍然"在等人点"。
            !enabled -> colors.glassTint.copy(alpha = colors.glassTint.alpha * 0.55f)
            else -> colors.glassTint
        },
        enabled = enabled,
        contentPadding = contentPadding,
        contentDescription = label,
        haptic = haptic
    ) {
        if (glyph != null) {
            GlyphIcon(
                glyph = glyph,
                tint = labelTint,
                size = 18.dp
            )
        }
        BasicText(
            text = label,
            style = Type.body.copy(
                color = labelTint,
                fontWeight = FontWeight.Medium
            ),
            // 超长文案与超大字号下不换行、不撑破胶囊：
            // 按钮的高度是固定的，文案一换行就会顶出去。
            // 省略号比溢出更体面。
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
