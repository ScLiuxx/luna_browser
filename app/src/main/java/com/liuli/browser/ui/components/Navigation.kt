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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.liuli.browser.ui.liquid.pressScale
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.Radius
import com.liuli.browser.ui.theme.Space
import com.liuli.browser.ui.theme.reduceMotion
import kotlinx.coroutines.delay

data class NavItem(
    val glyph: Glyph,
    val label: String
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
        // 高光位置用「圆心左上方 40%/35%」定义，而不是写死的像素偏移：
        // 尺寸变化时高光仍落在球体的左上肩部，符合球面受光直觉。
        // 呼吸时高光跟着轻轻游走，球面不会像一张静止的贴纸。
        val drift = breath * radius * 0.06f
        val highlightCenter = Offset(
            center.x - radius * 0.40f + drift,
            center.y - radius * 0.35f - drift * 0.6f
        )
        // 外发光：球体从背景里"浮"起来，而不是直接贴在背景上。
        drawCircle(
            brush = Brush.radialGradient(
                0f to colors.accent.copy(alpha = if (colors.isDark) 0.26f else 0.12f),
                0.5f to colors.accent.copy(alpha = if (colors.isDark) 0.10f else 0.05f),
                1f to Color.Transparent,
                center = center,
                radius = radius * 1.5f
            ),
            radius = radius * 1.5f,
            center = center
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.88f),
                0.22f to colors.accentTertiary,
                0.62f to colors.accent,
                1f to colors.accentSecondary,
                center = highlightCenter,
                radius = radius * 1.7f
            ),
            radius = radius,
            center = center
        )
        // 明暗交界：右下侧压暗。没有这一层，球体只剩一团平贴的渐变，
        // 看上去像贴纸而不是一个立体的球。
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Black.copy(alpha = if (colors.isDark) 0.30f else 0.16f),
                0.5f to Color.Black.copy(alpha = if (colors.isDark) 0.14f else 0.07f),
                1f to Color.Transparent,
                center = Offset(center.x + radius * 0.45f, center.y + radius * 0.55f),
                radius = radius * 1.35f
            ),
            radius = radius,
            center = center
        )
        // 边缘反光：内侧描一圈亮边，球体才有厚度而不是一张贴纸。
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Transparent,
                0.82f to Color.Transparent,
                1f to Color.White.copy(alpha = 0.30f),
                center = highlightCenter,
                radius = radius
            ),
            radius = radius,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.42f),
            radius = radius * 0.93f,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )
        // 高光椭圆：跟着同一个高光中心走，不再是固定偏移。
        drawOval(
            color = Color.White.copy(alpha = 0.34f),
            topLeft = Offset(highlightCenter.x - radius * 0.48f, highlightCenter.y - radius * 0.30f),
            size = androidx.compose.ui.geometry.Size(radius * 0.96f, radius * 0.46f),
            style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.65f),
            radius = radius * 0.16f,
            center = Offset(highlightCenter.x, highlightCenter.y)
        )
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
            .width(74.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(Radius.xl),
        tint = colors.dockTint,
        blurRadius = 26.dp,
        lensHeight = 22.dp,
        lensAmount = 26.dp,
        shadow = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandOrb(size = 38.dp)
            Spacer(Modifier.height(10.dp))
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
                            .width(58.dp)
                            .height(indicatorHeight)
                            .graphicsLayer { alpha = indicatorAlpha },
                        shape = RoundedCornerShape(Radius.md),
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
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items.forEachIndexed { index, item ->
                        val selected = index == selectedIndex
                        val itemInteraction = remember(item) { MutableInteractionSource() }
                        // 导航项较小，按压回缩得更明显一些
                        val itemPress = Modifier.pressScale(
                            pressedScale = 0.92f,
                            interactionSource = itemInteraction
                        )
                        // 选中与未选中之间平滑过渡，而不是瞬时换色
                        val iconTint by animateColorAsState(
                            targetValue = if (selected) colors.accent else colors.textSecondary,
                            animationSpec = MotionSpec.color(Motion.Quick),
                            label = "rail-icon-tint"
                        )
                        val labelTint by animateColorAsState(
                            targetValue = if (selected) colors.accent else colors.textTertiary,
                            animationSpec = MotionSpec.color(Motion.Quick),
                            label = "rail-label-tint"
                        )
                        Column(
                            modifier = Modifier
                                .width(58.dp)
                                .clip(RoundedCornerShape(Radius.md))
                                .onGloballyPositioned { coordinates ->
                                    itemBounds[index] = coordinates.boundsInParent()
                                }
                                .semantics {
                                    contentDescription = item.label
                                    role = Role.Button
                                }
                                .then(itemPress)
                                // 不用系统水波纹：液态玻璃语言下，指下高光由玻璃层自己给。
                                .clickable(
                                    interactionSource = itemInteraction,
                                    indication = null,
                                    role = Role.Button,
                                    onClickLabel = item.label
                                ) {
                                    onSelect(index)
                                }
                                .padding(vertical = Space.sm),
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
                                    Box(
                                        Modifier
                                            .offset(x = 11.dp, y = (-8).dp)
                                            .graphicsLayer {
                                                scaleX = badgeScale
                                                scaleY = badgeScale
                                            }
                                            .size(15.dp)
                                            .clip(CircleShape)
                                            .background(colors.accent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (tabCount > 0) {
                                            BasicText(
                                                text = tabCount.coerceAtMost(99).toString(),
                                                style = TextStyle(
                                                    color = Color.White,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            BasicText(
                                text = item.label,
                                style = TextStyle(
                                    color = labelTint,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // 新建标签页此前是一块半透明的纯色圆片：没有模糊、没有折射，
            // 在一整列液态玻璃里显得是另一个世界的零件。改成玻璃件之后，
            // 它和导航栏终于用上了同一套材质。
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = "新建标签页"
                        role = Role.Button
                    },
                // 50 表示短边的一半，圆形玻璃件；用圆角形状而不是 CircleShape，
                // 折射着色器只认圆角形状。
                shape = RoundedCornerShape(50),
                tint = colors.accent.copy(alpha = if (colors.isDark) 0.24f else 0.14f),
                blurRadius = 12.dp,
                lensHeight = 10.dp,
                lensAmount = 14.dp,
                contentAlignment = Alignment.Center,
                onClick = onNewTab
            ) {
                GlyphIcon(Glyph.Plus, colors.accent, size = 22.dp)
            }
        }
    }
}
