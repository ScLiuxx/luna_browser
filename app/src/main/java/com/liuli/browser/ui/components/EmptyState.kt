package com.liuli.browser.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.Space
import com.liuli.browser.ui.theme.Type

/**
 * 空状态。
 *
 * 三处空状态（书签/历史、标签页、Agent 面板）此前各写各的：
 * 图标底座 56dp / 66dp / 76dp 三种尺寸，
 * 底色有纯强调色、渐变、玻璃三种，标题还有 15sp 与 17sp 两档。
 * 同一个"这里还没有东西"，在三个地方长成三种样子。
 * 这里收成一套：底座、配色、标题与说明的层级全部一致。
 */
@Composable
fun EmptyState(
    glyph: Glyph,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    /** 补充说明：放在说明行下方。 */
    footer: (@Composable () -> Unit)? = null
) {
    val colors = LocalLiuliColors.current
    // 空状态也要有落位动作：图标先弹一下，文字再跟上。
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val iconScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.7f,
        animationSpec = MotionSpec.pageSpring(),
        label = "empty-icon-scale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = MotionSpec.value(Motion.Normal),
        label = "empty-alpha"
    )
    Column(
        modifier.graphicsLayer { alpha = contentAlpha },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
                .clip(CircleShape)
                // 强调色底座：空状态也要有一点存在感，
                // 灰底的圆圈看着像没画完的占位符。
                .background(colors.accent.copy(alpha = if (colors.isDark) 0.18f else 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(
                glyph = glyph,
                tint = colors.accent.copy(alpha = 0.9f),
                size = 28.dp
            )
        }
        Spacer(Modifier.height(Space.md))
        BasicText(
            text = title,
            style = Type.title.copy(color = colors.textPrimary, textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(Space.xs))
        BasicText(
            text = message,
            style = Type.bodySmall.copy(color = colors.textTertiary, textAlign = TextAlign.Center)
        )
        if (footer != null) {
            Spacer(Modifier.height(Space.md))
            footer()
        }
    }
}
