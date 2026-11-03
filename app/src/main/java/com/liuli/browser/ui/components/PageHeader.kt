package com.liuli.browser.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Space
import com.liuli.browser.ui.theme.Type

/**
 * 页面标题块：书签、设置、标签三页共用。
 *
 * 此前三页各写一遍标题：字号在 display 之上再按平板/手机改一次
 * （26.sp / 32.sp），说明行是 label，行距靠手算。
 * 任何一处改动漏掉另外两处，三页的标题就会长短不一。
 * 这里收成一份，三页共用同一套字号、行距与结构。
 */
@Composable
fun ScreenHeader(
    title: String,
    isTablet: Boolean,
    modifier: Modifier = Modifier,
    /** 说明行：需要动画计数时自行组合，否则用 [HeaderSubtitle]。 */
    subtitle: (@Composable () -> Unit)? = null
) {
    val colors = LocalLiuliColors.current
    Column(modifier) {
        BasicText(
            text = title,
            // 手机档直接用 display 令牌，平板放大一档：
            // 令牌自己定住手机上的字号，三页就不会各写一遍。
            style = Type.display.copy(
                color = colors.textPrimary,
                fontSize = if (isTablet) 32.sp else Type.display.fontSize
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Spacer(Modifier.height(Space.xxs))
            subtitle()
        }
    }
}

/**
 * 标题下方的说明行。
 *
 * 三页的副标题都在同一个位置，字号与颜色也该一样：
 * 此前书签页与设置页用 label，标签页在空态时又换成别的写法。
 */
@Composable
fun HeaderSubtitle(text: String, modifier: Modifier = Modifier) {
    val colors = LocalLiuliColors.current
    BasicText(
        text = text,
        style = Type.label.copy(color = colors.textTertiary),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
