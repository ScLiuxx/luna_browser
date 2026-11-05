package com.liuli.browser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.liuli.browser.ui.liquid.GlassShape
import com.liuli.browser.ui.liquid.pressScale
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.Radius
import com.liuli.browser.ui.theme.Space
import com.liuli.browser.ui.theme.reduceMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class NavItem(
    val glyph: Glyph,
    val label: String,
    /**
     * 这一项是否可用。
     *
     * 不可用的项要同时做两件事：退成灰的，并且点不动。
     * 只退色不拦点击，用户会反复去点一个毫无反应的按钮；
     * 只拦点击不退色，用户根本看不出它为什么点不动。
     */
    val enabled: Boolean = true
)

/**
 * 侧栏宽度（常规档）。
 *
 * 系统字号放大时改用 [Dimens.railWidth] 的宽档：
 * 标签会跟着变大，固定宽度会把"标签页"挤成省略号，导航项就认不出来了。
 */
private val RailWidth = 74.dp

/**
 * 图标与文字之间的间距。
 *
 * 与底栏保持同一档：两处导航在同一个 App 里，
 * 间距差一像素也会让切换页面时觉得"哪里不一样"。
 */
private val RailIconLabelGap = 3.dp

/**
 * 品牌球的光源方向：固定在屏幕左上方。
 *
 * 屏幕 y 轴向下，所以是负角。球面的高光、暗面、焦散、边缘光
 * 全部由这一个方向推出来，光源一旦统一，球才立得住；
 * 各层各画各的，球就塌成一张平贴的渐变贴纸。
 */
private val BrandLightAngle = (-135.0 * PI / 180.0).toFloat()

/** 环绕高光绕行一圈的时长（毫秒）。慢到几乎察觉不到，只在余光里发亮。 */
private const val GlintPeriodMillis = 7_200f

/**
 * 环绕高光的推进节拍（毫秒）。
 *
 * 高光绕一圈要七秒多，一步只挪零点几个像素：
 * 三十帧与十五帧看起来没有区别，重绘次数却差一倍，
 * 而这颗球在侧栏与首页都是常驻的。
 */
private const val GlintFrameMillis = 66L

/** 朝白色提亮：受光面用，保留色相才不会在浅色背景上变成一块白斑。 */
private fun Color.towardWhite(fraction: Float): Color = Color(
    red = this.red + (1f - this.red) * fraction,
    green = this.green + (1f - this.green) * fraction,
    blue = this.blue + (1f - this.blue) * fraction,
    alpha = this.alpha
)

/** 朝黑色压暗：背光面收边用。 */
private fun Color.towardBlack(factor: Float): Color = Color(
    red = this.red * factor,
    green = this.green * factor,
    blue = this.blue * factor,
    alpha = this.alpha
)

