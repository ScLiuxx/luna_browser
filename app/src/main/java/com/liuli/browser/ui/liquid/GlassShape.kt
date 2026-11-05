package com.liuli.browser.ui.liquid

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle

/**
 * 玻璃件的形状。
 *
 * 圆角有两种：正圆角与连续圆角（squircle）。
 * 正圆角是圆弧接直线，转角处曲率突变，玻璃看上去"削"了一下；
 * 连续圆角让曲率平滑过渡，看着更像一整块被磨圆的玻璃。
 *
 * 以前本项目只能用正圆角，因为 Compose 自带的 `RoundedCornerShape`
 * 只会画圆弧。kyant 的 shapes 库已经内置了连续圆角实现
 * （`RoundedCornerStyle.Continuous`），直接用即可，不必自己写 SDF。
 *
 * 连续圆角同样能被 Backdrop 的折射效果识别：
 * 折射只关心四角半径，而 `RoundedRectangle` 正好实现的是
 * `RoundedRectangularShape`（Backdrop 优先识别这个接口）。
 */
object GlassShape {

    /**
     * 连续圆角的矩形。
     *
     * 只支持四角同半径——玻璃件的圆角从来都是同一个值
     * （卡片 26dp、面板 30dp、地址栏半高），真要不等半径时
     * 用 `RoundedCornerShape` 换回正圆角即可。
     */
    fun squircle(radius: Dp): Shape = RoundedRectangle(radius, RoundedCornerStyle.Continuous)
}
