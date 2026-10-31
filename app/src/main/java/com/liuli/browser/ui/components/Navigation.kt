package com.liuli.browser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
    Canvas(modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.85f),
                0.22f to colors.accentTertiary,
                0.62f to colors.accent,
                1f to colors.accentSecondary,
                center = Offset(center.x - radius * 0.35f, center.y - radius * 0.4f),
                radius = radius * 1.7f
            ),
            radius = radius,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = radius * 0.93f,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )
        drawOval(
            color = Color.White.copy(alpha = 0.34f),
            topLeft = Offset(center.x - radius * 0.62f, center.y - radius * 0.82f),
            size = androidx.compose.ui.geometry.Size(radius * 1.24f, radius * 0.62f),
            style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.65f),
            radius = radius * 0.16f,
            center = Offset(center.x - radius * 0.36f, center.y - radius * 0.36f)
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
        shape = RoundedCornerShape(34.dp),
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
                        shape = RoundedCornerShape(18.dp),
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
                                .clip(RoundedCornerShape(18.dp))
                                .onGloballyPositioned { coordinates ->
                                    itemBounds[index] = coordinates.boundsInParent()
                                }
                                .semantics {
                                    contentDescription = item.label
                                    role = Role.Button
                                }
                                .then(itemPress)
                                .clickable(
                                    interactionSource = itemInteraction,
                                    indication = LocalIndication.current,
                                    role = Role.Button,
                                    onClickLabel = item.label
                                ) {
                                    onSelect(index)
                                }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                GlyphIcon(
                                    glyph = item.glyph,
                                    tint = iconTint,
                                    size = 22.dp
                                )
                                if (item.glyph == Glyph.Tabs && tabCount > 0) {
                                    Box(
                                        Modifier
                                            .offset(x = 11.dp, y = (-8).dp)
                                            .size(15.dp)
                                            .clip(CircleShape)
                                            .background(colors.accent),
                                        contentAlignment = Alignment.Center
                                    ) {
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
            val newTabInteraction = remember { MutableInteractionSource() }
            val newTabPress = Modifier.pressScale(
                pressedScale = 0.92f,
                interactionSource = newTabInteraction
            )
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = if (colors.isDark) 0.28f else 0.16f))
                    .semantics {
                        contentDescription = "新建标签页"
                        role = Role.Button
                    }
                    .then(newTabPress)
                    .clickable(
                        interactionSource = newTabInteraction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClickLabel = "新建标签页",
                        onClick = onNewTab
                    ),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.Plus, colors.accent, size = 22.dp)
            }
        }
    }
}