@Composable
fun BrandOrb(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val colors = LocalLiuliColors.current
    val reduced = reduceMotion
    // 呼吸：球体每隔两秒涨落一次，幅度极小，
    // 只为让这颗静止的品牌标记有一点生命感。
    // 减少动态效果开启时循环根本不启动，球体纹丝不动。
    var breathTarget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            breathTarget = 0f
            return@LaunchedEffect
        }
        while (true) {
            delay(2400)
            breathTarget = if (breathTarget > 0.5f) 0f else 1f
        }
    }
    val breath by animateFloatAsState(
        targetValue = breathTarget,
        animationSpec = MotionSpec.value(Motion.Slow),
        label = "brand-orb-breath"
    )
    // 环绕高光：一粒极小的镜面反光绕着球面慢慢游走。
    // 固定的主高光说明"这是一颗玻璃球"，游走的这一点说明"它还活着"；
    // 它转到背光面时几乎熄灭，所以始终被读作同一盏灯的反光，
    // 而不是凭空多出来的一盏。
    var glintPhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            glintPhase = 0f
            return@LaunchedEffect
        }
        // 按固定节拍推进而不是逐帧推进：反光走得极慢，
        // 三十帧足够，省下的却是整屏 60 次重绘。
        var phase = 0f
        val step = GlintFrameMillis / GlintPeriodMillis
        while (true) {
            delay(GlintFrameMillis)
            phase += step
            if (phase >= 1f) phase -= 1f
            glintPhase = phase
        }
    }
    // 呼吸只做极小的涨缩：品牌标记在导航栏里是常驻元素，
    // 动得太明显会抢走内容的注意力。
    val swell = 1f + 0.02f * breath

    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = swell
                scaleY = swell
            }
    ) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        // 光源方向固定在左上，球面所有明暗都从它推出来。
        val lightDir = Offset(cos(BrandLightAngle), sin(BrandLightAngle))
        val lightCenter = Offset(
            center.x + lightDir.x * radius * 0.56f,
            center.y + lightDir.y * radius * 0.56f
        )
        // 背光面落在右下。
        val shadeCenter = Offset(
            center.x - lightDir.x * radius * 0.52f,
            center.y - lightDir.y * radius * 0.52f
        )
        // 玻璃球把穿过它的光聚到背面，形成一小块焦散亮斑。
        // 这一笔是"琉璃"与普通渐变圆点的分界。
        val causticCenter = Offset(
            center.x - lightDir.x * radius * 0.44f,
            center.y - lightDir.y * radius * 0.44f
        )
        // 主高光停在受光的肩部。
        val specularCenter = Offset(
            center.x + lightDir.x * radius * 0.40f,
            center.y + lightDir.y * radius * 0.40f
        )

        // 外发光：球体从背景里"浮"起来，而不是直接贴在背景上。
        drawCircle(
            brush = Brush.radialGradient(
                0f to colors.accent.copy(alpha = if (colors.isDark) 0.26f else 0.12f),
                0.5f to colors.accent.copy(alpha = if (colors.isDark) 0.10f else 0.05f),
                1f to Color.Transparent,
                center = center,
                radius = radius * 1.55f
            ),
            radius = radius * 1.55f,
            center = center
        )
        // 球面本体：以光源为圆心的球面渐变。
        // 渐变半径必须盖住整颗球，色标才落在可见范围里。
        // 早先深色那一档放在渐变末端，球外永远看不到，
        // 背光面只剩一片平涂的强调色，球就塌成了一个圆片。
        // 三档色相依次排开，正好把调色板里的主色、次色、三色串成一条彩石般的带子。
        drawCircle(
            brush = Brush.radialGradient(
                0f to colors.accentTertiary.towardWhite(0.88f),
                0.28f to colors.accentTertiary.towardWhite(0.72f),
                0.44f to colors.accentTertiary.towardWhite(0.28f),
                0.58f to colors.accentTertiary,
                0.76f to colors.accent,
                0.90f to colors.accentSecondary,
                1f to colors.accentSecondary.towardBlack(0.70f),
                center = lightCenter,
                radius = radius * 1.58f
            ),
            radius = radius,
            center = center
        )
        // 明暗交界：背光侧压出一片柔和的暗面。
        // 少了这一层，球面只剩一条从亮到暗的直带，看不出球。
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Black.copy(alpha = if (colors.isDark) 0.30f else 0.18f),
                0.45f to Color.Black.copy(alpha = if (colors.isDark) 0.11f else 0.07f),
                1f to Color.Transparent,
                center = shadeCenter,
                radius = radius * 1.2f
            ),
            radius = radius,
            center = center
        )
        // 地面反光：球体下缘接住一点来自下方的环境光，
        // 下沿才不会和背景一样平。
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = if (colors.isDark) 0.16f else 0.20f),
                0.5f to Color.White.copy(alpha = 0.05f),
                1f to Color.Transparent,
                center = Offset(center.x, center.y + radius * 0.66f),
                radius = radius * 0.6f
            ),
            radius = radius,
            center = center
        )
        // 焦散：穿过来的光在球体背面聚成一小块亮斑，
        // 球读起来才是透光的玻璃，而不是实心弹珠。
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.55f),
                0.45f to colors.accentTertiary.copy(alpha = 0.42f),
                1f to Color.Transparent,
                center = causticCenter,
                radius = radius * 0.3f
            ),
            radius = radius * 0.3f,
            center = causticCenter
        )
        // 边缘光：受光的那一侧边缘最亮，背光侧自然熄灭。
        // 早先这一圈不论方向都是白的，看着像贴纸的描边。
        drawCircle(
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.55f),
                0.45f to Color.White.copy(alpha = 0.16f),
                1f to Color.Transparent,
                start = Offset(center.x + lightDir.x * radius, center.y + lightDir.y * radius),
                end = Offset(center.x - lightDir.x * radius, center.y - lightDir.y * radius)
            ),
            radius = radius * 0.94f,
            center = center,
            style = Stroke(width = 1.1.dp.toPx())
        )
        // 主高光：受光肩部一块柔亮的椭圆，外加一个小硬芯。
        // 早先用"描边椭圆"当高光，那是一圈铁丝，不是光。
        // 椭圆压扁得厉害，宽窄比接近球面受光时的拉伸比，才像一层釉光。
        scale(scaleX = 0.34f, scaleY = 0.18f, pivot = specularCenter) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.55f),
                    0.55f to Color.White.copy(alpha = 0.16f),
                    1f to Color.Transparent,
                    center = specularCenter,
                    radius = radius * 0.8f
                ),
                radius = radius * 0.8f,
                center = specularCenter
            )
        }
        drawCircle(
            color = Color.White.copy(alpha = 0.8f),
            radius = radius * 0.11f,
            center = specularCenter
        )
        // 环绕高光：一粒会走的小反光。转到背光面就几乎熄灭，
        // 因此它永远被读作同一盏灯的反光，而不是第二盏灯。
        if (!reduced) {
            val glintAngle = glintPhase * 2f * PI.toFloat() - PI.toFloat() / 2f
            val glintCenter = Offset(
                center.x + cos(glintAngle) * radius * 0.55f,
                center.y + sin(glintAngle) * radius * 0.55f
            )
            val facing = 0.5f + 0.5f * cos(glintAngle - BrandLightAngle)
            val glintAlpha = 0.05f + 0.42f * facing * facing
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = glintAlpha),
                    0.5f to Color.White.copy(alpha = glintAlpha * 0.3f),
                    1f to Color.Transparent,
                    center = glintCenter,
                    radius = radius * 0.17f
                ),
                radius = radius * 0.17f,
                center = glintCenter
            )
        }
    }
}

