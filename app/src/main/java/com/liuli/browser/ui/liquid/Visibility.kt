package com.liuli.browser.ui.liquid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 显隐动画的统一入口。
 *
 * Compose 为 `ColumnScope` / `RowScope` 各提供了一个 `AnimatedVisibility` 扩展，
 * 它们与顶层同名函数重载。放在 `LazyColumn` 的 `item {}` 里时，
 * 内层的 `LazyItemScope` 接收者会挡住外层的 `ColumnScope`，
 * 扩展版本的派发接收者拿不到，编译直接报
 * "cannot be called in this context with an implicit receiver"。
 *
 * 这里包一层顶层函数：函数体内没有任何布局作用域接收者，
 * 只会解析到顶层版本，调用点不受容器结构影响。
 */
@Composable
fun Visibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandIn(),
    exit: ExitTransition = shrinkOut() + fadeOut(),
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit
    ) {
        content()
    }
}
