package com.liuli.browser.ui.liquid

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * 一份玻璃材质：模糊半径与色层是**一件东西**。
 *
 * Luna 就是这么组织的：`GlassMaterial(blurRadius, first, second, third)`
 * （`defpackage/hd5.java`），半径与三层色同属一个对象，
 * 换半径时色层跟着换，改不了配错。
 *
 * 本项目之前把两者拆开传（`blurRadius` 一个参数、`blendLayers` 另一个），
 * 于是"半径 60dp 却配了轻色层"这种错配在调用处无从察觉——
 * 色层是按某个半径调的，半径被改小，错配就悄悄发生。
 * 合成一个对象之后，取材质只能整份取，错配在源头就做不到。
 *
 * @param blurRadius 模糊半径。
 * @param layers 色层。空列表表示只有模糊与折射，没有混色。
 */
@Immutable
data class GlassMaterial(
    val blurRadius: Dp,
    val layers: List<GlassColorLayer> = emptyList()
) {
    companion object {
        /**
         * 只有模糊与折射的素玻璃。
         *
         * 绝大多数玻璃件要的就是这个：内容自己透过来，
         * 不额外调色。
         */
        fun plain(blurRadius: Dp): GlassMaterial = GlassMaterial(blurRadius)
    }
}

/**
 * 玻璃的材质预设。
 *
 * 数值来自 Luna 的实际调校结果，直接照搬：
 * 它的玻璃之所以看起来是"玻璃"而不是"磨砂片"，
 * 靠的就是这几层的组合方式，而不是模糊半径本身。
 *
 * Luna 的八套预设是「模糊半径 × 色层」的组合，而**用哪套是由场合定的**，
 * 不是由模糊半径定的（`defpackage/gv4.java:1105-1125`）：
 *
 * - 顶栏用 **a / b** 两套的**颜色**，但模糊半径另取 44.72dp（浅）/ 63.25dp（深）；
 * - 弹窗用 **c / d** 两套（自带 60dp 模糊）。
 *
 * 所以这里把**颜色**与**模糊半径**分开取：
 * 颜色按「顶栏 / 弹窗」两档给，模糊半径由调用方自己定。
 * 照搬成"模糊大就用重色层"是错的——Luna 的顶栏就是大模糊配轻色层。
 *
 * 另注：Luna 的模糊半径**不随玻璃尺寸缩放**
 * （`defpackage/fd5.java:48`：`clamp(dp, 0, 150) * density`），
 * 它只受一个绝对上限约束。本项目一度按"最短边的 35%"夹一次，
 * 结果同一个浮层换个内容就换了一副样子。
 */
object GlassMaterials {

    /**
     * 顶栏 · 浅色。
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
     * 弹窗 · 浅色。
     *
     * 与顶栏只差第三层：强光 40% 改成 60%。
     * 弹窗比顶栏"厚"——它整个盖住底下的内容，
     * 边界不该靠强光去压，得多给一层白才压得住底下的字。
     */
    val LightLarge: List<GlassColorLayer> = listOf(
        GlassColorLayer(Color.Black.copy(alpha = 0.020f), GlassBlendMode.PlusDarker),
        GlassColorLayer(Color.White.copy(alpha = 0.60f), GlassBlendMode.SoftLight),
        GlassColorLayer(Color.White.copy(alpha = 0.60f), GlassBlendMode.HardLight)
    )

    /**
     * 顶栏 · 深色。
     *
     * 深色下反过来：先减色 10% 把整体压下去，
     * 再用亮度混合把明度往中灰拉，最后叠一层遮罩把对比拉平。
     *
     * 亮度层用的是**中灰而不是纯黑**：亮度混合只取色层的明度，
     * 纯黑会把背后内容一路压到全黑，玻璃就成了一个洞；
     * 中灰则保留住"底下还有东西"的知觉。
     *
     * 注意颜色的 alpha 是**这一层的强度**（0x66 = 40%、0x99 = 60%），
     * 不是不透明：色层靠 alpha 决定混进去多少。
     */
    val Dark: List<GlassColorLayer> = listOf(
        GlassColorLayer(Color.Black.copy(alpha = 0.102f), GlassBlendMode.PlusDarker),
        GlassColorLayer(Color(0x66565656), GlassBlendMode.Luminosity),
        GlassColorLayer(Color(0x993F3F3F), GlassBlendMode.Overlay)
    )

    /**
     * 弹窗 · 深色。
     *
     * 亮度层要拉得更狠（60% 而不是 40%）才压得住，
     * 同时把遮罩换成近黑的 6%：
     * 深色玻璃底下透出的东西越少，遮罩就越要实。
     */
    val DarkLarge: List<GlassColorLayer> = listOf(
        GlassColorLayer(Color.Black.copy(alpha = 0.102f), GlassBlendMode.PlusDarker),
        GlassColorLayer(Color(0x996C6C6C), GlassBlendMode.Luminosity),
        GlassColorLayer(Color(0x99060606), GlassBlendMode.SoftLight)
    )

    /**
     * 顶栏那档（浅色 / 深色）。
     *
     * @param isDark 深色主题。
     */
    fun topBar(isDark: Boolean): List<GlassColorLayer> =
        if (isDark) Dark else Light

    /**
     * 弹窗那档（浅色 / 深色）。
     *
     * 弹窗比顶栏"厚"——它整个盖住底下的内容，
     * 底下的字不能透上来，所以色层给得更重。
     *
     * @param isDark 深色主题。
     */
    fun popup(isDark: Boolean): List<GlassColorLayer> =
        if (isDark) DarkLarge else LightLarge

    /**
     * 顶栏的整份材质。
     *
     * 色层取 Luna 预设 a / b，但**模糊半径另给**——
     * Luna 顶栏就是"轻色层 + 大模糊"
     * （`defpackage/gv4.java:1119` 把预设 a / b 的半径改成 44.72dp / 63.25dp，
     * 色层不动）。本项目的顶栏贴着屏幕顶边，采样环不能越出窗口，
     * 所以半径比它小——见调用处的说明。
     *
     * @param isDark 深色主题。
     * @param blurRadius 模糊半径。
     */
    fun topBarMaterial(isDark: Boolean, blurRadius: Dp): GlassMaterial = GlassMaterial(
        blurRadius = blurRadius,
        layers = topBar(isDark)
    )

    /**
     * 弹窗的整份材质。
     *
     * 预设 c / d 自带 60dp 模糊与配套的重色层
     * （`defpackage/id5.java`），两者是一套，整体取用。
     *
     * @param isDark 深色主题。
     */
    fun popupMaterial(isDark: Boolean): GlassMaterial = GlassMaterial(
        blurRadius = 60.dp,
        layers = popup(isDark)
    )
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
