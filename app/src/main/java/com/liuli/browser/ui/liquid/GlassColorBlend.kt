package com.liuli.browser.ui.liquid

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported

/**
 * 玻璃的混色模式。
 *
 * 玻璃不是一层半透明的色纸：它把身后的内容**按某种方式重新混合**一遍，
 * 于是亮处更亮、暗处更暗，内容的层次被保留下来。
 * 只铺一层等 alpha 的白纱做不到这一点——纱会把背后的一切都洗成牛奶。
 *
 * 这些模式与 Photoshop 那一套同名同义，编号与 Luna 的实现一一对应。
 */
enum class GlassBlendMode {
    /** 直接替换。 */
    SrcOver,

    /** 减色：把底色往下压，落地到黑而不是变灰。 */
    PlusDarker,

    /** 加色：往上叠，叠到白而不是溢出。 */
    PlusLighter,

    /** 柔光：底色暗处提得少、亮处压得多，对比保留。 */
    SoftLight,

    /** 强光：由色层决定亮暗，底色只提供"谁在上"的信息。 */
    HardLight,

    /** 叠加：与柔光相反，底色决定。 */
    Overlay,

    /** 亮度：只取色层的明度，色相与饱和度留给底色。 */
    Luminosity,

    /** 颜色减淡：底色越暗效果越强。 */
    ColorDodge,

    /** 颜色加深：底色越亮效果越强。 */
    ColorBurn
}

/**
 * 玻璃的一层颜色。
 *
 * @param color 这一层的颜色。[color] 的 alpha 即混合强度：
 *   0 = 完全不起作用，1 = 完全由这一层决定。
 * @param mode 混合方式。
 */
@Immutable
data class GlassColorLayer(
    val color: Color,
    val mode: GlassBlendMode = GlassBlendMode.SrcOver
)

/**
 * 玻璃的材质预设。
 *
 * 数值来自 Luna 的实际调校结果，直接照搬：
 * 它的玻璃之所以看起来是"玻璃"而不是"磨砂片"，
 * 靠的就是这几层的组合方式，而不是模糊半径本身。
 *
 * Luna 把材质分成**四档**，每档按模糊半径分组：
 * 小模糊（20dp）与大模糊（60dp），各有一浅一深。
 * 模糊越大，身后内容被糊得越彻底，色层就越要"补"回层次，
 * 于是两档的色层强度并不相同——不能只换模糊半径、照抄同一组颜色。
 */
object GlassMaterials {

    /**
     * 浅色 · 小模糊（20dp）。
     *
     * 三层各司其职：
     * - 先减色 2%：玻璃整体压一点，厚玻璃挡光；
     * - 再柔光叠白 60%：把背后内容提亮，但不冲掉它的层次；
     * - 最后强光叠白 40%：亮部再收一档，边界更清楚。
     */
    val Light: List<GlassColorLayer> = listOf(
        GlassColorLayer(Color.Black.copy(alpha = 0.020f), GlassBlendMode.PlusDarker),
        GlassColorLayer(Color.White.copy(alpha = 0.60f), GlassBlendMode.SoftLight),
        GlassColorLayer(Color.White.copy(alpha = 0.40f), GlassBlendMode.HardLight)
    )

    /**
     * 浅色 · 大模糊（60dp）。
     *
     * 模糊到 60dp 时背后内容几乎只剩色块，
     * 于是第三层从"强光收边界"换成"再叠 60% 白"：
     * 边界此时靠形状本身交代，不需要强光去压。
     */
    val LightLarge: List<GlassColorLayer> = listOf(
        GlassColorLayer(Color.Black.copy(alpha = 0.020f), GlassBlendMode.PlusDarker),
        GlassColorLayer(Color.White.copy(alpha = 0.60f), GlassBlendMode.SoftLight),
        GlassColorLayer(Color.White.copy(alpha = 0.60f), GlassBlendMode.HardLight)
    )

