package com.liuli.browser.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.liuli.browser.data.LibraryEntry
import com.liuli.browser.data.LibraryStore
import com.liuli.browser.ui.components.GlassIconButton
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
import com.liuli.browser.ui.theme.reduceMotion
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 离场动画播完再动数据要等的时长，与离场时长对齐。 */
private const val REMOVE_EXIT_DELAY = 180L

/** 等待删除的条目：先播完离场动画，再真正从数据里删掉。 */
private data class PendingRemoval(
    val key: String,
    val entry: LibraryEntry,
    val isBookmark: Boolean
)

@Composable
fun LibraryScreen(
    library: LibraryStore,
    backdrop: LayerBackdrop,
    isTablet: Boolean,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalLiuliColors.current
    var section by rememberSaveable { mutableIntStateOf(0) }
    // 页面左右留白走间距阶梯：平板 Space.xl，手机 Space.lg。
    val horizontal = if (isTablet) Space.xl else Space.lg
    // 已经演过入场动画的条目：滚动出去再回来不重播。
    val revealedKeys = remember { mutableStateListOf<String>() }
    // 点删除后先排队：行的离场动画播完，数据才真的删掉。
    val pendingRemovals = remember { mutableStateListOf<PendingRemoval>() }
    val reduced = reduceMotion
    // 计数是"落"下去的，不是啪地换掉：删掉一条时数字能看见在动。
    val bookmarkCount by animateIntAsState(
        targetValue = library.bookmarks.size,
        animationSpec = MotionSpec.intValue(Motion.Quick),
        label = "bookmark-count"
    )
    val historyCount by animateIntAsState(
        targetValue = library.history.size,
        animationSpec = MotionSpec.intValue(Motion.Quick),
        label = "history-count"
    )

    // 排队中的删除统一在这里落地：动画播完才改数据，行不会凭空消失。
    LaunchedEffect(pendingRemovals.size) {
        if (pendingRemovals.isEmpty()) return@LaunchedEffect
        delay(REMOVE_EXIT_DELAY)
        pendingRemovals.toList().forEach { request ->
            if (request.isBookmark) library.removeBookmark(request.entry.url)
            else library.removeHistory(request.entry)
        }
        pendingRemovals.clear()
    }

    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 1000.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = Space.sm)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = horizontal),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        text = "书签与历史",
                        style = Type.display.copy(
                            color = colors.textPrimary,
                            fontSize = if (isTablet) 32.sp else 26.sp
                        )
                    )
                    Spacer(Modifier.height(Space.xxs))
                    BasicText(
                        text = "仅保存在本机，不上传云端",
                        style = Type.label.copy(color = colors.textTertiary)
                    )
                }
                // 清空历史的按钮位置常驻：切到收藏页时它淡出，
                // 关闭按钮不会因为按钮出现/消失而左右跳。
                Box(
                    Modifier.size(Dimens.minTouch),
                    contentAlignment = Alignment.Center
                ) {
                    Visibility(
                        visible = section == 1 && library.history.isNotEmpty(),
                        enter = fadeIn(animationSpec = MotionSpec.enter(Motion.Quick)) +
                            scaleIn(animationSpec = MotionSpec.pageSpring(), initialScale = 0.7f),
                        exit = fadeOut(animationSpec = MotionSpec.exit(Motion.Quick)) +
                            scaleOut(animationSpec = MotionSpec.pageSpring(), targetScale = 0.7f)
                    ) {
                        GlassIconButton(
                            glyph = Glyph.Trash,
                            contentDescription = "清空历史",
                            onClick = { library.clearHistory() },
                            backdrop = backdrop,
                            size = 44.dp,
                            glyphSize = 18.dp
                        )
                    }
                }
                Spacer(Modifier.width(Space.sm))
                GlassIconButton(
                    glyph = Glyph.Close,
                    contentDescription = "关闭",
                    onClick = onDismiss,
                    backdrop = backdrop,
                    size = 46.dp,
                    glyphSize = 18.dp
                )
            }

            Spacer(Modifier.height(Space.lg))

            LibrarySegments(
                selected = section,
                bookmarkCount = bookmarkCount,
                historyCount = historyCount,
                onSelect = { section = it },
                modifier = Modifier.padding(horizontal = horizontal)
            )

            Spacer(Modifier.height(Space.md))

            // 换段时列表是交叉推入的：视线有方向，不会像硬切那样内容凭空换了。
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = section,
                    transitionSpec = {
                        if (reduced) {
                            // "减少动态效果"：只交叉淡化，不做位移。
                            fadeIn(MotionSpec.enter(reduced, Motion.Fast)) togetherWith
                                fadeOut(MotionSpec.exit(reduced, Motion.Fast))
                        } else {
                            val enter = fadeIn(MotionSpec.enter(reduced)) +
                                slideInVertically(
                                    animationSpec = MotionSpec.pageSpring(reduced, IntOffset.Zero),
                                    initialOffsetY = { it / 8 }
                                )
                            val exit = fadeOut(MotionSpec.exit(reduced)) +
                                slideOutVertically(
                                    animationSpec = MotionSpec.pageSpring(reduced, IntOffset.Zero),
                                    targetOffsetY = { -it / 12 }
                                )
                            enter togetherWith exit
                        }
                    },
                    label = "library-section"
                ) { target ->
                    LibrarySection(
                        section = target,
                        library = library,
                        horizontal = horizontal,
                        revealedKeys = revealedKeys,
                        pendingRemovals = pendingRemovals,
                        onOpenUrl = onOpenUrl
                    )
                }
            }
        }
    }
}

