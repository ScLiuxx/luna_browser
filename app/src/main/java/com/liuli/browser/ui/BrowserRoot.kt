package com.liuli.browser.ui

import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.SystemClock
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import com.liuli.browser.ui.agent.AgentShareCard
import com.liuli.browser.ui.components.GlassNavigationRail
import com.liuli.browser.ui.components.GlassPopup
import com.liuli.browser.ui.components.GlassPopupAnchor
import com.liuli.browser.ui.components.GlassPopupItem
import com.liuli.browser.ui.components.GlassSurface
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.NavItem
import com.liuli.browser.ui.liquid.GlassShape
import com.liuli.browser.ui.liquid.LiquidDock
import com.liuli.browser.ui.liquid.Visibility
import com.liuli.browser.ui.screens.BrowserScreen
import com.liuli.browser.ui.screens.HomeScreen
import com.liuli.browser.ui.screens.LibraryScreen
import com.liuli.browser.ui.screens.SettingsScreen
import com.liuli.browser.ui.screens.TabSwitcherScreen
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.Radius
import com.liuli.browser.web.AppOverlay
import com.liuli.browser.web.BrowserTab
import com.liuli.browser.web.BrowserViewModel
import com.liuli.browser.web.DownloadRequest
import com.liuli.browser.web.WebSnapshotBackdrop
import com.liuli.browser.web.agent.AgentController
import com.liuli.browser.web.agent.AgentCreation
import com.liuli.browser.web.agent.AgentCreationFormat
import com.liuli.browser.web.agent.AgentPolicyStore
import com.liuli.browser.web.agent.AgentReport
import com.liuli.browser.web.agent.AgentReportFormat
import com.liuli.browser.web.agent.CreationEngine
import com.liuli.browser.web.agent.ResearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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

/**
 * 页面层级。不同层级之间的转场方向不同：
 * - Content 是网页本身；
 * - Page 是首页这类根页面；
 * - Overlay 是标签、书签、设置这类从底层升起的浮层。
 */
private fun MainScreen.layer(): ScreenLayer = when (this) {
    MainScreen.Browser -> ScreenLayer.Content
    MainScreen.Home -> ScreenLayer.Page
    MainScreen.Tabs, MainScreen.Library, MainScreen.Settings -> ScreenLayer.Overlay
}

private enum class ScreenLayer { Content, Page, Overlay }

/**
 * 这一对页面之间是否有共享元素在飞行。
 *
 * 地址栏（首页 ↔ 网页）与标签页快照（网页 ↔ 标签页）都是共享元素：
 * 它们在两层之间"一镜到底"地移动。飞行途中容器如果大幅缩放，
 * 正在飞的图片就会和它要落进去的卡片错位，所以这类转场只能轻微缩放。
 */
private fun MainScreen.sharesElementWith(other: MainScreen): Boolean = when {
    this == other -> false
    // 地址栏：首页 ↔ 网页
    this == MainScreen.Home && other == MainScreen.Browser -> true
    this == MainScreen.Browser && other == MainScreen.Home -> true
    // 标签页快照：网页 ↔ 标签页
    this == MainScreen.Browser && other == MainScreen.Tabs -> true
    this == MainScreen.Tabs && other == MainScreen.Browser -> true
    else -> false
}

/**
 * 主页面转场。
 *
 * 此前所有页面之间都是纯 fadeIn/fadeOut，五个页面观感完全一致、缺方向感，
 * 而且两屏交叉淡化时中间帧会发灰。现在按层级区分方向：
 * 浮层升起时底层轻微后退，网页与首页之间保持克制以免破坏共享元素转场。
 *
 * 方向感来自"前后景"而不是单纯的淡入淡出：
 * - 浮层（标签 / 书签 / 设置）朝观众压过来，底层同时后退并压暗；
 * - 关闭时完全镜像，系统返回键看到的就是打开时的倒放；
 * - 共享元素在飞的那两对只用同一条（不过冲的）曲线做极轻缩放，
 *   容器与共享元素同步，不会一个弹一个不弹。
 */
