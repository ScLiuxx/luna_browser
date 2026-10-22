package com.liuli.browser.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.liuli.browser.ui.liquid.LocalReduceMotion
import com.liuli.browser.ui.liquid.motionMillis
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
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
                items(ordered, key = { it.id }) { tab ->
                    TabCard(
                        tab = tab,
                        isActive = tab.id == activeId,
                        sharedScope = sharedScope,
                        animatedScope = animatedScope,
                        onOpen = { onSelectTab(tab) },
                        onClose = { onCloseTab(tab.id) },
                        modifier = Modifier.animateItem(
                            placementSpec = spring(dampingRatio = 0.9f, stiffness = 380f)
                        )
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
    isActive: Boolean,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLiuliColors.current
    val reduceMotion = LocalReduceMotion.current
    val closeMillis = motionMillis(reduceMotion, 190)
    var closing by remember(tab.id) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (closing && !reduceMotion) 0.86f else 1f,
        animationSpec = tween(closeMillis),
        label = "tab-card-scale"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (closing) 0f else 1f,
        animationSpec = tween(closeMillis),
        label = "tab-card-alpha"
    )
    LaunchedEffect(closing) {
        if (closing) {
            if (closeMillis > 0) delay(closeMillis.toLong())
            onClose()
        }
    }

    Box(
        Modifier
            .then(modifier)
            .fillMaxWidth()
            .aspectRatio(0.70f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = cardAlpha
            }
            .clip(RoundedCornerShape(24.dp))
            .background(colors.glassTintStrong)
            .semantics { contentDescription = tab.pageTitle() }
            .then(
                if (isActive) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = colors.accent.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(24.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
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
                    boundsTransform = { _, _ -> spring(dampingRatio = 0.9f, stiffness = 380f) },
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
                .clickable(role = Role.Button, onClickLabel = "关闭标签页") {
                    if (!closing) closing = true
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
