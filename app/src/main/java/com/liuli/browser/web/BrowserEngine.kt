package com.liuli.browser.web

import android.content.Context
import android.webkit.CookieManager
import android.webkit.ReferrerPolicy
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * 当前内核抽象以系统 WebView 为边界，集中管理内核识别、WebSettings 配置
 * 与隐私默认值。未来若要接入 GeckoView，需要在这一层之上再扩展 View 适配。
 */
interface BrowserEngine {
    val label: String

    fun info(context: Context): BrowserEngineInfo

    fun defaultUserAgent(context: Context): String

    fun create(context: Context): WebView

    fun configure(view: WebView, config: BrowserEngineConfig)
}

data class BrowserEngineConfig(
    val isPrivate: Boolean,
    val userAgent: String,
    val blockThirdPartyCookies: Boolean,
    val textZoomPercent: Int
)

enum class BrowserEngineStatus {
    Current,
    Aging,
    Unsupported,
    Unknown
}

data class BrowserEngineInfo(
    val provider: String,
    val versionName: String?,
    val chromiumMajor: Int?,
    val minimumMajor: Int = WebViewBaseline.MIN_CHROMIUM_MAJOR,
    val recommendedMajor: Int = WebViewBaseline.RECOMMENDED_CHROMIUM_MAJOR
) {
    val status: BrowserEngineStatus
        get() {
            val major = chromiumMajor
            return when {
                major == null -> BrowserEngineStatus.Unknown
                major < minimumMajor -> BrowserEngineStatus.Unsupported
                major < recommendedMajor -> BrowserEngineStatus.Aging
                else -> BrowserEngineStatus.Current
            }
        }

    val isUnsupported: Boolean
        get() = status == BrowserEngineStatus.Unsupported

    val statusLabel: String
        get() = when (status) {
            BrowserEngineStatus.Current -> "安全基线内"
            BrowserEngineStatus.Aging -> "版本偏低，建议更新"
            BrowserEngineStatus.Unsupported -> "低于安全基线"
            BrowserEngineStatus.Unknown -> "版本未知"
        }

    val versionLabel: String
        get() = versionName ?: "未知"
}

/**
 * 安全基线按 Chromium 发布节奏定期复核。低于最低版本时启动会提示更新；
 * 介于最低与建议版本之间时只提示，不阻断浏览。
 */
object WebViewBaseline {
    const val MIN_CHROMIUM_MAJOR = 120
    const val RECOMMENDED_CHROMIUM_MAJOR = 130
}

class SystemWebViewEngine : BrowserEngine {

    override val label = "系统 Chromium WebView"

    override fun info(context: Context): BrowserEngineInfo {
        val pkg = runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()
        val version = pkg?.versionName
        val major = version
            ?.let { Regex("^([0-9]+)\\.").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
        return BrowserEngineInfo(
            provider = pkg?.packageName ?: label,
            versionName = version,
            chromiumMajor = major
        )
    }

    override fun defaultUserAgent(context: Context): String =
        WebSettings.getDefaultUserAgent(context)

    override fun create(context: Context): WebView = WebView(context)

    override fun configure(view: WebView, config: BrowserEngineConfig) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(
            view,
            !config.blockThirdPartyCookies
        )
        with(view.settings) {
            javaScriptEnabled = true
            // 隐私标签在引擎层直接关闭 WebStorage，杜绝脚本抢先写入的可能。
            domStorageEnabled = !config.isPrivate
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
            // 不向跨站请求泄露完整 URL 或当前站点。
            referrerPolicy = ReferrerPolicy.NO_REFERRER
            @Suppress("DEPRECATION")
            setPluginState(WebSettings.PluginState.OFF)
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = if (config.isPrivate) {
                WebSettings.LOAD_NO_CACHE
            } else {
                WebSettings.LOAD_DEFAULT
            }
            userAgentString = config.userAgent
            setGeolocationEnabled(false)
            setNeedInitialFocus(true)
            // 保留系统安全浏览以换取钓鱼防护；如需承诺完全不接触 Google，
            // 应在此关闭，并明确说明防护能力会下降。
            @Suppress("DEPRECATION")
            setSafeBrowsingEnabled(true)
            @Suppress("DEPRECATION")
            setSavePassword(false)
            @Suppress("DEPRECATION")
            setSaveFormData(false)
            textZoom = config.textZoomPercent
        }
    }
}
