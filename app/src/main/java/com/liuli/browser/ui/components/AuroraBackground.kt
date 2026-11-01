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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.reduceMotion
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * 一个色团的"配方"。
 *
 * 只描述颜色、尺寸比例与摆动方式，与画布尺寸无关：
 * 渐变画笔因此可以被缓存住，逐帧变化的只有中心点。
 */
private data class AuroraBlob(
    val color: Color,
    /** 主色团半径（相对短边） */
    val radius: Float,
    /** 底层光晕半径（相对短边），0 表示不画光晕 */
    val glowRadius: Float,
    /** 光晕相对主色团的浓度 */
    val glowAlpha: Float,
    /** 中心基准位置（相对宽 / 高） */
    val baseX: Float,
    val baseY: Float,
    /** 摆动相位倍率 */
    val phase: Float,
    /** 水平摆动幅度（相对宽） */
    val swingX: Float,
    /** 垂直摆动幅度（相对高） */
    val swingY: Float
)

/** 一批可以复用的渐变画笔。 */
private class AuroraBrushes(
    val core: List<Brush>,
    val glow: List<Brush?>,
    val band: Brush,
    val vignette: Brush
)

/**
 * 渐变画笔缓存。
 *
 * 之前每一帧都要新建 6 个径向渐变和 2 个线性渐变，
 * 而着色器在原生层是真实分配，12fps 下每秒近百个。
 * 渐变只跟画布尺寸有关，尺寸不变时就复用同一批画笔。
 */
private class AuroraBrushCache {
    private var keyWidth = -1f
    private var keyHeight = -1f
    private var keyBlobs: List<AuroraBlob>? = null
    private var keyDark = false
    private var cached: AuroraBrushes? = null

