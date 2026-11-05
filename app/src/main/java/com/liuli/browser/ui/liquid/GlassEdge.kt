package com.liuli.browser.ui.liquid

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.RuntimeShaderCache
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.shapes.RoundedRectangularShape
import kotlin.math.min

/**
 * 玻璃边缘光。
 *
 * 此前玻璃的边是一圈等宽描边：每一处亮度相同，边缘看上去是"平"的，
 * 像贴了一条线，而不是玻璃本身的厚度。
 *
 * 这里改成**对向双光源**：
 * - 主光从一侧斜照过来，照到的那段边最亮；
 * - 副光从对面照过来，亮度只有主光的一半，于是背光的那段边仍看得见，
 *   但明显更暗——边缘因此有了方向感；
 * - 边光不是一条等宽的线，而是贴着边缘、由外向内渐隐的一带，
 *   像玻璃被磨出的斜面。
 *
 * 实现走 Backdrop 的 `HighlightStyle`：`Highlight` 本来就在玻璃轮廓上
 * 描边、并按形状裁剪，把着色器换掉即可，不必自己搭一套裁剪与混合。
 */
@Immutable
data class GlassEdgeStyle(
    /** 边缘光带的宽度（dp）。带越宽，玻璃的斜面越厚。 */
    val width: Dp = 3.dp,
    /** 边缘光的柔化半径（dp）。 */
    val blurRadius: Dp = 1.5.dp,
    /** 主光方向（度）。0 = 正右方，90 = 正下方（屏幕坐标，y 向下）。 */
    val lightAngle: Float = 235f,
    /** 主光强度。 */
    val lightIntensity: Float = 1f,
    /** 副光方向（度）。与主光相对。 */
    val oppositeAngle: Float = 55f,
    /** 副光强度。 */
    val oppositeIntensity: Float = 0.45f,
    /** 光照的集中程度。越大越锐，越小越散。 */
    val falloff: Float = 1.5f,
    /** 边光颜色。 */
    override val color: Color = Color.White,
    /** 混合方式。叠加（Plus）才是"加光"，否则只是盖一层颜色。 */
    override val blendMode: BlendMode = BlendMode.Plus
) : HighlightStyle {

    /**
     * 造出挂在玻璃轮廓上的高光。
     *
     * 宽度与柔化半径必须与本样式一致：着色器里的光带宽度
     * 靠的就是描边宽度，两者对不上，光带会在边缘处错位。
     */
    fun toHighlight(alpha: Float): Highlight = Highlight.Default.copy(
        width = width,
        blurRadius = blurRadius,
        alpha = alpha,
        style = this
    )

    override fun DrawScope.createShader(
        shape: Shape,
        runtimeShaderCache: RuntimeShaderCache
    ): RuntimeShader? {
        // 运行期着色器要 API 33。没有它就退回无着色器的高光：
        // Backdrop 会用纯色描边，形状仍然正确，只是没有方向感。
        if (!isRuntimeShaderSupported()) return null

        val shader = runtimeShaderCache.obtainRuntimeShader("GlassEdge", SHADER_SOURCE)
        val radii = shape.edgeCornerRadii(size, layoutDirection, this)
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("cornerRadii", radii[0], radii[1], radii[2], radii[3])
        // 着色器用 alpha 表达亮度，颜色本身保持不透明，
        // 否则叠加时颜色会被自己的 alpha 二次衰减。
        shader.setColorUniform("color", color.copy(alpha = 1f))
        shader.setFloatUniform("angle1", lightAngle * DEG_TO_RAD)
        shader.setFloatUniform("angle2", oppositeAngle * DEG_TO_RAD)
        shader.setFloatUniform("intensity1", lightIntensity)
        shader.setFloatUniform("intensity2", oppositeIntensity)
        shader.setFloatUniform("falloff", falloff)
        // 描边以轮廓为中心向两侧各铺一半，又被形状裁掉外侧那一半，
        // 所以形状内实际能看到的深度就是描边宽度本身。
        val visibleDepth = min(with(this) { width.toPx() }, min(size.width, size.height) / 2f)
        shader.setFloatUniform("bandWidth", visibleDepth)
        return shader
    }

    companion object {
        private const val DEG_TO_RAD = 0.017453292f

        /**
         * 边缘光着色器。
         *
         * 轮廓的有向距离场（SDF）用圆角矩形求：形状是矩形加四角半径，
         * 距离场可以解析求出，不必逐像素描边取样。
         * 有了距离场就能同时得到两样东西：
         * - 梯度方向 = 该点的表面法线，用来判断这一段边朝向哪盏灯；
         * - 距离本身 = 该点离边缘多远，用来让光带从边缘往里渐隐。
         *
         * SDF 的辅助函数与 Backdrop 自带的高光着色器保持一致，
         * 免得另写一套带来偏差。
         */
        private val SHADER_SOURCE = """
            uniform float2 size;
            uniform float4 cornerRadii;
            layout(color) uniform half4 color;
            uniform float angle1;
            uniform float angle2;
            uniform float intensity1;
            uniform float intensity2;
            uniform float falloff;
            uniform float bandWidth;

            float radiusAt(float2 coord, float4 radii) {
                if (coord.x >= 0.0) {
                    if (coord.y <= 0.0) return radii.y;
                    else return radii.z;
                } else {
                    if (coord.y <= 0.0) return radii.x;
                    else return radii.w;
                }
            }

            float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
                float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
                float outside = length(max(cornerCoord, 0.0)) - radius;
                float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
                return outside + inside;
            }

            float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
                float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
                if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
                    return sign(coord) * normalize(max(cornerCoord, 0.0));
                } else {
                    float gradX = step(cornerCoord.y, cornerCoord.x);
                    return sign(coord) * float2(gradX, 1.0 - gradX);
                }
            }

            half4 main(float2 coord) {
                float2 halfSize = size * 0.5;
                float2 centeredCoord = coord - halfSize;
                float radius = radiusAt(centeredCoord, cornerRadii);
                float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
                float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);

                // 两盏灯分别从两个方向照过来，各自按法线朝向加权。
                float2 n1 = float2(cos(angle1), sin(angle1));
                float2 n2 = float2(cos(angle2), sin(angle2));
                float d1 = max(dot(grad, n1), 0.0);
                float d2 = max(dot(grad, n2), 0.0);
                float lit = pow(d1, falloff) * intensity1 + pow(d2, falloff) * intensity2;

                // 越往里越暗：这条带是玻璃的斜面，不是一条等宽的线。
                float sd = sdRoundedRect(centeredCoord, halfSize, radius);
                float band = smoothstep(-bandWidth, 0.0, sd);

                return color * (lit * band);
            }
        """.trimIndent()
    }
}

