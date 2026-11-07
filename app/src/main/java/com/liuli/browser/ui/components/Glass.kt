package com.liuli.browser.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangularShape
import com.liuli.browser.ui.liquid.GlassColorLayer
import com.liuli.browser.ui.liquid.GlassEdgeStyle
import com.liuli.browser.ui.liquid.GlassMaterial
import com.liuli.browser.ui.liquid.colorBlend
import com.liuli.browser.ui.liquid.GlassShape
import com.liuli.browser.ui.liquid.InteractiveHighlight
import com.liuli.browser.ui.liquid.LiquidIconButton
import com.liuli.browser.ui.liquid.LiquidTextAction
import com.liuli.browser.ui.liquid.pressScale
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 液态玻璃容器。
 *
 * 默认它只是展示容器：背景模糊 + 静态高光，按下时完全静止。
 * 想要"被按到"的实感，有两种开启方式：
 * - 传入 [onClick]：整块玻璃变成可点击卡片，按下缩小回弹，
 *   同时玻璃层跟随手指轻微形变、指下泛起高光；
 * - 只把 [interactive] 置为 true：保留卡片内部子组件自己的点击，
 *   仅让玻璃层跟随手指（适合卡片里已经放了按钮的场景）。
 */
@Composable
fun GlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape.squircle(Dimens.cardRadius),
    tint: Color? = null,
    lensHeight: Dp = 16.dp,
    lensAmount: Dp = 24.dp,
    shadow: Boolean = true,
    highlight: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** 是否描出边缘。玻璃件没有轮廓会整个融进背景，深色下尤其明显。 */
    edgeStroke: Boolean = true,
    /**
     * 边缘光的样式。
     *
     * 玻璃的边不该是一圈等宽的线：等宽描边看上去像贴了一条边，
     * 而玻璃是有厚度的，边缘应当被光照出方向。
     * 这里用对向双光源——一侧主光、一侧弱副光——
     * 玻璃于是有了"哪边朝光"的知觉。
     */
    edgeStyle: GlassEdgeStyle = GlassEdgeStyle(),
    /** 是否画内阴影。有内阴影的玻璃才像一块有厚度的板，而不是一层贴图。 */
    innerShadow: Boolean = false,
    /**
     * 玻璃的材质：模糊半径与色层打包在一起。
     *
     * 给了它，半径与色层就都定了，调用处没法只改一半。
     *
     * 不给时用 [blurRadius] 与 [blendLayers]——
     * 素玻璃（只有模糊与折射）用不着包一层。
     */
    material: GlassMaterial? = null,
    /**
     * 着色强度，0..1。
     *
     * 它只改**着色**，模糊与折射一点不动。
     * 这与"整块 alpha 淡入淡出"是两件事：
     * 后者把模糊和折射一起淡掉，玻璃塌成一块普通半透明色块；
     * 前者让玻璃的底色自己变化，透出来的层次还在。
     *
     * Luna 就是这么分的：amount 乘进每层色自己的 alpha
     * （`defpackage/z02.java:94`），由调用方按动画量给
     * （`defpackage/vv4.java:943` 把一个 0..1 的动画量乘上去）。
     */
    blendAmount: Float = 1f,
    /**
     * 模糊半径。
     *
     * 只有在不给 [material] 时才用它。
     *
     * 这个值**不按玻璃尺寸缩放**（Luna 也不缩放，
     * `defpackage/fd5.java:48` 只受 150dp 的绝对上限约束）。
     * 缩放的后果是同一块玻璃换个内容就换了一副样子：
     * 菜单少两项时面板矮，模糊跟着变小，色层却还是按大模糊调的那一档，
     * 轻模糊配重色层，玻璃发浑。
     */
    blurRadius: Dp = 18.dp,
    /**
     * 混色层。
     *
     * 空列表时玻璃只有模糊与折射；给了层数，身后的内容会被按这些模式重新混合一遍，
     * 亮处更亮、暗处更暗，内容的层次得以保留——
     * 只铺一层等 alpha 的色纱会把背后的一切都洗成牛奶。
     *
     * 混色在折射与模糊**之后**进行：你看到的是折射后的画面，
     * 着色得作用在那个画面上，否则边缘的色会歪掉。
     *
     * 要用带色层的玻璃，从 [GlassMaterials] 取整份材质，别拆开手写——
     * 拆开就可能配错档，那正是本项目此前踩过的坑。
     */
    blendLayers: List<GlassColorLayer> = emptyList(),
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalLiuliColors.current
    // 材质优先：给了它，半径与色层都是它的，调用处改不了。
    val effectiveBlurRadius = material?.blurRadius ?: blurRadius
    val effectiveBlendLayers = material?.layers ?: blendLayers
    val requestedTint = tint ?: colors.glassTint
    // 着色强度只压暗**色**，不碰模糊与折射。
    // 玻璃的"透"由模糊与折射决定，"色"由 tint 与混色层决定，两者是分开的。
    val amount = blendAmount.coerceIn(0f, 1f)
    val effectiveTint = if (isRenderEffectSupported()) {
        requestedTint.copy(alpha = requestedTint.alpha * amount)
    } else {
        // 不支持 RenderEffect 时玻璃退化成半透明色块，
        // 这时颜色得自己扛住对比度，不能跟着强度一起淡掉。
        requestedTint.copy(alpha = max(requestedTint.alpha * amount, if (colors.isDark) 0.90f else 0.88f))
    }
    // 折射着色器只认圆角形状，喂给它别的形状会直接抛异常。
    // 玻璃件统一用圆角形状，这里再兜一次底，免得调用方踩到崩溃。
    val lensEnabled = lensHeight > 0.dp && lensAmount > 0.dp && shape.supportsLens()

    // 采样环的上限：玻璃到"有内容的地方"还有多远。
    //
    // 采样环是模糊核伸到玻璃外的那一圈真实内容（Backdrop 的 `padding`）。
    // 环伸到窗口外就没有内容了——那里是空白，混进来是透明，
    // 玻璃的边缘会淡成半透明，比拉散还难看。
    //
    // 位置每次布局后才知道（玻璃会被动画带着走），所以用 onGloballyPositioned 量。
    // 存成 mutableFloatStateOf 而不是普通 state：它只在**绘制**时被读，
    // 位置变了只重画、不重组（地址栏在顶/底之间移动时每帧位置都变）。
    val marginPx = remember { mutableFloatStateOf(0f) }

    // 有 onClick 定开启液态跟随，interactive 单独开启则只跟随不接管点击
    val liquidEnabled = interactive || onClick != null
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val interactionSource = remember { MutableInteractionSource() }
    // 按压回弹放在最外层，整块玻璃（含内容）一起缩小
    val pressModifier = if (onClick != null) {
        Modifier.pressScale(pressedScale = 0.96f, interactionSource = interactionSource)
    } else {
        Modifier
    }
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick
        )
    } else {
        Modifier
    }
    // 高光与手势放在 clickable 之后，保证手指移动时仍能跟到玻璃层
    val liquidModifier = if (liquidEnabled) {
        Modifier
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
    } else {
        Modifier
    }

    Box(
        modifier
            .then(pressModifier)
            .onGloballyPositioned { coordinates ->
                // 玻璃到"有内容的地方"还有多远：
                // 背景层只铺满窗口，玻璃贴着窗口边时，
                // 采样环伸出去的那一段是空白，混进来是透明。
                //
                // 四边取最近的一边——任何一边越界，那一圈就不完整。
                //
                // "有内容的地方"就是组合的根：背景层铺的正是它。
                val bounds = coordinates.boundsInWindow()
                val rootBounds = generateSequence(coordinates) { it.parentLayoutCoordinates }
                    .last()
                    .boundsInWindow()
                val margin = minOf(
                    bounds.left - rootBounds.left,
                    bounds.top - rootBounds.top,
                    rootBounds.right - bounds.right,
                    rootBounds.bottom - bounds.bottom
                ).coerceAtLeast(0f)
                if (margin != marginPx.floatValue) {
                    marginPx.floatValue = margin
                }
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    if (isRenderEffectSupported()) {
                        // 这里的**书写顺序就是生效顺序**：每加一个效果，
                        // 它都套在已有效果的外面（Backdrop 的 chain 是"新的在外层"）。
                        // 于是最后写的那个看到的是前面所有处理完的画面。
                        //
                        // 模糊半径**不按玻璃尺寸缩小**。
                        //
                        // Luna 也不这么做：它把半径当成材质的一部分
                        // （`defpackage/fd5.java:48`：`clamp(dp, 0, 150) * density`），
                        // 唯一的上限是 150dp 的绝对值，与形状多大无关。
                        //
                        // 边缘会不会被拉散也不靠缩小半径来避免，而是靠
                        // "把录到的背景向外扩一圈、让那一圈取到真实的邻接像素"。
                        // Backdrop 库正是这么做的：`blur()` 会把 `padding` 设成半径
                        // （`effects/Blur.kt:17`），录制的图层因此比形状大一圈
                        // （`DrawBackdropModifier.kt:293`），多出来的部分是真实的邻接内容。
                        //
                        // 之前这里按"最短边的 35%"夹一次，结果是**同一个浮层换个内容
                        // 就换了一副样子**：菜单少两项时面板矮，模糊跟着变小，
                        // 而色层仍是按 60dp 调的那一档——轻模糊配重色层，玻璃发浑。
                        // 现在按材质给的值走，色层与半径始终是同一档。
                        // 采样环：模糊核会伸到玻璃外多远，就得由多宽的**真实内容**垫着，
                        // 否则核只能取到记录层的边缘像素，玻璃边上就拉出一条散边。
                        //
                        // 库里 blur() 把 padding 设成半径（`effects/Blur.kt:16-20`），
                        // 正是这个意思；但 lens() 又按折射高度把它扣掉
                        // （`effects/Lens.kt:25-27`），环于是缩到 blurRadius - lensHeight，
                        // 模糊核伸到环外——散边就是这么来的。
                        //
                        // 这里把折射要扣的量先垫回去，lens() 减完，环正好回到模糊半径。
                        //
                        // 但环也不能越过窗口：窗外没有内容，混进来是透明，
                        // 玻璃的边缘会淡成半透明，比散边还难看。
                        // 所以环宽取"模糊半径"与"玻璃到窗口边的距离"里小的那个。
                        val blurPx = effectiveBlurRadius.toPx()
                        val lensHeightPx = lensHeight.toPx()
                        val ringPx = min(blurPx, marginPx.floatValue)
                        if (blurPx > 0f) {
                            blur(blurPx)
                        }
                        if (lensEnabled) {
                            // 折射 band 的深度要从环里扣，先补回去，lens() 自己会减。
                            padding = ringPx + lensHeightPx
                            lens(
                                refractionHeight = lensHeightPx,
                                refractionAmount = lensAmount.toPx(),
                                chromaticAberration = true
                            )
                        }
                        // 混色排在最后：它要作用在**折射与模糊之后**的画面上。
                        // 你隔着玻璃看到的是被折射、被模糊过的内容，
                        // 着色若发生在那之前，边缘的色会跟着折射再歪一次。
                        if (effectiveBlendLayers.isNotEmpty()) {
                            colorBlend(effectiveBlendLayers, amount)
                        }
                    }
                },
                layerBlock = if (liquidEnabled) {
                    {
                        // 玻璃层跟随手指轻微形变：位移用 tanh 限幅，缩放做各向异性
                        val width = size.width
                        val height = size.height.coerceAtLeast(1f)
                        val maxDimension = size.maxDimension.coerceAtLeast(1f)
                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 3f.dp.toPx() / height, progress)

                        val maxOffset = size.minDimension.coerceAtLeast(1f)
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = 3f.dp.toPx() / height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX = scale + maxDragScale *
                            abs(cos(offsetAngle) * offset.x / maxDimension) *
                            (width / height).fastCoerceAtMost(1f)
                        scaleY = scale + maxDragScale *
                            abs(sin(offsetAngle) * offset.y / maxDimension) *
                            (height / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                highlight = if (highlight) {
                    // 边缘光取代等宽描边：描边每一处都一样亮，
                    // 玻璃看上去是平的；换成对向双光源后，
                    // 朝光的一侧亮、背光的一侧暗，边缘才有厚度和方向。
                    // 亮度不能给满：边缘光下方还压着一圈很淡的均匀描边
                    // （玻璃在暗处也有反射，只是弱），两者相加会把迎光侧烧成一片白。
                    { edgeStyle.toHighlight(alpha = if (colors.isDark) 0.5f else 0.62f) }
                } else {
                    null
                },
                innerShadow = if (innerShadow) {
                    {
                        // 内阴影压在下沿：光从上方来，厚玻璃的下沿会自己遮掉一点光，
                        // 有它面板才像一块有厚度的板，而不是一张贴图。
                        InnerShadow(
                            radius = 16.dp,
                            offset = DpOffset(0.dp, -16.dp),
                            color = Color.Black.copy(alpha = if (colors.isDark) 0.26f else 0.10f)
                        )
                    }
                } else {
                    null
                },
                shadow = if (shadow) {
                    {
                        Shadow(
                            radius = 20.dp,
                            color = Color.Black.copy(alpha = if (colors.isDark) 0.26f else 0.10f)
                        )
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    // 混色层已经把颜色算进画面里了，这里再铺一层色纱就成了双重着色：
                    // 背后内容的层次被洗掉，混色带来的那点对比也一并没了。
                    // 两者只能选一个——有混色就不铺纱。
                    if (effectiveBlendLayers.isEmpty()) {
                        drawRect(effectiveTint)
                    }
                    // 均匀描边：没有它，深色背景上的玻璃会整个"融"进去，
                    // 轮廓消失，只剩一团模糊的色块。
                    // 描边必须跟着形状走：玻璃是圆角矩形、边框却是直角矩形的话，
                    // 四个角会露出不属于玻璃的直角。
                    //
                    // 它现在只是一层很淡的"环境反射"：迎光侧的方向感由边缘光给，
                    // 这圈描边只负责把背光侧的那段边从背景里勾出来。
                    // 两边都画满的话，迎光侧会烧成一条纯白线，方向感反而没了。
                    if (edgeStroke) {
                        drawShapeOutline(
                            shape,
                            colors.glassBorder.copy(alpha = colors.glassBorder.alpha * 0.45f),
                            1.dp.toPx()
                        )
                    }
                }
            )
            .then(clickModifier)
            .then(liquidModifier)
            .padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * Kept as a thin adapter so the whole app uses the catalog's InteractiveHighlight
 * and Backdrop lens pipeline.
 *
 * 禁用态由底层的 Liquid 组件统一表达（图标转灰、底座退淡、不铺主色），
 * 这里不再额外压一层透明度：两处各管一半，
 * 同一屏里的图标键与胶囊键会灰得不一样。
 *
 * 触感反馈也由底层的 Liquid 组件给出：按钮被点本来就该有触感，
 * 不必每个调用处手动补一遍。调用处若已经自己补过（破坏性操作想给更重的一下），
 * 把 [haptic] 关掉，否则同一次点击会震两下。
 */
@Composable
fun GlassIconButton(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.minTouch,
    glyphSize: Dp = 22.dp,
    accent: Boolean = false,
    /** 破坏性操作：图标染上危险色。 */
    danger: Boolean = false,
    enabled: Boolean = true,
    /** 是否由组件自己给一次轻触反馈。 */
    haptic: Boolean = true
) {
    LiquidIconButton(
        glyph = glyph,
        description = contentDescription,
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        size = maxOf(size, Dimens.minTouch),
        glyphSize = glyphSize,
        selected = accent,
        danger = danger,
        enabled = enabled,
        haptic = haptic
    )
}

@Composable
fun GlassPillButton(
    label: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    accent: Boolean = false,
    /** 破坏性操作：文字与图标都染上危险色。 */
    danger: Boolean = false,
    enabled: Boolean = true,
    /** 是否由组件自己给一次轻触反馈。 */
    haptic: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp)
) {
    LiquidTextAction(
        label = label,
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        accent = accent,
        danger = danger,
        glyph = glyph,
        enabled = enabled,
        haptic = haptic,
        contentPadding = contentPadding
    )
}

/**
 * 沿着形状轮廓描边。
 *
 * 用矩形描边时，玻璃本身是圆角矩形、边框却是直角矩形，
 * 四个角上会露出不属于玻璃的直角，一眼就穿帮。
 */
private fun DrawScope.drawShapeOutline(shape: Shape, color: Color, width: Float) {
    val outline = shape.createOutline(size, layoutDirection, this)
    when (outline) {
        is Outline.Rounded -> drawPath(
            // RoundRect 在 1.12 里没有公开 topLeft/size/cornerRadius，
            // 直接用它拼路径最省事，也能保留四个角各自不同的圆角。
            path = Path().apply { addRoundRect(outline.roundRect) },
            color = color,
            style = Stroke(width = width)
        )
        is Outline.Generic -> drawPath(
            path = outline.path,
            color = color,
            style = Stroke(width = width)
        )
        else -> drawRect(
            color = color,
            style = Stroke(width = width)
        )
    }
}

/**
 * 折射着色器只认三类形状：库自带的 RoundedRectangularShape、
 * 绝对圆角与普通圆角（CornerBasedShape，CircleShape 也是这一类）。
 * 喂给它别的形状会抛 UnsupportedOperationException，
 * 玻璃件在加折射前先问一句：形状不支持就只留模糊与高光，
 * 总好过整个面板崩掉。
 */
private fun Shape.supportsLens(): Boolean =
    this is RoundedRectangularShape || this is CornerBasedShape

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    val colors = LocalLiuliColors.current
    Canvas(modifier.padding(horizontal = 8.dp).size(1.dp, 22.dp)) {
        drawRect(colors.textPrimary.copy(alpha = 0.12f))
    }
}
