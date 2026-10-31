package com.liuli.browser.ui.liquid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.reduceMotion

/**
 * 轻量按压反馈：按下时缩小，松开时弹回。
 *
 * 此前只有 LiquidButton / LiquidIconButton / Toggle / Slider / Dock 具备按压形变，
 * 其余玻璃卡片与面板内的按钮按下时毫无反应。这里提供统一的最小实现，
 * 让任何组件只需加一个 Modifier 就能获得液态玻璃式的按压手感。
 *
 * 用法有两种：
 * - 组件自己管理 interactionSource 时，把 [interactionSource] 传进来；
 * - 否则用 [Modifier.pressScale] 的无参版本，它会自建一个。
 */
@Composable
fun Modifier.pressScale(
    pressedScale: Float = 0.96f,
    interactionSource: MutableInteractionSource? = null
): Modifier {
    if (reduceMotion) return this
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.PressSpring.spring(),
        label = "press-scale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 玻璃卡片的按压反馈：缩放 + 手指下方高光，两者一起才有液态玻璃的触感。
 *
 * [com.liuli.browser.ui.components.GlassSurface] 以及所有基于它的卡片都应当使用它，
 * 否则按下时完全静止，缺少"被按到"的实感。
 */
class GlassPressState(
    val animationScope: kotlinx.coroutines.CoroutineScope,
    val highlight: InteractiveHighlight
) {
    val pressProgress: Float get() = highlight.pressProgress
}