private fun screenTransition(
    initial: MainScreen,
    target: MainScreen,
    isTablet: Boolean,
    reduced: Boolean
): ContentTransform {
    // "减少动态效果"此前只管得到弹簧版本：补间一律照播。
    // 位移与缩放必须在这里自己掐掉，否则这个开关等于没开。
    if (reduced) {
        return fadeIn(MotionSpec.enter(reduced, Motion.Fast)) togetherWith
            fadeOut(MotionSpec.exit(reduced, Motion.Fast))
    }
    val from = initial.layer()
    val to = target.layer()
    val sharedFlight = initial.sharesElementWith(target)
    // 共享元素用的是不过冲的临界阻尼；容器跟着用同一条曲线，两者才同步。
    val scaleSpec = if (sharedFlight) {
        MotionSpec.sharedSpring(reduced, 0.01f)
    } else {
        MotionSpec.pageSpring(reduced)
    }
    val offsetSpec = MotionSpec.pageSpring(reduced, IntOffset.Zero)
    // 浮层前移、底层后退的幅度：没有共享元素时给足前后景差，
    // 有共享元素在飞时只留一点点，避免图片与卡片错位。
    val overlayScale = if (sharedFlight) 0.985f else 0.94f
    val baseScale = if (sharedFlight) 0.99f else 0.95f
    // 底层被压暗但不完全消失：留一层"还看得见的网页"，
    // 用户才知道自己还在哪个标签上。
    val dimAlpha = 0.35f

    // 浮层升起 / 落下：纵向滑动，桌面端从右侧滑入。
    if (from != to && (to == ScreenLayer.Overlay || from == ScreenLayer.Overlay)) {
        if (to == ScreenLayer.Overlay) {
            val enter = if (isTablet) {
                slideInHorizontally(animationSpec = offsetSpec) { it / 6 }
            } else {
                slideInVertically(animationSpec = offsetSpec) { it / 8 }
            }
            val exit = if (isTablet) {
                slideOutHorizontally(animationSpec = offsetSpec) { -it / 8 }
            } else {
                slideOutVertically(animationSpec = offsetSpec) { -it / 10 }
            }
            // 浮层朝观众压过来，底层往后退并压暗。
            return (enter +
                scaleIn(animationSpec = scaleSpec, initialScale = overlayScale) +
                fadeIn(MotionSpec.enter(reduced))) togetherWith
                (exit +
                    scaleOut(animationSpec = scaleSpec, targetScale = baseScale) +
                    fadeOut(MotionSpec.exit(reduced), targetAlpha = dimAlpha))
        }
        val enter = if (isTablet) {
            slideInHorizontally(animationSpec = offsetSpec) { -it / 8 }
        } else {
            slideInVertically(animationSpec = offsetSpec) { -it / 10 }
        }
        val exit = if (isTablet) {
            slideOutHorizontally(animationSpec = offsetSpec) { it / 6 }
        } else {
            slideOutVertically(animationSpec = offsetSpec) { it / 8 }
        }
        // 原路退回：底面回到前景，浮层缩回原处。
        return (enter +
            scaleIn(animationSpec = scaleSpec, initialScale = baseScale) +
            fadeIn(MotionSpec.enter(reduced), initialAlpha = dimAlpha)) togetherWith
            (exit +
                scaleOut(animationSpec = scaleSpec, targetScale = overlayScale) +
                fadeOut(MotionSpec.exit(reduced)))
    }
    // 网页与首页之间靠共享元素承接，这里只做轻微缩放，避免抢戏。
    val enterScale = if (to == ScreenLayer.Content) 0.99f else 1f
    val exitScale = if (from == ScreenLayer.Content) 1.01f else 1f
    // 交叉淡化的时长要和共享元素的飞行时间相当：
    // 淡出比淡入短，中途会露出背景，交叉淡化中间帧发灰。
    return (fadeIn(MotionSpec.enter(reduced, Motion.Slow)) +
        scaleIn(animationSpec = scaleSpec, initialScale = enterScale)) togetherWith
        (fadeOut(MotionSpec.exit(reduced, Motion.Slow)) +
            scaleOut(animationSpec = scaleSpec, targetScale = exitScale))
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
    // 全局"减少动态效果"开关：本文件里所有动效都要看它。
    val reduced = settings.reduceMotion
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

    // P4.3 创作产物同样只写到用户选定位置，Agent 不主动落盘。
    val creationHolder = remember { mutableStateOf<AgentCreation?>(null) }
    var creationFormat by remember { mutableStateOf(AgentCreationFormat.Markdown) }
    val saveCreation: (Uri?) -> Unit = save@{ uri ->
        val pending = creationHolder.value
        val format = creationFormat
        creationHolder.value = null
        if (uri == null || pending == null) return@save
        reportScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val content = CreationEngine.render(pending, format)
                    val stream = activity.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("无法写入目标文件")
                    stream.use { output ->
                        output.write(content)
                    }
                }.isSuccess
            }
            vm.showMessage(
                if (saved) "创作产物已保存（${format.label}）" else "创作产物保存失败"
            )
        }
    }
    val creationMarkdownLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AgentCreationFormat.Markdown.mimeType),
        saveCreation
    )
    val creationHtmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AgentCreationFormat.Html.mimeType),
        saveCreation
    )
    val creationTextLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AgentCreationFormat.Text.mimeType),
        saveCreation
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

    // 首页长按品牌球弹出的全局菜单。
    //
    // 浮层画在这里而不是首页里：底栏与提示都画在首页内容之上，
    // 浮层若画在首页里，它的遮罩就盖不住它们——
    // 遮罩铺满整屏却盖不住底栏，底栏浮在遮罩之上还会被误点跳走。
    //
    // 状态也只存在这一处。分成两处（首页一份、外层一份）再靠回调同步，
    // 中间会隔一帧：菜单刚展开的那一帧底栏还在原地，用户一点就跳走。
    var homeGlobalMenuOpen by remember { mutableStateOf(false) }
    val globalMenuAnchor = remember { GlassPopupAnchor() }
    // 菜单项按下后要执行的动作。
    //
    // 不在点击时直接切屏：面板这时还在缩回锚点的路上，
    // 切过去的话它会浮在新屏上再缩回去，看着像两块玻璃叠在一起。
    // 改成等浮层彻底收起再执行。
    var pendingMenuAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 离开首页时顺手复位：菜单里点"标签页"会直接切屏，
    // 回到首页时不该还记着上一次开着菜单。
    LaunchedEffect(screen) {
        if (screen != MainScreen.Home) homeGlobalMenuOpen = false
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

    // 退出确认：再按一次返回才真的退出。
    //
    // 浏览器里误触返回键太常见了——一次误触就关掉整个浏览器，
    // 用户刚开的一堆标签全没了。给一个 5 秒的二次确认窗口，
    // 想退出的人连按两次，不想退的人什么也不会丢。
    //
    // 窗口取 5 秒是 Luna 的取值（`cx8.java:245`），
    // 它的提示是 Snackbar；本项目的提示走自己的玻璃提示队列。
    var exitArmedAt by remember { mutableStateOf<Long?>(null) }
    val exitArmWindowMillis = 5000L

    BackHandler(enabled = vm.overlay == AppOverlay.None) {
        // 页面后退、关掉别的标签页都该立刻生效：用户明确要求了，
        // 让他再按一次才生效是在质疑他的操作。
        if (vm.activeTab != null && vm.handleBack()) {
            // 已经退回去了，刚才那次「再按一次」的预备也就作废：
            // 否则用户退到首页再退一次就真退出了，中间明明做过别的事。
            exitArmedAt = null
            return@BackHandler
        }
        // 退到头了——只剩离开应用这一条路。
        val now = SystemClock.elapsedRealtime()
        val armed = exitArmedAt
        if (armed != null && now - armed < exitArmWindowMillis) {
            activity.finish()
        } else {
            exitArmedAt = now
            vm.showMessage("再按一次返回离开")
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp
        val navBottom = WindowInsets.navigationBars.getBottom(density)
        val navBottomDp = with(density) { navBottom.toDp() }
        val bottomSpace = if (isTablet) navBottomDp + 22.dp else Dimens.dockHeight + navBottomDp + 22.dp
        // 侧栏展开/收起的尺寸补间：形态变化属于布局变化，
        // 即便开了"减少动态效果"也保留，只是时长缩短。
        val railSizeSpec = MotionSpec.sizeValue(reduced, if (reduced) Motion.Fast else Motion.Normal)
        // Dock 与提示都要"滑着来"：Dock 从下方升起，提示从底部冒出来。
        // "减少动态效果"时只保留淡化，去掉位移。
        // 这里刻意用非组合版本的 MotionSpec：开关切换时组合结构不变。
        val dockEnter = if (reduced) {
            fadeIn(MotionSpec.enter(reduced, Motion.Fast))
        } else {
            fadeIn(MotionSpec.enter(reduced)) + slideInVertically(
                animationSpec = MotionSpec.pageSpring(reduced, IntOffset.Zero),
                initialOffsetY = { it / 2 }
            )
        }
        val dockExit = if (reduced) {
            fadeOut(MotionSpec.exit(reduced, Motion.Fast))
        } else {
            fadeOut(MotionSpec.exit(reduced)) + slideOutVertically(
                animationSpec = MotionSpec.pageSpring(reduced, IntOffset.Zero),
                targetOffsetY = { it / 3 }
            )
        }
        val toastEnter = if (reduced) {
            fadeIn(MotionSpec.enter(reduced, Motion.Fast))
        } else {
            fadeIn(MotionSpec.enter(reduced)) + slideInVertically(
                animationSpec = MotionSpec.pageSpring(reduced, IntOffset.Zero),
                initialOffsetY = { it / 2 }
            )
        }
        // 提示的退场用补间而不是弹簧：
        // 弹簧的落位时间不确定，"等退场走完再播下一条"就等不准，
        // 下一条会在上一条还没退干净时顶上来，两条提示叠在一起，
        // 用户看到的是上条的字被下条的字替换掉。
        val toastExit = if (reduced) {
            fadeOut(MotionSpec.exit(reduced, Motion.Fast))
        } else {
            fadeOut(MotionSpec.exit(reduced)) + slideOutVertically(
                animationSpec = tween(Motion.Slow, easing = Motion.Exit),
                targetOffsetY = { it / 3 }
            )
        }

        Row(Modifier.fillMaxSize()) {
            // 侧栏随形态出现/消失：折叠屏展开、旋转屏幕时它从左侧展开，
            // 而不是"啪"地跳出来把网页挤走。
            // 展开用的是尺寸补间，属于布局变化，即便开了"减少动态效果"也保留，
            // 只是时长缩短、不再额外淡入淡出。
            // 用项目自己的 Visibility：这一行在 Row 作用域里，
            // 直接调 AnimatedVisibility 会解析到 RowScope 的同名扩展。
            Visibility(
                visible = isTablet,
                enter = fadeIn(MotionSpec.enter(reduced, Motion.Fast)) +
                    expandHorizontally(animationSpec = railSizeSpec),
                exit = fadeOut(MotionSpec.exit(reduced, Motion.Fast)) +
                    shrinkHorizontally(animationSpec = railSizeSpec),
                modifier = Modifier.fillMaxHeight()
            ) {
                Row(Modifier.fillMaxHeight()) {
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
                            screenTransition(initialState, targetState, isTablet, reduced)
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
                                    reduceMotion = reduced,
                                    startPageStyle = settings.startPageStyle,
                                    sharedScope = shared,
                                    animatedScope = animated,
                                    onSearchSubmit = { submitSearch(it) },
                                    onOpenUrl = { url -> vm.navigate(url, settings.searchEngine) },
                                    onOpenTab = { id -> vm.selectTab(id) },
                                    onOpenLibrary = { openLibrary() },
                                    onOpenSettings = { openSettings() },
                                    globalMenuAnchor = globalMenuAnchor,
                                    onOpenGlobalMenu = { homeGlobalMenuOpen = true }
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
                                        onExportCreation = { creation, format ->
                                            creationHolder.value = creation
                                            creationFormat = format
                                            val launcher = when (format) {
                                                AgentCreationFormat.Markdown -> creationMarkdownLauncher
                                                AgentCreationFormat.Html -> creationHtmlLauncher
                                                AgentCreationFormat.Text -> creationTextLauncher
                                            }
                                            runCatching {
                                                launcher.launch(creation.fileName(format))
                                            }.onFailure {
                                                creationHolder.value = null
                                                vm.showMessage("无法打开文件保存对话框")
                                            }
                                        },
                                        onShareCreation = { creation ->
                                            val text = buildString {
                                                appendLine(creation.title)
                                                appendLine()
                                                append(CreationEngine.plainText(creation))
                                                if (creation.sourceUrl.isNotBlank()) {
                                                    appendLine()
                                                    appendLine(creation.sourceUrl)
                                                }
                                            }
                                            val send = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, text)
                                                putExtra(Intent.EXTRA_SUBJECT, creation.title)
                                            }
                                            runCatching {
                                                activity.startActivity(Intent.createChooser(send, "分享创作产物"))
                                            }.onFailure { vm.showMessage("未找到可分享的应用") }
                                        },
                                        onShareCreationCard = { creation ->
                                            reportScope.launch {
                                                val uri = withContext(Dispatchers.IO) {
                                                    runCatching {
                                                        val file = AgentShareCard.writeToCache(activity, creation)
                                                        AgentShareCard.uriFor(activity, file)
                                                    }.getOrNull()
                                                }
                                                if (uri == null) {
                                                    vm.showMessage("分享卡片生成失败")
                                                    return@launch
                                                }
                                                val send = Intent(Intent.ACTION_SEND).apply {
                                                    type = "image/png"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    putExtra(Intent.EXTRA_TITLE, creation.title)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                val chooser = Intent.createChooser(send, "分享卡片").apply {
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                runCatching {
                                                    activity.startActivity(chooser)
                                                }.onFailure { vm.showMessage("未找到可分享的应用") }
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
                    Visibility(
                        // 全局菜单开着的时候底栏退场：
                        // 它是首页最显眼的一块玻璃，菜单展开时还杵在那里，
                        // 菜单像是浮在底栏上面而不是接管了整屏。
                        // 遮罩也画在底栏之后（整层的最后），
                        // 因此底栏退场动画期间它仍被遮罩挡住，不会被误点。
                        visible = !isTablet && screen == MainScreen.Home && !homeGlobalMenuOpen,
                        enter = dockEnter,
                        exit = dockExit
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

                // 提示队列：一次操作可能连着产生多个结果（保存、导出、跳转……），
                // 逐条播完比"后一条把前一条顶掉"更完整，
                // 否则用户只会看到最后一个结果，前面失败的原因全丢了。
                val toastChannel = remember { Channel<String>(Channel.UNLIMITED) }
                // 退场时文案必须留着：玻璃壳不能先塌成空白再淡出。
                var toastText by remember { mutableStateOf<String?>(null) }
                var toastVisible by remember { mutableStateOf(false) }
                val haptics = LocalHapticFeedback.current
                // 触感对象会随 Activity 重建而换：
                // 播放协程只跑一次，抓着第一次组合到的对象，
                // 旋转屏幕之后提示就再也震不动了。这里跟着最新值走。
                val hapticsState = rememberUpdatedState(haptics)
                // 新提示入队。
                LaunchedEffect(vm.message) {
                    val message = vm.message ?: return@LaunchedEffect
                    toastChannel.send(message)
                    vm.consumeMessage()
                }
                // 播放队列：一条播完再接下一条。
                LaunchedEffect(Unit) {
                    while (true) {
                        val message = toastChannel.receive()
                        toastText = message
                        toastVisible = true
                        // 提示弹出时给一次轻触反馈：用户往往没在看屏幕，
                        // 震动是唯一能立刻知道"操作结果"的通道。
                        hapticsState.value.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        delay(2600)
                        toastVisible = false
                        // 等退场动画走完再换下一条，两条提示不会挤在一起。
                        // 退场是 Motion.Slow 的补间，等同样长再留一点余量就够了。
                        delay(Motion.Slow.toLong() + 60L)
                    }
                }
                Visibility(
                    visible = toastVisible,
                    enter = toastEnter,
                    exit = toastExit,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        // 提示压在 Dock 上方，留出 Dock 的高度加一点余量，
                        // 两者不会叠在一起。
                        .padding(
                            bottom = if (isTablet) 24.dp else Dimens.dockHeight + 22.dp,
                            start = 20.dp,
                            end = 20.dp
                        )
                ) {
                    GlassSurface(
                        backdrop = chromeBackdrop,
                        modifier = Modifier.widthIn(max = 420.dp),
                        shape = GlassShape.squircle(24.dp),
                        tint = colors.glassTintStrong,
                        blurRadius = 18.dp,
                        lensHeight = 14.dp,
                        lensAmount = 20.dp,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        // 提示可能很长（比如保存失败的原因），允许换行并限高，
                        // 否则超出宽度会被裁掉，看不出到底出了什么问题。
                        BasicText(
                            // 这里必须用队列里的文案而不是 vm.message：
                            // message 一被消费就是 null，直接渲染它会让玻璃壳
                            // 先塌成空白再淡出。
                            text = toastText.orEmpty(),
                            style = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            ),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            // 提示说的是"刚刚发生的事"：
                            // 标成礼貌朗读区，读屏才会把它念出来，
                            // 否则用户只能自己低头去找那行字。
                            // 标记必须和文字在同一个节点上：
                            // 挂在外层容器上，朗读区里没有文字，念出来是空的。
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            }
        }

        // 全局菜单：整层画在最后，遮罩才盖得住整屏——
        // 底栏、提示与平板上的侧栏都在这一层之下，
        // 浮层若画在内容列里，遮罩只盖住内容列，侧栏仍亮着还能点。
        // 面板的位置跟着锚点走——锚点由首页的品牌球登记位置。
        GlassPopup(
            anchor = globalMenuAnchor,
            visible = homeGlobalMenuOpen,
            onDismissRequest = { homeGlobalMenuOpen = false },
            backdrop = auroraBackdrop,
            shape = GlassShape.squircle(Radius.lg),
            // 面板缩回锚点之后才执行菜单项：
            // 两者同时发生的话，面板会浮在新屏上再缩回去。
            onExitFinished = {
                pendingMenuAction?.let { action ->
                    pendingMenuAction = null
                    action()
                }
            }
        ) {
            GlassPopupItem(
                glyph = Glyph.Tabs,
                label = "标签页",
                onClick = {
                    pendingMenuAction = { openTabs() }
                    homeGlobalMenuOpen = false
                }
            )
            GlassPopupItem(
                glyph = Glyph.Bookmark,
                label = "书签与历史",
                onClick = {
                    pendingMenuAction = { openLibrary() }
                    homeGlobalMenuOpen = false
                }
            )
            GlassPopupItem(
                glyph = Glyph.Settings,
                label = "设置",
                onClick = {
                    pendingMenuAction = { openSettings() }
                    homeGlobalMenuOpen = false
                }
            )
        }
    }
}
