package com.liuli.browser.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.liuli.browser.ui.components.BrandOrb
import com.liuli.browser.ui.components.GlassIconButton
import com.liuli.browser.ui.components.GlassPillButton
import com.liuli.browser.ui.components.GlassSurface
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.GlyphIcon
import com.liuli.browser.ui.liquid.Visibility
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

/**
 * 常用网站。
 *
 * 颜色取各站品牌色，但统一压低了一档明度与饱和度：
 * 十个高饱和色块并排会互相打架，也会盖过搜索框；
 * 压暗之后仍认得出站点，整片网格却安静下来了。
 *
 * 顺序也按色相排过：同色系不挨着放，两块红色中间隔着别的颜色。
 */
private val QuickLinks = listOf(
    QuickLink("百度", "https://m.baidu.com/", Color(0xFF2F5FE0)),
    QuickLink("哔哩哔哩", "https://m.bilibili.com/", Color(0xFFF0618F)),
    QuickLink("微博", "https://m.weibo.cn/", Color(0xFFE85A2C)),
    QuickLink("知乎", "https://www.zhihu.com/", Color(0xFF1B6FD9)),
    QuickLink("小红书", "https://www.xiaohongshu.com/", Color(0xFFEF3F5C)),
    QuickLink("豆瓣", "https://m.douban.com/", Color(0xFF2F9E58)),
    QuickLink("GitHub", "https://github.com/", Color(0xFF414957)),
    QuickLink("京东", "https://m.jd.com/", Color(0xFFE1251B)),
    QuickLink("淘宝", "https://m.taobao.com/", Color(0xFFE8840E)),
    QuickLink("网易云音乐", "https://music.163.com/", Color(0xFFA8232B))
)

/**
 * 首屏入场的节拍。
 *
 * 所有入场元素共用同一条时间线，一拍 24ms。
 * 顺序按阅读顺序排：品牌行 → 搜索框 → 常用网站 → 继续浏览 → 隐私卡片。
 * 此前是整屏统一淡入，元素各自的错峰全被这层淡入盖住，
 * 用户只看到"整页亮了一下"，看不到摆放的过程；
 * 而搜索框还没落位、常用网站已经先亮起来时，页面读起来是乱的。
 */
private const val EntranceBeatMillis = 24

/**
 * 区块入场：淡入 + 轻微上移。
 *
 * 延迟由整屏节拍算出，所有区块共用一条时间线；
 * "减少动态效果"时既不淡入也不上移，直接落位。
 */
