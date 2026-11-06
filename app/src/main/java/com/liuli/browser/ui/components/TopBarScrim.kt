package com.liuli.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liuli.browser.ui.theme.LocalLiuliColors

/**
 * 顶栏的渐隐遮罩。
 *
 * 网页内容会从顶栏底下滚过去。顶栏自己是一块玻璃，
 * 玻璃再透，内容与顶栏里的字仍然会叠在一起——糊成一片。
 *
 * Luna 的做法是给顶栏铺一层**竖直渐变**：上半部完全不透明，
 * 保证文字对比度；往下用四档非线性 alpha 快速消散，
 * 内容于是"溶"进顶栏，而不是被一条硬边切断。
 *
 * 渐变档位照搬 Luna 的实际取值（`defpackage/uk7.java:143`）：
 * 位置 0.00 / 0.45 / 0.58 / 0.72 / 0.86 / 1.00，
 * 对应 alpha 1.00 / 1.00 / 0.82 / 0.58 / 0.34 / 0.00。
 *
 * 前 45% 完全不透明是刻意的：那一段盖住的是状态栏与顶栏控件，
 * 文字必须清楚。再往下才开始淡，四档的间距不均匀
 * （0.58 → 0.72 → 0.86 → 1.00），是让消散"先慢后快"，
 * 顶部那条实线与下方渐隐之间才不会有可见的转折。
 */
@Composable
fun BoxScope.TopBarScrim(
    modifier: Modifier = Modifier,
    /** 渐变的高度。顶栏这一摞有多高就填多高。 */
    height: Dp,
    /** 遮罩的颜色。默认取页面背景色，与内容浑然一体。 */
    color: Color = LocalLiuliColors.current.pageBackground
) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.00f to color.copy(alpha = 1.00f),
                    0.45f to color.copy(alpha = 1.00f),
                    0.58f to color.copy(alpha = 0.82f),
                    0.72f to color.copy(alpha = 0.58f),
                    0.86f to color.copy(alpha = 0.34f),
                    1.00f to color.copy(alpha = 0.00f)
                )
            )
    )
}
