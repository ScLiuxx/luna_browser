package com.liuli.browser.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.liuli.browser.ui.components.EmptyState
import com.liuli.browser.ui.components.GlassIconButton
import com.liuli.browser.ui.components.GlassPillButton
import com.liuli.browser.ui.components.GlassSurface
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.GlyphIcon
import com.liuli.browser.ui.components.HeaderSubtitle
import com.liuli.browser.ui.components.ScreenHeader
import com.liuli.browser.ui.liquid.GlassShape
import com.liuli.browser.ui.liquid.Visibility
import com.liuli.browser.ui.liquid.pressScale
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.Radius
import com.liuli.browser.ui.theme.Space
import com.liuli.browser.ui.theme.Type
import com.liuli.browser.ui.theme.reduceMotion
import com.liuli.browser.web.BrowserTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

/**
 * 网格的滚动位置记在文件级。
 *
 * 标签页视图每次进出都会被销毁重建（主页面用 AnimatedContent 切换），
 * 位置若只活在组合里，切一个标签再回来必定弹回顶部，用户得重新找。
 * 这里存的是纯数值而不是快照状态：滚动时不必触发整屏重组。
 */
private var savedGridScrollIndex = 0
private var savedGridScrollOffset = 0

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
    val reduced = reduceMotion
    val ordered = tabs.sortedWith(
        compareByDescending<BrowserTab> { it.id == activeId }
            .thenByDescending { it.lastUsedAt }
    )
    val columns = if (isTablet) 3 else 2
    // 平板上 3 列若沿用手机的比例，每张卡会宽到近 400dp、高到 500dp：
    // 一屏放不下两张，网页快照还被裁得只剩中间一条。
    // 平板改用更宽的卡片比例，卡片回到"一眼能看完两行"的尺度。
    val cardAspect = if (isTablet) 0.92f else 0.70f
    // 已经演过入场动画的标签：滚动出去再回来不重播。
    val revealedIds = remember { mutableStateListOf<Long>() }
    // 网格重排（当前标签置顶、关闭后回填）时的位移曲线：
    // 用弹性而不是瞬移，卡片是"滑"过去的，不是闪一下。
    val placementSpec = MotionSpec.pageSpring(IntOffset.VisibilityThreshold)
    val haptics = LocalHapticFeedback.current

    // 待确认关闭的标签页：只剩最后一个标签时，关闭是不可撤销的，
    // 先存住意图，等用户在确认条里点头。
    var pendingCloseId by remember { mutableStateOf<Long?>(null) }

    // 网格滚动位置：重建时从上次的落点接着看。
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedGridScrollIndex,
        initialFirstVisibleItemScrollOffset = savedGridScrollOffset
    )
    // 滚动位置回写文件级状态，供下次进入时恢复。
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                savedGridScrollIndex = index
                savedGridScrollOffset = offset
            }
    }

    BackHandler {
        // 有确认条时返回键先撤掉确认条，而不是直接退出整个视图。
        if (pendingCloseId != null) pendingCloseId = null else onDismiss()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                // 平板上把内容区收窄：否则 3 列的卡片会大得离谱。
                .widthIn(max = Dimens.pageContentWidth)
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = Space.sm)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = if (isTablet) Space.xl else Space.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScreenHeader(
                    title = "标签页",
                    isTablet = isTablet,
                    modifier = Modifier.weight(1f)
                ) {
                    if (tabs.isEmpty()) {
                        HeaderSubtitle("还没有打开的标签页")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 计数滚动着换：标签增减时数字是"翻"过来的，
                            // 整行文案硬切会让标题区闪一下。
                            AnimatedContent(
                                targetState = tabs.size,
                                transitionSpec = {
                                    if (reduced) {
                                        fadeIn(MotionSpec.enter(reduced, Motion.Fast)) togetherWith
                                            fadeOut(MotionSpec.exit(reduced, Motion.Fast))
                                    } else {
                                        (slideInVertically(
                                            animationSpec = MotionSpec.pageSpring(reduced, IntOffset.Zero),
                                            initialOffsetY = { it / 2 }
                                        ) + fadeIn(MotionSpec.enter(reduced))) togetherWith
                                            (slideOutVertically(
                                                animationSpec = MotionSpec.pageSpring(reduced, IntOffset.Zero),
                                                targetOffsetY = { -it / 2 }
                                            ) + fadeOut(MotionSpec.exit(reduced)))
                                    }
                                },
                                contentAlignment = Alignment.CenterStart,
                                label = "tab-count"
                            ) { count ->
                                BasicText(
                                    text = count.toString(),
                                    style = Type.label.copy(color = colors.textTertiary)
                                )
                            }
                            BasicText(
                                text = " 个标签 · 轻触切换",
                                style = Type.label.copy(color = colors.textTertiary)
                            )
                        }
                    }
                }
                GlassIconButton(
                    glyph = Glyph.Plus,
                    contentDescription = "新建标签页",
                    onClick = { onNewTab(false) },
                    backdrop = backdrop,
                    size = 46.dp,
                    glyphSize = 20.dp
                )
                Spacer(Modifier.width(Space.sm))
                GlassIconButton(
                    glyph = Glyph.Close,
                    contentDescription = "关闭标签页视图",
                    onClick = onDismiss,
                    backdrop = backdrop,
                    size = 46.dp,
                    glyphSize = 18.dp
                )
            }

            Spacer(Modifier.height(Space.lg))

            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = if (isTablet) Space.xl else Space.lg,
                        end = if (isTablet) Space.xl else Space.lg,
                        top = Space.xs,
                        bottom = bottomSpace + Space.xl
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.md)
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
                            // 当前标签置顶、关闭标签后其余卡片回填时，
                            // 用位移动画过渡；淡入淡出交给上面的错峰逻辑，
                            // 两套动画不要互相打架。
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                placementSpec = placementSpec,
                                fadeOutSpec = null
                            ),
                            revealed = revealed,
                            isActive = tab.id == activeId,
                            cardAspect = cardAspect,
                            sharedScope = sharedScope,
                            animatedScope = animatedScope,
                            onOpen = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectTab(tab)
                            },
                            // 先问父级：只剩最后一个标签页时不能直接关，
                            // 要等用户在确认条里点头，卡片也才允许开始退场。
                            onCloseRequest = {
                                if (tabs.size <= 1) {
                                    pendingCloseId = tab.id
                                    false
                                } else {
                                    true
                                }
                            },
                            onClose = { onCloseTab(tab.id) }
                        )
                    }
                }

                // 空状态：没有标签页时也要有内容，并带入场动画，
                // 否则网格空白一片，用户会以为界面卡住了。
                Visibility(
                    visible = tabs.isEmpty(),
                    enter = fadeIn(MotionSpec.enter()) + scaleIn(
                        MotionSpec.pageSpring(),
                        initialScale = 0.92f
                    ),
                    exit = fadeOut(MotionSpec.exit()) + scaleOut(
                        MotionSpec.exit(),
                        targetScale = 0.96f
                    )
                ) {
                    EmptyTabState(
                        backdrop = backdrop,
                        onNewTab = { onNewTab(false) }
                    )
                }
            }

            if (isTablet) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Space.xl, vertical = Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.md)
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

        // 关闭最后一个标签页的确认条。
        // 玻璃条贴着底部升起，不遮挡网格，也不挡住导航栏手势区。
        Visibility(
            visible = pendingCloseId != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = bottomSpace + Space.lg,
                    start = Space.lg,
                    end = Space.lg
                ),
            enter = if (reduced) {
                // 减少动态效果：位移纯属装饰，只留淡化。
                fadeIn(MotionSpec.enter(reduced, Motion.Fast))
            } else {
                fadeIn(MotionSpec.enter()) +
                    slideInVertically(MotionSpec.pageSpring(IntOffset.Zero)) { it / 2 }
            },
            exit = if (reduced) {
                fadeOut(MotionSpec.exit(reduced, Motion.Fast))
            } else {
                fadeOut(MotionSpec.exit()) +
                    slideOutVertically(MotionSpec.pageSpring(IntOffset.Zero)) { it / 2 }
            }
        ) {
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.widthIn(max = 460.dp),
                shape = GlassShape.squircle(Radius.xl),
                tint = colors.glassTintStrong,
                blurRadius = 20.dp,
                lensHeight = 14.dp,
                lensAmount = 20.dp,
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    BasicText(
                        text = "关闭最后一个标签页？",
                        style = Type.body.copy(color = colors.textPrimary),
                        modifier = Modifier.weight(1f)
                    )
                    GlassPillButton(
                        label = "取消",
                        onClick = { pendingCloseId = null },
                        backdrop = backdrop
                    )
                    GlassPillButton(
                        label = "关闭",
                        onClick = {
                            // 确认关闭是破坏性操作，给一次明确的触感确认。
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            pendingCloseId?.let(onCloseTab)
                            pendingCloseId = null
                        },
                        backdrop = backdrop,
                        // 收尾的标签页点掉就没有了，确认键用危险色：
                        // 与对话框里的确认键同一套，用户按之前就知道这一步不可逆。
                        accent = true,
                        danger = true,
                        // 组件按下时已经给过一次触感，
                        // 这里补的是"破坏性确认"，两次含义不同，不算重复。
                        haptic = false
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
    modifier: Modifier = Modifier,
    revealed: Boolean,
    isActive: Boolean,
    cardAspect: Float,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
    onOpen: () -> Unit,
    /** 询问父级能否关闭：返回 false 表示要等用户确认，卡片保持原样。 */
    onCloseRequest: () -> Boolean,
    /** 退场动画播完，真正移除标签页。 */
    onClose: () -> Unit
) {
    val colors = LocalLiuliColors.current
    var closing by remember(tab.id) { mutableStateOf(false) }
    // 关闭按钮触发过之后，本次手势不再打开这张卡片：
    // "关掉它"和"切到它"必须是两件独立的事。
    var closeTriggered by remember(tab.id) { mutableStateOf(false) }

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
        targetValue = if (isActive) colors.accent.copy(alpha = 0.92f) else Color.Transparent,
        animationSpec = borderColorSpec,
        label = "tab-card-border-color"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isActive) 2.dp else 0.dp,
        animationSpec = borderWidthSpec,
        label = "tab-card-border-width"
    )

    val cardInteraction = remember { MutableInteractionSource() }
    val closeInteraction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current

    // 关闭意图只有一条路径：右上角的小叉与长按卡片都走这里，
    // 否则两处的退场动画、确认逻辑与触感反馈会各走各的。
    val requestClose = { haptic: HapticFeedbackType? ->
        if (!closing) {
            // 长按关闭时框架已经给过一次长按触感，这里不再补一次：
            // 两次震动叠在一起只会变成一顿乱响。
            if (haptic != null) haptics.performHapticFeedback(haptic)
            // 先问父级：最后一个标签页要等用户点头才能关，
            // 确认之前卡片不能开始退场，也不能变成"点不动"。
            if (onCloseRequest()) {
                // 先记一笔：这次点击属于"关闭"，不能再触发卡片的打开。
                closeTriggered = true
                closing = true
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(cardAspect)
            // 按压回弹放在最外层，整张卡片（含背景与共享快照）一起缩小。
            .pressScale(pressedScale = 0.96f, interactionSource = cardInteraction)
            .graphicsLayer {
                scaleX = scale * enterScale
                scaleY = scale * enterScale
                alpha = cardAlpha * enterAlpha
            }
            .clip(GlassShape.squircle(Radius.lg))
            .background(colors.glassTintStrong)
            // 一句话把卡片说清楚：标题、域名、是不是当前页、是不是隐私页。
            // 卡片内的标题与徽标随后清掉语义，读屏才不会把同一句话念两遍。
            .semantics {
                contentDescription = buildString {
                    append(tab.pageTitle())
                    append("，")
                    append(tab.displayHost)
                    if (isActive) append("，当前标签页")
                    if (tab.isPrivate) append("，隐私标签页")
                }
            }
            .border(
                width = borderWidth,
                color = borderColor,
                shape = GlassShape.squircle(Radius.lg)
            )
            // 长按直接关掉这张卡：标签页一多，逐个去够右上角的小叉很费事，
            // 长按是浏览器里最通用的"关掉这张卡"手势。
            .combinedClickable(
                interactionSource = cardInteraction,
                indication = null,
                role = Role.Button,
                onClickLabel = "切换到 ${tab.pageTitle()}",
                onLongClickLabel = "关闭 ${tab.pageTitle()}",
                onLongClick = { requestClose(null) },
                onClick = {
                    // 关闭按钮按过就不该再打开这张卡片。
                    if (!closeTriggered) onOpen()
                }
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

        // 上下各压一层暗：顶部给徽标让位，底部给标题让位。
        // 快照来自真实网页，明暗不定，白字落在亮页面上会糊成一片。
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        0.17f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.76f)
                    )
                )
        )

        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 隐私标记也要淡入淡出：它和右边的"当前"标记是同一排，
            // 一个会滑进来、另一个硬切出现，两者的节奏对不上。
            Visibility(
                visible = tab.isPrivate,
                enter = fadeIn(MotionSpec.enter(Motion.Quick)) +
                    expandHorizontally(animationSpec = MotionSpec.sizeValue(Motion.Quick)),
                exit = fadeOut(MotionSpec.exit(Motion.Quick)) +
                    shrinkHorizontally(animationSpec = MotionSpec.sizeValue(Motion.Quick))
            ) {
                TabBadge(
                    glyph = Glyph.Incognito,
                    text = "隐私",
                    tint = colors.accentSecondary
                )
            }
            // "当前"标记用进场动画切换：活动标签变化时标记是"长"进去的，
            // 而不是啪地一下出现，旁边的隐私标记也会顺势让位。
            // 用项目自己的 Visibility：这一行在 Row 作用域里，
            // 直接调 AnimatedVisibility 会解析到 RowScope 的同名扩展。
            Visibility(
                visible = isActive,
                enter = fadeIn(MotionSpec.enter()) + scaleIn(
                    MotionSpec.pageSpring(),
                    initialScale = 0.6f
                ),
                exit = fadeOut(MotionSpec.exit(Motion.Quick)) + scaleOut(
                    MotionSpec.exit(),
                    targetScale = 0.6f
                )
            ) {
                // 徽标用实心强调色：半透明深底上的彩色对勾在深色截图上
                // 太轻，扫一眼根本看不出当前页是哪一张。
                TabBadge(
                    glyph = Glyph.Check,
                    text = "当前",
                    tint = colors.accent,
                    filled = true
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
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "关闭标签页"
                ) {
                    requestClose(HapticFeedbackType.TextHandleMove)
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    // 视觉底座比热区小一圈：48dp 的深色圆会压住卡片右上角的画面，
                    // 32dp 的圆点既看得清，又不挡住网页内容。
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.38f)),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.Close, Color.White, size = 15.dp)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(Space.md)
        ) {
            BasicText(
                text = tab.pageTitle(),
                // 用正文档号加半粗：卡片标题比页面标题小一档，
                // 但仍要比域名重，两行的层级才分得开。
                style = Type.body.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // 卡片已经把标题拼进 contentDescription，这里不再重复播报。
                modifier = Modifier.clearAndSetSemantics {}
            )
            Spacer(Modifier.height(Space.xs))
            BasicText(
                text = tab.displayHost,
                style = Type.caption.copy(color = Color.White.copy(alpha = 0.72f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
    }
}

@Composable
private fun TabBadge(
    glyph: Glyph,
    text: String,
    tint: Color,
    /** 实心底：当前页这类关键标记要能从截图里跳出来。 */
    filled: Boolean = false
) {
    Row(
        Modifier
            .clip(GlassShape.capsule())
            .background(if (filled) tint else Color.Black.copy(alpha = 0.34f))
            .padding(horizontal = Space.sm, vertical = Space.xs)
            // 徽标文字已并入卡片的描述，这里清掉避免读两遍。
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(glyph, if (filled) Color.White else tint, size = 11.dp)
        BasicText(
            text = text,
            style = Type.micro.copy(color = Color.White)
        )
    }
}

/**
 * 没有标签页时的空状态。
 * 网格空白一片会让人以为界面卡住了，这里给出明确的下一步。
 */
@Composable
private fun EmptyTabState(
    backdrop: LayerBackdrop,
    onNewTab: () -> Unit
) {
    EmptyState(
        glyph = Glyph.Tabs,
        title = "还没有打开的标签页",
        message = "开一个新标签页开始浏览",
        // 空状态里给一个能点的按钮：只写一句提示，用户还得自己去找右上角的加号。
        footer = {
            GlassPillButton(
                label = "新建标签页",
                onClick = onNewTab,
                backdrop = backdrop,
                glyph = Glyph.Plus,
                accent = true
            )
        }
    )
}
