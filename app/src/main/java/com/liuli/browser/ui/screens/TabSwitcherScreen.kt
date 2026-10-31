package com.liuli.browser.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.liuli.browser.ui.components.GlassIconButton
import com.liuli.browser.ui.components.GlassPillButton
import com.liuli.browser.ui.components.GlassSurface
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.GlyphIcon
import com.liuli.browser.ui.liquid.pressScale
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.web.BrowserTab
import kotlinx.coroutines.delay

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TabSwitcherScreen(
    tabs: List<BrowserTab>,
    activeId: Long,
    backdrop: LayerBackdrop,
    isTablet: Boolean,
    bottomSpace: androidx.compose.ui.unit.Dp,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
    onSelectTab: (BrowserTab) -> Unit,
    onCloseTab: (Long) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val ordered = tabs.sortedWith(
        compareByDescending<BrowserTab> { it.id == activeId }
            .thenByDescending { it.lastUsedAt }
    )
    val columns = if (isTablet) 3 else 2
    // 已经演过入场动画的标签：滚动出去再回来不重播。
    val revealedIds = remember { mutableStateListOf<Long>() }

    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 1160.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = if (isTablet) 26.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        text = "标签页",
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = if (isTablet) 32.sp else 26.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        text = "${tabs.size} 个标签 · 轻触切换",
                        style = TextStyle(color = colors.textTertiary, fontSize = 11.sp)
                    )
                }
                GlassIconButton(
                    glyph = Glyph.Plus,
                    contentDescription = "新建标签页",
                    onClick = { onNewTab(false) },
                    backdrop = backdrop,
                    size = 46.dp,
                    glyphSize = 20.dp
                )
                Spacer(Modifier.width(8.dp))
                GlassIconButton(
                    glyph = Glyph.Close,
                    contentDescription = "关闭标签页视图",
                    onClick = onDismiss,
                    backdrop = backdrop,
                    size = 46.dp,
                    glyphSize = 18.dp
                )
            }

            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = if (isTablet) 26.dp else 18.dp,
                    end = if (isTablet) 26.dp else 18.dp,
                    top = 4.dp,
                    bottom = bottomSpace + 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(ordered, key = { _, tab -> tab.id }) { index, tab ->
                    // 逐张浮现：错峰延迟由 MotionSpec 统一给出，已演过的标签不重播。
                    val stagger = MotionSpec.staggerDelay(index)
                    var revealed by remember(tab.id) {
                        mutableStateOf(tab.id in revealedIds)
                    }
                    LaunchedEffect(tab.id) {
                        if (!revealed) {
                            delay(stagger.toLong())
                            revealed = true
                            if (tab.id !in revealedIds) revealedIds.add(tab.id)
                        }
                    }
                    TabCard(
                        tab = tab,
                        revealed = revealed,
                        isActive = tab.id == activeId,
                        sharedScope = sharedScope,
                        animatedScope = animatedScope,
                        onOpen = { onSelectTab(tab) },
                        onClose = { onCloseTab(tab.id) }
                    )
                }
            }

            if (isTablet) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 26.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassPillButton(
                        label = "新建标签页",
                        onClick = { onNewTab(false) },
                        backdrop = backdrop,
                        glyph = Glyph.Plus,
                        accent = true
                    )
                    GlassPillButton(
                        label = "隐私标签",
                        onClick = { onNewTab(true) },
                        backdrop = backdrop,
                        glyph = Glyph.Incognito
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun TabCard(
    tab: BrowserTab,
    revealed: Boolean,
    isActive: Boolean,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalLiuliColors.current
    var closing by remember(tab.id) { mutableStateOf(false) }

    // 动效规格先在组合作用域里取好：动画参数位不是 @Composable 上下文。
    val enterFadeSpec = MotionSpec.enter()
    val enterScaleSpec = MotionSpec.pageSpring()
    val closeSpec = MotionSpec.exit(Motion.Quick)
    val borderColorSpec = MotionSpec.color()
    val borderWidthSpec = MotionSpec.pageSpring(0.01.dp)
    // sharedBounds 的 boundsTransform 不是组合上下文，规格要提前算好。
    val imageBoundsSpec = MotionSpec.pageSpring(Rect(0f, 0f, 0f, 0f))

    // 入场：轻微放大 + 淡入，和共享元素落位同步。
    val enterAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = enterFadeSpec,
        label = "tab-card-enter-alpha"
    )
    val enterScale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.94f,
        animationSpec = enterScaleSpec,
        label = "tab-card-enter-scale"
    )

    // 关闭：先播退场动画，动画结束后再真正移除标签页。
    val scale by animateFloatAsState(
        targetValue = if (closing) 0.86f else 1f,
        animationSpec = closeSpec,
        label = "tab-card-scale"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (closing) 0f else 1f,
        animationSpec = closeSpec,
        label = "tab-card-alpha"
    )
    LaunchedEffect(closing) {
        if (closing) {
            delay((Motion.Quick + 20).toLong())
            onClose()
        }
    }

    // 当前标签的高亮边框：宽度与颜色都平滑过渡，而不是瞬间弹出。
    val borderColor by animateColorAsState(
        targetValue = if (isActive) colors.accent.copy(alpha = 0.85f) else Color.Transparent,
        animationSpec = borderColorSpec,
        label = "tab-card-border-color"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isActive) 1.5.dp else 0.dp,
        animationSpec = borderWidthSpec,
        label = "tab-card-border-width"
    )

    val cardInteraction = remember { MutableInteractionSource() }
    val closeInteraction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.70f)
            // 按压回弹放在最外层，整张卡片（含背景与共享快照）一起缩小。
            .pressScale(pressedScale = 0.96f, interactionSource = cardInteraction)
            .graphicsLayer {
                scaleX = scale * enterScale
                scaleY = scale * enterScale
                alpha = cardAlpha * enterAlpha
            }
            .clip(RoundedCornerShape(24.dp))
            .background(colors.glassTintStrong)
            .semantics { contentDescription = tab.pageTitle() }
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(
                interactionSource = cardInteraction,
                indication = null,
                role = Role.Button,
                onClick = onOpen
            )
    ) {
        val snapshot = tab.snapshot
        if (snapshot != null) {
            val imageSharedModifier = with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "tab-image-${tab.id}"),
                    animatedVisibilityScope = animatedScope,
                    boundsTransform = { _, _ -> imageBoundsSpec },
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                        ContentScale.Crop,
                        Alignment.Center
                    )
                )
            }
            Image(
                bitmap = snapshot.asImageBitmap(),
                contentDescription = null,
                modifier = imageSharedModifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colors.accent.copy(alpha = 0.55f),
                                colors.accentSecondary.copy(alpha = 0.45f),
                                colors.accentTertiary.copy(alpha = 0.35f)
                            )
                        )
                    )
            ) {
                Box(
                    Modifier.align(Alignment.Center).size(54.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(
                        glyph = if (tab.isPrivate) Glyph.Incognito else Glyph.Globe,
                        tint = Color.White.copy(alpha = 0.9f),
                        size = 34.dp
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.62f)
                    )
                )
        )

        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (tab.isPrivate) {
                TabBadge(
                    glyph = Glyph.Incognito,
                    text = "隐私",
                    tint = colors.accentSecondary
                )
            }
            if (isActive) {
                TabBadge(
                    glyph = Glyph.Check,
                    text = "当前",
                    tint = colors.accent
                )
            }
        }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(Dimens.minTouch)
                .semantics {
                    contentDescription = "关闭标签页"
                    role = Role.Button
                }
                // 按压回弹与点击共用同一个 interactionSource，状态才会同步。
                .pressScale(pressedScale = 0.84f, interactionSource = closeInteraction)
                .clickable(
                    interactionSource = closeInteraction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = "关闭标签页"
                ) {
                    if (!closing) {
                        // 关闭标签页是破坏性操作，给一次明确的触感确认。
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        closing = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.34f)),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.Close, Color.White, size = 15.dp)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            BasicText(
                text = tab.pageTitle(),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                text = tab.displayHost,
                style = TextStyle(color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TabBadge(
    glyph: Glyph,
    text: String,
    tint: Color
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.34f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(glyph, tint, size = 11.dp)
        BasicText(
            text = text,
            style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        )
    }
}