@Composable
private fun Modifier.entranceReveal(
    beat: Int,
    reduceMotion: Boolean,
    /** 落位时带一点缩放：瓦片这类"摆上去"的元素需要它，整块内容不需要。 */
    fromScale: Float = 1f
): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(beat) {
        val delayMillis = if (reduceMotion) 0 else beat * EntranceBeatMillis
        if (delayMillis > 0) delay(delayMillis.toLong())
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown || reduceMotion) 1f else 0f,
        animationSpec = MotionSpec.value(reduceMotion, Motion.Normal),
        label = "entrance-alpha"
    )
    val rise by animateFloatAsState(
        targetValue = if (shown || reduceMotion) 0f else 1f,
        animationSpec = MotionSpec.value(reduceMotion, Motion.Normal),
        label = "entrance-rise"
    )
    val scale by animateFloatAsState(
        targetValue = if (shown || reduceMotion) 1f else fromScale,
        animationSpec = MotionSpec.value(reduceMotion, Motion.Normal),
        label = "entrance-scale"
    )
    return graphicsLayer {
        this.alpha = alpha
        // 减少动态效果时不上移：位移本身就是要被去掉的动效。
        translationY = if (reduceMotion) 0f else rise * 12.dp.toPx()
        scaleX = scale
        scaleY = scale
    }
}

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
    val haptics = LocalHapticFeedback.current

    val recentTabs = tabs.filter { !it.isPrivate && it.url.isNotBlank() }
        .sortedByDescending { it.lastUsedAt }
        .take(6)
    val quickLinks = QuickLinks.take(if (isTablet) 10 else 8)

    // 入场节拍：所有区块按阅读顺序依次落位。
    // 搜索框不在其中：它是地址栏共享元素的落点，
    // 从网页返回时地址栏要飞回来，搜索框必须已经在最终位置上等着。
    val headerBeat = 0
    val linksTitleBeat = 2
    val linksFirstBeat = linksTitleBeat + 2
    val recentTitleBeat = linksFirstBeat + quickLinks.size
    val recentFirstBeat = recentTitleBeat + 2
    val privacyBeat = recentFirstBeat + recentTabs.size + 1

    // 页面左右留白走间距阶梯，与其它页面一致。
    val horizontal = if (isTablet) Space.xl else Space.lg
    // 区块间距只有一档，手机 24 / 平板 32：
    // 之前是 26、24、22、28 混着用，页面看着总有一处没对齐。
    val sectionGap = if (isTablet) Space.xxl else Space.xl
    val columns = if (isTablet) 5 else 4

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
                .padding(horizontal = horizontal),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier
                    .widthIn(max = Dimens.pageContentWidth)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .entranceReveal(headerBeat, reduceMotion),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandOrb(size = if (isTablet) 42.dp else 36.dp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        // 产品名只当署名，不当标题：
                        // 浏览器首屏该由搜索框当主角，二十多号的产品名
                        // 压在搜索框上方，第一眼看到的是应用而不是输入框。
                        BasicText(
                            text = "琉璃浏览器",
                            style = Type.title.copy(color = colors.textPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(Space.xxs))
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
                        // 书签与设置并排放在右上角：
                        // 此前只有设置一个按钮，书签要翻到导航栏才够得着，
                        // 而书签恰恰是起始页上最高频的去处。
                        GlassIconButton(
                            glyph = Glyph.Bookmark,
                            contentDescription = "书签",
                            onClick = onOpenLibrary,
                            backdrop = backdrop,
                            size = 46.dp,
                            glyphSize = 20.dp
                        )
                        Spacer(Modifier.width(Space.sm))
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

                Spacer(Modifier.height(sectionGap))

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

                Spacer(Modifier.height(sectionGap))

                SectionTitle(
                    title = "常用网站",
                    hint = "轻触即可打开",
                    modifier = Modifier.entranceReveal(linksTitleBeat, reduceMotion)
                )
                Spacer(Modifier.height(Space.md))
                QuickLinkGrid(
                    backdrop = backdrop,
                    links = quickLinks,
                    columns = columns,
                    firstBeat = linksFirstBeat,
                    isTablet = isTablet,
                    reduceMotion = reduceMotion,
                    onOpenUrl = onOpenUrl
                )

                Spacer(Modifier.height(sectionGap))
                if (recentTabs.isNotEmpty()) {
                    SectionTitle(
                        title = "继续浏览",
                        hint = "轻触切回标签页",
                        modifier = Modifier.entranceReveal(recentTitleBeat, reduceMotion)
                    )
                    Spacer(Modifier.height(Space.md))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                        // 末尾留一段空白：最后一张卡要能完整停稳，
                        // 而不是永远卡在屏幕边缘被切掉。
                        contentPadding = PaddingValues(end = horizontal)
                    ) {
                        itemsIndexed(recentTabs, key = { _, tab -> tab.id }) { index, tab ->
                            // 逐张浮现：横向滚动条里的卡片错峰落位。
                            val beat = recentFirstBeat + index
                            var revealed by remember(tab.id) {
                                mutableStateOf(tab.id in revealedTabIds)
                            }
                            LaunchedEffect(tab.id) {
                                if (!revealed) {
                                    if (!reduceMotion) delay((beat * EntranceBeatMillis).toLong())
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
                                    onClick = {
                                        // 切回标签页是一次明确的跳转，给一次轻触感。
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onOpenTab(tab.id)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // 没有可继续的标签页时给一句话说明，
                    // 空白的一段会让用户以为这个功能坏了。
                    SectionTitle(
                        title = "继续浏览",
                        hint = null,
                        modifier = Modifier.entranceReveal(recentTitleBeat, reduceMotion)
                    )
                    Spacer(Modifier.height(Space.md))
                    Box(Modifier.entranceReveal(recentTitleBeat + 1, reduceMotion)) {
                        EmptyRecentHint(onOpenLibrary = onOpenLibrary)
                    }
                }

                Spacer(Modifier.height(sectionGap))

                Box(Modifier.entranceReveal(privacyBeat, reduceMotion)) {
                    PrivacyCard(backdrop = backdrop)
                }

                Spacer(Modifier.height(bottomSpace + Space.lg))
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    hint: String?,
    modifier: Modifier = Modifier
) {
    val colors = LocalLiuliColors.current
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = title,
            // 区块标题与设置页的分区名同色：同一层级的标题只该有一种颜色，
            // 此前首页用 textPrimary、设置页用 accent，跨页面看过去像两种产品。
            style = Type.sectionTitle.copy(color = colors.accent)
        )
        Spacer(Modifier.weight(1f))
        // 右侧提示跟着区块走：两个区块的交互不同，提示文案也不该共用一句。
        if (hint != null) {
            BasicText(
                text = hint,
                style = Type.label.copy(color = colors.textTertiary),
                // 大字号下提示会挤到标题身上：给它单行省略，
                // 标题与提示谁都不该被压掉半截。
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    val haptics = LocalHapticFeedback.current
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
    // 聚焦光晕：一圈随焦点亮起的强调色描边。
    // 画在玻璃的内容层里，它才会跟着共享元素一起飞到地址栏；
    // 画在外层的话，搜索框飞走了、光晕却留在原地。
    val ringColor by animateColorAsState(
        targetValue = if (focused) colors.accent.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = MotionSpec.color(Motion.Quick),
        label = "search-ring-color"
    )
    val ringWidth by animateDpAsState(
        targetValue = if (focused) 1.5.dp else 0.dp,
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "search-ring-width"
    )
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
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
        // 内边距交给内容自己处理：光晕要贴着玻璃的外沿画，
        // 若由 GlassSurface 统一内缩，光晕就缩在玻璃里面一圈，对不齐边缘。
        contentPadding = PaddingValues(0.dp)
    ) {
        // 不能用 fillMaxSize：玻璃只有 heightIn(min) 没有固定高度，
        // 一旦撑满就会把搜索框拉到整屏高。
        // 用 wrapContentSize + align 让内容居中，光晕再按玻璃高度对齐。
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
                // 选区底色与拖拽手柄改用强调色：
                // 平台默认的蓝色落在这套配色上很跳，选中的字会像贴错了色。
                // 整个输入框都要裹进去：手柄是画在装饰框之外的兄弟节点，
                // 只包 decorationBox 的话手柄仍是默认蓝。
                CompositionLocalProvider(
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = colors.accent,
                        backgroundColor = colors.accent.copy(alpha = 0.28f)
                    )
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Space.xxs)
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = "搜索或输入网址" },
                        interactionSource = interactionSource,
                        textStyle = Type.title.copy(
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        singleLine = true,
                        // 与地址栏共用同一套键盘规格：首页搜索框和地址栏本就是同一个
                        // 共享元素，输入法行为必须一致，否则两屏之间会突然换手感。
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                // 键盘的"前往"没有按下这一下，
                                // 触感得在这里自己给，否则走键盘提交时完全没有反馈。
                                if (query.isNotBlank()) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onSubmit()
                            }
                        ),
                        decorationBox = { innerTextField ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    if (query.isEmpty()) {
                                        BasicText(
                                            text = "搜索或输入网址",
                                            style = Type.title.copy(color = hintTint)
                                        )
                                    }
                                    innerTextField()
                                }
                                // 清空入口：全选删除太慢，长按也不够直觉，
                                // 给一个一键擦掉的按钮。
                                Visibility(
                                    visible = query.isNotEmpty(),
                                    enter = fadeIn(MotionSpec.enter(Motion.Quick)) +
                                        scaleIn(
                                            animationSpec = MotionSpec.pageSpring(),
                                            initialScale = 0.7f
                                        ),
                                    exit = fadeOut(MotionSpec.exit(Motion.Quick)) +
                                        scaleOut(
                                            animationSpec = MotionSpec.pageSpring(),
                                            targetScale = 0.7f
                                        )
                                ) {
                                    val clearInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            // 触摸区给到 48dp：28dp 的小叉在手指下根本点不准，
                                            // 视觉上仍是那颗小叉，只是手指不必精确命中。
                                            .size(Dimens.minTouch)
                                            .clip(CircleShape)
                                            .semantics {
                                                contentDescription = "清空搜索内容"
                                                role = Role.Button
                                            }
                                            .clickable(
                                                interactionSource = clearInteraction,
                                                indication = null,
                                                role = Role.Button,
                                                onClickLabel = "清空"
                                            ) { onQueryChange("") }
                                            .pressScale(
                                                pressedScale = 0.82f,
                                                interactionSource = clearInteraction
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        GlyphIcon(Glyph.Close, colors.textTertiary, size = 13.dp)
                                    }
                                }
                            }
                        }
                    )
                }
                // 「前往」按钮淡入并轻微右移：
                // 输入框里一有字它就弹出来，硬切会让键盘显得很急躁。
                // 用项目自己的 Visibility：这一行在 Row 作用域里，
                // 直接调 AnimatedVisibility 会解析到 RowScope 的同名扩展。
                Visibility(
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
                    // 「前往」按钮与地址栏编辑态里的那颗同款：
                    // 两边是同一块共享元素，首页用淡强调色底、地址栏用实底，
                    // 来回切换时按钮会整个变一次样。
                    GlassPillButton(
                        label = "前往",
                        onClick = onSubmit,
                        backdrop = emptyBackdrop(),
                        accent = true,
                        contentPadding = PaddingValues(horizontal = Space.lg)
                    )
                }
            }
            // 光晕画在最后：压在内容之上，边缘才不会被行内元素切出缺口。
            Box(
                Modifier
                    .matchParentSize()
                    .border(ringWidth, ringColor, RoundedCornerShape(Radius.xl))
            )
        }
    }
}

