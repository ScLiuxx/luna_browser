package com.liuli.browser.ui.liquid

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    content: @Composable RowScope.() -> Unit
) {
    val colors = LocalLiuliColors.current
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

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
                layerBlock = if (isInteractive) {
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
                highlight = { Highlight.Default.copy(alpha = if (colors.isDark) 0.65f else 0.85f) },
                shadow = {
                    Shadow(
                        radius = 18.dp,
                        color = Color.Black.copy(alpha = if (colors.isDark) 0.28f else 0.12f)
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
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (isInteractive) {
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
    enabled: Boolean = true
) {
    val colors = LocalLiuliColors.current
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val tint = if (selected) {
        colors.accent.copy(alpha = if (colors.isDark) 0.30f else 0.20f)
    } else {
        colors.glassTint.copy(alpha = if (colors.isDark) 0.42f else 0.50f)
    }

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
                layerBlock = {
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
                },
                highlight = { Highlight.Default.copy(alpha = if (colors.isDark) 0.6f else 0.8f) },
                shadow = {
                    Shadow(
                        radius = 12.dp,
                        color = Color.Black.copy(alpha = if (colors.isDark) 0.24f else 0.10f)
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
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            )
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(
            glyph = glyph,
            tint = if (selected) colors.accent else if (enabled) colors.textPrimary else colors.textTertiary.copy(alpha = 0.4f),
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
    glyph: Glyph? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)
) {
    val colors = LocalLiuliColors.current
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        tint = if (accent) colors.accent else Color.Unspecified,
        surfaceColor = if (accent) Color.Unspecified else colors.glassTint,
        enabled = enabled,
        contentPadding = contentPadding,
        contentDescription = label
    ) {
        if (glyph != null) {
            GlyphIcon(
                glyph = glyph,
                tint = if (accent) Color.White else colors.textPrimary,
                size = 18.dp
            )
        }
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = androidx.compose.ui.text.TextStyle(
                color = if (accent) Color.White else colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        )
    }
}
