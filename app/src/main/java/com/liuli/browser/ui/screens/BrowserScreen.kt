package com.liuli.browser.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.liuli.browser.data.SearchEngine
import com.liuli.browser.data.SettingsStore
import com.liuli.browser.ui.agent.AgentOrb
import com.liuli.browser.ui.agent.AgentPanel
import com.liuli.browser.ui.components.GlassIconButton
import com.liuli.browser.ui.components.GlassPillButton
import com.liuli.browser.ui.components.GlassSurface
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.GlyphIcon
import com.liuli.browser.ui.liquid.pressScale
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.reduceMotion
import com.liuli.browser.web.BrowserTab
import com.liuli.browser.web.BrowserViewModel
import com.liuli.browser.web.agent.AgentController
import com.liuli.browser.web.agent.AgentCreation
import com.liuli.browser.web.agent.AgentCreationFormat
import com.liuli.browser.web.agent.AgentReport
import com.liuli.browser.web.agent.AgentReportFormat
import com.liuli.browser.web.agent.AgentResearchPage
import kotlinx.coroutines.delay

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BrowserScreen(
    vm: BrowserViewModel,
    tab: BrowserTab,
    settings: SettingsStore,
    agent: AgentController,
    backdrop: Backdrop,
    isTablet: Boolean,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
    sharedSnapshot: android.graphics.Bitmap?,
    sharedSnapshotKey: Long?,
    showSharedSnapshot: Boolean,
    onShare: (String, String) -> Unit,
    onExportReport: (AgentReport, AgentReportFormat) -> Unit,
    onExportCreation: (AgentCreation, AgentCreationFormat) -> Unit,
    onShareCreation: (AgentCreation) -> Unit,
    onShareCreationCard: (AgentCreation) -> Unit,
    onNewHomeTab: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = LocalLiuliColors.current
    var editing by rememberSaveable(tab.id) { mutableStateOf(false) }
    var menuOpen by rememberSaveable(tab.id) { mutableStateOf(false) }
    var shieldOpen by rememberSaveable(tab.id) { mutableStateOf(false) }
    var agentOpen by rememberSaveable(tab.id) { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(TextFieldValue(tab.url)) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(tab.id) {
        editing = false
        menuOpen = false
        agentOpen = false
        agent.onTabChanged(tab.id)
        editValue = TextFieldValue(tab.url)
    }
    LaunchedEffect(tab.url) {
        if (!editing) editValue = TextFieldValue(tab.url)
    }
    LaunchedEffect(editing) {
        if (editing) {
            delay(90)
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }
    BackHandler(enabled = menuOpen) { menuOpen = false }
    BackHandler(enabled = editing) {
        editing = false
        editValue = TextFieldValue(tab.url)
    }
    BackHandler(enabled = shieldOpen) { shieldOpen = false }
    BackHandler(enabled = tab.isFullscreen) { vm.exitFullscreen() }
    BackHandler(enabled = agentOpen) { agentOpen = false }

    val fontScale = LocalDensity.current.fontScale
    val idleBarHeight = if (fontScale > 1.3f) 76.dp else 64.dp
    val canNavigateBack = tab.canGoBack || vm.tabs.size > 1 || !tab.isHome
    val collapsed = settings.autoHideToolbar && vm.toolbarCollapsed && !editing && !menuOpen && !shieldOpen
    // sharedBounds 的 boundsTransform 不是 Composable 上下文，规格要在外面先算好。
    val addressBoundsSpec = MotionSpec.sharedSpring(Rect(0f, 0f, 0f, 0f))
    val snapshotBoundsSpec = MotionSpec.pageSpring(Rect(0f, 0f, 0f, 0f))
    val addressSharedModifier = with(sharedScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "address-bar"),
            animatedVisibilityScope = animatedScope,
            boundsTransform = { _, _ -> addressBoundsSpec },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
        )
    }
    val snapshotAlpha by animateFloatAsState(
        targetValue = if (showSharedSnapshot) 1f else 0f,
        animationSpec = MotionSpec.value(Motion.Normal),
        label = "shared-snapshot-alpha"
    )
    val barHeight by animateDpAsState(
        targetValue = when {
            editing -> 136.dp
            collapsed -> 42.dp
            else -> idleBarHeight
        },
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "address-morph"
    )
    // 地址栏在"浏览态 ↔ 编辑态"之间形变：
    // 容器高度用弹簧伸缩，内容同时做方向性滑动，
    // 新内容从下方推入、旧内容向上收起，避免纯淡化的"凭空替换"。
    val editProgress by animateFloatAsState(
        targetValue = if (editing) 1f else 0f,
        animationSpec = MotionSpec.value(Motion.Quick),
        label = "address-edit-progress"
    )

    Box(Modifier.fillMaxSize()) {
        if (sharedSnapshot != null && sharedSnapshotKey != null) {
            val snapshotSharedModifier = with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "tab-image-$sharedSnapshotKey"),
                    animatedVisibilityScope = animatedScope,
                    boundsTransform = { _, _ -> snapshotBoundsSpec },
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                        ContentScale.Crop,
                        Alignment.Center
                    )
                )
            }
            Image(
                bitmap = sharedSnapshot.asImageBitmap(),
                contentDescription = null,
                modifier = snapshotSharedModifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = snapshotAlpha },
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 6.dp, start = if (isTablet) 18.dp else 10.dp, end = if (isTablet) 18.dp else 10.dp)
                .widthIn(max = 1000.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassSurface(
                backdrop = backdrop,
                modifier = addressSharedModifier
                    .fillMaxWidth()
                    .height(barHeight),
                shape = RoundedCornerShape(barHeight / 2),
                tint = colors.glassTintStrong,
                blurRadius = 22.dp,
                lensHeight = 18.dp,
                lensAmount = 26.dp,
                contentPadding = PaddingValues(
                    horizontal = if (collapsed) 6.dp else 8.dp,
                    vertical = if (collapsed) 3.dp else 6.dp
                )
            ) {
                Crossfade(
                    targetState = editing,
                    animationSpec = MotionSpec.value(Motion.Quick),
                    label = "address-content"
                ) { isEditing ->
                    // 交叉淡化的同时给一点方向：编辑态从下方推入，
                    // 浏览态向上收起，避免纯淡化的"凭空替换"。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = if (isEditing) {
                                    (1f - editProgress) * 14.dp.toPx()
                                } else {
                                    -editProgress * 10.dp.toPx()
                                }
                            }
                    ) {
                        if (isEditing) {
                            AddressEditContent(
                                settings = settings,
                                value = editValue,
                                onValueChange = { editValue = it },
                                onCancel = {
                                    editing = false
                                    editValue = TextFieldValue(tab.url)
                                },
                                onSubmit = {
                                    vm.navigate(editValue.text)
                                    editing = false
                                },
                                focusRequester = focusRequester
                            )
                        } else {
                            AddressIdleContent(
                                tab = tab,
                                isTablet = isTablet,
                                onClick = {
                                    editValue = TextFieldValue(
                                        text = tab.url,
                                        selection = TextRange(0, tab.url.length)
                                    )
                                    editing = true
                                },
                                onReload = {
                                    if (tab.isLoading) vm.stopLoading() else vm.reload()
                                },
                                onMenu = {
                                    vm.expandToolbar()
                                    agentOpen = false
                                    menuOpen = true
                                },
                                onBack = { vm.handleBack() },
                                onForward = { vm.goForward() },
                                canNavigateBack = canNavigateBack,
                                shieldOn = settings.privacyShield,
                                blockedTrackers = tab.blockedTrackers,
                                onShieldClick = {
                                    agentOpen = false
                                    shieldOpen = true
                                },
                                compact = collapsed,
                                onExpand = { vm.expandToolbar() }
                            )
                        }
                    }
                }
            }

            // 加载进度条：淡入淡出，不再"啪"地一下冒出来。
            AnimatedVisibility(
                visible = tab.isLoading || tab.progress in 1..99,
                enter = fadeIn(MotionSpec.enter(Motion.Quick)),
                exit = fadeOut(MotionSpec.exit(Motion.Quick))
            ) {
                val fraction by animateFloatAsState(
                    targetValue = tab.progress.coerceIn(0, 100) / 100f,
                    animationSpec = MotionSpec.value(Motion.Normal),
                    label = "page-progress"
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.textPrimary.copy(alpha = 0.08f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(colors.accent, colors.accentTertiary)
                                )
                            )
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !tab.isFullscreen && !isTablet && !collapsed,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(MotionSpec.enter()) + slideInVertically(
                animationSpec = MotionSpec.pageSpring(IntOffset.Zero),
                initialOffsetY = { it / 2 }
            ),
            exit = fadeOut(MotionSpec.exit()) + slideOutVertically(
                animationSpec = MotionSpec.pageSpring(IntOffset.Zero),
                targetOffsetY = { it / 3 }
            )
        ) {
            BrowserDock(
                backdrop = backdrop,
                tab = tab,
                tabCount = vm.tabs.size,
                isTablet = isTablet,
                onBack = { vm.handleBack() },
                onForward = { vm.goForward() },
                onHome = onNewHomeTab,
                onTabs = onOpenTabs,
                onMenu = {
                    vm.expandToolbar()
                    menuOpen = true
                },
                backEnabled = canNavigateBack,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .widthIn(max = 560.dp)
            )
        }
        AnimatedVisibility(
            visible = !tab.isFullscreen && !isTablet && collapsed,
            enter = fadeIn(MotionSpec.enter()) + slideInVertically(
                animationSpec = MotionSpec.pageSpring(IntOffset.Zero),
                initialOffsetY = { it / 2 }
            ),
            exit = fadeOut(MotionSpec.exit()) + slideOutVertically(
                animationSpec = MotionSpec.pageSpring(IntOffset.Zero),
                targetOffsetY = { it / 3 }
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 6.dp)
        ) {
            CollapsedToolbarHandle(
                backdrop = backdrop,
                onClick = { vm.expandToolbar() }
            )
        }

        AnimatedVisibility(
            visible = tab.errorText != null,
            enter = fadeIn(MotionSpec.enter()) + scaleIn(
                animationSpec = MotionSpec.pageSpring(),
                initialScale = 0.94f
            ),
            exit = fadeOut(MotionSpec.exit()) + scaleOut(
                animationSpec = MotionSpec.pageSpring(),
                targetScale = 0.96f
            ),
            modifier = Modifier.align(Alignment.Center)
        ) {
            PageErrorCard(
                backdrop = backdrop,
                message = tab.errorText.orEmpty(),
                onRetry = { vm.reload() },
                onHome = onNewHomeTab,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 380.dp)
            )
        }

        AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn(MotionSpec.enter()),
            exit = fadeOut(MotionSpec.exit())
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { menuOpen = false }
            )
        }

        AnimatedVisibility(
            visible = menuOpen,
            enter = slideInVertically(
                animationSpec = MotionSpec.pageSpring(IntOffset.Zero),
                initialOffsetY = { it / 2 }
            ) + fadeIn(MotionSpec.enter()),
            exit = slideOutVertically(
                animationSpec = MotionSpec.pageSpring(IntOffset.Zero),
                targetOffsetY = { it / 3 }
            ) + fadeOut(MotionSpec.exit()),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (isTablet) 18.dp else 86.dp)
                .padding(horizontal = 12.dp)
                .widthIn(max = 440.dp)
        ) {
            BrowserMenu(
                vm = vm,
                tab = tab,
                backdrop = backdrop,
                onShare = { onShare(tab.url, tab.pageTitle()) },
                onOpenLibrary = {
                    menuOpen = false
                    onOpenLibrary()
                },
                onOpenSettings = {
                    menuOpen = false
                    onOpenSettings()
                },
                onCloseTab = {
                    menuOpen = false
                    vm.closeTab(tab.id)
                },
                onDismiss = { menuOpen = false }
            )
        }

        AnimatedVisibility(
            visible = shieldOpen,
            enter = fadeIn(MotionSpec.enter()),
            exit = fadeOut(MotionSpec.exit())
        ) {
            PrivacyShieldPanel(
                blocked = tab.blockedTrackers,
                blockThirdParty = settings.blockThirdPartyCookies,
                fingerprintProtection = settings.fingerprintProtection,
                isPrivate = tab.isPrivate,
                clearOnExit = settings.clearOnExit,
                secureScreen = settings.secureScreen,
                onClose = { shieldOpen = false }
            )
        }

        // P7 一镜到底：Agent 球从右下角缩小隐去，面板从同一角展开，避免场景跳变。
        AnimatedVisibility(
            visible = settings.agentEnabled && !tab.isFullscreen && !menuOpen && !shieldOpen && !agentOpen,
            enter = fadeIn(MotionSpec.enter()) + scaleIn(
                animationSpec = MotionSpec.pageSpring(),
                initialScale = 0.88f,
                transformOrigin = TransformOrigin(1f, 1f)
            ),
            exit = fadeOut(MotionSpec.exit()) + scaleOut(
                animationSpec = MotionSpec.pageSpring(),
                targetScale = 0.88f,
                transformOrigin = TransformOrigin(1f, 1f)
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(
                    end = if (isTablet) 24.dp else 16.dp,
                    bottom = if (isTablet) 24.dp else 100.dp
                )
        ) {
            AgentOrb(
                backdrop = backdrop,
                onClick = {
                    menuOpen = false
                    shieldOpen = false
                    agentOpen = true
                }
            )
        }

        AnimatedVisibility(
            visible = agentOpen,
            enter = fadeIn(MotionSpec.enter()) + scaleIn(
                animationSpec = MotionSpec.pageSpring(),
                initialScale = 0.94f,
                transformOrigin = TransformOrigin(if (isTablet) 1f else 0.5f, if (isTablet) 0.5f else 1f)
            ),
            exit = fadeOut(MotionSpec.exit()) + scaleOut(
                animationSpec = MotionSpec.pageSpring(),
                targetScale = 0.94f,
                transformOrigin = TransformOrigin(if (isTablet) 1f else 0.5f, if (isTablet) 0.5f else 1f)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            AgentPanel(
                agent = agent,
                tabTitle = tab.pageTitle(),
                pageUrl = tab.url,
                isTablet = isTablet,
                isPrivate = tab.isPrivate,
                backdrop = backdrop,
                onClose = { agentOpen = false },
                onSubmit = { text ->
                    agent.send(
                        rawInput = text,
                        webView = vm.activeWebView,
                        pageTitle = tab.pageTitle(),
                        isPrivate = tab.isPrivate,
                        tabId = tab.id,
                        researchPages = vm.tabs
                            .sortedWith(
                                compareByDescending<BrowserTab> { it.id == tab.id }
                                    .thenByDescending { it.lastUsedAt }
                            )
                            .mapNotNull { candidate ->
                                if (candidate.isHome) {
                                    null
                                } else {
                                    vm.webViewForTab(candidate.id)?.let { view ->
                                        AgentResearchPage(
                                            tabId = candidate.id,
                                            isPrivate = candidate.isPrivate,
                                            webView = view
                                        )
                                    }
                                }
                            }
                    )
                },
                onConfirmAction = { messageId ->
                    agent.confirmAction(messageId, vm.activeWebView, tab.id)
                },
                onCancelAction = { messageId ->
                    agent.cancelAction(messageId)
                },
                onPlanCommand = { command ->
                    agent.handlePlanCommand(command, vm.activeWebView, tab.id)
                },
                onExportReport = onExportReport,
                onExportCreation = onExportCreation,
                onShareCreation = onShareCreation,
                onShareCreationCard = onShareCreationCard,
                onOpenSource = { url, isPrivate ->
                    vm.createTab(
                        url = url,
                        isPrivate = isPrivate || tab.isPrivate,
                        select = true
                    )
                    agentOpen = false
                },
                onSharePage = { onShare(tab.url, tab.pageTitle()) }
            )
        }
    }
}