    /**
     * 深色 · 小模糊（20dp）。
     *
     * 深色下反过来：先减色 10% 把整体压下去，
     * 再用亮度混合把明度往中灰拉，最后叠一层遮罩把对比拉平。
     *
     * 亮度层用的是**中灰而不是纯黑**：亮度混合只取色层的明度，
     * 纯黑会把背后内容一路压到全黑，玻璃就成了一个洞；
     * 中灰则保留住"底下还有东西"的知觉。
     */
    val Dark: List<GlassColorLayer> = listOf(
        GlassColorLayer(Color.Black.copy(alpha = 0.102f), GlassBlendMode.PlusDarker),
        GlassColorLayer(Color(0xFF565656), GlassBlendMode.Luminosity),
        GlassColorLayer(Color(0xFF3F3F3F), GlassBlendMode.Overlay)
    )

    /**
     * 深色 · 大模糊（60dp）。
     *
     * 模糊极大时，亮度层要拉得更狠（60% 而不是 40%）才压得住，
     * 同时把遮罩换成近黑的 6%：
     * 深色玻璃底下透出的东西越少，遮罩就越要实。
     */
    val DarkLarge: List<GlassColorLayer> = listOf(
        GlassColorLayer(Color.Black.copy(alpha = 0.102f), GlassBlendMode.PlusDarker),
        GlassColorLayer(Color(0xFF6C6C6C), GlassBlendMode.Luminosity),
        GlassColorLayer(Color(0xFF060606), GlassBlendMode.SoftLight)
    )

    /**
     * 按主题与模糊档取预设。
     *
     * 调用处不必自己判断深浅色：深色下用错了浅色那组，
     * 玻璃会变成一块发白的脏纱，是最常见的"液态玻璃没做对"。
     */
    fun of(isDark: Boolean, large: Boolean = false): List<GlassColorLayer> = when {
        isDark && large -> DarkLarge
        isDark -> Dark
        large -> LightLarge
        else -> Light
    }
}

/**
 * 把一层层颜色按各自的混合方式混进背后的内容。
 *
 * 混色发生在**内容被折射与模糊之后**：
 * 玻璃呈现给人看的是折射后的画面，着色应当作用在那个画面上，
 * 否则着色会被折射再拉一遍，边缘的色会歪掉。
 *
 * 这一层由 AGSL 着色器完成，一次最多三层。
 * 着色器要 API 33；更低的版本上这个函数什么都不做，
 * 玻璃退回"只有模糊与折射"的朴素样子，不会崩。
 */
fun BackdropEffectScope.colorBlend(layers: List<GlassColorLayer>) {
    if (!isRuntimeShaderSupported()) return
    if (layers.isEmpty()) return
    // 着色器最多吃三层，超出的部分丢弃。
    val used = layers.take(MAX_LAYERS)
    runtimeShaderEffect(
        key = "GlassColorBlend",
        shaderString = COLOR_BLEND_SHADER,
        uniformShaderName = "child",
    ) {
        for (index in 0 until MAX_LAYERS) {
            val layer = used.getOrNull(index)
            val r = layer?.color?.red ?: 0f
            val g = layer?.color?.green ?: 0f
            val b = layer?.color?.blue ?: 0f
            val a = layer?.color?.alpha ?: 0f
            setFloatUniform("in_blend$index", r, g, b, a)
        }
        // 模式与层数打包在一个 float4 里：前三项是各层的模式编号，
        // 第四项是实际生效的层数——着色器靠它决定还要不要再读下一层。
        setFloatUniform(
            "in_blendMode",
            (used.getOrNull(0)?.mode?.ordinal ?: 0).toFloat(),
            (used.getOrNull(1)?.mode?.ordinal ?: 0).toFloat(),
            (used.getOrNull(2)?.mode?.ordinal ?: 0).toFloat(),
            used.size.toFloat()
        )
    }
}

