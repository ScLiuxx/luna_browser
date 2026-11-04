package com.liuli.browser.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.reduceMotion
import kotlinx.coroutines.delay

/**
 * 玻璃对话框容器。
 *
 * 此前设置页的"清除数据""撤销授权"与隐私说明都是硬 `if` 挂载，
 * 出现与消失没有任何过渡，遮罩是"啪"地盖上去的。这里统一成：
 * 遮罩淡入淡出、面板从下方升起并缩放，两者互不干扰。
 *
 * [visible] 由调用方持有，这样关闭时才有退场动画；
 * 面板内容由 [content] 提供。
 *
 * @param maxPanelWidth 面板整体的最大宽度（含内边距）。
 *   对话框在手机上不该顶满屏幕，在平板上也不该拉成一条横幅。
 *   传 null 表示由 [panelModifier] 自己决定宽度。
 */
@Composable
fun LiquidDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    panelModifier: Modifier = Modifier,
    maxPanelWidth: Dp? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalLiuliColors.current
    val reduced = reduceMotion
    val density = LocalDensity.current

    // 规格统一从 MotionSpec 的组合版入口取：它内部已经按全局开关降级。
    // 这里再手动挑时长就成了两套逻辑，减动效开关也就名存实亡。
    val fadeInSpec = MotionSpec.enter()
    val exitSpec = MotionSpec.exit()
    val scaleInSpec = MotionSpec.pageSpring()
    val slideInSpec = MotionSpec.pageSpring(IntOffset.Zero)

    // 面板升起的高度是固定值，不按面板自身高度取比例：
    // 按比例算的话，矮对话框几乎不动、高对话框要飞越大半屏，手感完全不同。
    val rise = with(density) { 32.dp.roundToPx() }

    // 面板从下方轻轻升起并缩放到位：比单纯缩放更有"被拿起来"的重量感。
    // 减少动态效果开启时退化为纯淡入淡出：缩放与位移纯属装饰，
    // 对动效敏感的人没有收益，只会让人不适。
    val panelEnter = if (reduced) {
        fadeIn(animationSpec = fadeInSpec)
    } else {
        fadeIn(animationSpec = fadeInSpec) +
            scaleIn(animationSpec = scaleInSpec, initialScale = 0.94f) +
            slideInVertically(animationSpec = slideInSpec, initialOffsetY = { rise })
    }
    // 退场用补间而不是弹簧：弹簧在消失时会回弹一下，像是不舍得走。
    // 关闭动作要干脆，拖泥带水的退场会让"取消"显得迟疑。
    val panelExit = if (reduced) {
        fadeOut(animationSpec = exitSpec)
    } else {
        fadeOut(animationSpec = exitSpec) +
            scaleOut(animationSpec = exitSpec, targetScale = 0.96f)
    }

    // 退场期间仍然吞掉返回键。
    // 面板还在淡出时返回键就失效的话，这一下会穿透到下面的页面，
    // 用户明明只想关掉对话框，却把整页也退掉了。
    var exiting by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            exiting = false
        } else {
            exiting = true
            try {
                delay(ExitGuardMillis)
            } finally {
                // 清理必须放在 finally 里：协程随时可能被取消（对话框重新打开），
                // 不放 finally 的话 exiting 会永远停在 true，之后返回键就再也关不掉对话框。
                exiting = false
            }
        }
    }

    // 系统返回键此前关不掉对话框：只有点遮罩一条路，
    // 用户按返回却没有任何反应，只能在屏幕上乱点。
    BackHandler(enabled = visible || exiting) {
        if (visible) onDismiss()
    }

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = fadeInSpec),
            exit = fadeOut(animationSpec = exitSpec)
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
                Modifier
                    // 宽度上限放在最外层：它管的是"整个对话框有多宽"，
                    // 而不是"内边距里的内容有多宽"。
                    // 放在内层的话，实际总宽还要再加两遍内边距。
                    .then(
                        maxPanelWidth?.let { Modifier.widthIn(max = it) } ?: Modifier
                    )
                    .then(panelModifier)
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

/**
 * 退场期间吞掉返回键的时长。
 *
 * 略长于退场动画本身：这段时间里对话框还在屏幕上，
 * 返回键理应属于它。
 */
private val ExitGuardMillis = Motion.Normal.toLong()
