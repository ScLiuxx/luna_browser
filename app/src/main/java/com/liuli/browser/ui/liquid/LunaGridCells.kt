package com.liuli.browser.ui.liquid

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * 网格的列宽策略：随可用宽度自适应，但**至少两列**。
 *
 * Luna 的网格不是"按屏幕宽度选一个固定列数"，而是给每列定一个**最小宽度**，
 * 再由可用宽度算出能放几列（`defpackage/cwc.java` 的 `mo4538a`）：
 *
 * ```java
 * int iMax = Math.max((i + i2) / (ko3Var.mo1858O0(this.f9324a) + i2), 2);
 * ```
 *
 * 拆开看：
 * - `i` = 可用宽度（px），`i2` = 列间距（px）；
 * - `mo1858O0(f9324a)` = 最小列宽从 dp 换算成 px（`f9324a` 是 135f 或 170f）；
 * - 外层 `Math.max(..., 2)` = **列数下限是 2**。
 *
 * 列宽有余数时，前面的列各多拿 1px，余数给靠前的列
 * （`cwc.java:26`：`arrayList.add((i6 < i5 ? 1 : 0) + i4)`）。
 *
 * "至少两列"这一条是它与 Compose 自带 `GridCells.Adaptive` 的唯一区别：
 * 自带那个下限是 1（`GridCells$Adaptive` 里 `Math.max(II)` 的第二个参数是 1），
 * 窄屏上会退成一列。一列的网格读起来不是网格，是列表——
 * 标签页网格退成一列，用户会以为点错了。
 *
 * @param minSize 单列的最小宽度。
 * @param minColumns 列数下限。
 */
class LunaGridCells(
    private val minSize: Dp,
    private val minColumns: Int = 2
) : GridCells {

    /**
     * Compose 的 `GridCells` 把密度换算放在签名里（`Density` 是接收者），
     * 所以这里直接用接收者上的换算，不必自己拿 density 传进来。
     */
    override fun Density.calculateCrossAxisCellSizes(
        availableSize: Int,
        spacing: Int
    ): List<Int> {
        // 最小列宽先换算成 px 再参与除法，与 Luna 的 mo1858O0 一致（它取整）。
        val minSizePx = minSize.roundToPx()
        // 列数 = (可用宽 + 间距) / (最小列宽 + 间距)。
        // 两边都加间距，是因为最后一列右侧没有间距，需要多借一格来抵消。
        val columns = maxOf(
            (availableSize + spacing) / (minSizePx + spacing),
            minColumns
        )
        // 余数分给靠前的列。
        val totalWidth = availableSize - spacing * (columns - 1)
        val baseWidth = totalWidth / columns
        val remainder = totalWidth % columns
        return List(columns) { index ->
            baseWidth + if (index < remainder) 1 else 0
        }
    }
}