@Composable
private fun QuickLinkGrid(
    backdrop: LayerBackdrop,
    links: List<QuickLink>,
    columns: Int,
    firstBeat: Int,
    isTablet: Boolean,
    reduceMotion: Boolean,
    onOpenUrl: (String) -> Unit
) {
    // 常用网站逐块浮现：九宫格一次整片亮起像张贴图，
    // 错开落位才看得出是"摆上去"的。
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        links.chunked(columns).forEachIndexed { rowIndex, rowLinks ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                rowLinks.forEachIndexed { columnIndex, link ->
                    Box(
                        Modifier
                            .weight(1f)
                            .entranceReveal(
                                beat = firstBeat + rowIndex * columns + columnIndex,
                                reduceMotion = reduceMotion,
                                fromScale = 0.94f
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
            .height(if (isTablet) 108.dp else 92.dp)
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
                    .size(if (isTablet) 42.dp else 36.dp)
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
            Spacer(Modifier.height(Space.sm))
            BasicText(
                text = link.label,
                // 手机与平板用同一档字号：站点名是这里唯一能读的字，
                // 11sp 在高密度屏上偏小，12sp 才不至于眯眼看。
                style = Type.bodySmall.copy(
                    color = colors.textPrimary,
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
    val interaction = remember { MutableInteractionSource() }
    val title = tab.pageTitle()
    val host = tab.displayHost
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier
            .width(if (isTablet) 260.dp else 214.dp)
            .height(if (isTablet) 100.dp else 88.dp)
            .clip(RoundedCornerShape(Radius.md))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "切回 $title",
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
            Modifier.fillMaxSize().padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabIcon(tab)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = title,
                    style = Type.body.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 标题就是域名时不重复写一遍：那行是给"站点名 ≠ 域名"的页面用的。
                if (host != title) {
                    Spacer(Modifier.height(Space.xs))
                    BasicText(
                        text = host,
                        style = Type.label.copy(color = colors.textTertiary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 标签页的站点图标。
 *
 * 有 favicon 就用真的：颜色和形状本身就是最强的身份提示，
 * 一排相同的地球图标只能靠文字认人。拿不到时退回地球符号。
 */
@Composable
private fun TabIcon(tab: BrowserTab) {
    val colors = LocalLiuliColors.current
    val favicon = tab.favicon?.asImageBitmap()
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(colors.glassTintStrong),
        contentAlignment = Alignment.Center
    ) {
        if (favicon != null) {
            Image(
                bitmap = favicon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            GlyphIcon(Glyph.Globe, colors.accent, size = 20.dp)
        }
    }
}

/**
 * 没有可继续的标签页时的提示。
 *
 * 底部空着一段会让人以为"继续浏览"出了故障，
 * 这里补一句话说明它什么时候才有内容，并给出去书签的入口。
 */
@Composable
private fun EmptyRecentHint(onOpenLibrary: () -> Unit) {
    val colors = LocalLiuliColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(colors.glassTint),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.History, colors.textTertiary, size = 20.dp)
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            BasicText(
                text = "还没有打开的网页",
                style = Type.body.copy(
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Space.xxs))
            BasicText(
                text = "打开过的页面会出现在这里",
                style = Type.label.copy(color = colors.textTertiary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val libraryInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .heightIn(min = Dimens.minTouch)
                .clip(RoundedCornerShape(Radius.pill))
                .background(colors.accent.copy(alpha = 0.12f))
                .clickable(
                    interactionSource = libraryInteraction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "打开书签",
                    onClick = onOpenLibrary
                )
                .pressScale(pressedScale = 0.90f, interactionSource = libraryInteraction)
                .padding(horizontal = Space.md),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = "书签",
                style = Type.label.copy(
                    color = colors.accent,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
private fun PrivacyCard(
    backdrop: LayerBackdrop
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
            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.success.copy(alpha = if (colors.isDark) 0.22f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.Shield, colors.success, size = 18.dp)
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                // 这一句是承诺，不是条款：
                // 讲清"数据在哪儿、什么时候消失"，比列一堆开关名更像人话。
                BasicText(
                    text = "浏览数据不会离开这台设备",
                    style = Type.body.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Space.xxs))
                BasicText(
                    text = "拦截追踪器 · 退出即清除",
                    style = Type.caption.copy(color = colors.textTertiary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