    fun prepare(size: Size, blobs: List<AuroraBlob>, isDark: Boolean): AuroraBrushes {
        val current = cached
        if (
            current != null &&
            size.width == keyWidth &&
            size.height == keyHeight &&
            keyBlobs === blobs &&
            keyDark == isDark
        ) {
            return current
        }
        val min = size.minDimension
        val core = blobs.map { blob -> radial(blob.color, min * blob.radius) }
        val glow = blobs.map { blob ->
            if (blob.glowRadius > 0f) {
                radial(
                    color = blob.color.copy(alpha = blob.color.alpha * blob.glowAlpha),
                    radius = min * blob.glowRadius
                )
            } else {
                null
            }
        }
        val band = Brush.linearGradient(
            0f to Color.White.copy(alpha = if (isDark) 0.03f else 0.22f),
            0.5f to Color.Transparent,
            1f to Color.Transparent,
            start = Offset(0f, size.height * 0.05f),
            end = Offset(size.width, size.height * 0.55f)
        )
        val vignette = Brush.radialGradient(
            0.55f to Color.Transparent,
            1f to (if (isDark) Color.Black.copy(alpha = 0.38f) else Color(0xFF6E86B8).copy(alpha = 0.14f)),
            center = Offset(size.width * 0.5f, size.height * 0.46f),
            radius = size.maxDimension * 0.72f
        )
        keyWidth = size.width
        keyHeight = size.height
        keyBlobs = blobs
        keyDark = isDark
        val built = AuroraBrushes(core = core, glow = glow, band = band, vignette = vignette)
        cached = built
        return built
    }
}

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
    val reduced = reduceMotion
    // 色团共享一个角度基准：各个色团用不同的倍率取样，
    // 既有整体流动感，又保持彼此之间的相位差，不会挤在一起。
    var angle by remember { mutableFloatStateOf(0.7f) }

    LaunchedEffect(dynamic, reduced) {
        // "减少动态效果"开启时连循环都不启动：背景一直在缓慢流动，
        // 对动效敏感的人这是持续的干扰。
        if (!dynamic || reduced) return@LaunchedEffect
        var current = 0.7f
        val cycle = 2f * Math.PI.toFloat() * 8f
        while (true) {
            delay(82)
            current += 0.016f
            if (current > cycle) {
                current -= cycle
            }
            angle = current
        }
    }

    val baseTop = if (colors.isDark) Color(0xFF060912) else Color(0xFFF1F6FF)
    val baseMiddle = if (colors.isDark) Color(0xFF0C1426) else Color(0xFFDCE8FF)
    val baseBottom = if (colors.isDark) Color(0xFF071019) else Color(0xFFEAF8FF)
    val blobAlpha = if (colors.isDark) 0.62f else 0.48f

    // 配方与尺寸无关，画笔因此能缓存；只有中心点在逐帧移动。
    val blobs = remember(colors, blobAlpha) {
        listOf(
            AuroraBlob(
                color = colors.accent.copy(alpha = blobAlpha),
                radius = 0.72f,
                glowRadius = 0.95f,
                glowAlpha = 0.35f,
                baseX = 0.16f,
                baseY = 0.20f,
                phase = 1.0f,
                swingX = 0.07f,
                swingY = 0.045f
            ),
            AuroraBlob(
                color = colors.accentSecondary.copy(alpha = blobAlpha * 0.9f),
                radius = 0.62f,
                glowRadius = 0.82f,
                glowAlpha = 0.33f,
                baseX = 0.90f,
                baseY = 0.28f,
                phase = 0.9f,
                swingX = 0.06f,
                swingY = 0.06f
            ),
            AuroraBlob(
                color = colors.accentTertiary.copy(alpha = blobAlpha * 0.85f),
                radius = 0.78f,
                glowRadius = 0f,
                glowAlpha = 0f,
                baseX = 0.52f,
                baseY = 0.82f,
                phase = 1.1f,
                swingX = 0.09f,
                swingY = 0.045f
            ),
            AuroraBlob(
                color = colors.accent.copy(alpha = blobAlpha * 0.55f),
                radius = 0.5f,
                glowRadius = 0f,
                glowAlpha = 0f,
                baseX = 0.14f,
                baseY = 0.88f,
                phase = 0.6f,
                swingX = 0.05f,
                swingY = 0f
            )
        )
    }
    val baseBrush = remember(baseTop, baseMiddle, baseBottom) {
        Brush.verticalGradient(
            0f to baseTop,
            0.48f to baseMiddle,
            1f to baseBottom
        )
    }
    val brushCache = remember { AuroraBrushCache() }

    Canvas(
        modifier
            .layerBackdrop(backdrop)
            .fillMaxSize()
    ) {
        val brushes = brushCache.prepare(size, blobs, colors.isDark)
        drawRect(brush = baseBrush)

        // 光晕先铺在色团底下：色团边缘才有"被照亮"的过渡，
        // 而不是直接糊在渐变背景上。
        blobs.forEachIndexed { index, blob ->
            val glow = brushes.glow[index] ?: return@forEachIndexed
            drawBlob(
                brush = glow,
                center = blobCenter(blob, angle, size),
                radius = size.minDimension * blob.glowRadius
            )
        }
        blobs.forEachIndexed { index, blob ->
            drawBlob(
                brush = brushes.core[index],
                center = blobCenter(blob, angle, size),
                radius = size.minDimension * blob.radius
            )
        }

        // A soft diagonal light band, similar to a specular highlight on glass.
        drawRect(brush = brushes.band)

        // Vignette keeps text contrast stable at the edges.
        drawRect(brush = brushes.vignette)
    }
}

/** 色团中心：所有色团共享同一个角度基准，各自按倍率取样。 */
private fun blobCenter(blob: AuroraBlob, angle: Float, size: Size): Offset {
    val x = size.width * blob.baseX + sin(angle * blob.phase) * size.width * blob.swingX
    val y = size.height * blob.baseY + cos(angle * blob.phase * 0.8f) * size.height * blob.swingY
    return Offset(x, y)
}

/**
 * 以原点为中心的径向渐变。
 *
 * 画笔建在原点上，绘制时再把画布平移到色团中心：
 * 这样渐变本身可以缓存，逐帧只需要改平移量。
 */
private fun radial(color: Color, radius: Float): Brush =
    Brush.radialGradient(
        0f to color,
        0.42f to color.copy(alpha = color.alpha * 0.45f),
        1f to Color.Transparent,
        center = Offset.Zero,
        radius = radius
    )

private fun DrawScope.drawBlob(brush: Brush, center: Offset, radius: Float) {
    translate(center.x, center.y) {
        drawCircle(brush = brush, radius = radius, center = Offset.Zero)
    }
}
