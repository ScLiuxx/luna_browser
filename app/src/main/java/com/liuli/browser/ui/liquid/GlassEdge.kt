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
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangularShape
import kotlin.math.min
import kotlin.math.sqrt

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
 * 着色器算法来自 Luna 的 `MiBloomStrokeFilter`（描边高光那一支）：
 * 它不直接用形状的轮廓方向当"法线"，而是**从轮廓的距离场推出一个真正的斜面法线**——
 * 越靠近轮廓，法线越朝着侧向倾斜，像光线掠过一道被磨圆的边。
 * 这与"沿轮廓描一圈线"在数学上是两回事：前者有厚度，后者只是加粗的边。
 *
 * 距离场用的是连续圆角（超椭圆）版本：形状本身是连续圆角，
 * 而距离场若按正圆角算，四角的等距线会与真实轮廓错开一小段，
 * 边光在角上就会"浮"起来。
 */
@Immutable
data class GlassEdgeStyle(
    /** 边缘光带的宽度（dp）。带越宽，玻璃的斜面越厚。 */
    val width: Dp = 3.dp,
    /** 边缘光的柔化半径（dp）。它同时决定斜面的进深。 */
    val blurRadius: Dp = 1.5.dp,
    /** 主光方向（度）。0 = 正右方，90 = 正下方（屏幕坐标，y 向下）。 */
    val lightAngle: Float = 235f,
    /** 主光强度。 */
    val lightIntensity: Float = 1f,
    /** 副光方向（度）。与主光相对。 */
    val oppositeAngle: Float = 55f,
    /** 副光强度。 */
    val oppositeIntensity: Float = 0.45f,
    /**
     * 光照的集中程度。越大越锐，越小越散。
     *
     * 这是指数：亮度按"光与面的夹角"的这个次方衰减。
     */
    val falloff: Float = 2f,
    /**
     * 光的进深（z 分量）。
     *
     * 垂直于屏幕的深度决定光"从哪个高度斜切下来"：
     * 为 0 时光完全平行于屏幕，只照到正对着的边；
     * 负值则光偏向观众一侧，斜面朝上的那一段会被整片照亮。
     */
    val lightDepth: Float = -0.4f,
    /**
     * 底光强度。
     *
     * 底光是不分方向的淡反射，只负责把玻璃的边从背景里勾出来；
     * 方向感由两盏灯给。底光给满了，边就成了一条均匀的白线。
     */
    val baseIntensity: Float = 0.35f,
    /** 底色：整条边共有的那一份底光。 */
    override val color: Color = Color.White,
    /** 主光的颜色。 */
    val lightColor: Color = Color.White,
    /** 副光的颜色。 */
    val oppositeColor: Color = Color.White,
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
        // 连续圆角是形状自己的属性：形状是连续圆角而距离场按正圆角算，
        // 四角的等距线会与真实轮廓错开，边光在角上会浮起来。
        shader.setFloatUniform("smoothing", shape.edgeSmoothing())
        // 着色器用 alpha 表达亮度，颜色本身保持不透明，
        // 否则叠加时颜色会被自己的 alpha 二次衰减。
        shader.setColorUniform("color", color.copy(alpha = 1f))
        shader.setFloatUniform("baseIntensity", baseIntensity)
        shader.setFloatUniform("light1", lightDirection(lightAngle, lightIntensity))
        shader.setFloatUniform("light1Color", lightColor.red, lightColor.green, lightColor.blue)
        shader.setFloatUniform("light2", lightDirection(oppositeAngle, oppositeIntensity))
        shader.setFloatUniform("light2Color", oppositeColor.red, oppositeColor.green, oppositeColor.blue)
        shader.setFloatUniform("falloff", falloff)
        // 描边以轮廓为中心向两侧各铺一半，又被形状裁掉外侧那一半，
        // 所以形状内实际能看到的深度就是描边宽度本身。
        val visibleDepth = min(with(this) { width.toPx() }, min(size.width, size.height) / 2f)
        shader.setFloatUniform("bandWidth", visibleDepth)
        // 斜面进深：柔化半径越大，边越"软"，法线倾斜得越缓。
        shader.setFloatUniform("innerDepth", maxOf(with(this) { blurRadius.toPx() }, 0.5f))
        return shader
    }

    /**
     * 光的方向向量与强度。
     *
     * 屏幕平面内的方位角 + 垂直于屏幕的进深。
     * 屏幕坐标 y 向下，而 z 朝向观众，因此进深为负表示"光偏向观众一侧"。
     * 第四个分量是强度。
     */
    private fun lightDirection(angle: Float, intensity: Float): FloatArray {
        val radians = angle * DEG_TO_RAD
        val x = kotlin.math.cos(radians)
        val y = kotlin.math.sin(radians)
        val length = sqrt(x * x + y * y + lightDepth * lightDepth)
        if (length < 1e-6f) return floatArrayOf(0f, 0f, 1f, intensity)
        return floatArrayOf(x / length, y / length, lightDepth / length, intensity)
    }

    companion object {
        private const val DEG_TO_RAD = 0.017453292f

        /**
         * 边缘光着色器。
         *
         * 三段算法：
         * 1. **距离场**：形状的有向距离场（SDF）。
         *    形状是矩形加四角半径，距离场可以解析求出，不必逐像素描边取样。
         *    连续圆角则要用超椭圆插值——形状本身是连续圆角，
         *    距离场若按正圆角算，四角会错开一小段。
         * 2. **斜面法线**：由距离场推出一个带深度的法线。
         *    越靠近轮廓，深度越浅，法线越朝侧向倾斜；
         *    往里走深度变大，法线渐渐立起来正对观众。
         *    这一步是"边缘有厚度"与"边缘只是一条粗线"的分界。
         * 3. **两盏灯**：各自按法线与光向的夹角加权，再按次方衰减。
         *    背光的那一段仍然看得见，只是明显更暗。
         */
        private val SHADER_SOURCE = """
            uniform float2 size;
            uniform float4 cornerRadii;   // 左上, 右上, 右下, 左下
            uniform float smoothing;      // 0 = 正圆角, 1 = 连续圆角
            layout(color) uniform half4 color;
            uniform float4 light1;        // 方向 xyz, 强度 w
            uniform float3 light1Color;
            uniform float4 light2;
            uniform float3 light2Color;
            uniform float falloff;
            uniform float baseIntensity;  // 底光强度
            uniform float bandWidth;      // 光带深度（像素）
            uniform float innerDepth;     // 斜面进深（像素）

            // 圆角矩形的距离场，形状跨在以原点为中心的 [-b, b] 上。
            float roundedBoxSdf(float2 p, float2 b, float r) {
                float2 d = p - b + r;
                return min(max(d.x, d.y), 0.0) + length(max(d, float2(0.0))) - r;
            }

            // 超椭圆（连续圆角）的距离场。
            // tile 是超椭圆的"半轴"，取自圆角半径；
            // fit 是超椭圆在 45 度方向上的拟合系数。
            float supercircleSdf(float2 off, float tile) {
                float2 q = max(float2(0.0), (float2(tile) + off) / tile);
                float hi = max(q.x, q.y);
                float ratio = (hi == 0.0) ? 0.0 : clamp(min(q.x, q.y) / hi, 0.0, 1.0);
                float fit = ((((-0.7391197 * ratio + 2.4034927) * ratio + -2.490732) * ratio + 0.4768709) * ratio + 0.47478476);
                float len = length(q);
                float distBase = (len + 1.0) - 1.0 / (1.0 - ratio * ratio * clamp(len, 0.0, 1.0) * fit);
                return min(max(tile + off.x, tile + off.y), 0.0) + tile * (distBase - 1.0);
            }

            // 四角各自的半径：按所在象限取。
            float pickRadius(float2 local, float2 b) {
                float top = (local.x < b.x) ? cornerRadii.x : cornerRadii.y;
                float bottom = (local.x < b.x) ? cornerRadii.w : cornerRadii.z;
                return min((local.y < b.y) ? top : bottom, min(b.x, b.y));
            }

            float sdfShape(float2 p, float2 b, float r) {
                float box = roundedBoxSdf(p, b, r);
                float minHalf = min(b.x, b.y);
                if (smoothing <= 0.001 || r < 0.5 || (minHalf - r) <= 1.0) {
                    return box;
                }
                float tile = min(1.528665 * r, minHalf);
                return mix(box, supercircleSdf(p - b, tile), smoothing);
            }

            // 轮廓的有向距离场：内部为负。
            float silhouetteSdf(float2 local) {
                float2 b = size * 0.5;
                return sdfShape(abs(local - b), b, pickRadius(local, b));
            }

            // 斜面法线。
            //
            // 深度由距离场决定：贴着轮廓处深度最大，往里逐渐变小。
            // 把点从平面上抬起来，抬升高度取自深度，法线就是"抬起点"与
            // 斜面参考点之间的方向——于是轮廓附近法线朝侧向倾斜，
            // 往里则渐渐立起来。
            //
            // 折叠中心取 floor(size/2)：折线落在像素边界上，
            // 否则法线在两半之间的接缝处会跳一下。
            float3 bevelNormal(float2 local, float2 b, float sdf, float radius, float inner) {
                float2 offset = local - floor(b);
                float2 folded = abs(offset);
                float depth = smoothstep(-inner, 0.0, sdf);
                float3 point = float3(folded, -sqrt(max(inner * inner - depth * depth, 0.0)));

                float bevel = max(radius, inner);
                float2 reference = b - bevel;
                reference.x = min(reference.x, folded.x);
                reference.y = min(reference.y, folded.y);
                float2 toPoint = point.xy - reference;
                float len = length(toPoint);
                reference += ((len > 0.0001) ? toPoint / len : float2(0.0, 1.0)) * (bevel - inner);

                // 参考点之外是平的：那里法线正对观众。
                if (folded.x < reference.x || folded.y < reference.y) {
                    return float3(0.0, 0.0, -1.0);
                }

                float3 normal = normalize(point - float3(reference, 0.0));
                // 折到象限里的法线要按原象限还原朝向。
                normal.xy *= sign(offset);
                return normal;
            }

            // 一盏灯：按法线与光向的夹角加权，再按 falloff 次方衰减。
            float3 rimLight(float3 normal, float4 light, float3 lightColor) {
                float lit = clamp(dot(normal, light.xyz), 0.0, 1.0);
                float shaped = pow(lit, falloff);
                return lightColor * (shaped * light.w);
            }

            half4 main(float2 coord) {
                float2 b = size * 0.5;
                float radius = pickRadius(coord, b);
                float sdf = silhouetteSdf(coord);
                float inner = max(innerDepth, 0.5);

                // 光带：由外向内渐隐，贴着轮廓最亮。
                float band = smoothstep(-bandWidth, 0.0, sdf);

                float3 normal = bevelNormal(coord, b, sdf, radius, inner);
                // 底光：一圈不分方向的淡反射，给玻璃一个"有厚度"的底子。
                float3 rgb = color.rgb * (baseIntensity * band * band);
                // 两盏灯：方向感由它们给，底光只负责把边勾出来。
                rgb += rimLight(normal, light1, light1Color) * band;
                rgb += rimLight(normal, light2, light2Color) * band;

                // 着色器返回的是预乘颜色：alpha 必须与颜色同量级，
                // 否则叠加时颜色会被自己的 alpha 裁掉。
                float alpha = clamp(max(max(rgb.r, rgb.g), rgb.b), 0.0, 1.0);
                return half4(half3(rgb), half(alpha));
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

/**
 * 形状的连续圆角程度：0 = 正圆角，1 = 连续圆角。
 *
 * 距离场要按形状自己的圆角方式算，否则边光在角上会与轮廓错开。
 * kyant 的 `RoundedCornerStyle` 只有两种取值，没有中间档，
 * 所以这里也只有 0 与 1。
 */
internal fun Shape.edgeSmoothing(): Float = when {
    this is RoundedRectangularShape -> when (style) {
        RoundedCornerStyle.Continuous -> 1f
        else -> 0f
    }
    else -> 0f
}