@Composable
private fun PrivacyShieldPanel(
    blocked: Int,
    blockThirdParty: Boolean,
    fingerprintProtection: Boolean,
    isPrivate: Boolean,
    clearOnExit: Boolean,
    secureScreen: Boolean,
    onClose: () -> Unit
) {
    val colors = LocalLiuliColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            backdrop = com.kyant.backdrop.backdrops.emptyBackdrop(),
            modifier = Modifier
                .padding(22.dp)
                .widthIn(max = 380.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            shape = RoundedCornerShape(28.dp),
            tint = colors.glassTintStrong.copy(alpha = 0.96f),
            blurRadius = 0.dp,
            lensHeight = 0.dp,
            shadow = true
        ) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.success.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(Glyph.Shield, colors.success, size = 20.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            text = "隐私护盾已开启",
                            style = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        BasicText(
                            text = "全部拦截在本机完成",
                            style = TextStyle(color = colors.textTertiary, fontSize = 10.sp)
                        )
                    }
                    GlassIconButton(
                        glyph = Glyph.Close,
                        contentDescription = "关闭隐私护盾",
                        onClick = onClose,
                        backdrop = com.kyant.backdrop.backdrops.emptyBackdrop(),
                        size = 40.dp,
                        glyphSize = 16.dp
                    )
                }
                Spacer(Modifier.height(14.dp))
                ShieldPoint("已拦截 $blocked 个跟踪请求", colors.success, 0)
                ShieldPoint(
                    if (blockThirdParty) "第三方 Cookie 已阻止" else "第三方 Cookie 已允许",
                    if (blockThirdParty) colors.success else colors.textTertiary,
                    1
                )
                ShieldPoint("定位、相机、麦克风请求默认拒绝", colors.success, 2)
                ShieldPoint(
                    if (clearOnExit) "退出应用时清除 Cookie、缓存与历史" else "退出时保留本站数据",
                    if (clearOnExit) colors.success else colors.textTertiary,
                    3
                )
                ShieldPoint("通用 User-Agent，减少设备指纹", colors.success, 4)
                ShieldPoint(
                    if (fingerprintProtection) "防浏览器指纹：统一 Canvas/WebGL2/Client Hints/硬件/时区，阻止 WebRTC 与 Service Worker"
                    else "防浏览器指纹：已关闭",
                    if (fingerprintProtection) colors.success else colors.textTertiary,
                    5
                )
                ShieldPoint(
                    if (isPrivate) "隐私标签：隔离本地存储与脚本 Cookie，关闭时清除该站点 Cookie"
                    else "本地存储仅本机保存，退出可清除",
                    if (isPrivate) colors.accentSecondary else colors.textTertiary,
                    6
                )
                ShieldPoint("SSL 错误阻断 · 文件与本地存储访问禁用", colors.success, 7)
                ShieldPoint("DNS 预取已关闭 · Do Not Track 与 GPC 已开启", colors.success, 8)
                ShieldPoint(
                    if (secureScreen) "截屏与最近任务预览已禁止"
                    else "截屏与最近任务预览未禁止",
                    if (secureScreen) colors.success else colors.textTertiary,
                    9
                )
                Spacer(Modifier.height(10.dp))
                BasicText(
                    text = "网页无法读取剪贴板、通讯录、定位、相机或麦克风。跟踪参数会在导航时自动移除，HTTP 自动升级为 HTTPS，并阻止 DNS 预取、sendBeacon 与 window.open 数据外泄。",
                    style = TextStyle(
                        color = colors.textTertiary,
                        fontSize = 10.sp,
                        lineHeight = 16.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun ShieldPoint(text: String, tint: Color, index: Int = 0) {
    val colors = LocalLiuliColors.current
    // 逐条浮现：护盾清单一屏六条，依次出现比整片同时亮更好读。
    val stagger = MotionSpec.staggerDelay(index, stepMillis = 22)
    var appeared by remember(text) { mutableStateOf(false) }
    LaunchedEffect(text) {
        if (stagger > 0) kotlinx.coroutines.delay(stagger.toLong())
        appeared = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = MotionSpec.value(Motion.Normal),
        label = "shield-point-alpha"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer {
                this.alpha = alpha
                translationX = (1f - alpha) * 10.dp.toPx()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.Check, tint, size = 9.dp)
        }
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = text,
            style = TextStyle(color = colors.textSecondary, fontSize = 12.sp)
        )
    }
}

