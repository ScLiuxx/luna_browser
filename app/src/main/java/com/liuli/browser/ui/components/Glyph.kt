package com.liuli.browser.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A small, dependency-free icon set drawn with Canvas. Keeping the icons in
 * code avoids a multi-megabyte icon font and keeps the APK (and memory) small.
 */
enum class Glyph {
    Home,
    Tabs,
    Bookmark,
    BookmarkFilled,
    Settings,
    Back,
    Forward,
    Reload,
    Stop,
    Close,
    Plus,
    More,
    Search,
    Lock,
    Globe,
    Share,
    Download,
    Desktop,
    History,
    Trash,
    Check,
    Sun,
    Moon,
    Edit,
    ChevronRight,
    ChevronDown,
    ChevronUp,
    Shield,
    Sparkle,
    Info,
    NewTab,
    Incognito,
    Copy,
    External
}

@Composable
fun GlyphIcon(
    glyph: Glyph,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    Canvas(modifier = modifier.size(size)) {
        drawGlyph(glyph, tint, this.size.minDimension)
    }
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color, box: Float) {
    val s = box / 24f
    val stroke = Stroke(width = 2.0f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        drawLine(
            color = color,
            start = Offset(x1 * s, y1 * s),
            end = Offset(x2 * s, y2 * s),
            strokeWidth = 2.0f * s,
            cap = StrokeCap.Round
        )
    }

    fun path(block: Path.() -> Unit) {
        val p = Path().apply(block)
        drawPath(p, color, style = stroke)
    }

    fun fill(block: Path.() -> Unit) {
        val p = Path().apply(block)
        drawPath(p, color, style = Fill)
    }

    when (glyph) {
        Glyph.Home -> {
            path {
                moveTo(3.5f, 11.5f); lineTo(12f, 3.2f); lineTo(20.5f, 11.5f)
                moveTo(5.5f, 9.8f); lineTo(5.5f, 20.5f); lineTo(18.5f, 20.5f); lineTo(18.5f, 9.8f)
            }
        }
        Glyph.Tabs -> {
            drawRoundRect(
                color,
                topLeft = Offset(4f * s, 4f * s),
                size = Size(16f * s, 16f * s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.6f * s),
                style = stroke
            )
            line(4f, 9f, 20f, 9f)
            drawCircle(color, 1.4f * s, Offset(8f * s, 6.5f * s), style = Fill)
        }
        Glyph.Bookmark -> path {
            moveTo(4f, 3.8f); lineTo(20f, 3.8f); lineTo(20f, 20.5f)
            lineTo(12f, 16.2f); lineTo(4f, 20.5f); close()
        }
        Glyph.BookmarkFilled -> fill {
            moveTo(4f, 3.8f); lineTo(20f, 3.8f); lineTo(20f, 20.5f)
            lineTo(12f, 16.2f); lineTo(4f, 20.5f); close()
        }
        Glyph.Settings -> {
            drawCircle(color, 3.0f * s, Offset(12f * s, 12f * s), style = stroke)
            repeat(8) { index ->
                val angle = Math.toRadians((index * 45).toDouble())
                val start = Offset(
                    12f * s + (6.4f * s * kotlin.math.cos(angle)).toFloat(),
                    12f * s + (6.4f * s * kotlin.math.sin(angle)).toFloat()
                )
                val end = Offset(
                    12f * s + (8.4f * s * kotlin.math.cos(angle)).toFloat(),
                    12f * s + (8.4f * s * kotlin.math.sin(angle)).toFloat()
                )
                drawLine(color, start, end, strokeWidth = 2f * s, cap = StrokeCap.Round)
            }
        }
        Glyph.Back -> {
            line(20f, 12f, 5f, 12f)
            line(5f, 12f, 11f, 6f)
            line(5f, 12f, 11f, 18f)
        }
        Glyph.Forward -> {
            line(4f, 12f, 19f, 12f)
            line(19f, 12f, 13f, 6f)
            line(19f, 12f, 13f, 18f)
        }
        Glyph.Reload -> {
            drawArc(
                color = color,
                startAngle = 40f,
                sweepAngle = 285f,
                useCenter = false,
                topLeft = Offset(4f * s, 4f * s),
                size = Size(16f * s, 16f * s),
                style = stroke
            )
            line(15.5f, 3f, 19f, 6f)
            line(19f, 6f, 15f, 8.2f)
        }
        Glyph.Stop -> drawRoundRect(
            color,
            topLeft = Offset(7f * s, 7f * s),
            size = Size(10f * s, 10f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * s),
            style = Fill
        )
        Glyph.Close -> {
            line(6f, 6f, 18f, 18f)
            line(18f, 6f, 6f, 18f)
        }
        Glyph.Plus -> {
            line(12f, 4.5f, 12f, 19.5f)
            line(4.5f, 12f, 19.5f, 12f)
        }
        Glyph.More -> {
            drawCircle(color, 1.7f * s, Offset(5.5f * s, 12f * s), style = Fill)
            drawCircle(color, 1.7f * s, Offset(12f * s, 12f * s), style = Fill)
            drawCircle(color, 1.7f * s, Offset(18.5f * s, 12f * s), style = Fill)
        }
        Glyph.Search -> {
            drawCircle(color, 6.3f * s, Offset(10.5f * s, 10.5f * s), style = stroke)
            line(15.3f, 15.3f, 20.5f, 20.5f)
        }
        Glyph.Lock -> {
            drawRoundRect(
                color,
                topLeft = Offset(5f * s, 10f * s),
                size = Size(14f * s, 10.5f * s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * s),
                style = stroke
            )
            path {
                moveTo(8f, 10f); lineTo(8f, 7.5f)
                arcTo(Rect(8f * s, 3.5f * s, 16f * s, 11.5f * s), 180f, 180f, false)
                lineTo(16f, 10f)
            }
            drawCircle(color, 1.3f * s, Offset(12f * s, 15f * s), style = Fill)
        }
        Glyph.Globe -> {
            drawCircle(color, 8.5f * s, Offset(12f * s, 12f * s), style = stroke)
            drawOval(
                color,
                topLeft = Offset(7f * s, 3.5f * s),
                size = Size(10f * s, 17f * s),
                style = stroke
            )
            line(3.5f, 12f, 20.5f, 12f)
            path {
                moveTo(5.2f, 7.2f); quadraticTo(12f, 9.6f, 18.8f, 7.2f)
                moveTo(5.2f, 16.8f); quadraticTo(12f, 14.4f, 18.8f, 16.8f)
            }
        }
        Glyph.Share -> {
            drawCircle(color, 2.2f * s, Offset(18f * s, 5.5f * s), style = stroke)
            drawCircle(color, 2.2f * s, Offset(6f * s, 12f * s), style = stroke)
            drawCircle(color, 2.2f * s, Offset(18f * s, 18.5f * s), style = stroke)
            line(8f, 11f, 16f, 6.6f)
            line(8f, 13f, 16f, 17.4f)
        }
        Glyph.Download -> {
            line(12f, 3.5f, 12f, 14.5f)
            line(7.5f, 10f, 12f, 14.5f)
            line(16.5f, 10f, 12f, 14.5f)
            path {
                moveTo(4f, 15.5f); lineTo(4f, 19.5f)
                quadraticTo(4f, 21f, 5.5f, 21f)
                lineTo(18.5f, 21f)
                quadraticTo(20f, 21f, 20f, 19.5f)
                lineTo(20f, 15.5f)
            }
        }
        Glyph.Desktop -> {
            drawRoundRect(
                color,
                topLeft = Offset(2.5f * s, 4f * s),
                size = Size(19f * s, 13f * s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * s),
                style = stroke
            )
            line(12f, 17f, 12f, 20.5f)
            line(8f, 20.5f, 16f, 20.5f)
        }
        Glyph.History -> {
            drawCircle(color, 8.5f * s, Offset(12f * s, 12f * s), style = stroke)
            line(12f, 7f, 12f, 12.4f)
            line(12f, 12.4f, 16f, 15f)
            path {
                moveTo(3.5f, 12f); lineTo(3.5f, 7.5f)
                moveTo(3.5f, 12f); lineTo(7.7f, 9.7f)
            }
        }
        Glyph.Trash -> {
            drawRoundRect(
                color,
                topLeft = Offset(5f * s, 6.5f * s),
                size = Size(14f * s, 14.5f * s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * s),
                style = stroke
            )
            line(3.8f, 6.5f, 20.2f, 6.5f)
            line(9f, 6.3f, 9f, 3.8f)
            line(15f, 6.3f, 15f, 3.8f)
            line(9.5f, 10f, 9.5f, 17.5f)
            line(14.5f, 10f, 14.5f, 17.5f)
        }
        Glyph.Check -> path {
            moveTo(4.5f, 12.5f); lineTo(9.5f, 17.5f); lineTo(19.5f, 6.5f)
        }
        Glyph.Sun -> {
            drawCircle(color, 4.3f * s, Offset(12f * s, 12f * s), style = stroke)
            repeat(8) { index ->
                val angle = Math.toRadians((index * 45).toDouble())
                val start = Offset(
                    12f * s + (7.2f * s * kotlin.math.cos(angle)).toFloat(),
                    12f * s + (7.2f * s * kotlin.math.sin(angle)).toFloat()
                )
                val end = Offset(
                    12f * s + (10.2f * s * kotlin.math.cos(angle)).toFloat(),
                    12f * s + (10.2f * s * kotlin.math.sin(angle)).toFloat()
                )
                drawLine(color, start, end, strokeWidth = 2f * s, cap = StrokeCap.Round)
            }
        }
        Glyph.Moon -> fill {
            moveTo(20.5f, 14.2f)
            cubicTo(18.8f, 20.2f, 11.8f, 23f, 6.4f, 18.4f)
            cubicTo(1.6f, 14.2f, 2.4f, 6.4f, 8.2f, 3.3f)
            cubicTo(5.4f, 8.5f, 6.8f, 15.2f, 11.8f, 18f)
            cubicTo(15f, 19.7f, 18f, 18.8f, 20.5f, 14.2f)
            close()
        }
        Glyph.Edit -> {
            path {
                moveTo(4f, 20f); lineTo(4.8f, 15.5f); lineTo(16f, 4.3f)
                lineTo(19.7f, 8f); lineTo(8.5f, 19.2f); close()
                moveTo(14.5f, 5.8f); lineTo(18.2f, 9.5f)
            }
        }
        Glyph.ChevronRight -> {
            line(9f, 5f, 16f, 12f)
            line(16f, 12f, 9f, 19f)
        }
        Glyph.ChevronDown -> {
            line(5f, 9f, 12f, 16f)
            line(12f, 16f, 19f, 9f)
        }
        Glyph.ChevronUp -> {
            line(5f, 15f, 12f, 8f)
            line(12f, 8f, 19f, 15f)
        }
        Glyph.Shield -> {
            path {
                moveTo(12f, 2.8f); lineTo(19.2f, 5.8f); lineTo(19.2f, 11.2f)
                cubicTo(19.2f, 16.2f, 16.2f, 19.8f, 12f, 21.2f)
                cubicTo(7.8f, 19.8f, 4.8f, 16.2f, 4.8f, 11.2f)
                lineTo(4.8f, 5.8f); close()
                moveTo(8.8f, 11.8f); lineTo(11.2f, 14.2f); lineTo(15.5f, 8.8f)
            }
        }
        Glyph.Sparkle -> fill {
            moveTo(12f, 2.4f)
            quadraticTo(13.4f, 9.2f, 21.5f, 12f)
            quadraticTo(13.4f, 14.8f, 12f, 21.6f)
            quadraticTo(10.6f, 14.8f, 2.5f, 12f)
            quadraticTo(10.6f, 9.2f, 12f, 2.4f)
            close()
        }
        Glyph.Info -> {
            drawCircle(color, 8.5f * s, Offset(12f * s, 12f * s), style = stroke)
            drawCircle(color, 1.3f * s, Offset(12f * s, 7.3f * s), style = Fill)
            line(12f, 10.5f, 12f, 17f)
        }
        Glyph.NewTab -> {
            drawRoundRect(
                color,
                topLeft = Offset(3.5f * s, 5f * s),
                size = Size(17f * s, 15.5f * s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * s),
                style = stroke
            )
            line(12f, 9f, 12f, 16.5f)
            line(8.3f, 12.75f, 15.7f, 12.75f)
        }
        Glyph.Incognito -> {
            drawCircle(color, 3.2f * s, Offset(7f * s, 14f * s), style = stroke)
            drawCircle(color, 3.2f * s, Offset(17f * s, 14f * s), style = stroke)
            line(10.2f, 14f, 13.8f, 14f)
            line(3.5f, 10f, 20.5f, 10f)
            path {
                moveTo(7f, 10f); lineTo(9f, 5.5f); lineTo(15f, 5.5f); lineTo(17f, 10f)
            }
        }
        Glyph.Copy -> {
            drawRoundRect(
                color,
                topLeft = Offset(8f * s, 8f * s),
                size = Size(12f * s, 12f * s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * s),
                style = stroke
            )
            path {
                moveTo(16f, 5.5f); lineTo(5.5f, 5.5f)
                quadraticTo(4f, 5.5f, 4f, 7f)
                lineTo(4f, 16f)
            }
        }
        Glyph.External -> {
            path {
                moveTo(13f, 4f); lineTo(20f, 4f); lineTo(20f, 11f)
                moveTo(20f, 4f); lineTo(11f, 13f)
                moveTo(17f, 14f); lineTo(17f, 19f)
                quadraticTo(17f, 20.5f, 15.5f, 20.5f)
                lineTo(5f, 20.5f)
                quadraticTo(3.5f, 20.5f, 3.5f, 19f)
                lineTo(3.5f, 8.5f)
                quadraticTo(3.5f, 7f, 5f, 7f)
                lineTo(10f, 7f)
            }
        }
    }
}