/**
 * 取形状四角的半径（像素），顺序为左上、右上、右下、左下。
 *
 * Backdrop 自带的取法只认 Compose 的 `CornerBasedShape`，
 * 碰上 kyant shapes 的连续圆角矩形会一律当成胶囊。
 * 这里自己取：先认 `RoundedRectangularShape`（连续圆角矩形），
 * 再认 `CornerBasedShape`（Compose 的圆角），最后按胶囊处理。
 */
internal fun Shape.edgeCornerRadii(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density
): FloatArray {
    if (this is RoundedRectangularShape) {
        val corners = corners(size, layoutDirection, density)
        return floatArrayOf(
            corners.topLeft,
            corners.topRight,
            corners.bottomRight,
            corners.bottomLeft
        )
    }
    if (this is CornerBasedShape) {
        val ltr = layoutDirection == LayoutDirection.Ltr
        val topStart = topStart.toPx(size, density)
        val topEnd = topEnd.toPx(size, density)
        val bottomEnd = bottomEnd.toPx(size, density)
        val bottomStart = bottomStart.toPx(size, density)
        val maxRadius = min(size.width, size.height) / 2f
        fun clamp(value: Float) = value.coerceAtMost(maxRadius)
        return if (ltr) {
            floatArrayOf(clamp(topStart), clamp(topEnd), clamp(bottomEnd), clamp(bottomStart))
        } else {
            floatArrayOf(clamp(topEnd), clamp(topStart), clamp(bottomStart), clamp(bottomEnd))
        }
    }
    val fallback = min(size.width, size.height) / 2f
    return floatArrayOf(fallback, fallback, fallback, fallback)
}