@Composable
private fun AddressIdleContent(
    tab: BrowserTab,
    isTablet: Boolean,
    onClick: () -> Unit,
    onReload: () -> Unit,
    onMenu: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    canNavigateBack: Boolean,
    shieldOn: Boolean,
    blockedTrackers: Int,
    onShieldClick: () -> Unit,
    compact: Boolean,
    onExpand: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val reduced = reduceMotion
    val securityGlyph = when {
        tab.isPrivate -> Glyph.Incognito
        shieldOn -> Glyph.Shield
        tab.url.startsWith("https://") -> Glyph.Lock
        tab.url.startsWith("http://") -> Glyph.Info
        else -> Glyph.Search
    }
    val securityTint = when {
        tab.isPrivate -> colors.accentSecondary
        shieldOn -> colors.success
        tab.url.startsWith("https://") -> colors.success
        tab.url.startsWith("http://") -> colors.danger
        else -> colors.textSecondary
    }
    // 安全徽标在 http/https、私密/普通之间切换时是淡入淡出，而不是硬切颜色。
    val animatedSecurityTint by animateColorAsState(
        targetValue = securityTint,
        animationSpec = MotionSpec.color(Motion.Quick),
        label = "security-tint"
    )
    val shieldInteraction = remember { MutableInteractionSource() }
    // 拦截计数角标：从 0 冒出时弹入，归零时缩回。
    val shieldBadgeScale by animateFloatAsState(
        targetValue = if (blockedTrackers > 0) 1f else 0f,
        animationSpec = MotionSpec.pageSpring(),
        label = "shield-badge-scale"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isTablet && !compact) {
            val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
            PlainToolButton(
                glyph = if (isLtr) Glyph.Back else Glyph.Forward,
                description = "后退",
                enabled = canNavigateBack,
                onClick = onBack
            )
            PlainToolButton(
                glyph = if (isLtr) Glyph.Forward else Glyph.Back,
                description = "前进",
                enabled = tab.canGoForward,
                onClick = onForward
            )
        }
        Box(
            Modifier
                .size(if (compact) 30.dp else 38.dp)
                .pressScale(pressedScale = 0.9f, interactionSource = shieldInteraction)
                .clip(CircleShape)
                .background(animatedSecurityTint.copy(alpha = if (colors.isDark) 0.18f else 0.10f))
                .semantics {
                    if (shieldOn) contentDescription = "隐私护盾，已拦截 $blockedTrackers 个跟踪请求"
                }
                .clickable(
                    interactionSource = shieldInteraction,
                    indication = null,
                    enabled = shieldOn,
                    role = Role.Button,
                    onClickLabel = "隐私护盾",
                    onClick = onShieldClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // 安全图标本身也会换（锁↔感叹号↔隐身），换形时淡入淡出而不是硬切。
            AnimatedContent(
                targetState = securityGlyph,
                modifier = Modifier.size(if (compact) 15.dp else 18.dp),
                contentAlignment = Alignment.Center,
                transitionSpec = {
                    ((fadeIn(animationSpec = MotionSpec.enter(reduced, Motion.Quick)) +
                        scaleIn(
                            animationSpec = MotionSpec.enter(reduced, Motion.Quick),
                            initialScale = 0.7f
                        )) togetherWith
                        (fadeOut(animationSpec = MotionSpec.exit(reduced, Motion.Quick)) +
                            scaleOut(
                                animationSpec = MotionSpec.exit(reduced, Motion.Quick),
                                targetScale = 0.7f
                            ))).using(null)
                },
                label = "security-glyph"
            ) { glyph ->
                GlyphIcon(glyph, animatedSecurityTint, size = if (compact) 15.dp else 18.dp)
            }
            // 角标常驻组合，用缩放与透明度控制显隐，不突然冒出或消失。
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        scaleX = shieldBadgeScale
                        scaleY = shieldBadgeScale
                        alpha = shieldBadgeScale
                    }
                    .size(if (compact) 13.dp else 15.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center
            ) {
                if (blockedTrackers > 0) {
                    BasicText(
                        text = blockedTrackers.coerceAtMost(99).toString(),
                        style = TextStyle(
                            color = Color.White,
                            fontSize = if (compact) 7.sp else 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
        Spacer(Modifier.width(if (compact) 6.dp else 10.dp))
        if (compact) {
            val compactInteraction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = compactInteraction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "展开工具栏",
                        onClick = onExpand
                    )
                    .pressScale(pressedScale = 0.97f, interactionSource = compactInteraction)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = tab.displayHost,
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            CompactExpandButton(onExpand)
        } else {
            val urlInteraction = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = urlInteraction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "编辑网址",
                        onClick = onClick
                    )
                    .pressScale(pressedScale = 0.98f, interactionSource = urlInteraction)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = tab.displayHost,
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // "桌面版"徽标淡入淡出：切到桌面模式时徽标是"化"出来的，
                    // 而不是硬生生地插进标题旁边。
                    AnimatedVisibility(
                        visible = tab.desktopMode,
                        enter = fadeIn(MotionSpec.enter(Motion.Quick)) +
                            expandHorizontally(animationSpec = MotionSpec.sizeValue()),
                        exit = fadeOut(MotionSpec.exit(Motion.Quick)) +
                            shrinkHorizontally(animationSpec = MotionSpec.sizeValue())
                    ) {
                        Box(
                            Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(colors.accent.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            BasicText(
                                text = "桌面版",
                                style = TextStyle(
                                    color = colors.accent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.height(1.dp))
                BasicText(
                    text = tab.pageTitle(),
                    style = TextStyle(color = colors.textTertiary, fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            PlainToolButton(
                glyph = if (tab.isLoading) Glyph.Stop else Glyph.Reload,
                description = if (tab.isLoading) "停止加载" else "刷新",
                enabled = true,
                onClick = onReload
            )
            if (isTablet) {
                PlainToolButton(
                    glyph = Glyph.More,
                    description = "菜单",
                    enabled = true,
                    onClick = onMenu
                )
            }
        }
    }
}

@Composable
private fun CollapsedToolbarHandle(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLiuliColors.current
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .width(64.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics {
                contentDescription = "显示工具栏"
                role = Role.Button
            }
            .clickable(role = Role.Button, onClickLabel = "显示工具栏", onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tint = colors.glassTintStrong,
        blurRadius = 16.dp,
        lensHeight = 8.dp,
        lensAmount = 12.dp,
        contentPadding = PaddingValues(0.dp),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(Glyph.ChevronUp, colors.textSecondary, size = 14.dp)
    }
}

@Composable
private fun CompactExpandButton(onExpand: () -> Unit) {
    val colors = LocalLiuliColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(32.dp)
            .pressScale(pressedScale = 0.86f, interactionSource = interaction)
            .clip(CircleShape)
            .semantics {
                contentDescription = "展开工具栏"
                role = Role.Button
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "展开工具栏",
                onClick = onExpand
            ),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(Glyph.ChevronDown, colors.textSecondary, size = 16.dp)
    }
}

@Composable
private fun AddressEditContent(
    settings: SettingsStore,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester
) {
    val colors = LocalLiuliColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphIcon(Glyph.Search, colors.textSecondary, size = 19.dp)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() })
            )
            // 「清空」按钮淡入：有文字才出现，硬切会让输入框看起来在闪。
            AnimatedVisibility(
                visible = value.text.isNotEmpty(),
                enter = fadeIn(MotionSpec.enter(Motion.Quick)) +
                    scaleIn(
                        animationSpec = MotionSpec.pageSpring(),
                        initialScale = 0.7f
                    ),
                exit = fadeOut(MotionSpec.exit(Motion.Quick)) +
                    scaleOut(
                        animationSpec = MotionSpec.pageSpring(),
                        targetScale = 0.7f
                    )
            ) {
                PlainToolButton(
                    glyph = Glyph.Close,
                    description = "清空",
                    enabled = true,
                    onClick = { onValueChange(TextFieldValue("")) }
                )
            }
            val cancelInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .height(Dimens.minTouch)
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = cancelInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onCancel
                    )
                    .pressScale(pressedScale = 0.90f, interactionSource = cancelInteraction)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "取消",
                    style = TextStyle(color = colors.textSecondary, fontSize = 13.sp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchEngine.entries.forEach { engine ->
                    val selected = settings.searchEngine == engine
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) colors.accent.copy(alpha = if (colors.isDark) 0.26f else 0.13f)
                                else colors.textPrimary.copy(alpha = 0.06f)
                            )
                            .clickable(role = Role.Button) { settings.updateSearchEngine(engine) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            text = engine.label,
                            style = TextStyle(
                                color = if (selected) colors.accent else colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            GlassPillButton(
                label = "前往",
                onClick = onSubmit,
                backdrop = com.kyant.backdrop.backdrops.emptyBackdrop(),
                accent = true,
                contentPadding = PaddingValues(horizontal = 14.dp)
            )
        }
    }
}

@Composable
private fun PlainToolButton(
    glyph: Glyph,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val reduced = reduceMotion
    val interaction = remember { MutableInteractionSource() }
    // 可用与不可用之间用颜色补间淡入淡出：前进键变灰是渐变，不是瞬切。
    val tint by animateColorAsState(
        targetValue = if (enabled) colors.textPrimary else colors.textTertiary.copy(alpha = 0.4f),
        animationSpec = MotionSpec.color(Motion.Quick),
        label = "plain-tool-tint"
    )
    Box(
        Modifier
            .size(Dimens.minTouch)
            .clip(CircleShape)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            )
            .pressScale(pressedScale = 0.86f, interactionSource = interaction),
        contentAlignment = Alignment.Center
    ) {
        // 图标切换（刷新↔停止、前进↔后退）也做淡入淡出，不硬切。
        AnimatedContent(
            targetState = glyph,
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
            transitionSpec = {
                ((fadeIn(animationSpec = MotionSpec.enter(reduced, Motion.Quick)) +
                    scaleIn(
                        animationSpec = MotionSpec.enter(reduced, Motion.Quick),
                        initialScale = 0.7f
                    )) togetherWith
                    (fadeOut(animationSpec = MotionSpec.exit(reduced, Motion.Quick)) +
                        scaleOut(
                            animationSpec = MotionSpec.exit(reduced, Motion.Quick),
                            targetScale = 0.7f
                        ))).using(null)
            },
            label = "plain-tool-glyph"
        ) { target ->
            GlyphIcon(
                glyph = target,
                tint = tint,
                size = 20.dp
            )
        }
    }
}

@Composable
private fun RowScope.ToolDockButton(
    glyph: Glyph,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    badge: Int = 0
) {
    val colors = LocalLiuliColors.current
    val reduced = reduceMotion
    val interaction = remember { MutableInteractionSource() }
    // 可用/禁用之间淡入淡出：标签页键不可用时是"淡灰"，不是硬切。
    val tint by animateColorAsState(
        targetValue = if (enabled) colors.textPrimary else colors.textTertiary.copy(alpha = 0.38f),
        animationSpec = MotionSpec.color(Motion.Quick),
        label = "tool-dock-tint"
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            )
            .pressScale(pressedScale = 0.88f, interactionSource = interaction),
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            GlyphIcon(
                glyph = glyph,
                tint = tint,
                size = 22.dp
            )
            // 角标常驻组合，靠缩放与透明度控制显隐：
            // 标签数变化时角标是弹入/缩回，而不是突然冒出或消失。
            // 用 offset 而不是 padding，角标不参与布局，不会挤动图标。
            val badgeScale by animateFloatAsState(
                targetValue = if (badge > 0) 1f else 0f,
                animationSpec = MotionSpec.pageSpring(),
                label = "tool-badge-scale"
            )
            Box(
                Modifier
                    .offset(x = 11.dp, y = (-11).dp)
                    .graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                        alpha = badgeScale
                    }
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center
            ) {
                // 数字变化时交叉淡入：11 → 12 是换字，不是整块角标闪一下。
                AnimatedContent(
                    targetState = badge,
                    modifier = Modifier.size(16.dp),
                    contentAlignment = Alignment.Center,
                    transitionSpec = {
                        ((fadeIn(animationSpec = MotionSpec.enter(reduced, Motion.Quick)) +
                            scaleIn(
                                animationSpec = MotionSpec.enter(reduced, Motion.Quick),
                                initialScale = 0.6f
                            )) togetherWith
                            (fadeOut(animationSpec = MotionSpec.exit(reduced, Motion.Quick)) +
                                scaleOut(
                                    animationSpec = MotionSpec.exit(reduced, Motion.Quick),
                                    targetScale = 0.6f
                                ))).using(null)
                    },
                    label = "tool-badge-count"
                ) { count ->
                    if (count > 0) {
                        BasicText(
                            text = count.coerceAtMost(99).toString(),
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserDock(
    backdrop: Backdrop,
    tab: BrowserTab,
    tabCount: Int,
    isTablet: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    backEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalLiuliColors.current
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(31.dp),
        tint = colors.dockTint,
        blurRadius = 24.dp,
        lensHeight = 18.dp,
        lensAmount = 24.dp,
        shadow = true
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            // RTL 下前进/后退箭头要跟着翻转，否则方向和实际行为相反。
            val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
            ToolDockButton(if (isLtr) Glyph.Back else Glyph.Forward, "后退", backEnabled, onBack)
            ToolDockButton(if (isLtr) Glyph.Forward else Glyph.Back, "前进", tab.canGoForward, onForward)
            ToolDockButton(Glyph.Home, "首页", true, onHome)
            ToolDockButton(Glyph.Tabs, "标签页", true, onTabs, badge = tabCount)
            ToolDockButton(Glyph.More, "菜单", true, onMenu)
        }
    }
}

@Composable
private fun PageErrorCard(
    backdrop: Backdrop,
    message: String,
    onRetry: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLiuliColors.current
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        tint = colors.glassTintStrong,
        blurRadius = 24.dp,
        lensHeight = 18.dp,
        lensAmount = 26.dp,
        shadow = true
    ) {
        Column(
            Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 整卡已有缩放淡入，内部元素再错开一点：
            // 图标先落位，标题、说明、按钮依次跟上，读起来更有层次。
            Box(
                Modifier
                    .errorReveal(index = 0, pop = true)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(colors.danger.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.Info, colors.danger, size = 24.dp)
            }
            Spacer(Modifier.height(12.dp))
            BasicText(
                text = "页面无法打开",
                modifier = Modifier.errorReveal(index = 1),
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = message,
                modifier = Modifier.errorReveal(index = 2),
                style = TextStyle(
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.errorReveal(index = 3),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassPillButton(
                    label = "重试",
                    onClick = onRetry,
                    backdrop = backdrop,
                    accent = true,
                    glyph = Glyph.Reload
                )
                GlassPillButton(
                    label = "回到首页",
                    onClick = onHome,
                    backdrop = backdrop,
                    glyph = Glyph.Home
                )
            }
        }
    }
}

/**
 * 错误卡内的元素依次浮现。
 *
 * 延迟值必须在组合作用域里取好再进 LaunchedEffect，
 * 因为 [MotionSpec.staggerDelay] 是 @Composable，不能在协程里调用。
 */
@Composable
private fun Modifier.errorReveal(index: Int, pop: Boolean = false): Modifier {
    val stagger = MotionSpec.staggerDelay(index, stepMillis = 40)
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (stagger > 0) delay(stagger.toLong())
        appeared = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = MotionSpec.value(Motion.Normal),
        label = "error-reveal-alpha"
    )
    return graphicsLayer {
        this.alpha = alpha
        translationY = (1f - alpha) * 10.dp.toPx()
        if (pop) {
            val scale = 0.72f + 0.28f * alpha
            scaleX = scale
            scaleY = scale
        }
    }
}

@Composable
private fun BrowserMenu(
    vm: BrowserViewModel,
    tab: BrowserTab,
    backdrop: Backdrop,
    onShare: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseTab: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val bookmarked = vm.isActiveBookmarked()
    data class MenuEntry(
        val glyph: Glyph,
        val label: String,
        val onClick: () -> Unit,
        val trailing: String? = null
    )

    val entries = listOf(
        MenuEntry(Glyph.NewTab, "新建标签页", {
            onDismiss()
            vm.createTab(select = true)
        }),
        MenuEntry(Glyph.Incognito, "新建隐私标签", {
            onDismiss()
            vm.createTab(isPrivate = true, select = true)
        }),
        MenuEntry(
            if (bookmarked) Glyph.BookmarkFilled else Glyph.Bookmark,
            if (bookmarked) "取消收藏" else "添加收藏",
            { vm.toggleBookmark() }
        ),
        MenuEntry(Glyph.History, "书签与历史", onOpenLibrary),
        MenuEntry(Glyph.Share, "分享此页", onShare),
        MenuEntry(
            Glyph.Desktop,
            if (tab.desktopMode) "切换为移动版" else "请求桌面版网站",
            { vm.toggleDesktopMode() }
        ),
        MenuEntry(Glyph.Copy, "复制链接", {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            clipboard?.setPrimaryClip(
                android.content.ClipData.newPlainText("链接", tab.url)
            )
            vm.showMessage("链接已复制")
        }),
        MenuEntry(Glyph.ChevronUp, "收起工具栏", {
            onDismiss()
            vm.collapseToolbar()
        }),
        MenuEntry(Glyph.Settings, "设置", onOpenSettings),
        MenuEntry(Glyph.Trash, "关闭此标签页", onCloseTab)
    )

    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { },
        shape = RoundedCornerShape(28.dp),
        tint = colors.glassTintStrong,
        blurRadius = 26.dp,
        lensHeight = 20.dp,
        lensAmount = 28.dp,
        shadow = true
    ) {
        Column(
            Modifier
                .heightIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandMini(colors)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(
                        text = tab.pageTitle(),
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    BasicText(
                        text = tab.displayHost,
                        style = TextStyle(color = colors.textTertiary, fontSize = 11.sp),
                        maxLines = 1
                    )
                }
            }
            entries.forEachIndexed { index, entry ->
                val interaction = remember { MutableInteractionSource() }
                // 菜单项逐条浮现：十项同时出现会糊成一片，错峰更好定位。
                val stagger = MotionSpec.staggerDelay(index, stepMillis = 16)
                var appeared by remember(entry.label) { mutableStateOf(false) }
                LaunchedEffect(entry.label) {
                    if (stagger > 0) kotlinx.coroutines.delay(stagger.toLong())
                    appeared = true
                }
                val alpha by animateFloatAsState(
                    targetValue = if (appeared) 1f else 0f,
                    animationSpec = MotionSpec.value(Motion.Normal),
                    label = "menu-entry-alpha"
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.minTouch)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button
                        ) { entry.onClick() }
                        .pressScale(pressedScale = 0.97f, interactionSource = interaction)
                        .graphicsLayer {
                            this.alpha = alpha
                            translationX = (1f - alpha) * 14.dp.toPx()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphIcon(entry.glyph, colors.textSecondary, size = 20.dp)
                    Spacer(Modifier.width(12.dp))
                    BasicText(
                        text = entry.label,
                        style = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                        modifier = Modifier.weight(1f)
                    )
                    if (entry.trailing != null) {
                        BasicText(
                            text = entry.trailing,
                            style = TextStyle(color = colors.textTertiary, fontSize = 11.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandMini(colors: com.liuli.browser.ui.theme.LiuliColors) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(colors.accent, colors.accentSecondary, colors.accentTertiary)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(Glyph.Globe, Color.White, size = 18.dp)
    }
}