/**
 * 收藏 / 历史 分段控件。
 *
 * 指示器是一块独立的底色，位置与尺寸都按分段实测值滑动：
 * 此前每段各自带底色、靠淡入淡出换人，看上去是"闪"而不是"滑"。
 */
@Composable
private fun LibrarySegments(
    selected: Int,
    bookmarkCount: Int,
    historyCount: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLiuliColors.current
    val density = LocalDensity.current
    // 每个分段的真实位置：指示器按实际尺寸滑过去，不能靠估算。
    val segmentBounds = remember { mutableStateMapOf<Int, Rect>() }
    val selectedBounds = segmentBounds[selected]
    // 位移与尺寸共用页面级弹性：指示器是被"推"过去的，不是瞬移。
    val indicatorLeft by animateDpAsState(
        targetValue = selectedBounds?.left?.let { with(density) { it.toDp() } } ?: 0.dp,
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "segment-indicator-left"
    )
    val indicatorTop by animateDpAsState(
        targetValue = selectedBounds?.top?.let { with(density) { it.toDp() } } ?: 0.dp,
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "segment-indicator-top"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = selectedBounds?.width?.let { with(density) { it.toDp() } } ?: 0.dp,
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "segment-indicator-width"
    )
    val indicatorHeight by animateDpAsState(
        targetValue = selectedBounds?.height?.let { with(density) { it.toDp() } } ?: 0.dp,
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "segment-indicator-height"
    )
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.pill))
            .background(
                colors.textPrimary.copy(alpha = if (colors.isDark) 0.10f else 0.06f)
            )
    ) {
        // 指示器画在文字下面，切换时底色滑过去，文字始终压在最上层。
        if (selectedBounds != null) {
            Box(
                Modifier
                    .offset(x = indicatorLeft, y = indicatorTop)
                    .width(indicatorWidth)
                    .height(indicatorHeight)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(colors.glassTintStrong)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            SegmentTab(
                label = "收藏 $bookmarkCount",
                selected = selected == 0,
                onClick = { onSelect(0) },
                onBoundsChanged = { segmentBounds[0] = it }
            )
            SegmentTab(
                label = "历史 $historyCount",
                selected = selected == 1,
                onClick = { onSelect(1) },
                onBoundsChanged = { segmentBounds[1] = it }
            )
        }
    }
}

@Composable
private fun SegmentTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onBoundsChanged: (Rect) -> Unit
) {
    val colors = LocalLiuliColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val labelColor by animateColorAsState(
        targetValue = if (selected) colors.textPrimary else colors.textSecondary,
        animationSpec = MotionSpec.color(),
        label = "segment-label"
    )
    // 字重也平滑过渡：选中瞬间换字重，文字会明显"跳"一下。
    val weight by animateFloatAsState(
        targetValue = if (selected) 600f else 400f,
        animationSpec = MotionSpec.value(Motion.Quick),
        label = "segment-weight"
    )
    Box(
        Modifier
            .onGloballyPositioned { onBoundsChanged(it.boundsInParent()) }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .pressScale(pressedScale = 0.9f, interactionSource = interactionSource)
            // 分段控件本身很矮，补足最小触控高度，
            // 否则"收藏/历史"这两个小胶囊很难点中。
            .heightIn(min = Dimens.minTouch)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = Type.sectionTitle.copy(
                color = labelColor,
                fontWeight = FontWeight(weight.toInt())
            )
        )
    }
}

