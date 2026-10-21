package com.liuli.browser.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.Shadow
import com.liuli.browser.ui.liquid.LiquidIconButton
import com.liuli.browser.ui.liquid.LiquidTextAction
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import kotlin.math.max

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
    content: @Composable BoxScope.() -> Unit = {}
) {
    val colors = LocalLiuliColors.current
    val requestedTint = tint ?: colors.glassTint
    val effectiveTint = if (isRenderEffectSupported()) {
        requestedTint
    } else {
        requestedTint.copy(alpha = max(requestedTint.alpha, if (colors.isDark) 0.90f else 0.88f))
    }

    Box(
        modifier
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
