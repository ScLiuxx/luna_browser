package com.liuli.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(MotionSpec.enter()),
            exit = fadeOut(MotionSpec.exit())
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
            enter = fadeIn(MotionSpec.enter()) + scaleIn(
                animationSpec = MotionSpec.pageSpring(),
                initialScale = 0.92f
            ),
            exit = fadeOut(MotionSpec.exit()) + scaleOut(
                animationSpec = MotionSpec.pageSpring(),
                targetScale = 0.95f
            ),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(panelModifier) { content() }
        }
    }
}
