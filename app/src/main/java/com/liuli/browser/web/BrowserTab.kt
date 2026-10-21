package com.liuli.browser.web

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.net.Uri

@Stable
class BrowserTab(
    val id: Long,
    val isPrivate: Boolean = false,
    initialUrl: String = "",
    initialTitle: String = "",
    initialDesktopMode: Boolean = false
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf(initialTitle.ifBlank { "新标签页" })
    var progress by mutableIntStateOf(if (initialUrl.isBlank()) 100 else 0)
    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var isSecure by mutableStateOf(initialUrl.startsWith("https://"))
    var isFullscreen by mutableStateOf(false)
    var errorText by mutableStateOf<String?>(null)
    var desktopMode by mutableStateOf(initialDesktopMode)
    var favicon by mutableStateOf<Bitmap?>(null)
    var snapshot by mutableStateOf<Bitmap?>(null)
    var snapshotBounds by mutableStateOf(Rect())
    var lastUsedAt by mutableStateOf(System.currentTimeMillis())
    var blockedTrackers by mutableIntStateOf(0)

    val isHome: Boolean get() = url.isBlank()

    val displayHost: String
        get() {
            if (url.isBlank()) return "新标签页"
            val host = runCatching { Uri.parse(url).host }.getOrNull()
            return host?.removePrefix("www.") ?: url
        }

    val securityLabel: String
        get() = when {
            url.startsWith("https://") -> "安全连接"
            url.startsWith("http://") -> "不安全连接"
            else -> "本地页面"
        }

    fun pageTitle(): String = title.ifBlank { displayHost }
}
