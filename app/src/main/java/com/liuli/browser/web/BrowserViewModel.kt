package com.liuli.browser.web

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.ReferrerPolicy
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.liuli.browser.data.LibraryStore
import com.liuli.browser.data.SearchEngine
import com.liuli.browser.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max

enum class AppOverlay {
    None,
    Tabs,
    Library,
    Settings
}

class FileChooserRequest(
    val params: WebChromeClient.FileChooserParams,
    val callback: ValueCallback<Array<Uri>>
)

data class DownloadRequest(
    val url: String,
    val userAgent: String,
    val contentDisposition: String,
    val mimeType: String,
    val suggestedName: String
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private var currentActivity: ComponentActivity? = null

    private var settings: SettingsStore? = null
    private var library: LibraryStore? = null
    private var bound = false

    @Volatile
    private var privacyShieldActive = true

    @Volatile
    private var fingerprintProtection = true

    private val webViews = HashMap<Long, WebView>()
    private val loadedTabs = HashSet<Long>()
    private val snapshotRunnables = HashMap<Long, Runnable>()
    private val httpUpgradeOrigins = HashMap<Long, String>()
    private val privateVisitedHosts = HashMap<Long, LinkedHashSet<String>>()
    private var container: FrameLayout? = null
    private var nextId = 1L

    private val defaultUserAgent: String = WebSettings.getDefaultUserAgent(application)
    private val chromeVersion: String by lazy {
        Regex("Chrome/([0-9.]+)")
            .find(defaultUserAgent)?.groupValues?.getOrNull(1) ?: "120.0.0.0"
    }
    private val desktopUserAgent: String by lazy {
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$chromeVersion Safari/537.36"
    }

    /**
     * A generic UA removes the device model/build fingerprint while keeping
     * Chromium feature detection intact.
     */
    private val privacyUserAgent: String by lazy {
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$chromeVersion Mobile Safari/537.36"
    }

    private fun effectiveUserAgent(tab: BrowserTab): String = when {
        tab.desktopMode -> desktopUserAgent
        settings?.privacyShield == true -> privacyUserAgent
        else -> defaultUserAgent
    }

    val tabs = mutableStateListOf<BrowserTab>()
    var activeId by mutableLongStateOf(0L)
        private set
    var overlay by mutableStateOf(AppOverlay.None)
        private set
    var webContentAlpha by mutableFloatStateOf(1f)
        private set

    var toolbarCollapsed by mutableStateOf(false)
        private set

    var fileChooserRequest by mutableStateOf<FileChooserRequest?>(null)
        private set
    var downloadRequest by mutableStateOf<DownloadRequest?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    var maxTabs: Int = 8
        private set

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenTabId: Long? = null

    val activeTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == activeId } ?: tabs.firstOrNull()

    val activeWebView: WebView?
        get() = webViews[activeId]

    init {
        val activityManager = app.getSystemService(Context.ACTIVITY_SERVICE)
            as? android.app.ActivityManager
        maxTabs = if (activityManager?.isLowRamDevice == true) 4 else 8
        restoreSession()
    }

    fun bind(
        settings: SettingsStore,
        library: LibraryStore
    ) {
        if (bound) return
        this.settings = settings
        this.library = library
        this.maxTabs = settings.maxTabs
        this.privacyShieldActive = settings.privacyShield
        this.fingerprintProtection = settings.fingerprintProtection
        bound = true
        CookieManager.getInstance().setAcceptCookie(true)
        if (tabs.isEmpty()) createDefaultTab()
        syncWebViews()
    }

    fun bindActivity(activity: ComponentActivity) {
        currentActivity = activity
        applySecureScreen()
    }

    /** 禁止截屏与最近任务预览，避免页面内容出现在系统缩略图或录屏中。 */
    private fun applySecureScreen() {
        val activity = currentActivity ?: return
        if (settings?.secureScreen == true) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun attachContainer(frameLayout: FrameLayout) {
        container = frameLayout
        webViews.forEach { (_, view) ->
            if (view.parent == null) {
                frameLayout.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }
        syncWebViews()
    }

    fun detachContainer(frameLayout: FrameLayout) {
        if (container === frameLayout) {
            frameLayout.removeAllViews()
            container = null
        }
    }

    fun createTab(
        url: String = "",
        isPrivate: Boolean = false,
        select: Boolean = true
    ): BrowserTab {
        if (!isPrivate) {
            while (tabs.count { !it.isPrivate } >= maxTabs) {
                val victim = tabs.filter { !it.isPrivate && it.id != activeId }
                    .minByOrNull { it.lastUsedAt }
                    ?: break
                closeTab(victim.id, keepOverlay = true)
            }
        }
        val sanitized = if (url.isNotBlank() && settings?.privacyShield == true) {
            TrackerBlocker.sanitize(url)
        } else {
            url
        }
        val destination = upgradeHttps(sanitized)
        val tab = BrowserTab(
            id = nextId++,
            isPrivate = isPrivate,
            initialUrl = destination,
            initialTitle = if (destination.isBlank()) "新标签页" else "",
            initialDesktopMode = settings?.desktopModeDefault == true
        )
        tabs.add(tab)
        if (select) {
            selectTabInternal(tab.id)
            webContentAlpha = 1f
        }
        if (destination.isNotBlank()) {
            rememberHttpUpgrade(tab.id, sanitized, destination)
            ensureWebView(tab)?.let { view ->
                if (loadedTabs.add(tab.id)) view.loadUrl(destination)
            }
        }
        if (isPrivate) {
            message = "隐私标签不记录历史，本地存储与脚本 Cookie 已禁用，关闭时清除该站点 Cookie"
        }
        syncWebViews()
        persistSession()
        return tab
    }

    fun selectTab(id: Long) {
        if (tabs.none { it.id == id }) return
        selectTabInternal(id)
        overlay = AppOverlay.None
        webContentAlpha = 1f
        syncWebViews()
    }

    fun beginOpenTab(id: Long): BrowserTab? {
        val tab = tabs.firstOrNull { it.id == id } ?: return null
        selectTabInternal(id)
        toolbarCollapsed = false
        overlay = AppOverlay.None
        webContentAlpha = 0f
        ensureWebView(tab)
        syncWebViews()
        return tab
    }

    fun endOpenTab() {
        webContentAlpha = 1f
        syncWebViews()
    }

    private fun selectTabInternal(id: Long) {
        activeId = id
        toolbarCollapsed = false
        tabs.firstOrNull { it.id == id }?.lastUsedAt = System.currentTimeMillis()
        ensureWebView(activeTab)
    }

    fun collapseToolbar() {
        toolbarCollapsed = true
    }

    fun expandToolbar() {
        toolbarCollapsed = false
    }

    fun closeTab(id: Long, keepOverlay: Boolean = false) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        if (fullscreenTabId == id) exitFullscreen()
        val tab = tabs[index]
        // 隐私标签关闭即清理它访问过的站点 Cookie，避免与普通浏览交叉。
        if (tab.isPrivate) clearPrivateCookies(tab.id)
        destroyWebView(id)
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            createDefaultTab()
            syncWebViews()
            return
        }
        if (activeId == id) {
            val next = tabs.getOrNull(index.coerceAtMost(tabs.lastIndex)) ?: tabs.first()
            activeId = next.id
            ensureWebView(next)
        }
        if (!keepOverlay) {
            overlay = AppOverlay.None
        }
        persistSession()
        syncWebViews()
    }

    fun createDefaultTab() {
        val tab = BrowserTab(
            id = nextId++,
            initialTitle = "新标签页",
            initialDesktopMode = settings?.desktopModeDefault == true
        )
        tabs.add(tab)
        activeId = tab.id
        persistSession()
    }

    fun openHomeTab() {
        overlay = AppOverlay.None
        webContentAlpha = 1f
        createTab(select = true)
    }

    fun navigate(input: String, searchEngine: SearchEngine? = null) {
        val normalized = normalizeUrl(input, searchEngine ?: settings?.searchEngine ?: SearchEngine.Baidu)
        if (normalized == null) {
            message = "请输入网址或搜索内容"
            return
        }
        val sanitized = if (settings?.privacyShield == true) {
            TrackerBlocker.sanitize(normalized)
        } else {
            normalized
        }
        val destination = upgradeHttps(sanitized)
        val tab = activeTab ?: return
        val view = ensureWebView(tab)
        rememberHttpUpgrade(tab.id, sanitized, destination)
        tab.url = destination
        tab.title = tab.displayHost
        tab.progress = 0
        tab.errorText = null
        tab.isLoading = true
        tab.lastUsedAt = System.currentTimeMillis()
        toolbarCollapsed = false
        overlay = AppOverlay.None
        webContentAlpha = 1f
        loadedTabs.add(tab.id)
        view?.loadUrl(destination)
        syncWebViews()
        persistSession()
    }

    fun openUrl(url: String) {
        val tab = activeTab ?: return
        val view = ensureWebView(tab)
        val sanitized = if (settings?.privacyShield == true) TrackerBlocker.sanitize(url) else url
        val destination = upgradeHttps(sanitized)
        rememberHttpUpgrade(tab.id, sanitized, destination)
        tab.url = destination
        tab.title = tab.displayHost
        tab.progress = 0
        tab.errorText = null
        tab.isLoading = true
        tab.blockedTrackers = 0
        tab.lastUsedAt = System.currentTimeMillis()
        toolbarCollapsed = false
        overlay = AppOverlay.None
        webContentAlpha = 1f
        loadedTabs.add(tab.id)
        view?.loadUrl(destination)
        syncWebViews()
        persistSession()
    }

    fun reload() {
        val tab = activeTab ?: return
        tab.errorText = null
        ensureWebView(tab)?.reload()
    }

    fun stopLoading() {
        activeTab?.let { ensureWebView(it)?.stopLoading() }
    }

    fun goBack(): Boolean {
        val view = activeTab?.let { ensureWebView(it) } ?: return false
        return if (view.canGoBack()) {
            view.goBack()
            true
        } else {
            false
        }
    }

    /**
     * Browser-level back: page history first, then the previous tab, then the
     * new-tab page. Only the new-tab page with a single tab may leave the app.
     */
    fun handleBack(): Boolean {
        if (goBack()) return true
        if (tabs.size > 1) {
            closeTab(activeId)
            return true
        }
        val tab = activeTab ?: return false
        if (tab.isHome) return false
        val view = webViews[tab.id]
        runCatching {
            view?.stopLoading()
            view?.clearHistory()
        }
        tab.url = ""
        tab.title = "新标签页"
        tab.progress = 100
        tab.isLoading = false
        tab.canGoBack = false
        tab.canGoForward = false
        tab.errorText = null
        tab.isSecure = false
        tab.snapshot = null
        toolbarCollapsed = false
        overlay = AppOverlay.None
        webContentAlpha = 1f
        syncWebViews()
        persistSession()
        return true
    }

    fun goForward(): Boolean {
        val view = activeTab?.let { ensureWebView(it) } ?: return false
        return if (view.canGoForward()) {
            view.goForward()
            true
        } else {
            false
        }
    }

    fun toggleDesktopMode() {
        val tab = activeTab ?: return
        tab.desktopMode = !tab.desktopMode
        ensureWebView(tab)?.let { view ->
            view.settings.userAgentString = effectiveUserAgent(tab)
            view.reload()
        }
        message = if (tab.desktopMode) "已切换为桌面版网站" else "已切换为移动版网站"
    }

    fun applyCookiePolicy() {
        val block = settings?.blockThirdPartyCookies ?: true
        webViews.values.forEach { view ->
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, !block)
        }
    }

    fun applyPrivacySettings() {
        privacyShieldActive = settings?.privacyShield == true
        fingerprintProtection = settings?.fingerprintProtection == true
        applySecureScreen()
        webViews.forEach { (id, view) ->
            val tab = tabs.firstOrNull { it.id == id } ?: return@forEach
            view.settings.userAgentString = effectiveUserAgent(tab)
            // 开关重新打开时立即对已加载页面补注入，无需手动刷新。
            if (fingerprintProtection) {
                PrivacyGuard.install(view)
                if (tab.isPrivate) PrivacyGuard.installPrivate(view)
            }
            if (tab.blockedTrackers > 0) tab.blockedTrackers = 0
        }
    }

    fun enterTabSwitcher(): Bitmap? {
        val tab = activeTab
        val snapshot = tab?.let { captureSnapshot(it) }
        overlay = AppOverlay.Tabs
        webContentAlpha = 0f
        syncWebViews()
        return snapshot
    }

    fun exitTabSwitcher() {
        overlay = AppOverlay.None
        webContentAlpha = 1f
        ensureWebView(activeTab)
        syncWebViews()
    }

    fun openOverlay(target: AppOverlay) {
        overlay = target
        webContentAlpha = 0f
        syncWebViews()
    }

    fun closeOverlay() {
        overlay = AppOverlay.None
        webContentAlpha = 1f
        ensureWebView(activeTab)
        syncWebViews()
    }

    fun updateWebContentAlpha(alpha: Float) {
        webContentAlpha = alpha.coerceIn(0f, 1f)
        val view = activeWebView
        if (view != null && view.visibility == View.VISIBLE) {
            view.alpha = webContentAlpha
        }
    }

    fun applyMaxTabs(value: Int) {
        maxTabs = value.coerceIn(4, 12)
    }

    fun updateTextZoom(fontScale: Float) {
        val zoom = (fontScale * 100f).toInt().coerceIn(80, 220)
        webViews.values.forEach { it.settings.textZoom = zoom }
    }

    fun captureActiveSnapshot(): Bitmap? {
        val tab = activeTab ?: return null
        return captureSnapshot(tab)
    }

    fun captureTabSnapshot(id: Long): Bitmap? {
        val tab = tabs.firstOrNull { it.id == id } ?: return null
        return captureSnapshot(tab)
    }

    fun showMessage(text: String) {
        message = text
    }

    fun consumeMessage() {
        message = null
    }

    fun consumeDownloadRequest(): DownloadRequest? {
        val request = downloadRequest
        downloadRequest = null
        return request
    }

    fun onFileChooserResult(resultCode: Int, data: Intent?) {
        val request = fileChooserRequest ?: return
        fileChooserRequest = null
        val uris = runCatching {
            WebChromeClient.FileChooserParams.parseResult(resultCode, data)
        }.getOrNull()
        request.callback.onReceiveValue(uris)
    }

    fun cancelFileChooser() {
        fileChooserRequest?.callback?.onReceiveValue(null)
        fileChooserRequest = null
    }

    fun startDownload(request: DownloadRequest, destination: Uri) {
        viewModelScope.launch {
            var connection: HttpURLConnection? = null
            try {
                withContext(Dispatchers.IO) {
                    val url = URL(request.url)
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 20_000
                        readTimeout = 30_000
                        setRequestProperty("User-Agent", request.userAgent)
                        CookieManager.getInstance().getCookie(request.url)?.let {
                            setRequestProperty("Cookie", it)
                        }
                    }
                    val code = connection!!.responseCode
                    if (code !in 200..299) {
                        throw IllegalStateException("HTTP $code")
                    }
                    val output = app.contentResolver.openOutputStream(destination)
                        ?: throw IllegalStateException("无法写入目标文件")
                    output.use { out ->
                        connection!!.inputStream.use { input ->
                            input.copyTo(out)
                        }
                    }
                }
                message = "下载完成"
            } catch (error: Exception) {
                message = "下载失败：${error.message ?: "网络错误"}"
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun toggleBookmark(): Boolean {
        val tab = activeTab ?: return false
        val store = library ?: return false
        val url = tab.url.ifBlank { return false }
        val added = store.toggleBookmark(tab.pageTitle(), url)
        message = if (added) "已添加到收藏" else "已取消收藏"
        return added
    }

    fun isActiveBookmarked(): Boolean {
        val tab = activeTab ?: return false
        return library?.isBookmarked(tab.url) == true
    }

    fun openLibraryEntry(url: String) {
        if (url.isBlank()) return
        val tab = activeTab
        if (tab == null || !tab.isHome) {
            val newTab = createTab(url = url, select = true)
            ensureWebView(newTab)?.let { view ->
                if (loadedTabs.add(newTab.id)) view.loadUrl(url)
            }
        } else {
            openUrl(url)
        }
        overlay = AppOverlay.None
        webContentAlpha = 1f
        syncWebViews()
    }

    fun shareActivePage(): String {
        val tab = activeTab ?: return ""
        return tab.url
    }

    fun clearBrowsingData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        android.webkit.WebStorage.getInstance().deleteAllData()
        webViews.values.forEach { view ->
            view.clearHistory()
            view.clearFormData()
            view.clearCache(true)
        }
        library?.clearAll()
        message = "浏览数据已清除"
        syncWebViews()
    }

    fun resetAll() {
        exitFullscreen()
        android.webkit.WebStorage.getInstance().deleteAllData()
        webViews.values.forEach { view ->
            view.clearCache(true)
            view.clearHistory()
            view.clearFormData()
        }
        webViews.keys.toList().forEach { destroyWebView(it) }
        tabs.clear()
        loadedTabs.clear()
        overlay = AppOverlay.None
        webContentAlpha = 1f
        activeId = 0
        nextId = 1
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        library?.clearAll()
        createDefaultTab()
        syncWebViews()
    }

    fun pause() {
        webViews.values.forEach { view ->
            if (view.isAttachedToWindow) view.onPause()
        }
    }

    fun resume() {
        webViews.values.forEach { view ->
            if (view.isAttachedToWindow) view.onResume()
        }
    }

    fun onTrimMemory(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) {
            tabs.forEach { tab ->
                if (tab.id != activeId) tab.snapshot = null
            }
        }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            // Fair-memory policy: release the least recently used background
            // WebViews first. Their URL is kept and reloaded on demand.
            tabs.filter { it.id != activeId && webViews.containsKey(it.id) }
                .sortedBy { it.lastUsedAt }
                .take(2)
                .forEach { destroyWebView(it.id) }
        }
    }

    fun persistSession() {
        val data = JSONObject()
        data.put("active", activeId)
        val array = JSONArray()
        tabs.filter { !it.isPrivate }.forEach { tab ->
            array.put(
                JSONObject()
                    .put("id", tab.id)
                    .put("url", tab.url)
                    .put("title", tab.title)
                    .put("desktop", tab.desktopMode)
            )
        }
        data.put("tabs", array)
        app.getSharedPreferences(SESSION_PREFS, Application.MODE_PRIVATE)
            .edit().putString(KEY_SESSION, data.toString()).apply()
    }

    private fun restoreSession() {
        val raw = app.getSharedPreferences(SESSION_PREFS, Application.MODE_PRIVATE)
            .getString(KEY_SESSION, null)
        var restoredActive = 0L
        var maxId = 0L
        if (raw != null) {
            runCatching {
                val data = JSONObject(raw)
                val array = data.optJSONArray("tabs") ?: JSONArray()
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    val url = item.optString("url")
                    val title = item.optString("title")
                    val desktop = item.optBoolean("desktop")
                    val id = if (item.has("id")) item.getLong("id") else (index + 1).toLong()
                    maxId = max(maxId, id)
                    if (url.isBlank() && tabs.isNotEmpty()) return@repeat
                    tabs.add(
                        BrowserTab(
                            id = id,
                            initialUrl = url,
                            initialTitle = title,
                            initialDesktopMode = desktop
                        )
                    )
                }
                restoredActive = data.optLong("active", 0L)
            }
        }
        if (tabs.isEmpty()) {
            val tab = BrowserTab(id = 1L, initialTitle = "新标签页")
            tabs.add(tab)
            activeId = tab.id
            nextId = 2L
        } else {
            nextId = maxId + 1
            activeId = tabs.firstOrNull { it.id == restoredActive }?.id ?: tabs.first().id
        }
    }

    private fun normalizeUrl(input: String, engine: SearchEngine): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        if (lower.startsWith("javascript:") ||
            lower.startsWith("file:") ||
            lower.startsWith("content:") ||
            lower.startsWith("data:")
        ) {
            return null
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) return trimmed
        val looksLikeHost = Regex("^[A-Za-z0-9\\u4e00-\\u9fa5.-]+(:\\d+)?(/.*)?$")
            .matches(trimmed) && trimmed.contains('.')
        if (looksLikeHost) {
            return "https://$trimmed"
        }
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        return engine.queryUrl.format(encoded)
    }

    /**
     * 将 HTTP 升级为 HTTPS，避免明文传输泄露访问记录与页面内容。
     * 仅对 http:// 开头的主框架 URL 生效，其余保持不变。
     */
    private fun upgradeHttps(url: String): String =
        if (url.startsWith("http://", ignoreCase = true)) {
            "https://" + url.substring("http://".length)
        } else {
            url
        }

    /**
     * 仅当本次导航确实把 HTTP 升级为 HTTPS 时记录来源，
     * 以便握手失败时提示「站点不支持加密连接」而不是笼统报错。
     */
    private fun rememberHttpUpgrade(id: Long, source: String, destination: String) {
        if (source.startsWith("http://", ignoreCase = true) &&
            destination.startsWith("https://", ignoreCase = true)
        ) {
            httpUpgradeOrigins[id] = source
        } else {
            httpUpgradeOrigins.remove(id)
        }
    }

    /** 记录隐私标签访问过的主机，关闭标签时按站点清理 Cookie。 */
    private fun rememberPrivateHost(id: Long, url: String) {
        val host = runCatching { Uri.parse(url).host }.getOrNull()
            ?.lowercase()?.takeIf { it.isNotBlank() } ?: return
        privateVisitedHosts.getOrPut(id) { LinkedHashSet() }.add(host)
    }

    /**
     * CookieManager 没有按域名删除的 API，这里读取该站点的 Cookie 头，
     * 再用 Max-Age=0 逐条过期。HttpOnly 或非根路径 Cookie 可能残留，属尽力而为。
     */
    private fun clearPrivateCookies(id: Long) {
        val hosts = privateVisitedHosts.remove(id) ?: return
        if (hosts.isEmpty()) return
        val cookies = CookieManager.getInstance()
        hosts.forEach { host ->
            for (base in listOf("https://$host/", "http://$host/")) {
                val header = runCatching { cookies.getCookie(base) }.getOrNull() ?: continue
                header.split(';').forEach { pair ->
                    val name = pair.substringBefore('=').trim()
                    if (name.isNotEmpty()) {
                        runCatching {
                            cookies.setCookie(base, "$name=; Max-Age=0; Path=/")
                        }
                    }
                }
            }
        }
        runCatching { cookies.flush() }
    }

    private fun ensureWebView(tab: BrowserTab?): WebView? {
        if (tab == null || !bound) return null
        webViews[tab.id]?.let { return it }
        val store = settings
        val webView = WebView(currentActivity ?: app).apply {
            tag = tab.id
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            val systemNight = (app.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val darkSurface = store?.darkModeOption == com.liuli.browser.ui.theme.DarkModeOption.Dark ||
                (store?.darkMode == 0 && systemNight)
            setBackgroundColor(if (darkSurface) Color.rgb(12, 16, 28) else Color.WHITE)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            configureSettings(this, tab)
            webViewClient = createWebViewClient()
            webChromeClient = createChromeClient()
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                if (!URLUtil.isNetworkUrl(url)) {
                    message = "暂不支持该下载链接"
                    return@setDownloadListener
                }
                downloadRequest = DownloadRequest(
                    url = url,
                    userAgent = userAgent ?: defaultUserAgent,
                    contentDisposition = contentDisposition ?: "",
                    mimeType = mimeType ?: "application/octet-stream",
                    suggestedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                )
            }
            setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                if (store?.autoHideToolbar != false) {
                    if (scrollY - oldScrollY > 8 && scrollY > 120) {
                        this@BrowserViewModel.toolbarCollapsed = true
                    } else if (oldScrollY - scrollY > 6 || scrollY <= 4) {
                        this@BrowserViewModel.toolbarCollapsed = false
                    }
                }
                scheduleSnapshot(this, tab)
            }
        }
        webViews[tab.id] = webView
        container?.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        if (tab.url.isNotBlank() && tab.id !in loadedTabs) {
            loadedTabs.add(tab.id)
            webView.loadUrl(tab.url)
        }
        return webView
    }

    private fun configureSettings(webView: WebView, tab: BrowserTab) {
        val blockThirdParty = settings?.blockThirdPartyCookies ?: true
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, !blockThirdParty)
        with(webView.settings) {
            javaScriptEnabled = true
            // 隐私标签在引擎层直接关闭 WebStorage，杜绝脚本抢先写入的可能。
            domStorageEnabled = !tab.isPrivate
            @Suppress("DEPRECATION")
            databaseEnabled = false
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            allowFileAccess = false
            allowContentAccess = false
            // Prevent the page from leaking the full URL (with tracking params)
            // or the current site in the Referer header of cross-origin requests.
            referrerPolicy = ReferrerPolicy.NO_REFERRER
            @Suppress("DEPRECATION")
            setPluginState(WebSettings.PluginState.OFF)
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = if (tab.isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            userAgentString = effectiveUserAgent(tab)
            setGeolocationEnabled(false)
            setNeedInitialFocus(true)
            @Suppress("DEPRECATION")
            setSafeBrowsingEnabled(true)
            @Suppress("DEPRECATION")
            setSavePassword(false)
            @Suppress("DEPRECATION")
            setSaveFormData(false)
            textZoom = (app.resources.configuration.fontScale * 100f)
                .toInt().coerceIn(80, 220)
        }
    }

    private fun createWebViewClient(): WebViewClient = object : WebViewClient() {

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            // This callback runs off the UI thread; only touch Compose state
            // after posting back to the main thread.
            if (privacyShieldActive &&
                !request.isForMainFrame &&
                TrackerBlocker.isTracker(request.url)
            ) {
                val id = view.tag as? Long
                if (id != null) {
                    view.post {
                        tabs.firstOrNull { it.id == id }?.let { tab ->
                            tab.blockedTrackers += 1
                        }
                    }
                }
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    ByteArrayInputStream(ByteArray(0))
                )
            }
            return null
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean = handleUrl(
            view,
            request.url.toString(),
            request.isForMainFrame,
            request.isRedirect
        )

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
            handleUrl(view, url, true, false)

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            val tab = tabFor(view) ?: return
            if (tab.isPrivate && !url.isNullOrBlank()) rememberPrivateHost(tab.id, url)
            // Inject the anti-fingerprint script as early as possible. It is
            // idempotent and only takes effect on the current document.
            if (fingerprintProtection) {
                PrivacyGuard.install(view)
            }
            // Private tabs additionally get storage isolation so their data
            // never mixes with regular browsing.
            if (tab.isPrivate) {
                PrivacyGuard.installPrivate(view)
            }
            if (url != null && url != tab.url) tab.blockedTrackers = 0
            tab.url = url ?: tab.url
            tab.isLoading = true
            tab.progress = max(tab.progress, 6)
            tab.errorText = null
            tab.isSecure = url?.startsWith("https://") == true
            tab.title = if (tab.title.isBlank()) tab.displayHost else tab.title
            updateNavigationState(tab, view)
            syncWebViews()
        }

        override fun onPageFinished(view: WebView, url: String?) {
            val tab = tabFor(view) ?: return
            tab.isLoading = false
            tab.progress = 100
            if (!url.isNullOrBlank() && !url.startsWith("about:")) {
                tab.url = url
                tab.isSecure = url.startsWith("https://")
                if (tab.isSecure) {
                    (view.tag as? Long)?.let { httpUpgradeOrigins.remove(it) }
                }
            }
            if (tab.title.isBlank() || tab.title == "新标签页") {
                tab.title = view.title?.takeIf { it.isNotBlank() } ?: tab.displayHost
            }
            tab.lastUsedAt = System.currentTimeMillis()
            updateNavigationState(tab, view)
            if (!tab.isPrivate && tab.errorText == null && tab.url.isNotBlank()) {
                library?.addHistory(tab.pageTitle(), tab.url)
            }
            scheduleSnapshot(view, tab)
            syncWebViews()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (!request.isForMainFrame) return
            val tab = tabFor(view) ?: return
            val id = view.tag as? Long
            val upgradedFrom = id?.let { httpUpgradeOrigins.remove(it) }
            tab.isLoading = false
            tab.errorText = if (upgradedFrom != null) {
                "该网站不支持 HTTPS 加密连接，为保护隐私已阻止明文访问"
            } else {
                error.description?.toString()?.takeIf { it.isNotBlank() } ?: "无法打开网页"
            }
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            error: android.net.http.SslError
        ) {
            // A browser must never silently bypass certificate validation.
            handler.cancel()
            val tab = tabFor(view) ?: return
            val id = view.tag as? Long
            val upgradedFrom = id?.let { httpUpgradeOrigins.remove(it) }
            tab.isLoading = false
            tab.errorText = if (upgradedFrom != null) {
                "该网站不支持 HTTPS 加密连接（证书校验未通过），为保护隐私已阻止明文访问"
            } else {
                "安全连接失败，证书校验未通过，访问已被阻止"
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            val tab = tabFor(view) ?: return
            if (tab.isPrivate && !url.isNullOrBlank()) rememberPrivateHost(tab.id, url)
            if (!url.isNullOrBlank()) {
                tab.url = url
                tab.isSecure = url.startsWith("https://")
            }
            updateNavigationState(tab, view)
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail
        ): Boolean {
            val tab = tabFor(view)
            if (tab != null) {
                (view.parent as? ViewGroup)?.removeView(view)
                webViews.remove(tab.id)
                loadedTabs.remove(tab.id)
                httpUpgradeOrigins.remove(tab.id)
                view.destroy()
                tab.isLoading = false
                tab.progress = 0
                tab.errorText = "网页渲染进程异常退出，已安全恢复，请点击重试"
                syncWebViews()
            }
            return true
        }
    }

    private fun handleUrl(
        view: WebView,
        url: String,
        mainFrame: Boolean,
        isRedirect: Boolean
    ): Boolean {
        val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
        if (scheme == "http" || scheme == "https") {
            // 将主框架的 HTTP 导航升级为 HTTPS，避免明文传输泄露访问记录。
            if (scheme == "http" && mainFrame) {
                val id = view.tag as? Long
                // 服务器把 HTTPS 重定向回同一站点的明文地址时终止循环。
                val pendingHost = id?.let { httpUpgradeOrigins[it] }
                    ?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                val targetHost = runCatching { Uri.parse(url).host }.getOrNull()
                if (isRedirect && pendingHost != null && pendingHost == targetHost) {
                    if (id != null) httpUpgradeOrigins.remove(id)
                    tabFor(view)?.let {
                        it.isLoading = false
                        it.errorText = "该网站将加密连接重定向回明文 HTTP，为保护隐私已阻止访问"
                    }
                    return true
                }
                val upgraded = upgradeHttps(url)
                if (upgraded != url) {
                    if (id != null) httpUpgradeOrigins[id] = url
                    view.loadUrl(upgraded)
                    return true
                }
            }
            if (mainFrame && !isRedirect && settings?.privacyShield == true) {
                val sanitized = TrackerBlocker.sanitize(url)
                if (sanitized != url) {
                    view.loadUrl(sanitized)
                    return true
                }
            }
            return false
        }
        return when {
            scheme == "about" || scheme == "javascript" -> false
            scheme == null -> false
            url.startsWith("blob:") -> {
                val tab = tabFor(view)
                if (tab != null && mainFrame) tab.errorText = "该页面使用了浏览器不支持的下载方式"
                true
            }
            scheme == "file" || scheme == "content" || scheme == "data" -> {
                message = "为保护隐私，已阻止访问本地文件"
                true
            }
            else -> {
                openExternal(url)
                true
            }
        }
    }

    private fun openExternal(url: String) {
        val intent = runCatching {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }.getOrNull()
        if (intent == null) {
            message = "无法识别该链接"
            return
        }
        try {
            app.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            message = "未找到可处理该链接的应用"
        } catch (_: SecurityException) {
            message = "该链接被系统安全策略阻止"
        }
    }

    private fun createChromeClient(): WebChromeClient = object : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            val tab = tabFor(view) ?: return
            tab.progress = newProgress.coerceIn(0, 100)
            if (newProgress in 1..99) tab.isLoading = true
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            val tab = view?.let { tabFor(it) } ?: activeTab ?: return
            if (!title.isNullOrBlank()) {
                tab.title = title
                if (tab.url.isNotBlank()) {
                    library?.updateHistoryTitle(tab.url, title)
                }
            }
        }

        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
            val tab = view?.let { tabFor(it) } ?: activeTab ?: return
            tab.favicon = icon
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: android.os.Message
        ): Boolean {
            // Popups without a user gesture are blocked, matching the Chromium
            // popup blocker and avoiding unsolicited tabs.
            if (!isUserGesture) return false
            val source = tabFor(view)
            val newTab = createTab(
                url = "",
                isPrivate = source?.isPrivate == true,
                select = true
            )
            val newView = ensureWebView(newTab) ?: return false
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            transport.webView = newView
            resultMsg.sendToTarget()
            overlay = AppOverlay.None
            webContentAlpha = 1f
            syncWebViews()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            val tab = tabFor(window) ?: return
            closeTab(tab.id)
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            fileChooserRequest?.callback?.onReceiveValue(null)
            fileChooserRequest = FileChooserRequest(fileChooserParams, filePathCallback)
            return true
        }

        override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
            // Camera, microphone and other sensitive capabilities are denied by
            // default; the application never asks for these system permissions.
            request.deny()
            message = "已阻止网页申请敏感权限"
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: android.webkit.GeolocationPermissions.Callback
        ) {
            callback.invoke(origin, false, false)
        }

        override fun onShowCustomView(
            view: View,
            callback: CustomViewCallback
        ) {
            val activity = currentActivity ?: run {
                callback.onCustomViewHidden()
                return
            }
            exitFullscreen(notify = false)
            val decor = activity.window.decorView as? ViewGroup ?: run {
                callback.onCustomViewHidden()
                return
            }
            customView = view
            customViewCallback = callback
            fullscreenTabId = activeId
            activeTab?.isFullscreen = true
            view.setBackgroundColor(Color.BLACK)
            decor.addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowInsetsControllerCompat(activity.window, decor).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        override fun onHideCustomView() {
            exitFullscreen(notify = true)
        }
    }

    fun exitFullscreen(notify: Boolean = true) {
        val activity = currentActivity
        val view = customView
        val callback = customViewCallback
        customView = null
        customViewCallback = null
        if (view != null) {
            (view.parent as? ViewGroup)?.removeView(view)
        }
        if (notify) {
            callback?.onCustomViewHidden()
        }
        val tab = tabs.firstOrNull { it.id == fullscreenTabId }
        tab?.isFullscreen = false
        fullscreenTabId = null
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun tabFor(view: WebView): BrowserTab? {
        val id = view.tag as? Long ?: return null
        return tabs.firstOrNull { it.id == id }
    }

    private fun updateNavigationState(tab: BrowserTab, view: WebView) {
        tab.canGoBack = view.canGoBack()
        tab.canGoForward = view.canGoForward()
    }

    private fun scheduleSnapshot(view: WebView, tab: BrowserTab) {
        snapshotRunnables.remove(tab.id)?.let { view.removeCallbacks(it) }
        val runnable = Runnable { captureSnapshot(tab) }
        snapshotRunnables[tab.id] = runnable
        view.postDelayed(runnable, SNAPSHOT_DELAY_MS)
    }

    private fun captureSnapshot(tab: BrowserTab): Bitmap? {
        val view = webViews[tab.id] ?: return null
        if (view.width <= 0 || view.height <= 0 || view.visibility != View.VISIBLE) {
            return tab.snapshot
        }
        return runCatching {
            val scale = SNAPSHOT_SCALE
            val width = (view.width * scale).toInt().coerceAtLeast(1)
            val height = (view.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            view.draw(canvas)
            val location = IntArray(2)
            view.getLocationInWindow(location)
            tab.snapshot = bitmap
            tab.snapshotBounds = Rect(
                location[0],
                location[1],
                location[0] + view.width,
                location[1] + view.height
            )
            bitmap
        }.getOrNull()
    }

    private fun syncWebViews() {
        val tab = activeTab
        val shouldShow = overlay == AppOverlay.None && tab?.url?.isNotBlank() == true
        if (shouldShow) ensureWebView(tab)
        webViews.forEach { (id, view) ->
            view.visibility = if (shouldShow && id == activeId) View.VISIBLE else View.GONE
            view.alpha = if (id == activeId) webContentAlpha else 1f
        }
    }

    private fun destroyWebView(id: Long) {
        val view = webViews.remove(id) ?: return
        loadedTabs.remove(id)
        httpUpgradeOrigins.remove(id)
        snapshotRunnables.remove(id)?.let { view.removeCallbacks(it) }
        (view.parent as? ViewGroup)?.removeView(view)
        runCatching {
            view.stopLoading()
            view.loadUrl("about:blank")
            view.clearHistory()
            view.removeAllViews()
            view.destroy()
        }
    }

    override fun onCleared() {
        exitFullscreen(notify = false)
        val shouldClear = settings?.clearOnExit == true
        if (shouldClear) {
            clearPrivateData()
        } else {
            // 即使未开启“退出即清除”，隐私标签的站点 Cookie 也不保留。
            tabs.filter { it.isPrivate }.forEach { clearPrivateCookies(it.id) }
            persistSession()
        }
        webViews.keys.toList().forEach { destroyWebView(it) }
        currentActivity = null
        super.onCleared()
    }

    private fun clearPrivateData() {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            webViews.values.forEach { view ->
                view.clearCache(true)
                view.clearFormData()
                view.clearHistory()
            }
            library?.clearHistory()
            app.getSharedPreferences(SESSION_PREFS, Application.MODE_PRIVATE)
                .edit().remove(KEY_SESSION).apply()
        }
    }

    companion object {
        private const val SESSION_PREFS = "liuli_session"
        private const val KEY_SESSION = "session"
        private const val SNAPSHOT_SCALE = 0.45f
        private const val SNAPSHOT_DELAY_MS = 320L
    }
}
