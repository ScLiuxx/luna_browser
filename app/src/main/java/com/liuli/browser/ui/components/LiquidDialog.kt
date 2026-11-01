package com.liuli.browser.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.unit.IntOffset
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.reduceMotion

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
    val colors = LocalLiuliColors.current
    val reduced = reduceMotion
    // 规格先在组合作用域算好：动画参数位不是 @Composable 上下文。
    val fadeInSpec = MotionSpec.enter(if (reduced) Motion.Fast else Motion.Normal)
    val fadeOutSpec = MotionSpec.exit(if (reduced) Motion.Fast else Motion.Quick)
    val scaleSpec = MotionSpec.pageSpring()
    val slideSpec = MotionSpec.pageSpring(IntOffset.Zero)
    // 面板从下方轻轻升起并缩放到位：比单纯缩放更有"被拿起来"的重量感。
    // 减少动态效果开启时退化为纯淡入淡出：缩放与位移纯属装饰，
    // 对动效敏感的人没有收益，只会让人不适。
    val panelEnter = if (reduced) {
        fadeIn(animationSpec = fadeInSpec)
    } else {
        fadeIn(animationSpec = fadeInSpec) +
            scaleIn(animationSpec = scaleSpec, initialScale = 0.92f) +
            slideInVertically(animationSpec = slideSpec, initialOffsetY = { it / 8 })
    }
    val panelExit = if (reduced) {
        fadeOut(animationSpec = fadeOutSpec)
    } else {
        fadeOut(animationSpec = fadeOutSpec) +
            scaleOut(animationSpec = scaleSpec, targetScale = 0.95f) +
            slideOutVertically(animationSpec = slideSpec, targetOffsetY = { it / 10 })
    }

    // 系统返回键此前关不掉对话框：只有点遮罩一条路，
    // 用户按返回却没有任何反应，只能在屏幕上乱点。
    BackHandler(enabled = visible) { onDismiss() }

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = fadeInSpec),
            exit = fadeOut(animationSpec = fadeOutSpec)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    // 遮罩取调色板里的 scrim，而不是写死的纯黑：
                    // 浅色主题下纯黑会糊成一片脏灰，看着像掉了一块阴影。
                    .background(colors.scrim.copy(alpha = if (colors.isDark) 0.46f else 0.26f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = panelEnter,
            exit = panelExit,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                panelModifier
                    // 面板自己吃掉点击：面板没消费点击时，命中测试会落到下层遮罩，
                    // 于是"点面板上的空白处也会把对话框关掉"。
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
            ) { content() }
        }
    }
}
