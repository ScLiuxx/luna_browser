package com.liuli.browser.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.liuli.browser.data.LibraryEntry
import com.liuli.browser.data.LibraryStore
import com.liuli.browser.ui.components.GlassIconButton
import com.liuli.browser.ui.components.GlassPillButton
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.GlyphIcon
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
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
    val horizontal = if (isTablet) 26.dp else 18.dp

    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 1000.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = horizontal),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        text = "书签与历史",
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = if (isTablet) 32.sp else 26.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        text = "仅保存在本机，不上传云端",
                        style = TextStyle(color = colors.textTertiary, fontSize = 11.sp)
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
                    Spacer(Modifier.width(8.dp))
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

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier
                    .padding(horizontal = horizontal)
                    .clip(RoundedCornerShape(50))
                    .background(
                        colors.textPrimary.copy(alpha = if (colors.isDark) 0.10f else 0.06f)
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
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

            Spacer(Modifier.height(12.dp))

            if (list.isEmpty()) {
                EmptyLibrary(
                    isBookmarks = section == 0,
                    modifier = Modifier.padding(horizontal = horizontal, vertical = 56.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = horizontal,
                        end = horizontal,
                        top = 4.dp,
                        bottom = 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.url + it.timestamp }) { entry ->
                        LibraryRow(
                            entry = entry,
                            isBookmark = section == 0,
                            onClick = { onOpenUrl(entry.url) },
                            onDelete = {
                                if (section == 0) library.removeBookmark(entry.url)
                                else library.removeHistory(entry)
                            },
                            modifier = Modifier.animateItem(
                                placementSpec = spring(dampingRatio = 0.9f, stiffness = 380f)
                            )
                        )
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
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    colors.glassTintStrong
                } else {
                    Color.Transparent
                }
            )
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (selected) colors.textPrimary else colors.textSecondary,
                fontSize = 13.sp,
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
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLiuliColors.current
    Row(
        Modifier
            .then(modifier)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.glassTint)
            .clickable(role = Role.Button, onClickLabel = entry.title, onClick = onClick)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent.copy(alpha = if (colors.isDark) 0.22f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(
                glyph = if (isBookmark) Glyph.BookmarkFilled else Glyph.History,
                tint = colors.accent,
                size = 18.dp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                text = entry.title.ifBlank { entry.url },
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = "${hostOf(entry.url)} · ${formatTime(entry.timestamp)}",
                style = TextStyle(color = colors.textTertiary, fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier
                .size(Dimens.minTouch)
                .clickable(role = Role.Button, onClickLabel = "删除", onClick = onDelete),
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
        Spacer(Modifier.height(14.dp))
        BasicText(
            text = if (isBookmarks) "还没有收藏" else "还没有浏览记录",
            style = TextStyle(
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        Spacer(Modifier.height(5.dp))
        BasicText(
            text = if (isBookmarks) "在浏览页面菜单中点击“添加收藏”" else "浏览网页后会自动记录在这里",
            style = TextStyle(color = colors.textTertiary, fontSize = 12.sp)
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
