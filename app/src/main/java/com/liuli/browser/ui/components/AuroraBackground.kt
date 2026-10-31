package com.liuli.browser.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.liuli.browser.ui.theme.LocalLiuliColors
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * The "liquid" backdrop that gives the glass something to refract.
 * Animation is intentionally quantised to ~12 fps: the blobs move slowly, and
 * this keeps the blur pipeline cheap enough for the alliance's power budget.
 */
@Composable
fun AuroraBackground(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    dynamic: Boolean = true
) {
    val colors = LocalLiuliColors.current
    // 色团共享一个角度基准：各个色团用不同的倍率取样，
    // 既有整体流动感，又保持彼此之间的相位差，不会挤在一起。
    var angle by remember { mutableFloatStateOf(0.7f) }

    LaunchedEffect(dynamic) {
        if (!dynamic) return@LaunchedEffect
        var current = 0.7f
        while (true) {
            delay(82)
            current += 0.016f
            if (current > 2f * Math.PI.toFloat() * 8f) {
                current -= 2f * Math.PI.toFloat() * 8f
            }
            angle = current
        }
    }

    val baseTop = if (colors.isDark) Color(0xFF060912) else Color(0xFFF1F6FF)
    val baseMiddle = if (colors.isDark) Color(0xFF0C1426) else Color(0xFFDCE8FF)
    val baseBottom = if (colors.isDark) Color(0xFF071019) else Color(0xFFEAF8FF)
    val blobAlpha = if (colors.isDark) 0.62f else 0.48f

    Canvas(
        modifier
            .layerBackdrop(backdrop)
            .fillMaxSize()
    ) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to baseTop,
                0.48f to baseMiddle,
                1f to baseBottom
            )
        )

        // 光晕先铺在色团底下：色团边缘才有"被照亮"的过渡，
        // 而不是直接糊在渐变背景上。
        drawBlob(
            color = colors.accent.copy(alpha = blobAlpha * 0.35f),
            center = Offset(
                size.width * 0.16f + sin(angle) * size.width * 0.07f,
                size.height * 0.20f + cos(angle * 0.8f) * size.height * 0.045f
            ),
            radius = size.minDimension * 0.95f
        )
        drawBlob(
            color = colors.accentSecondary.copy(alpha = blobAlpha * 0.30f),
            center = Offset(
                size.width * 0.90f + cos(angle * 0.9f) * size.width * 0.06f,
                size.height * 0.28f + sin(angle) * size.height * 0.06f
            ),
            radius = size.minDimension * 0.82f
        )

        drawBlob(
            color = colors.accent.copy(alpha = blobAlpha),
            center = Offset(
                size.width * 0.16f + sin(angle) * size.width * 0.07f,
                size.height * 0.20f + cos(angle * 0.8f) * size.height * 0.045f
            ),
            radius = size.minDimension * 0.72f
        )
        drawBlob(
            color = colors.accentSecondary.copy(alpha = blobAlpha * 0.9f),
            center = Offset(
                size.width * 0.90f + cos(angle * 0.9f) * size.width * 0.06f,
                size.height * 0.28f + sin(angle) * size.height * 0.06f
            ),
            radius = size.minDimension * 0.62f
        )
        drawBlob(
            color = colors.accentTertiary.copy(alpha = blobAlpha * 0.85f),
            center = Offset(
                size.width * 0.52f + sin(angle * 1.1f) * size.width * 0.09f,
                size.height * 0.82f + cos(angle * 0.7f) * size.height * 0.045f
            ),
            radius = size.minDimension * 0.78f
        )
        drawBlob(
            color = colors.accent.copy(alpha = blobAlpha * 0.55f),
            center = Offset(
                size.width * 0.14f + cos(angle * 0.6f) * size.width * 0.05f,
                size.height * 0.88f
            ),
            radius = size.minDimension * 0.5f
        )

        // A soft diagonal light band, similar to a specular highlight on glass.
        drawRect(
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = if (colors.isDark) 0.03f else 0.22f),
                0.5f to Color.Transparent,
                1f to Color.Transparent,
                start = Offset(0f, size.height * 0.05f),
                end = Offset(size.width, size.height * 0.55f)
            )
        )

        // Vignette keeps text contrast stable at the edges.
        drawRect(
            brush = Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to (if (colors.isDark) Color.Black.copy(alpha = 0.38f) else Color(0xFF6E86B8).copy(alpha = 0.14f)),
                center = Offset(size.width * 0.5f, size.height * 0.46f),
                radius = size.maxDimension * 0.72f
            )
        )
    }
}

private fun DrawScope.drawBlob(color: Color, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to color,
            0.42f to color.copy(alpha = color.alpha * 0.45f),
            1f to Color.Transparent,
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