@Composable
fun GlassNavigationRail(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onNewTab: () -> Unit,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    tabCount: Int = 0
) {
    val colors = LocalLiuliColors.current
    val density = LocalDensity.current
    val reduced = reduceMotion
    val haptics = LocalHapticFeedback.current
    // 侧栏宽度跟着系统字号走：大字号下标签会变宽，
    // 固定宽度会把"标签页"挤成省略号，导航项就认不出来了。
    val railWidth = if (density.fontScale > 1.3f) Dimens.railWidth else RailWidth
    // 导航项与选中指示器共用一个宽度：
    // 指示器比项目宽一点或窄一点，滑动时就对不齐。
    val itemWidth = railWidth - 16.dp
    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }
    val selectedBounds = itemBounds[selectedIndex]
    // 指示器位置与高度共用同一套页面级弹性，上下两端因此完全对称
    val indicatorTop by animateDpAsState(
        targetValue = selectedBounds?.top?.let { with(density) { it.toDp() } } ?: 0.dp,
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "rail-indicator-top"
    )
    val indicatorHeight by animateDpAsState(
        targetValue = selectedBounds?.height?.let { with(density) { it.toDp() } } ?: 0.dp,
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "rail-indicator-height"
    )
    // 出现与消失用互为镜像的进入/退出曲线，两端观感一致
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selectedBounds != null) 1f else 0f,
        animationSpec = if (selectedBounds != null) {
            MotionSpec.enter(Motion.Quick)
        } else {
            MotionSpec.exit(Motion.Quick)
        },
        label = "rail-indicator-alpha"
    )
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight(),
        shape = GlassShape.squircle(Radius.xl),
        tint = colors.dockTint,
        blurRadius = 26.dp,
        lensHeight = 22.dp,
        lensAmount = 26.dp,
        shadow = true
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 横屏或小屏时轨道高度吃紧：这时收紧图标、间距与留白，
            // 否则底部的"新建标签"会被挤出可视区，够不着。
            // 高屏则维持原样：导航项紧跟在品牌球下面，轨道读起来才是一段整体。
            // 阈值按常规布局的实际高度留了余量，大字号把标签撑高也不至于挤爆。
            val compact = maxHeight < 460.dp
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = if (compact) 10.dp else 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrandOrb(size = if (compact) 30.dp else 38.dp)
                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (indicatorHeight > 0.dp || indicatorAlpha > 0f) {
                        GlassSurface(
                            backdrop = backdrop,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = indicatorTop)
                                .width(itemWidth)
                                .height(indicatorHeight)
                                .graphicsLayer { alpha = indicatorAlpha },
                            shape = GlassShape.squircle(Radius.md),
                            tint = colors.accent.copy(alpha = if (colors.isDark) 0.22f else 0.13f),
                            blurRadius = 14.dp,
                            lensHeight = 12.dp,
                            lensAmount = 16.dp,
                            shadow = false
                        ) {}
                    }
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)
                    ) {
                        items.forEachIndexed { index, item ->
                            val isSelected = index == selectedIndex
                            val enabled = item.enabled
                            val itemInteraction = remember(item) { MutableInteractionSource() }
                            // 导航项较小，按压回缩得更明显一些
                            val itemPress = Modifier.pressScale(
                                pressedScale = 0.92f,
                                interactionSource = itemInteraction
                            )
                            // 选中与未选中之间平滑过渡，而不是瞬时换色
                            val iconTint by animateColorAsState(
                                targetValue = when {
                                    !enabled -> colors.textTertiary.copy(alpha = 0.5f)
                                    isSelected -> colors.accent
                                    else -> colors.textSecondary
                                },
                                animationSpec = MotionSpec.color(Motion.Quick),
                                label = "rail-icon-tint"
                            )
                            val labelTint by animateColorAsState(
                                targetValue = when {
                                    !enabled -> colors.textTertiary.copy(alpha = 0.5f)
                                    isSelected -> colors.accent
                                    else -> colors.textTertiary
                                },
                                animationSpec = MotionSpec.color(Motion.Quick),
                                label = "rail-label-tint"
                            )
                            Column(
                                modifier = Modifier
                                    .width(itemWidth)
                                    .clip(GlassShape.squircle(Radius.md))
                                    .onGloballyPositioned { coordinates ->
                                        itemBounds[index] = coordinates.boundsInParent()
                                    }
                                    .semantics {
                                        // 角标数字并进名字里：单独念一个"3"没有意义，
                                        // "标签，3 个标签页"才是完整的一句。
                                        contentDescription = if (item.glyph == Glyph.Tabs && tabCount > 0) {
                                            "${item.label}，$tabCount 个标签页"
                                        } else {
                                            item.label
                                        }
                                        role = Role.Button
                                        if (isSelected) {
                                            // 当前项要能被读屏直接听出来，
                                            // 光靠图标变个颜色是不够的。
                                            selected = true
                                            stateDescription = "已选中"
                                        }
                                        if (!enabled) {
                                            disabled()
                                        }
                                    }
                                    .then(itemPress)
                                    // 不用系统水波纹：液态玻璃语言下，指下高光由玻璃层自己给。
                                    .clickable(
                                        interactionSource = itemInteraction,
                                        indication = null,
                                        role = Role.Button,
                                        enabled = enabled,
                                        onClickLabel = item.label
                                    ) {
                                        if (!isSelected) {
                                            // 与底栏同一套触感：切换主导航要有一记轻震。
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        onSelect(index)
                                    }
                                    .padding(vertical = if (compact) Space.xs else Space.sm),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    GlyphIcon(
                                        glyph = item.glyph,
                                        tint = iconTint,
                                        size = 22.dp
                                    )
                                    if (item.glyph == Glyph.Tabs) {
                                        // 角标常驻组合，靠缩放控制显隐，新增标签时弹入。
                                        val badgeScale by animateFloatAsState(
                                            targetValue = if (tabCount > 0) 1f else 0f,
                                            animationSpec = MotionSpec.pageSpring(),
                                            label = "rail-badge-scale"
                                        )
                                        // 数量变了要有个动静：新增标签不该只是数字悄悄跳一下。
                                        var badgePulse by remember { mutableFloatStateOf(0f) }
                                        LaunchedEffect(tabCount, reduced) {
                                            if (reduced || tabCount <= 0) {
                                                // 归零也要在这里做：上一次脉冲若是在等待中被取消，
                                                // 标志会永远停在 1，角标就一直是被撑大的样子。
                                                badgePulse = 0f
                                                return@LaunchedEffect
                                            }
                                            badgePulse = 1f
                                            try {
                                                delay(100)
                                            } finally {
                                                // 清理必须放在 finally：
                                                // 等待期间标签数再变，协程会被取消，
                                                // 没有 finally 这一次脉冲就再也回不去了。
                                                badgePulse = 0f
                                            }
                                        }
                                        val pulse by animateFloatAsState(
                                            targetValue = badgePulse,
                                            animationSpec = MotionSpec.value(Motion.Quick),
                                            label = "rail-badge-pulse"
                                        )
                                        // 两位数以上角标要加宽，否则数字会被裁掉；
                                        // 早先一律显示 99，真实数量反而看不见了。
                                        val badgeText = if (tabCount > 99) "99+" else tabCount.toString()
                                        val badgeWidth = when {
                                            tabCount > 99 -> 22.dp
                                            tabCount > 9 -> 17.dp
                                            else -> 15.dp
                                        }
                                        Box(
                                            Modifier
                                                // 贴着图标的右上角，而不是靠固定偏移硬凑
                                                .align(Alignment.TopEnd)
                                                .offset(x = 6.dp, y = (-6).dp)
                                                .graphicsLayer {
                                                    val pulseScale = badgeScale * (1f + 0.24f * pulse)
                                                    scaleX = pulseScale
                                                    scaleY = pulseScale
                                                }
                                                .width(badgeWidth)
                                                .height(15.dp)
                                                .clip(GlassShape.capsule())
                                                .background(colors.accent),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (tabCount > 0) {
                                                BasicText(
                                                    text = badgeText,
                                                    style = TextStyle(
                                                        color = Color.White,
                                                        fontSize = if (tabCount > 9) 7.sp else 8.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    // 数字已并进外层的内容描述，
                                                    // 这里不要再重复播报一遍。
                                                    modifier = Modifier.clearAndSetSemantics {}
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(RailIconLabelGap))
                                BasicText(
                                    text = item.label,
                                    style = TextStyle(
                                        color = labelTint,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    maxLines = 1,
                                    // 大字号下宁可省略也不能换行：
                                    // 换行会把轨道撑高，底部的"新建标签"会被挤出可视区。
                                    overflow = TextOverflow.Ellipsis,
                                    // 名字已经并进外层的内容描述，
                                    // 这里清掉，避免读屏念两遍。
                                    modifier = Modifier.clearAndSetSemantics {}
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
                // 新建标签页此前是一块半透明的纯色圆片：没有模糊、没有折射，
                // 在一整列液态玻璃里显得是另一个世界的零件。改成玻璃件之后，
                // 它和导航栏终于用上了同一套材质。
                GlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier
                        // 不跟着紧凑模式缩小：可点区域低于 48dp 就该重新评估，
                        // 竖向省下的那几像素不值得拿触控目标去换。
                        .size(Dimens.minTouch)
                        .semantics {
                            contentDescription = "新建标签页"
                            role = Role.Button
                        },
                    // 50 表示短边的一半，圆形玻璃件；用圆角形状而不是 CircleShape，
                    // 折射着色器只认圆角形状。
                    shape = GlassShape.capsule(),
                    tint = colors.accent.copy(alpha = if (colors.isDark) 0.24f else 0.14f),
                    blurRadius = 12.dp,
                    lensHeight = 10.dp,
                    lensAmount = 14.dp,
                    contentAlignment = Alignment.Center,
                    onClick = {
                        // 触感只能跟着点击走：玻璃件没有把按下的状态暴露出来，
                        // 在按下那一刻给震动得另接一套手势监听，不值当。
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNewTab()
                    }
                ) {
                    GlyphIcon(Glyph.Plus, colors.accent, size = if (compact) 18.dp else 22.dp)
                }
            }
        }
    }
}
