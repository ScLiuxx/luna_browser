package com.liuli.browser.data

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liuli.browser.ui.theme.DarkModeOption

enum class SearchEngine(
    val id: String,
    val label: String,
    val queryUrl: String,
    val homeUrl: String
) {
    Baidu(
        id = "baidu",
        label = "百度",
        queryUrl = "https://www.baidu.com/s?wd=%s",
        homeUrl = "https://m.baidu.com/"
    ),
    Bing(
        id = "bing",
        label = "Bing",
        queryUrl = "https://cn.bing.com/search?q=%s",
        homeUrl = "https://cn.bing.com/"
    ),
    Google(
        id = "google",
        label = "Google",
        queryUrl = "https://www.google.com/search?q=%s",
        homeUrl = "https://www.google.com/"
    ),
    DuckDuckGo(
        id = "duckduckgo",
        label = "DuckDuckGo",
        queryUrl = "https://duckduckgo.com/?q=%s",
        homeUrl = "https://duckduckgo.com/"
    );

    companion object {
        fun fromId(id: String?): SearchEngine =
            entries.firstOrNull { it.id == id } ?: Baidu
    }
}

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var privacyAccepted by mutableStateOf(prefs.getBoolean(KEY_PRIVACY, false))
        private set

    var darkMode by mutableIntStateOf(prefs.getInt(KEY_DARK_MODE, 0))
        private set

    var accentIndex by mutableIntStateOf(prefs.getInt(KEY_ACCENT, 0))
        private set

    var dynamicBackground by mutableStateOf(prefs.getBoolean(KEY_DYNAMIC_BG, true))
        private set

    var reduceMotion by mutableStateOf(prefs.getBoolean(KEY_REDUCE_MOTION, false))
        private set

    var desktopModeDefault by mutableStateOf(prefs.getBoolean(KEY_DESKTOP, false))
        private set

    var blockThirdPartyCookies by mutableStateOf(prefs.getBoolean(KEY_BLOCK_3P, true))
        private set

    var privacyShield by mutableStateOf(prefs.getBoolean(KEY_SHIELD, true))
        private set

    var fingerprintProtection by mutableStateOf(prefs.getBoolean(KEY_FINGERPRINT, true))
        private set

    var clearOnExit by mutableStateOf(prefs.getBoolean(KEY_CLEAR_EXIT, true))
        private set

    var autoHideToolbar by mutableStateOf(prefs.getBoolean(KEY_AUTO_HIDE, true))
        private set

    var maxTabs by mutableIntStateOf(prefs.getInt(KEY_MAX_TABS, defaultMaxTabs(context)))
        private set

    var searchEngine by mutableStateOf(SearchEngine.fromId(prefs.getString(KEY_SEARCH, null)))
        private set

    var agentEnabled by mutableStateOf(prefs.getBoolean(KEY_AGENT_ENABLED, true))
        private set

    var agentActionsEnabled by mutableStateOf(prefs.getBoolean(KEY_AGENT_ACTIONS, true))
        private set

    var agentCloudEnabled by mutableStateOf(prefs.getBoolean(KEY_AGENT_CLOUD, false))
        private set

    var agentEndpoint by mutableStateOf(
        prefs.getString(KEY_AGENT_ENDPOINT, DEFAULT_AGENT_ENDPOINT) ?: DEFAULT_AGENT_ENDPOINT
    )
        private set

    var agentModel by mutableStateOf(
        prefs.getString(KEY_AGENT_MODEL, DEFAULT_AGENT_MODEL) ?: DEFAULT_AGENT_MODEL
    )
        private set

    /** Kept in memory only: it is never written to SharedPreferences. */
    var agentApiKey by mutableStateOf("")
        private set

    val darkModeOption: DarkModeOption
        get() = when (darkMode) {
            1 -> DarkModeOption.Light
            2 -> DarkModeOption.Dark
            else -> DarkModeOption.System
        }

    fun acceptPrivacy() {
        privacyAccepted = true
        prefs.edit().putBoolean(KEY_PRIVACY, true).apply()
    }

    fun revokePrivacy() {
        privacyAccepted = false
        prefs.edit().putBoolean(KEY_PRIVACY, false).apply()
    }

    fun revokePrivacyAndReset() {
        prefs.edit().clear().apply()
        privacyAccepted = false
        darkMode = 0
        accentIndex = 0
        dynamicBackground = true
        reduceMotion = false
        desktopModeDefault = false
        blockThirdPartyCookies = true
        privacyShield = true
        fingerprintProtection = true
        clearOnExit = true
        autoHideToolbar = true
        maxTabs = 8
        searchEngine = SearchEngine.Baidu
        agentEnabled = true
        agentActionsEnabled = true
        agentCloudEnabled = false
        agentEndpoint = DEFAULT_AGENT_ENDPOINT
        agentModel = DEFAULT_AGENT_MODEL
        agentApiKey = ""
    }

    fun updateDarkMode(value: Int) {
        darkMode = value.coerceIn(0, 2)
        prefs.edit().putInt(KEY_DARK_MODE, darkMode).apply()
    }

    fun updateAccentIndex(value: Int) {
        accentIndex = value
        prefs.edit().putInt(KEY_ACCENT, accentIndex).apply()
    }

    fun updateDynamicBackground(value: Boolean) {
        dynamicBackground = value
        prefs.edit().putBoolean(KEY_DYNAMIC_BG, value).apply()
    }

    fun updateReduceMotion(value: Boolean) {
        reduceMotion = value
        prefs.edit().putBoolean(KEY_REDUCE_MOTION, value).apply()
    }

    fun updateDesktopModeDefault(value: Boolean) {
        desktopModeDefault = value
        prefs.edit().putBoolean(KEY_DESKTOP, value).apply()
    }

    fun updateBlockThirdPartyCookies(value: Boolean) {
        blockThirdPartyCookies = value
        prefs.edit().putBoolean(KEY_BLOCK_3P, value).apply()
    }

    fun updateSearchEngine(engine: SearchEngine) {
        searchEngine = engine
        prefs.edit().putString(KEY_SEARCH, engine.id).apply()
    }

    fun updateMaxTabs(value: Int) {
        maxTabs = value.coerceIn(4, 12)
        prefs.edit().putInt(KEY_MAX_TABS, maxTabs).apply()
    }

    fun updatePrivacyShield(value: Boolean) {
        privacyShield = value
        prefs.edit().putBoolean(KEY_SHIELD, value).apply()
    }

    fun updateFingerprintProtection(value: Boolean) {
        fingerprintProtection = value
        prefs.edit().putBoolean(KEY_FINGERPRINT, value).apply()
    }

    fun updateClearOnExit(value: Boolean) {
        clearOnExit = value
        prefs.edit().putBoolean(KEY_CLEAR_EXIT, value).apply()
    }

    fun updateAutoHideToolbar(value: Boolean) {
        autoHideToolbar = value
        prefs.edit().putBoolean(KEY_AUTO_HIDE, value).apply()
    }

    fun updateAgentEnabled(value: Boolean) {
        agentEnabled = value
        prefs.edit().putBoolean(KEY_AGENT_ENABLED, value).apply()
    }

    fun updateAgentActionsEnabled(value: Boolean) {
        agentActionsEnabled = value
        prefs.edit().putBoolean(KEY_AGENT_ACTIONS, value).apply()
    }

    fun updateAgentCloudEnabled(value: Boolean) {
        agentCloudEnabled = value
        prefs.edit().putBoolean(KEY_AGENT_CLOUD, value).apply()
    }

    fun updateAgentEndpoint(value: String) {
        agentEndpoint = value.take(200)
        prefs.edit().putString(KEY_AGENT_ENDPOINT, agentEndpoint).apply()
    }

    fun updateAgentModel(value: String) {
        agentModel = value.take(80)
        prefs.edit().putString(KEY_AGENT_MODEL, agentModel).apply()
    }

    fun updateAgentApiKey(value: String) {
        agentApiKey = value.trim().take(300)
    }

    private fun defaultMaxTabs(context: Context): Int {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return if (manager?.isLowRamDevice == true) 4 else 8
    }

    companion object {
        private const val PREFS_NAME = "liuli_settings"
        private const val KEY_PRIVACY = "privacy_accepted"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_ACCENT = "accent"
        private const val KEY_DYNAMIC_BG = "dynamic_background"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_DESKTOP = "desktop_default"
        private const val KEY_BLOCK_3P = "block_third_party_cookies"
        private const val KEY_SEARCH = "search_engine"
        private const val KEY_MAX_TABS = "max_tabs"
        private const val KEY_SHIELD = "privacy_shield"
        private const val KEY_FINGERPRINT = "fingerprint_protection"
        private const val KEY_CLEAR_EXIT = "clear_on_exit"
        private const val KEY_AUTO_HIDE = "auto_hide_toolbar"
        private const val KEY_AGENT_ENABLED = "agent_enabled"
        private const val KEY_AGENT_ACTIONS = "agent_actions_enabled"
        private const val KEY_AGENT_CLOUD = "agent_cloud_enabled"
        private const val KEY_AGENT_ENDPOINT = "agent_endpoint"
        private const val KEY_AGENT_MODEL = "agent_model"

        const val DEFAULT_AGENT_ENDPOINT = "https://api.openai.com/v1"
        const val DEFAULT_AGENT_MODEL = "gpt-4o-mini"
    }
}
