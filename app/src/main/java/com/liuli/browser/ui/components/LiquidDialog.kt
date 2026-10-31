package com.liuli.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import com.liuli.browser.ui.theme.MotionSpec

/**
 * 玻璃对话框容器。
 *
 * 此前设置页的"清除数据""撤销授权"与隐私说明都是硬 `if` 挂载，
 * 出现与消失没有任何过渡，遮罩是"啪"地盖上去的。这里统一成：
 * 遮罩淡入淡出、面板从 0.92 弹性缩放到位，两者互不干扰。
 *
 * [visible] 由调用方持有，这样关闭时才有退场动画；
 * 面板内容由 [content] 提供。
 */
@Composable
fun LiquidDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    panelModifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // 规格先在组合作用域算好：动画参数位不是 @Composable 上下文。
    val fadeInSpec = MotionSpec.enter()
    val fadeOutSpec = MotionSpec.exit()
    val scaleSpec = MotionSpec.pageSpring()
    val slideSpec = MotionSpec.pageSpring(IntOffset.Zero)

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = fadeInSpec),
            exit = fadeOut(animationSpec = fadeOutSpec)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
        }
        AnimatedVisibility(
            visible = visible,
            // 面板从下方轻轻升起并缩放到位：比单纯缩放更有"被拿起来"的重量感。
            enter = fadeIn(animationSpec = fadeInSpec) + scaleIn(
                animationSpec = scaleSpec,
                initialScale = 0.92f
            ) + slideInVertically(
                animationSpec = slideSpec,
                initialOffsetY = { it / 8 }
            ),
            exit = fadeOut(animationSpec = fadeOutSpec) + scaleOut(
                animationSpec = scaleSpec,
                targetScale = 0.95f
            ) + slideOutVertically(
                animationSpec = slideSpec,
                targetOffsetY = { it / 10 }
            ),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(panelModifier) { content() }
        }
    }
}
