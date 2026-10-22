package com.liuli.browser.ui

import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.liuli.browser.data.LibraryStore
import com.liuli.browser.data.SettingsStore
import com.liuli.browser.ui.components.GlassNavigationRail
import com.liuli.browser.ui.components.GlassSurface
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.NavItem
import com.liuli.browser.ui.liquid.LiquidDock
import com.liuli.browser.ui.screens.BrowserScreen
import com.liuli.browser.ui.screens.HomeScreen
import com.liuli.browser.ui.screens.LibraryScreen
import com.liuli.browser.ui.screens.SettingsScreen
import com.liuli.browser.ui.screens.TabSwitcherScreen
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.web.AppOverlay
import com.liuli.browser.web.BrowserTab
import com.liuli.browser.web.BrowserViewModel
import com.liuli.browser.web.DownloadRequest
import com.liuli.browser.web.WebSnapshotBackdrop
import com.liuli.browser.web.agent.AgentController
import com.liuli.browser.web.agent.AgentPolicyStore
import com.liuli.browser.web.agent.AgentReport
import com.liuli.browser.web.agent.AgentReportFormat
import com.liuli.browser.web.agent.ResearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MainScreen {
    Home,
    Browser,
    Tabs,
    Library,
    Settings
}

private val RailItems = listOf(
    NavItem(Glyph.Home, "首页"),
    NavItem(Glyph.Tabs, "标签"),
    NavItem(Glyph.Bookmark, "书签"),
    NavItem(Glyph.Settings, "设置")
)

