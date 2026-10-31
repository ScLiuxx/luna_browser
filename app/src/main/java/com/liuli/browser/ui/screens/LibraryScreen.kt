package com.liuli.browser.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.liuli.browser.data.LibraryEntry
import com.liuli.browser.data.LibraryStore
import com.liuli.browser.ui.components.GlassIconButton
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val list = if (section == 0) library.bookmarks else library.history
    // 页面左右留白走间距阶梯：平板 Space.xl，手机 Space.lg。
    val horizontal = if (isTablet) Space.xl else Space.lg
    // 已经演过入场动画的条目：滚动出去再回来不重播。
    val revealedKeys = remember { mutableStateListOf<String>() }
    // 空状态首次出现时淡入上移。
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = MotionSpec.value(Motion.Slow),
        label = "library-content"
    )

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
                if (section == 1 && library.history.isNotEmpty()) {
                    GlassIconButton(
                        glyph = Glyph.Trash,
                        contentDescription = "清空历史",
                        onClick = { library.clearHistory() },
                        backdrop = backdrop,
                        size = 44.dp,
                        glyphSize = 18.dp
                    )
                    Spacer(Modifier.width(Space.sm))
                }
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

            Row(
                Modifier
                    .padding(horizontal = horizontal)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(
                        colors.textPrimary.copy(alpha = if (colors.isDark) 0.10f else 0.06f)
                    )
                    .padding(Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                LibraryTab(
                    label = "收藏 ${library.bookmarks.size}",
                    selected = section == 0,
                    onClick = { section = 0 }
                )
                LibraryTab(
                    label = "历史 ${library.history.size}",
                    selected = section == 1,
                    onClick = { section = 1 }
                )
            }

            Spacer(Modifier.height(Space.md))

            if (list.isEmpty()) {
                EmptyLibrary(
                    isBookmarks = section == 0,
                    modifier = Modifier
                        .padding(horizontal = horizontal, vertical = 56.dp)
                        .graphicsLayer {
                            alpha = contentAlpha
                            translationY = (1f - contentAlpha) * 14.dp.toPx()
                        }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
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
                        val fadeOutSpec = MotionSpec.exit()
                        val slideSpec = MotionSpec.pageSpring(IntOffset.Zero)
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
                        AnimatedVisibility(
                            visible = revealed,
                            enter = fadeIn(
                                animationSpec = fadeInSpec
                            ) + slideInVertically(
                                animationSpec = slideSpec,
                                initialOffsetY = { it / 5 }
                            ),
                            exit = fadeOut(animationSpec = fadeOutSpec)
                        ) {
                            LibraryRow(
                                entry = entry,
                                isBookmark = section == 0,
                                onClick = { onOpenUrl(entry.url) },
                                onDelete = {
                                    if (section == 0) library.removeBookmark(entry.url)
                                    else library.removeHistory(entry)
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
    }
}

@Composable
private fun LibraryTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val background by animateColorAsState(
        targetValue = if (selected) colors.glassTintStrong else Color.Transparent,
        animationSpec = MotionSpec.color(),
        label = "tab-bg"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) colors.textPrimary else colors.textSecondary,
        animationSpec = MotionSpec.color(),
        label = "tab-label"
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .pressScale(pressedScale = 0.92f, interactionSource = interactionSource)
            .padding(horizontal = Space.lg, vertical = Space.sm)
    ) {
        BasicText(
            text = label,
            style = Type.sectionTitle.copy(
                color = labelColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        )
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
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(66.dp)
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
            style = Type.title.copy(fontSize = 15.sp, color = colors.textPrimary)
        )
        Spacer(Modifier.height(Space.xs))
        BasicText(
            text = if (isBookmarks) "在浏览页面菜单中点击“添加收藏”" else "浏览网页后会自动记录在这里",
            style = Type.bodySmall.copy(color = colors.textTertiary)
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