@Composable
private fun LibrarySection(
    section: Int,
    library: LibraryStore,
    horizontal: Dp,
    revealedKeys: SnapshotStateList<String>,
    pendingRemovals: SnapshotStateList<PendingRemoval>,
    onOpenUrl: (String) -> Unit
) {
    val list = if (section == 0) library.bookmarks else library.history
    if (list.isEmpty()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = horizontal, vertical = 56.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            EmptyLibrary(isBookmarks = section == 0)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontal,
                end = horizontal,
                top = Space.xs,
                bottom = Space.xl
            ),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            itemsIndexed(
                list,
                key = { _, entry -> entry.url + entry.timestamp }
            ) { index, entry ->
                val rowKey = entry.url + entry.timestamp
                // 规格先在组合作用域里算好：fadeIn / LaunchedEffect 的参数位
                // 不是 @Composable 上下文，不能直接调用 MotionSpec 的组合版本。
                val stagger = MotionSpec.staggerDelay(index)
                val fadeInSpec = MotionSpec.enter()
                val fadeOutSpec = MotionSpec.exit(Motion.Quick)
                val slideSpec = MotionSpec.pageSpring(IntOffset.Zero)
                val shrinkSpec = MotionSpec.sizeValue(Motion.Quick)
                var revealed by remember(rowKey) {
                    mutableStateOf(rowKey in revealedKeys)
                }
                LaunchedEffect(rowKey) {
                    if (!revealed) {
                        delay(stagger.toLong())
                        revealed = true
                        if (rowKey !in revealedKeys) revealedKeys.add(rowKey)
                    }
                }
                // 删除中的行先播离场动画：行在原位收缩淡出，
                // 数据等动画播完再删，行不会凭空消失。
                val removing = pendingRemovals.any { it.key == rowKey }
                Visibility(
                    visible = !removing,
                    enter = fadeIn(
                        animationSpec = fadeInSpec
                    ) + slideInVertically(
                        animationSpec = slideSpec,
                        initialOffsetY = { it / 5 }
                    ),
                    exit = fadeOut(animationSpec = fadeOutSpec) + shrinkVertically(
                        animationSpec = shrinkSpec,
                        targetHeight = { it }
                    )
                ) {
                    LibraryRow(
                        entry = entry,
                        isBookmark = section == 0,
                        onClick = { onOpenUrl(entry.url) },
                        onDelete = {
                            if (pendingRemovals.none { it.key == rowKey }) {
                                pendingRemovals.add(
                                    PendingRemoval(
                                        key = rowKey,
                                        entry = entry,
                                        isBookmark = section == 0
                                    )
                                )
                            }
                        }
                    )
                }
            }
            item {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun LibraryRow(
    entry: LibraryEntry,
    isBookmark: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val rowInteraction = remember { MutableInteractionSource() }
    val deleteInteraction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(colors.glassTint)
            .clickable(
                interactionSource = rowInteraction,
                indication = null,
                role = Role.Button,
                onClickLabel = entry.title,
                onClick = onClick
            )
            .pressScale(pressedScale = 0.97f, interactionSource = rowInteraction)
            .padding(start = Space.md, top = Space.sm, bottom = Space.sm, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(colors.accent.copy(alpha = if (colors.isDark) 0.22f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(
                glyph = if (isBookmark) Glyph.BookmarkFilled else Glyph.History,
                tint = colors.accent,
                size = 18.dp
            )
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            BasicText(
                text = entry.title.ifBlank { entry.url },
                style = Type.body.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Space.xxs))
            BasicText(
                text = "${hostOf(entry.url)} · ${formatTime(entry.timestamp)}",
                style = Type.caption.copy(color = colors.textTertiary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier
                .size(Dimens.minTouch)
                // 关闭图标是纯图形，读屏软件读不到它，得自己报出这一项的名字。
                .semantics {
                    contentDescription = "删除"
                    role = Role.Button
                }
                .clickable(
                    interactionSource = deleteInteraction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "删除",
                    onClick = onDelete
                )
                .pressScale(pressedScale = 0.82f, interactionSource = deleteInteraction),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.Close, colors.textTertiary, size = 16.dp)
        }
    }
}

@Composable
private fun EmptyLibrary(
    isBookmarks: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalLiuliColors.current
    // 空状态也要有落位动作：图标先弹一下，文字再跟上。
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val iconScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.7f,
        animationSpec = MotionSpec.pageSpring(),
        label = "empty-icon-scale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = MotionSpec.value(Motion.Normal),
        label = "empty-alpha"
    )
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(66.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                    alpha = contentAlpha
                }
                .clip(CircleShape)
                .background(colors.glassTint),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(
                glyph = if (isBookmarks) Glyph.Bookmark else Glyph.History,
                tint = colors.textTertiary,
                size = 28.dp
            )
        }
        Spacer(Modifier.height(Space.md))
        BasicText(
            text = if (isBookmarks) "还没有收藏" else "还没有浏览记录",
            style = Type.title.copy(fontSize = 15.sp, color = colors.textPrimary),
            modifier = Modifier.graphicsLayer { alpha = contentAlpha }
        )
        Spacer(Modifier.height(Space.xs))
        BasicText(
            text = if (isBookmarks) "在浏览页面菜单中点击“添加收藏”" else "浏览网页后会自动记录在这里",
            style = Type.bodySmall.copy(color = colors.textTertiary),
            modifier = Modifier.graphicsLayer { alpha = contentAlpha }
        )
    }
}

private fun hostOf(url: String): String =
    runCatching { android.net.Uri.parse(url).host?.removePrefix("www.") }.getOrNull()
        ?: url

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
