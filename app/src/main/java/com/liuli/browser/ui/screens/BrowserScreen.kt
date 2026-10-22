package com.liuli.browser.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.web.BrowserTab
import com.liuli.browser.web.BrowserViewModel
import com.liuli.browser.web.agent.AgentController
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
    val addressSharedModifier = with(sharedScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "address-bar"),
            animatedVisibilityScope = animatedScope,
            boundsTransform = { _, _ -> spring(dampingRatio = 1f, stiffness = 342f) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
        )
    }
    val snapshotAlpha by animateFloatAsState(
        targetValue = if (showSharedSnapshot) 1f else 0f,
        animationSpec = tween(220),
        label = "shared-snapshot-alpha"
    )
    val barHeight by animateDpAsState(
        targetValue = when {
            editing -> 136.dp
            collapsed -> 42.dp
            else -> idleBarHeight
        },
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
        label = "address-morph"
    )

    Box(Modifier.fillMaxSize()) {
        if (sharedSnapshot != null && sharedSnapshotKey != null) {
            val snapshotSharedModifier = with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "tab-image-$sharedSnapshotKey"),
                    animatedVisibilityScope = animatedScope,
                    boundsTransform = { _, _ -> spring(dampingRatio = 0.9f, stiffness = 380f) },
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
                    animationSpec = tween(160),
                    label = "address-content"
                ) { isEditing ->
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

            if (tab.isLoading || tab.progress in 1..99) {
                val fraction by animateFloatAsState(
                    targetValue = tab.progress.coerceIn(0, 100) / 100f,
                    animationSpec = tween(220),
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
            enter = fadeIn(tween(180)) + slideInVertically(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
                initialOffsetY = { it / 2 }
            ),
            exit = fadeOut(tween(140)) + slideOutVertically(
                animationSpec = tween(140),
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
        if (!tab.isFullscreen && !isTablet && collapsed) {
            CollapsedToolbarHandle(
                backdrop = backdrop,
                onClick = { vm.expandToolbar() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 6.dp)
            )
        }

        if (tab.errorText != null) {
            PageErrorCard(
                backdrop = backdrop,
                message = tab.errorText.orEmpty(),
                onRetry = { vm.reload() },
                onHome = onNewHomeTab,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 380.dp)
            )
        }

        AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120))
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
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
                initialOffsetY = { it / 2 }
            ) + fadeIn(tween(160)),
            exit = slideOutVertically(
                animationSpec = tween(160),
                targetOffsetY = { it / 3 }
            ) + fadeOut(tween(120)),
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

        if (shieldOpen) {
            PrivacyShieldPanel(
                blocked = tab.blockedTrackers,
                blockThirdParty = settings.blockThirdPartyCookies,
                fingerprintProtection = settings.fingerprintProtection,
                isPrivate = tab.isPrivate,
                clearOnExit = settings.clearOnExit,
                onClose = { shieldOpen = false }
            )
        }

        if (settings.agentEnabled && !tab.isFullscreen && !menuOpen && !shieldOpen && !agentOpen) {
            AgentOrb(
                backdrop = backdrop,
                onClick = {
                    menuOpen = false
                    shieldOpen = false
                    agentOpen = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(
                        end = if (isTablet) 24.dp else 16.dp,
                        bottom = if (isTablet) 24.dp else 100.dp
                    )
            )
        }

        if (agentOpen) {
            AgentPanel(
                agent = agent,
                tabTitle = tab.pageTitle(),
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
                        tabId = tab.id
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
                }
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
                ShieldPoint("已拦截 $blocked 个跟踪请求", colors.success)
                ShieldPoint(
                    if (blockThirdParty) "第三方 Cookie 已阻止" else "第三方 Cookie 已允许",
                    if (blockThirdParty) colors.success else colors.textTertiary
                )
                ShieldPoint("定位、相机、麦克风请求默认拒绝", colors.success)
                ShieldPoint(
                    if (clearOnExit) "退出应用时清除 Cookie、缓存与历史" else "退出时保留本站数据",
                    if (clearOnExit) colors.success else colors.textTertiary
                )
                ShieldPoint("通用 User-Agent，减少设备指纹", colors.success)
                ShieldPoint(
                    if (fingerprintProtection) "防浏览器指纹：统一 Canvas/WebGL2/Client Hints/硬件/时区，阻止 WebRTC 与 Service Worker"
                    else "防浏览器指纹：已关闭",
                    if (fingerprintProtection) colors.success else colors.textTertiary
                )
                ShieldPoint(
                    if (isPrivate) "隐私标签：已隔离本地存储与脚本 Cookie，不写入任何持久数据"
                    else "本地存储仅本机保存，退出可清除",
                    if (isPrivate) colors.accentSecondary else colors.textTertiary
                )
                ShieldPoint("SSL 错误阻断 · 文件与本地存储访问禁用", colors.success)
                ShieldPoint("DNS 预取已关闭 · Do Not Track 与 GPC 已开启", colors.success)
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
private fun ShieldPoint(text: String, tint: Color) {
    val colors = LocalLiuliColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
    Row(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isTablet && !compact) {
            PlainToolButton(
                glyph = Glyph.Back,
                description = "后退",
                enabled = canNavigateBack,
                onClick = onBack
            )
            PlainToolButton(
                glyph = Glyph.Forward,
                description = "前进",
                enabled = tab.canGoForward,
                onClick = onForward
            )
        }
        Box(
            Modifier
                .size(if (compact) 30.dp else 38.dp)
                .clip(CircleShape)
                .background(securityTint.copy(alpha = if (colors.isDark) 0.18f else 0.10f))
                .semantics {
                    if (shieldOn) contentDescription = "隐私护盾，已拦截 $blockedTrackers 个跟踪请求"
                }
                .clickable(
                    enabled = shieldOn,
                    role = Role.Button,
                    onClickLabel = "隐私护盾",
                    onClick = onShieldClick
                ),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(securityGlyph, securityTint, size = if (compact) 15.dp else 18.dp)
            if (blockedTrackers > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(if (compact) 13.dp else 15.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center
                ) {
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
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClickLabel = "展开工具栏", onClick = onExpand)
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
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "编辑网址", onClick = onClick)
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
                    if (tab.desktopMode) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
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
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .semantics {
                contentDescription = "展开工具栏"
                role = Role.Button
            }
            .clickable(role = Role.Button, onClickLabel = "展开工具栏", onClick = onExpand),
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
            if (value.text.isNotEmpty()) {
                PlainToolButton(
                    glyph = Glyph.Close,
                    description = "清空",
                    enabled = true,
                    onClick = { onValueChange(TextFieldValue("")) }
                )
            }
            Box(
                Modifier
                    .height(Dimens.minTouch)
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onCancel)
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
    Box(
        Modifier
            .size(Dimens.minTouch)
            .clip(CircleShape)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(
            glyph = glyph,
            tint = if (enabled) colors.textPrimary else colors.textTertiary.copy(alpha = 0.4f),
            size = 20.dp
        )
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
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            GlyphIcon(
                glyph = glyph,
                tint = if (enabled) colors.textPrimary else colors.textTertiary.copy(alpha = 0.38f),
                size = 22.dp
            )
            if (badge > 0) {
                Box(
                    Modifier
                        .padding(start = 22.dp, bottom = 22.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = badge.coerceAtMost(99).toString(),
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
            ToolDockButton(Glyph.Back, "后退", backEnabled, onBack)
            ToolDockButton(Glyph.Forward, "前进", tab.canGoForward, onForward)
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
            Box(
                Modifier
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
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = message,
                style = TextStyle(
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            entries.forEach { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.minTouch)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { entry.onClick() }
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
