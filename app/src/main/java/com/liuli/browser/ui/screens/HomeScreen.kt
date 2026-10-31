package com.liuli.browser.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.liuli.browser.ui.liquid.pressScale
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.Radius
import com.liuli.browser.ui.theme.Space
import com.liuli.browser.ui.theme.Type
import com.liuli.browser.web.BrowserTab
import kotlinx.coroutines.delay

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
    // sharedBounds 的 boundsTransform 不是组合上下文，规格要在外面先算好。
    val addressBoundsSpec = MotionSpec.sharedSpring(reduceMotion, Rect(0f, 0f, 0f, 0f))
    val addressSharedModifier = with(sharedScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "address-bar"),
            animatedVisibilityScope = animatedScope,
            boundsTransform = { _, _ -> addressBoundsSpec },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
        )
    }
    var query by rememberSaveable { mutableStateOf("") }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    // 整屏入场：淡入 + 上移。规格统一取自 MotionSpec；
    // "减少动态效果"时直接落位，不做补间。
    val entranceSpec: FiniteAnimationSpec<Float> = if (reduceMotion) {
        tween(durationMillis = 0)
    } else {
        MotionSpec.value(Motion.Slow)
    }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = entranceSpec,
        label = "home-entrance"
    )
    // 最近标签逐张浮现：淡入用补间，缩放用弹簧；
    // 规格先在组合作用域算好，动画参数位不能直接调用组合版 MotionSpec。
    val cardFadeSpec: FiniteAnimationSpec<Float> = if (reduceMotion) {
        tween(durationMillis = 0)
    } else {
        MotionSpec.value(Motion.Slow)
    }
    val cardScaleSpec: FiniteAnimationSpec<Float> = MotionSpec.pageSpring(reduceMotion)
    // 已经演过入场动画的最近标签：横向滚出去再回来不重播。
    val revealedTabIds = remember { mutableStateListOf<Long>() }

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
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            text = "琉璃浏览器",
                            style = Type.display.copy(
                                color = colors.textPrimary,
                                fontSize = if (isTablet) 28.sp else 23.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        BasicText(
                            text = if (isPrivate) "隐私标签 · 不记录历史" else "轻盈 · 安全 · 一镜到底",
                            style = Type.bodySmall.copy(
                                color = if (isPrivate) colors.accentSecondary else colors.textTertiary,
                                fontWeight = if (isPrivate) FontWeight.Medium else FontWeight.Normal
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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

                SectionTitle("常用网站", "轻触即可打开")
                Spacer(Modifier.height(Space.md))
                QuickLinkGrid(
                    backdrop = backdrop,
                    links = QuickLinks.take(if (isTablet) 10 else 8),
                    columns = columns,
                    isTablet = isTablet,
                    reduceMotion = reduceMotion,
                    onOpenUrl = onOpenUrl
                )

                if (recentTabs.isNotEmpty()) {
                    Spacer(Modifier.height(if (isTablet) 30.dp else 24.dp))
                    SectionTitle("继续浏览", "轻触继续浏览")
                    Spacer(Modifier.height(Space.md))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        itemsIndexed(recentTabs, key = { _, tab -> tab.id }) { index, tab ->
                            // 逐张浮现：横向滚动条里的卡片错峰落位。
                            val stagger = MotionSpec.staggerDelay(index, stepMillis = 24)
                            var revealed by remember(tab.id) {
                                mutableStateOf(tab.id in revealedTabIds)
                            }
                            LaunchedEffect(tab.id) {
                                if (!revealed) {
                                    delay(stagger.toLong())
                                    revealed = true
                                    if (tab.id !in revealedTabIds) revealedTabIds.add(tab.id)
                                }
                            }
                            val alpha by animateFloatAsState(
                                targetValue = if (revealed) 1f else 0f,
                                animationSpec = cardFadeSpec,
                                label = "recent-tab-alpha"
                            )
                            val scale by animateFloatAsState(
                                targetValue = if (revealed) 1f else 0.94f,
                                animationSpec = cardScaleSpec,
                                label = "recent-tab-scale"
                            )
                            Box(
                                Modifier.graphicsLayer {
                                    this.alpha = alpha
                                    scaleX = scale
                                    scaleY = scale
                                }
                            ) {
                                RecentTabCard(
                                    tab = tab,
                                    backdrop = backdrop,
                                    isTablet = isTablet,
                                    onClick = { onOpenTab(tab.id) }
                                )
                            }
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
private fun SectionTitle(title: String, hint: String) {
    val colors = LocalLiuliColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = title,
            style = Type.sectionTitle.copy(color = colors.textPrimary)
        )
        Spacer(Modifier.weight(1f))
        // 右侧提示跟着区块走：两个区块的交互不同，提示文案也不该共用一句。
        BasicText(
            text = hint,
            style = Type.label.copy(color = colors.textTertiary)
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
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    // 搜索框获得焦点时轻微放大，输入时玻璃件也跟着"醒"过来。
    val focused by interactionSource.collectIsFocusedAsState()
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 1.018f else 1f,
        animationSpec = MotionSpec.pageSpring(),
        label = "search-focus-scale"
    )
    // 玻璃本身是半透明的，只靠放大看不出焦点落在哪：
    // 放大镜染上强调色、占位文字提亮，焦点才立得住。
    val focusTint by animateColorAsState(
        targetValue = if (focused) colors.accent else colors.textSecondary,
        animationSpec = MotionSpec.color(Motion.Quick),
        label = "search-icon-tint"
    )
    val hintTint by animateColorAsState(
        targetValue = if (focused) colors.textSecondary else colors.textTertiary,
        animationSpec = MotionSpec.color(Motion.Quick),
        label = "search-hint-tint"
    )
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
            },
        shape = RoundedCornerShape(Radius.xl),
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
                GlyphIcon(Glyph.Search, focusTint, size = 21.dp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "搜索或输入网址" },
                interactionSource = interactionSource,
                textStyle = Type.title.copy(
                    color = colors.textPrimary,
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
                            style = Type.title.copy(color = hintTint)
                        )
                    }
                    innerTextField()
                }
            )
            // 「前往」按钮淡入并轻微右移：
            // 输入框里一有字它就弹出来，硬切会让键盘显得很急躁。
            AnimatedVisibility(
                visible = query.isNotBlank(),
                enter = fadeIn(MotionSpec.enter(Motion.Quick)) +
                    expandHorizontally(animationSpec = MotionSpec.sizeValue()) +
                    slideInHorizontally(
                        animationSpec = MotionSpec.pageSpring(androidx.compose.ui.unit.IntOffset.Zero),
                        initialOffsetX = { -it / 3 }
                    ),
                exit = fadeOut(MotionSpec.exit(Motion.Quick)) +
                    shrinkHorizontally(animationSpec = MotionSpec.sizeValue())
            ) {
                val goInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .heightIn(min = Dimens.minTouch)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(colors.accent.copy(alpha = 0.14f))
                        .clickable(
                            interactionSource = goInteraction,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = "搜索",
                            onClick = onSubmit
                        )
                        .pressScale(pressedScale = 0.90f, interactionSource = goInteraction)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "前往",
                        style = Type.body.copy(
                            color = colors.accent,
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
    reduceMotion: Boolean,
    onOpenUrl: (String) -> Unit
) {
    // 常用网站逐块浮现：九宫格一次整片亮起像张贴图，
    // 错开落位才看得出是"摆上去"的。
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        links.chunked(columns).forEachIndexed { rowIndex, rowLinks ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowLinks.forEachIndexed { columnIndex, link ->
                    Box(
                        Modifier
                            .weight(1f)
                            .gridReveal(
                                index = rowIndex * columns + columnIndex,
                                reduceMotion = reduceMotion
                            )
                    ) {
                        QuickLinkTile(
                            link = link,
                            backdrop = backdrop,
                            isTablet = isTablet,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onOpenUrl(link.url) }
                        )
                    }
                }
                repeat(columns - rowLinks.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 常用网站逐块浮现。
 *
 * 延迟值必须在组合作用域里取好再进 LaunchedEffect，
 * 因为 [MotionSpec.staggerDelay] 是 @Composable，不能在协程里调用。
 */
@Composable
private fun Modifier.gridReveal(index: Int, reduceMotion: Boolean): Modifier {
    val stagger = MotionSpec.staggerDelay(index, stepMillis = 22)
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(index) {
        if (stagger > 0) delay(stagger.toLong())
        revealed = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = if (reduceMotion) tween(durationMillis = 0) else MotionSpec.value(Motion.Slow),
        label = "grid-alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.94f,
        animationSpec = MotionSpec.pageSpring(reduceMotion),
        label = "grid-scale"
    )
    return graphicsLayer {
        this.alpha = alpha
        scaleX = scale
        scaleY = scale
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
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .height(if (isTablet) 112.dp else 96.dp)
            .clip(RoundedCornerShape(Radius.md))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = link.label,
                onClick = onClick
            )
            .pressScale(pressedScale = 0.94f, interactionSource = interaction),
        shape = RoundedCornerShape(Radius.md),
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
                    style = Type.title.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(Modifier.height(9.dp))
            BasicText(
                text = link.label,
                style = if (isTablet) {
                    Type.bodySmall.copy(fontWeight = FontWeight.Medium)
                } else {
                    Type.label
                },
                color = colors.textPrimary,
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
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier
            .width(if (isTablet) 270.dp else 220.dp)
            .height(if (isTablet) 104.dp else 92.dp)
            .clip(RoundedCornerShape(Radius.md))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "继续浏览",
                onClick = onClick
            )
            .pressScale(pressedScale = 0.95f, interactionSource = interaction),
        shape = RoundedCornerShape(Radius.md),
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
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(colors.accent.copy(alpha = if (colors.isDark) 0.24f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.Globe, colors.accent, size = 20.dp)
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = tab.pageTitle(),
                    style = Type.body.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Space.xs))
                BasicText(
                    text = tab.displayHost,
                    style = Type.label.copy(color = colors.textTertiary),
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
        shape = RoundedCornerShape(Radius.md),
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
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = "隐私优先，数据留在本地",
                    style = Type.body.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Space.xxs))
                BasicText(
                    text = "隐私护盾默认开启 · 退出即清除数据",
                    style = Type.label.copy(color = colors.textTertiary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 按下反馈要跟点击共用同一个 interactionSource，
            // 否则 pressScale 监听的是另一个源，按下时不会缩。
            val libraryInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .clickable(
                        interactionSource = libraryInteraction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "打开书签",
                        onClick = onOpenLibrary
                    )
                    .pressScale(pressedScale = 0.90f, interactionSource = libraryInteraction)
                    .padding(horizontal = 14.dp)
            ) {
                BasicText(
                    text = "书签",
                    style = Type.bodySmall.copy(
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}
