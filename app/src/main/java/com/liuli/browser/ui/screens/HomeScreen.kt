package com.liuli.browser.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.liuli.browser.ui.components.BrandOrb
import com.liuli.browser.ui.components.GlassIconButton
import com.liuli.browser.ui.components.GlassSurface
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.GlyphIcon
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.web.BrowserTab

private data class QuickLink(
    val label: String,
    val url: String,
    val color: Color
)

private val QuickLinks = listOf(
    QuickLink("百度", "https://m.baidu.com/", Color(0xFF3A6BFF)),
    QuickLink("哔哩哔哩", "https://m.bilibili.com/", Color(0xFFFB7299)),
    QuickLink("知乎", "https://www.zhihu.com/", Color(0xFF1772F6)),
    QuickLink("微博", "https://m.weibo.cn/", Color(0xFFE36A28)),
    QuickLink("小红书", "https://www.xiaohongshu.com/", Color(0xFFE64A50)),
    QuickLink("GitHub", "https://github.com/", Color(0xFF4A4F5A)),
    QuickLink("淘宝", "https://m.taobao.com/", Color(0xFFFF6A00)),
    QuickLink("豆瓣", "https://m.douban.com/", Color(0xFF2E9E58)),
    QuickLink("京东", "https://m.jd.com/", Color(0xFFE1251B)),
    QuickLink("网易云音乐", "https://music.163.com/", Color(0xFFD93B3B))
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    backdrop: LayerBackdrop,
    tabs: List<BrowserTab>,
    isPrivate: Boolean,
    isTablet: Boolean,
    bottomSpace: Dp,
    reduceMotion: Boolean,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
    onSearchSubmit: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenTab: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val addressSharedModifier = with(sharedScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "address-bar"),
            animatedVisibilityScope = animatedScope,
            boundsTransform = { _, _ -> spring(dampingRatio = 1f, stiffness = 342f) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
        )
    }
    var query by rememberSaveable { mutableStateOf("") }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(
        targetValue = if (entered && !reduceMotion) 1f else if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 520, easing = FastOutSlowInEasing),
        label = "home-entrance"
    )

    val recentTabs = tabs.filter { !it.isPrivate && it.url.isNotBlank() }
        .sortedByDescending { it.lastUsedAt }
        .take(6)
    val horizontalPadding = if (isTablet) 28.dp else 18.dp
    val columns = if (isTablet) 5 else 4

    Box(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .graphicsLayer {
                    alpha = entrance
                    translationY = (1f - entrance) * 22.dp.toPx()
                }
                .padding(horizontal = horizontalPadding)
                .padding(top = if (isTablet) 18.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandOrb(size = if (isTablet) 46.dp else 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            text = "琉璃浏览器",
                            style = TextStyle(
                                color = colors.textPrimary,
                                fontSize = if (isTablet) 30.sp else 24.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(Modifier.height(2.dp))
                        BasicText(
                            text = if (isPrivate) "隐私标签 · 不记录历史" else "轻盈 · 安全 · 一镜到底",
                            style = TextStyle(
                                color = if (isPrivate) colors.accentSecondary else colors.textTertiary,
                                fontSize = 12.sp,
                                fontWeight = if (isPrivate) FontWeight.Medium else FontWeight.Normal
                            )
                        )
                    }
                    if (!isTablet) {
                        GlassIconButton(
                            glyph = Glyph.Settings,
                            contentDescription = "设置",
                            onClick = onOpenSettings,
                            backdrop = backdrop,
                            size = 46.dp,
                            glyphSize = 20.dp
                        )
                    }
                }

                Spacer(Modifier.height(if (isTablet) 34.dp else 26.dp))

                HomeSearchBar(
                    backdrop = backdrop,
                    query = query,
                    onQueryChange = { query = it },
                    onSubmit = {
                        if (query.isNotBlank()) {
                            onSearchSubmit(query)
                            query = ""
                        }
                    },
                    modifier = addressSharedModifier
                )

                Spacer(Modifier.height(if (isTablet) 30.dp else 24.dp))

                SectionTitle("常用网站")
                Spacer(Modifier.height(12.dp))
                QuickLinkGrid(
                    backdrop = backdrop,
                    links = QuickLinks.take(if (isTablet) 10 else 8),
                    columns = columns,
                    isTablet = isTablet,
                    onOpenUrl = onOpenUrl
                )

                if (recentTabs.isNotEmpty()) {
                    Spacer(Modifier.height(if (isTablet) 30.dp else 24.dp))
                    SectionTitle("继续浏览")
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        items(recentTabs, key = { it.id }) { tab ->
                            RecentTabCard(
                                tab = tab,
                                backdrop = backdrop,
                                isTablet = isTablet,
                                onClick = { onOpenTab(tab.id) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(if (isTablet) 28.dp else 22.dp))

                PrivacyCard(backdrop = backdrop, onOpenLibrary = onOpenLibrary)

                Spacer(Modifier.height(bottomSpace + 20.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    val colors = LocalLiuliColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = title,
            style = TextStyle(
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        Spacer(Modifier.weight(1f))
        BasicText(
            text = "轻触即可打开",
            style = TextStyle(
                color = colors.textTertiary,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun HomeSearchBar(
    backdrop: LayerBackdrop,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLiuliColors.current
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(32.dp),
        tint = colors.glassTintStrong,
        blurRadius = 22.dp,
        lensHeight = 20.dp,
        lensAmount = 28.dp,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.Search, colors.textSecondary, size = 21.dp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .semantics { contentDescription = "搜索或输入网址" },
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        BasicText(
                            text = "搜索或输入网址",
                            style = TextStyle(
                                color = colors.textTertiary,
                                fontSize = 17.sp
                            )
                        )
                    }
                    innerTextField()
                }
            )
            if (query.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .heightIn(min = Dimens.minTouch)
                        .clip(RoundedCornerShape(50))
                        .clickable(role = Role.Button, onClickLabel = "搜索", onClick = onSubmit)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "前往",
                        style = TextStyle(
                            color = colors.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickLinkGrid(
    backdrop: LayerBackdrop,
    links: List<QuickLink>,
    columns: Int,
    isTablet: Boolean,
    onOpenUrl: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        links.chunked(columns).forEach { rowLinks ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowLinks.forEach { link ->
                    QuickLinkTile(
                        link = link,
                        backdrop = backdrop,
                        isTablet = isTablet,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenUrl(link.url) }
                    )
                }
                repeat(columns - rowLinks.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickLinkTile(
    link: QuickLink,
    backdrop: LayerBackdrop,
    isTablet: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val colors = LocalLiuliColors.current
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .height(if (isTablet) 112.dp else 96.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClickLabel = link.label, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        tint = colors.glassTint,
        blurRadius = 18.dp,
        lensHeight = 14.dp,
        lensAmount = 20.dp,
        shadow = true
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(if (isTablet) 44.dp else 38.dp)
                    .clip(CircleShape)
                    .background(link.color.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = link.label.take(1),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = if (isTablet) 18.sp else 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(Modifier.height(9.dp))
            BasicText(
                text = link.label,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = if (isTablet) 13.sp else 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentTabCard(
    tab: BrowserTab,
    backdrop: LayerBackdrop,
    isTablet: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalLiuliColors.current
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier
            .width(if (isTablet) 270.dp else 220.dp)
            .height(if (isTablet) 104.dp else 92.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        tint = colors.glassTint,
        blurRadius = 18.dp,
        lensHeight = 14.dp,
        lensAmount = 20.dp,
        shadow = true
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.accent.copy(alpha = if (colors.isDark) 0.24f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.Globe, colors.accent, size = 20.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = tab.pageTitle(),
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = tab.displayHost,
                    style = TextStyle(
                        color = colors.textTertiary,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PrivacyCard(
    backdrop: LayerBackdrop,
    onOpenLibrary: () -> Unit
) {
    val colors = LocalLiuliColors.current
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        tint = colors.glassTint,
        blurRadius = 18.dp,
        lensHeight = 14.dp,
        lensAmount = 22.dp,
        shadow = true
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.success.copy(alpha = if (colors.isDark) 0.22f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.Shield, colors.success, size = 21.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = "隐私优先，数据留在本地",
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(Modifier.height(3.dp))
                BasicText(
                    text = "隐私护盾默认开启 · 退出即清除数据",
                    style = TextStyle(
                        color = colors.textTertiary,
                        fontSize = 11.sp
                    )
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onOpenLibrary)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                BasicText(
                    text = "书签",
                    style = TextStyle(
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}