private val DockItems = listOf(
    NavItem(Glyph.Home, "首页"),
    NavItem(Glyph.Tabs, "标签"),
    NavItem(Glyph.Bookmark, "书签"),
    NavItem(Glyph.Settings, "设置")
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BrowserRoot(
    settings: SettingsStore,
    library: LibraryStore,
    auroraBackdrop: LayerBackdrop,
    incomingUrl: String?,
    onIncomingConsumed: () -> Unit,
    onBackgroundActiveChanged: (Boolean) -> Unit
) {
    val activity = LocalContext.current as ComponentActivity
    val vm = remember(activity) {
        ViewModelProvider(
            activity,
            AndroidViewModelFactory.getInstance(activity.application)
        ).get(BrowserViewModel::class.java)
    }
    val colors = LocalLiuliColors.current
    val density = LocalDensity.current
    val agentPolicies = remember(activity) { AgentPolicyStore(activity.applicationContext) }
    // 本机历史只读传入；Agent 不会把它上传，云端路径也不读取该回调。
    val agent = remember(settings, agentPolicies, library) {
        AgentController(
            settings = settings,
            policies = agentPolicies,
            localHistory = { library.history.toList() },
            localBookmarks = { library.bookmarks.toList() }
        )
    }

    LaunchedEffect(vm, activity) { vm.bindActivity(activity) }
    LaunchedEffect(vm, settings, library) { vm.bind(settings, library) }
    LaunchedEffect(density.fontScale) { vm.updateTextZoom(density.fontScale) }
    LaunchedEffect(settings.maxTabs) { vm.applyMaxTabs(settings.maxTabs) }
    LaunchedEffect(settings.privacyShield) { vm.applyPrivacySettings() }
    DisposableEffect(agent) { onDispose { agent.dispose() } }
    LaunchedEffect(incomingUrl) {
        if (!incomingUrl.isNullOrBlank()) {
            vm.createTab(url = incomingUrl, select = true)
            vm.closeOverlay()
            onIncomingConsumed()
        }
    }

    DisposableEffect(activity, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.resume()
                Lifecycle.Event.ON_STOP -> vm.pause()
                Lifecycle.Event.ON_PAUSE -> vm.persistSession()
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        val callbacks = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) = vm.onTrimMemory(level)
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = vm.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        }
        activity.application.registerComponentCallbacks(callbacks)
        onDispose {
            activity.lifecycle.removeObserver(observer)
            activity.application.unregisterComponentCallbacks(callbacks)
        }
    }

    var contentRect by remember { mutableStateOf(Rect.Zero) }
    var showSharedSnapshot by remember { mutableStateOf(false) }
    var webRevealToken by remember { mutableIntStateOf(0) }

    val downloadHolder = remember { mutableStateOf<DownloadRequest?>(null) }
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val request = downloadHolder.value
        downloadHolder.value = null
        if (uri != null && request != null) vm.startDownload(request, uri)
    }
    LaunchedEffect(vm.downloadRequest) {
        val request = vm.downloadRequest ?: return@LaunchedEffect
        downloadHolder.value = request
        vm.consumeDownloadRequest()
        runCatching { downloadLauncher.launch(request.suggestedName.ifBlank { "download" }) }
            .onFailure {
                downloadHolder.value = null
                vm.showMessage("无法打开文件保存对话框")
            }
    }

    // 调研报告只通过系统文件选择器写入用户选定的位置，Agent 不主动落盘。
    val reportHolder = remember { mutableStateOf<AgentReport?>(null) }
    var reportFormat by remember { mutableStateOf(AgentReportFormat.Markdown) }
    val reportScope = rememberCoroutineScope()
    val saveReport: (Uri?) -> Unit = save@{ uri ->
        val pending = reportHolder.value
        val format = reportFormat
        reportHolder.value = null
        if (uri == null || pending == null) return@save
        reportScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val content = ResearchEngine.render(pending, format)
                    val stream = activity.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("无法写入目标文件")
                    stream.use { output ->
                        output.write(content)
                    }
                }.isSuccess
            }
            vm.showMessage(
                if (saved) "调研报告已保存（${format.label}）" else "调研报告保存失败"
            )
        }
    }
    val markdownExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AgentReportFormat.Markdown.mimeType),
        saveReport
    )
    val htmlExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AgentReportFormat.Html.mimeType),
        saveReport
    )
    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AgentReportFormat.Csv.mimeType),
        saveReport
    )
    val pdfExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AgentReportFormat.Pdf.mimeType),
        saveReport
    )

    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> vm.onFileChooserResult(result.resultCode, result.data) }
    LaunchedEffect(vm.fileChooserRequest) {
        val request = vm.fileChooserRequest ?: return@LaunchedEffect
        runCatching { request.params.createIntent() }
            .onSuccess { fileChooserLauncher.launch(it) }
            .onFailure { vm.cancelFileChooser() }
    }

    val activeTab = vm.activeTab
    val screen = when {
        vm.overlay == AppOverlay.Tabs -> MainScreen.Tabs
        vm.overlay == AppOverlay.Library -> MainScreen.Library
        vm.overlay == AppOverlay.Settings -> MainScreen.Settings
        activeTab?.isHome != false -> MainScreen.Home
        else -> MainScreen.Browser
    }

    LaunchedEffect(screen) {
        onBackgroundActiveChanged(screen != MainScreen.Browser)
    }
    LaunchedEffect(contentRect) {
        if (contentRect.width > 0f) {
            delay(220)
            vm.captureActiveSnapshot()
        }
    }
    LaunchedEffect(showSharedSnapshot, vm.overlay, vm.activeId) {
        if (!showSharedSnapshot) return@LaunchedEffect
        delay(520)
        showSharedSnapshot = false
        if (vm.overlay == AppOverlay.None) vm.endOpenTab()
    }
    LaunchedEffect(webRevealToken) {
        if (webRevealToken > 0) {
            delay(500)
            if (!showSharedSnapshot) vm.endOpenTab()
        }
    }

    val activeSnapshot = activeTab?.snapshot
    val activeSnapshotBounds = activeTab?.snapshotBounds
    val webBackdrop = remember(activeSnapshot, activeSnapshotBounds) {
        if (activeSnapshot != null && activeSnapshotBounds != null) {
            WebSnapshotBackdrop(activeSnapshot, activeSnapshotBounds)
        } else {
            null
        }
    }
    val chromeBackdrop = rememberCombinedBackdrop(auroraBackdrop, webBackdrop ?: emptyBackdrop())

    fun submitSearch(text: String) {
        if (text.isBlank()) return
        vm.navigate(text, settings.searchEngine)
        vm.updateWebContentAlpha(0f)
        webRevealToken++
    }

    fun openTabs() {
        if (vm.overlay == AppOverlay.Tabs) {
            vm.exitTabSwitcher()
            showSharedSnapshot = false
            return
        }
        val tab = vm.activeTab
        if (tab == null || tab.isHome) {
            vm.openOverlay(AppOverlay.Tabs)
            showSharedSnapshot = false
            return
        }
        val snapshot = vm.enterTabSwitcher()
        showSharedSnapshot = snapshot != null
    }

    fun dismissTabs() {
        showSharedSnapshot = false
        vm.exitTabSwitcher()
    }

    fun selectTabFromSwitcher(tab: BrowserTab) {
        val hasSnapshot = tab.snapshot != null
        showSharedSnapshot = hasSnapshot
        vm.beginOpenTab(tab.id)
        if (!hasSnapshot) vm.endOpenTab()
    }

    fun openLibrary() {
        if (vm.overlay == AppOverlay.Library) vm.closeOverlay() else vm.openOverlay(AppOverlay.Library)
        showSharedSnapshot = false
    }

    fun openSettings() {
        if (vm.overlay == AppOverlay.Settings) vm.closeOverlay() else vm.openOverlay(AppOverlay.Settings)
        showSharedSnapshot = false
    }

    BackHandler(enabled = vm.overlay == AppOverlay.None) {
        if (vm.activeTab == null) {
            activity.finish()
            return@BackHandler
        }
        if (!vm.handleBack()) activity.finish()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp
        val navBottom = WindowInsets.navigationBars.getBottom(density)
        val navBottomDp = with(density) { navBottom.toDp() }
        val bottomSpace = if (isTablet) navBottomDp + 22.dp else Dimens.dockHeight + navBottomDp + 22.dp

        Row(Modifier.fillMaxSize()) {
            if (isTablet) {
                Box(
                    Modifier
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    val selectedIndex = when (screen) {
                        MainScreen.Tabs -> 1
                        MainScreen.Library -> 2
                        MainScreen.Settings -> 3
                        MainScreen.Home, MainScreen.Browser -> 0
                    }
                    GlassNavigationRail(
                        items = RailItems,
                        selectedIndex = selectedIndex,
                        onSelect = { index ->
                            when (index) {
                                0 -> {
                                    if (vm.overlay != AppOverlay.None) vm.closeOverlay()
                                    if (vm.activeTab?.isHome != true) vm.openHomeTab()
                                    showSharedSnapshot = false
                                }
                                1 -> openTabs()
                                2 -> openLibrary()
                                3 -> openSettings()
                            }
                        },
                        onNewTab = {
                            vm.openHomeTab()
                            showSharedSnapshot = false
                        },
                        backdrop = auroraBackdrop,
                        tabCount = vm.tabs.size
                    )
                }
                Spacer(Modifier.width(12.dp))
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onGloballyPositioned { contentRect = it.boundsInWindow() }
            ) {
                AndroidView(
                    factory = { context -> FrameLayout(context).also { vm.attachContainer(it) } },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { vm.detachContainer(it) }
                )

                SharedTransitionLayout(Modifier.fillMaxSize()) {
                    val shared = this
                    AnimatedContent(
                        targetState = screen,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                        },
                        label = "main-screen"
                    ) { target ->
                        val animated = this
                        when (target) {
                            MainScreen.Home -> {
                                HomeScreen(
                                    backdrop = auroraBackdrop,
                                    tabs = vm.tabs,
                                    isPrivate = activeTab?.isPrivate == true,
                                    isTablet = isTablet,
                                    bottomSpace = bottomSpace,
                                    reduceMotion = settings.reduceMotion,
                                    sharedScope = shared,
                                    animatedScope = animated,
                                    onSearchSubmit = { submitSearch(it) },
                                    onOpenUrl = { url -> vm.navigate(url, settings.searchEngine) },
                                    onOpenTab = { id -> vm.selectTab(id) },
                                    onOpenLibrary = { openLibrary() },
                                    onOpenSettings = { openSettings() }
                                )
                            }
                            MainScreen.Browser -> {
                                val tab = activeTab
                                if (tab != null) {
                                    BrowserScreen(
                                        vm = vm,
                                        tab = tab,
                                        settings = settings,
                                        agent = agent,
                                        backdrop = chromeBackdrop,
                                        isTablet = isTablet,
                                        sharedScope = shared,
                                        animatedScope = animated,
                                        sharedSnapshot = tab.snapshot,
                                        sharedSnapshotKey = tab.id,
                                        showSharedSnapshot = showSharedSnapshot,
                                        onShare = { url, title ->
                                            if (url.isBlank()) {
                                                vm.showMessage("没有可分享的链接")
                                            } else {
                                                val send = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, url)
                                                    putExtra(Intent.EXTRA_SUBJECT, title)
                                                }
                                                runCatching {
                                                    activity.startActivity(Intent.createChooser(send, "分享链接"))
                                                }.onFailure { vm.showMessage("未找到可分享的应用") }
                                            }
                                        },
                                        onExportReport = { report, format ->
                                            reportHolder.value = report
                                            reportFormat = format
                                            val launcher = when (format) {
                                                AgentReportFormat.Markdown -> markdownExportLauncher
                                                AgentReportFormat.Html -> htmlExportLauncher
                                                AgentReportFormat.Csv -> csvExportLauncher
                                                AgentReportFormat.Pdf -> pdfExportLauncher
                                            }
                                            runCatching {
                                                launcher.launch(report.fileName(format))
                                            }.onFailure {
                                                reportHolder.value = null
                                                vm.showMessage("无法打开文件保存对话框")
                                            }
                                        },
                                        onNewHomeTab = {
                                            vm.openHomeTab()
                                            showSharedSnapshot = false
                                        },
                                        onOpenTabs = { openTabs() },
                                        onOpenLibrary = { openLibrary() },
                                        onOpenSettings = { openSettings() }
                                    )
                                }
                            }
                            MainScreen.Tabs -> {
                                TabSwitcherScreen(
                                    tabs = vm.tabs,
                                    activeId = vm.activeId,
                                    backdrop = auroraBackdrop,
                                    isTablet = isTablet,
                                    bottomSpace = navBottomDp + 20.dp,
                                    sharedScope = shared,
                                    animatedScope = animated,
                                    onSelectTab = { selectTabFromSwitcher(it) },
                                    onCloseTab = { vm.closeTab(it, keepOverlay = true) },
                                    onNewTab = { private ->
                                        vm.createTab(isPrivate = private, select = true)
                                        vm.closeOverlay()
                                        showSharedSnapshot = false
                                    },
                                    onDismiss = { dismissTabs() }
                                )
                            }
                            MainScreen.Library -> {
                                LibraryScreen(
                                    library = library,
                                    backdrop = auroraBackdrop,
                                    isTablet = isTablet,
                                    onOpenUrl = { url ->
                                        vm.openLibraryEntry(url)
                                        showSharedSnapshot = false
                                    },
                                    onDismiss = {
                                        vm.closeOverlay()
                                        showSharedSnapshot = false
                                    }
                                )
                            }
                            MainScreen.Settings -> {
                                SettingsScreen(
                                    settings = settings,
                                    vm = vm,
                                    agent = agent,
                                    agentPolicies = agentPolicies,
                                    backdrop = auroraBackdrop,
                                    isTablet = isTablet,
                                    onClose = {
                                        vm.closeOverlay()
                                        showSharedSnapshot = false
                                    },
                                    onRevokePrivacy = {
                                        vm.resetAll()
                                        settings.revokePrivacyAndReset()
                                        agent.clear()
                                        agentPolicies.clear()
                                    }
                                )
                            }
                        }
                    }
                }

                Box(Modifier.align(Alignment.BottomCenter)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isTablet && screen == MainScreen.Home,
                        enter = fadeIn(tween(200)) + slideInVertically(
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
                            initialOffsetY = { it / 2 }
                        ),
                        exit = fadeOut(tween(150)) + slideOutVertically(
                            animationSpec = tween(150),
                            targetOffsetY = { it / 3 }
                        )
                    ) {
                        LiquidDock(
                            items = DockItems,
                            selectedIndex = 0,
                            onSelected = { index ->
                                when (index) {
                                    1 -> openTabs()
                                    2 -> openLibrary()
                                    3 -> openSettings()
                                }
                            },
                            backdrop = auroraBackdrop,
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                            tabCount = vm.tabs.size
                        )
                    }
                }

                val message = vm.message
                LaunchedEffect(message) {
                    if (message != null) {
                        delay(2600)
                        vm.consumeMessage()
                    }
                }
                if (message != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = if (isTablet) 24.dp else 88.dp, start = 20.dp, end = 20.dp)
                    ) {
                        GlassSurface(
                            backdrop = chromeBackdrop,
                            modifier = Modifier.widthIn(max = 420.dp),
                            shape = RoundedCornerShape(50),
                            tint = colors.glassTintStrong,
                            blurRadius = 18.dp,
                            lensHeight = 14.dp,
                            lensAmount = 20.dp,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            BasicText(
                                text = message,
                                style = TextStyle(color = colors.textPrimary, fontSize = 12.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}