/**
 * 混色着色器。
 *
 * 它读入的是"孩子"（上一层效果的结果，即折射模糊后的画面），
 * 逐层混合后再写回。混合前要 unpremultiply：
 * 预乘的颜色直接参与运算会算错——半透明的红与半透明的白相加，
 * 得到的不是"红加白"，而是"半透明的红加半透明的白"。
 */
private const val COLOR_BLEND_SHADER = """
    uniform shader child;

    uniform float4 in_blend0;      // 第 0 层：rgb + 强度
    uniform float4 in_blend1;
    uniform float4 in_blend2;
    uniform float4 in_blendMode;   // 各层模式 + 层数

    const float3 kLumWeights = float3(0.3, 0.59, 0.11);

    float lumOf(float3 c) { return dot(c, kLumWeights); }

    // 把颜色拉回 0..1 而不改变明度：
    // 不可分离的混合若直接裁剪，出来的色调会与算出来的那个不一样。
    float3 clipColor(float3 c) {
        float l = lumOf(c);
        float n = min(min(c.r, c.g), c.b);
        float x = max(max(c.r, c.g), c.b);
        if (n < 0.0) c = l + (c - l) * l / max(l - n, 0.0001);
        if (x > 1.0) c = l + (c - l) * (1.0 - l) / max(x - l, 0.0001);
        return c;
    }
    float3 setLum(float3 c, float l) { return clipColor(c + (l - lumOf(c))); }

    float softLightChannel(float d, float s) {
        float dd = (d <= 0.25) ? ((16.0 * d - 12.0) * d + 4.0) * d : sqrt(d);
        return (s <= 0.5)
            ? d - (1.0 - 2.0 * s) * d * (1.0 - d)
            : d + (2.0 * s - 1.0) * (dd - d);
    }

    float3 blendLayer(float3 d, float4 layer, float mode) {
        float3 s = layer.rgb;
        float a = layer.a;
        int m = int(mode + 0.5);
        float3 b;
        if (m == 1) {
            // 减色：往下压，压到黑，而不是压成灰。
            return clamp(d - a * (1.0 - s), 0.0, 1.0);
        } else if (m == 2) {
            // 加色：往上叠，叠到白，而不是溢出。
            return clamp(d + a * s, 0.0, 1.0);
        } else if (m == 3) {
            b = float3(
                softLightChannel(d.r, s.r),
                softLightChannel(d.g, s.g),
                softLightChannel(d.b, s.b)
            );
        } else if (m == 4) {
            // 强光：由色层决定。
            b = mix(1.0 - 2.0 * (1.0 - s) * (1.0 - d), 2.0 * s * d, step(s, float3(0.5)));
        } else if (m == 5) {
            // 叠加：由底色决定。
            b = mix(1.0 - 2.0 * (1.0 - d) * (1.0 - s), 2.0 * d * s, step(d, float3(0.5)));
        } else if (m == 6) {
            b = setLum(d, lumOf(s));
        } else if (m == 7) {
            b = min(float3(1.0), d / max(1.0 - s, float3(0.0001)));
        } else if (m == 8) {
            b = 1.0 - min(float3(1.0), (1.0 - d) / max(s, float3(0.0001)));
        } else {
            b = s;
        }
        return clamp(mix(d, b, a), 0.0, 1.0);
    }

    half4 main(float2 coord) {
        half4 src = child.eval(coord);
        float a = float(src.a);
        if (a <= 0.0) return src;
        float3 d = float3(src.rgb) / a;      // 去掉预乘
        d = blendLayer(d, in_blend0, in_blendMode.x);
        if (in_blendMode.w > 1.5) d = blendLayer(d, in_blend1, in_blendMode.y);
        if (in_blendMode.w > 2.5) d = blendLayer(d, in_blend2, in_blendMode.z);
        return half4(half3(d * a), src.a);    // 写回时补上预乘
    }
"""

/** 着色器一次最多吃三层。 */
private const val MAX_LAYERS = 3
