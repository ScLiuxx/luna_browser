package com.liuli.browser.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFirstOrNull
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
import com.liuli.browser.ui.liquid.Visibility
import com.liuli.browser.ui.liquid.pressScale
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.Radius
import com.liuli.browser.ui.theme.Space
import com.liuli.browser.ui.theme.Type
import com.liuli.browser.ui.theme.reduceMotion
import com.liuli.browser.web.BrowserTab
import com.liuli.browser.web.BrowserViewModel
import com.liuli.browser.web.agent.AgentController
import com.liuli.browser.web.agent.AgentCreation
import com.liuli.browser.web.agent.AgentCreationFormat
import com.liuli.browser.web.agent.AgentReport
import com.liuli.browser.web.agent.AgentReportFormat
import com.liuli.browser.web.agent.AgentResearchPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

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
    // 地址栏里的输入内容要能扛住配置变更：
    // TextFieldValue 不是 Parcelable，不能直接交给 rememberSaveable，
    // 但 TextFieldValue.Saver 会把文本、选区与输入法组合区一起存下来。
    // 用 remember 的话，旋转屏幕会丢掉刚输入的网址，
    // 正在打的拼音也会被硬生生打断。
    var editValue by rememberSaveable(tab.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(tab.url))
    }
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

    val density = LocalDensity.current
    val fontScale = density.fontScale
    val idleBarHeight = if (fontScale > 1.3f) 76.dp else 64.dp
    val canNavigateBack = tab.canGoBack || vm.tabs.size > 1 || !tab.isHome
    // 收起状态不看「自动收起」开关：
    // 那个开关管的是"滚动时要不要自动收"，由 ViewModel 里的滚动监听决定；
    // 菜单里的「收起工具栏」是用户手动下的指令，
    // 要是也被这个开关拦下来，自动收起关着时点了这一项就什么都不会发生。
    // ViewModel 在跳转与切换标签时都会把 toolbarCollapsed 复位，
    // 手动收起不会一直赖着不走。
    val collapsed = vm.toolbarCollapsed && !editing && !menuOpen && !shieldOpen
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
    // 全屏时顶部控件让位给视频：淡出并稍稍上移，
    // 硬切会让地址栏"啪"地消失，和视频的切换撞在一起。
    val chromeAlpha by animateFloatAsState(
        targetValue = if (tab.isFullscreen) 0f else 1f,
        animationSpec = MotionSpec.value(Motion.Quick),
        label = "fullscreen-chrome"
    )
    // 地址栏在"浏览态 ↔ 编辑态"之间形变：
    // 容器高度用弹簧伸缩，内容同时做方向性滑动，
    // 新内容从下方推入、旧内容向上收起，避免纯淡化的"凭空替换"。
    // 这条进度与容器高度、内容的交叉淡化共用同一条弹簧：
    // 三者各走各的曲线时，内容会在容器还在长的时候先停住，
    // 看上去像文字悬在半空。
    val editProgress by animateFloatAsState(
        targetValue = if (editing) 1f else 0f,
        animationSpec = MotionSpec.pageSpring(),
        label = "address-edit-progress"
    )

    // 边缘滑动手势：左边缘右滑 = 后退，右边缘左滑 = 前进。
    // 状态放在这里，手势协程与提示气泡共用同一份进度。
    val swipeActive = remember { mutableStateOf(false) }
    val swipeDirection = remember { mutableIntStateOf(0) }
    val swipeProgress = remember { mutableFloatStateOf(0f) }
    // 地址栏底边的位置：手势起点落在它下面才接管，
    // 否则在地址栏上横划会跟地址栏抢手势。
    val chromeBottom = remember { mutableFloatStateOf(0f) }
    val swipeHintAlpha by animateFloatAsState(
        targetValue = if (swipeActive.value) 1f else 0f,
        animationSpec = MotionSpec.value(Motion.Quick),
        label = "swipe-hint-alpha"
    )

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(canNavigateBack, tab.canGoForward, tab.isFullscreen, menuOpen, shieldOpen, agentOpen, editing) {
                val edge = 26.dp.toPx()
                val trigger = 72.dp.toPx()
                // 甩动的速度阈值：600dp/s 已经是"明确的一划"，
                // 再高就只剩位移判定起作用，甩一下反而常常不生效。
                val fling = 600.dp.toPx()
                // 底部控件所在的区域不接管：横划按钮会误触发点击。
                val bottomGuard = 140.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat()
                    val fromLeft = down.position.x <= edge
                    val fromRight = down.position.x >= width - edge
                    // 只认屏幕左右边缘的起手：页面中间的横向滑动留给网页自己，
                    // 纵向滚动更是完全不受这条手势影响。
                    if (!fromLeft && !fromRight) return@awaitEachGesture
                    if (down.position.y < chromeBottom.floatValue) return@awaitEachGesture
                    if (down.position.y > size.height - bottomGuard) return@awaitEachGesture
                    // 面板、全屏与编辑态各有各的手势，这里不去抢。
                    if (menuOpen || shieldOpen || agentOpen || editing || tab.isFullscreen) {
                        return@awaitEachGesture
                    }
                    val forward = fromRight
                    if (forward && !tab.canGoForward) return@awaitEachGesture
                    if (!forward && !canNavigateBack) return@awaitEachGesture
                    // 上一次手势的进度要清零：
                    // 否则这一划刚开始时，提示气泡会先以旧进度缩放一下再回弹。
                    swipeProgress.floatValue = 0f
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var travel = 0f
                    var dragging = false
                    val pointer = down.id
                    // 手势随时可能被取消：页面接管了指针、面板打开、
                    // 甚至整个协程被键值变化打断。
                    // 收尾必须放在 finally 里，否则提示气泡会永远停在屏幕上。
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.fastFirstOrNull { it.id == pointer } ?: break
                            if (!change.pressed) break
                            travel += change.positionChange().x
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!dragging && abs(travel) > viewConfiguration.touchSlop) {
                                // 方向要对上起手的那条边：右边缘往左滑才是前进。
                                dragging = if (forward) travel < 0f else travel > 0f
                                if (dragging) {
                                    swipeDirection.intValue = if (forward) 1 else -1
                                    swipeActive.value = true
                                }
                            }
                            if (dragging) {
                                // 接管之后才消费事件，之前让网页照常滚动。
                                change.consume()
                                swipeProgress.floatValue = (abs(travel) / trigger).coerceIn(0f, 1f)
                            }
                        }
                        val velocity = tracker.calculateVelocity().x
                        val far = if (forward) travel <= -trigger else travel >= trigger
                        val fast = if (forward) velocity <= -fling else velocity >= fling
                        val committed = dragging && (far || fast)
                        if (committed) {
                            if (forward) vm.goForward() else vm.handleBack()
                        }
                    } finally {
                        swipeActive.value = false
                    }
                }
            }
    ) {
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

        // 收起/展开时内容边距跟着一起过渡：硬切会让玻璃件里的内容"跳"一下。
        val barPaddingH by animateDpAsState(
            targetValue = if (collapsed) 6.dp else 8.dp,
            animationSpec = MotionSpec.pageSpring(0.01.dp),
            label = "address-padding-h"
        )
        val barPaddingV by animateDpAsState(
            targetValue = if (collapsed) 3.dp else 6.dp,
            animationSpec = MotionSpec.pageSpring(0.01.dp),
            label = "address-padding-v"
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                // 全屏时整块淡出，而不是硬切。
                .graphicsLayer { alpha = chromeAlpha }
                // 记下地址栏的底边位置，边缘手势只在它下面接管。
                .onGloballyPositioned {
                    chromeBottom.floatValue = it.positionInParent().y + it.size.height
                }
                .statusBarsPadding()
                .padding(top = 6.dp, start = if (isTablet) 18.dp else 10.dp, end = if (isTablet) 18.dp else 10.dp)
                .widthIn(max = 1000.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassSurface(
                backdrop = backdrop,
                modifier = addressSharedModifier
                    .fillMaxWidth()
                    .height(barHeight)
                    // 描边画在玻璃之上：玻璃自带的白色描边在白底页面上看不见，
                    // 地址栏会像"印"在页面里。这圈细边把它钉在页面之上。
                    .glassHairline(color = colors.textPrimary.copy(alpha = 0.11f)),
                shape = RoundedCornerShape(barHeight / 2),
                tint = colors.glassTintStrong,
                blurRadius = 22.dp,
                lensHeight = 18.dp,
                lensAmount = 26.dp,
                contentPadding = PaddingValues(
                    horizontal = barPaddingH,
                    vertical = barPaddingV
                )
            ) {
                Crossfade(
                    targetState = editing,
                    // 与容器高度同一条弹簧：内容换完的那一刻，
                    // 地址栏也刚好长到位，两者不会各走各的。
                    animationSpec = MotionSpec.pageSpring(),
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
                                    // 光标落在末尾而不是整条选中：
                                    // 多数人点地址栏是为了改最后一段路径，
                                    // 全选会让人一打字就把整条地址抹掉。
                                    editValue = TextFieldValue(
                                        text = tab.url,
                                        selection = TextRange(tab.url.length)
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
            // 进度动画放在可见性判断之外：放进去的话每次重新出现都要从 0 重新长，
            // 加载快的站点会看到进度条闪一下，而不是接着上一段继续走。
            val progressFraction by animateFloatAsState(
                targetValue = tab.progress.coerceIn(0, 100) / 100f,
                // 进度是连续上报的，补间每来一个新值就重算一次，视觉上会一顿一顿地追；
                // 弹簧则是一路把它拉过去。弹簧会略微过冲，落到宽度上时再夹住。
                animationSpec = MotionSpec.pageSpring(),
                label = "page-progress"
            )
            Visibility(
                // 出错时进度会停在半路，此时不该再显示进度条。
                visible = tab.errorText == null && (tab.isLoading || tab.progress in 1..99),
                enter = fadeIn(MotionSpec.enter(Motion.Quick)),
                exit = fadeOut(MotionSpec.exit(Motion.Quick))
            ) {
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
                            // 留一小截可见的头：进度为 0 时也要看得出页面在加载
                            .fillMaxWidth(progressFraction.coerceIn(0.015f, 1f))
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

        Visibility(
            // 菜单面板浮在底栏上方，两者同时出现会挤在一起。
            visible = !tab.isFullscreen && !isTablet && !collapsed && !menuOpen,
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
        Visibility(
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

        Visibility(
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
                    .widthIn(max = Dimens.dialogWidth)
            )
        }

        // 遮罩的深浅单独做动画：直接切背景色会"啪"地盖下来，
        // 遮罩与面板错开半拍才像同一件事。
        val menuScrim by animateFloatAsState(
            targetValue = if (menuOpen) 1f else 0f,
            animationSpec = MotionSpec.value(Motion.Quick),
            label = "menu-scrim"
        )
        if (menuScrim > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    // 遮罩取调色板里的 scrim，与 LiquidDialog 保持一致：
                    // 写死的纯黑在浅色主题下会糊成一片脏灰。
                    .background(colors.scrim.copy(alpha = 0.18f * menuScrim))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { menuOpen = false }
            )
        }

        Visibility(
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

        Visibility(
            visible = shieldOpen,
            enter = fadeIn(MotionSpec.enter()),
            exit = fadeOut(MotionSpec.exit())
        ) {
            PrivacyShieldPanel(
                visible = shieldOpen,
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
        Visibility(
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

        Visibility(
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

        // 边缘滑动的提示：告诉用户这一划是"后退"还是"前进"，
        // 力度跟着手指走。没提示的话这条手势几乎不可能被猜到。
        if (swipeHintAlpha > 0.01f) {
            // RTL 下前进在左、后退在右，箭头也要跟着翻：
            // 否则提示出现在错误的一侧，箭头还指着反方向。
            val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
            val goingForward = swipeDirection.intValue > 0
            Box(
                Modifier
                    .align(
                        if (goingForward == isLtr) Alignment.CenterEnd else Alignment.CenterStart
                    )
                    .padding(horizontal = 20.dp)
                    .graphicsLayer {
                        alpha = swipeHintAlpha
                        val scale = 0.82f + 0.18f * swipeProgress.floatValue
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center
            ) {
                GlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier
                        .size(52.dp)
                        // 提示也是浮在网页上的玻璃圆，白底页面上同样要看得见边。
                        .glassHairline(color = colors.textPrimary.copy(alpha = 0.13f)),
                    shape = CircleShape,
                    tint = colors.glassTintStrong,
                    blurRadius = 18.dp,
                    lensHeight = 12.dp,
                    lensAmount = 18.dp,
                    contentPadding = PaddingValues(0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(
                        glyph = if (goingForward == isLtr) Glyph.Forward else Glyph.Back,
                        tint = colors.textPrimary,
                        size = 22.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyShieldPanel(
    visible: Boolean,
    blocked: Int,
    blockThirdParty: Boolean,
    fingerprintProtection: Boolean,
    isPrivate: Boolean,
    clearOnExit: Boolean,
    secureScreen: Boolean,
    onClose: () -> Unit
) {
    val colors = LocalLiuliColors.current
    // 卡片本体用弹簧缩放：只做整体淡入的话，卡片像"贴"在屏幕上，没有厚度。
    // 关闭时 visible 变 false，缩回一点，遮罩同时淡出。
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val cardScale by animateFloatAsState(
        targetValue = when {
            !visible -> 0.94f
            entered -> 1f
            else -> 0.92f
        },
        animationSpec = MotionSpec.pageSpring(),
        label = "shield-card-scale"
    )
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // 遮罩取调色板里的 scrim：写死的纯黑在浅色主题下会糊成一片脏灰。
            .background(colors.scrim.copy(alpha = if (colors.isDark) 0.46f else 0.26f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            backdrop = com.kyant.backdrop.backdrops.emptyBackdrop(),
            modifier = Modifier
                .padding(Space.xl)
                .widthIn(max = Dimens.dialogWidth)
                // 十条说明加一段正文，屏幕矮一点就装不下。
                // 给卡片封顶并让内容滚动，否则底部那几条直接看不见。
                .heightIn(max = (maxHeight - 88.dp).coerceAtLeast(0.dp))
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            shape = RoundedCornerShape(Radius.xl),
            tint = colors.glassTintStrong.copy(alpha = 0.96f),
            blurRadius = 0.dp,
            lensHeight = 0.dp,
            shadow = true
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Space.xl)
            ) {
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
                    Spacer(Modifier.width(Space.sm))
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            text = "隐私护盾已开启",
                            style = Type.title.copy(color = colors.textPrimary)
                        )
                        BasicText(
                            text = "全部拦截在本机完成",
                            style = Type.caption.copy(color = colors.textTertiary)
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
                Spacer(Modifier.height(Space.md))
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
                Spacer(Modifier.height(Space.sm))
                BasicText(
                    text = "网页无法读取剪贴板、通讯录、定位、相机或麦克风。跟踪参数会在导航时自动移除，HTTP 自动升级为 HTTPS，并阻止 DNS 预取、sendBeacon 与 window.open 数据外泄。",
                    style = Type.caption.copy(color = colors.textTertiary)
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
            .padding(vertical = Space.xxs)
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
        Spacer(Modifier.width(Space.sm))
        BasicText(
            text = text,
            style = Type.bodySmall.copy(color = colors.textSecondary)
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
    // 地址栏要显示的站点名。data:/about: 这类地址没有主机名，
    // 直接显示整条原始 URL 会被撑爆（data: 的正文可能几十 KB）。
    val hostLabel = tab.addressLabel()
    // 加载中标题还停在域名上，上下两行会显示成同一个词；
    // 换成加载状态，用户才知道页面正在打开。
    val subtitle = if (tab.isLoading) "正在加载…" else tab.pageTitle()
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
    // 徽标要说清楚当前状态：护盾开着时报拦截数，
    // 否则说明连接本身安不安全（读屏也靠这句）。
    val securityDescription = when {
        tab.isPrivate -> "隐私标签页"
        shieldOn -> "隐私护盾，已拦截 $blockedTrackers 个跟踪请求"
        tab.url.startsWith("https://") -> "安全连接"
        tab.url.startsWith("http://") -> "连接不安全"
        else -> "尚未打开网页"
    }
    // 拦截计数角标：从 0 冒出时弹入，归零时缩回。
    val shieldBadgeScale by animateFloatAsState(
        targetValue = if (blockedTrackers > 0) 1f else 0f,
        animationSpec = MotionSpec.pageSpring(),
        label = "shield-badge-scale"
    )
    // 拦截数上涨时角标要"顶"一下：光靠换字的话，
    // 用户看不出护盾刚刚又挡下了一个请求。
    // 只在真的往上加时才顶：从 0 冒出角标有自己的弹入动画，不必再叠一次。
    val trackerPop = remember { Animatable(1f) }
    var lastTrackerCount by remember { mutableIntStateOf(blockedTrackers) }
    LaunchedEffect(blockedTrackers) {
        val grew = blockedTrackers > lastTrackerCount
        lastTrackerCount = blockedTrackers
        if (grew && !reduced) {
            // NonCancellable：拦截数连着涨时，后一次会取消前一次协程，
            // 收尾动画若跟着被取消，角标就会停在放大的状态上不下来。
            withContext(NonCancellable) {
                trackerPop.snapTo(1.34f)
                trackerPop.animateTo(1f, MotionSpec.pageSpring(reduced))
            }
        }
    }
    // 收起/展开时，护盾徽标、图标、角标与间距都跟着连续变化：
    // 直接换尺寸会让这几个元素在收起的一瞬间"缩"一下，与整体形变脱节。
    val shieldSize by animateDpAsState(
        targetValue = if (compact) 30.dp else 38.dp,
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "shield-size"
    )
    val shieldGlyphSize by animateDpAsState(
        targetValue = if (compact) 15.dp else 18.dp,
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "shield-glyph-size"
    )
    val shieldBadgeSize by animateDpAsState(
        targetValue = if (compact) 14.dp else 16.dp,
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "shield-badge-size"
    )
    val shieldGap by animateDpAsState(
        targetValue = if (compact) 6.dp else 10.dp,
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "shield-gap"
    )
    val shieldBadgeFont by animateIntAsState(
        targetValue = if (compact) 8 else 9,
        animationSpec = MotionSpec.intValue(Motion.Quick),
        label = "shield-badge-font"
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
                .size(shieldSize)
                .pressScale(pressedScale = 0.9f, interactionSource = shieldInteraction)
                .semantics {
                    contentDescription = securityDescription
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
            // 圆盘单独一层并裁剪：
            // 角标贴在圆盘右上角，若跟着圆盘一起裁会被切掉一块。
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(animatedSecurityTint.copy(alpha = if (colors.isDark) 0.20f else 0.13f))
                    // 描一圈边：玻璃件浮在任意底色的网页上，
                    // 光靠一层淡色底，圆形轮廓在白页上根本看不出来。
                    .drawBehind {
                        drawCircle(
                            color = animatedSecurityTint.copy(alpha = 0.45f),
                            radius = size.minDimension / 2f - 0.75.dp.toPx(),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // 安全图标本身也会换（锁↔感叹号↔隐身），换形时淡入淡出而不是硬切。
                AnimatedContent(
                    targetState = securityGlyph,
                    modifier = Modifier.size(shieldGlyphSize),
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
                    GlyphIcon(glyph, animatedSecurityTint, size = shieldGlyphSize)
                }
            }
            // 角标常驻组合，用缩放与透明度控制显隐，不突然冒出或消失。
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        val pop = trackerPop.value
                        scaleX = shieldBadgeScale * pop
                        scaleY = shieldBadgeScale * pop
                        alpha = shieldBadgeScale
                    }
                    .height(shieldBadgeSize)
                    // 药丸而不是圆点：两位数（12、25）在圆里会被压扁看不清。
                    .widthIn(min = shieldBadgeSize)
                    .clip(RoundedCornerShape(50))
                    .background(colors.accent)
                    // 白圈：角标压在圆盘上，没有这一圈就分不清两层。
                    .border(1.5.dp, colors.glassTintStrong, RoundedCornerShape(50))
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // 数字变化时交叉淡入：拦截数从 9 跳到 10，
                // 直接换字会"闪"一下。
                AnimatedContent(
                    targetState = blockedTrackers,
                    // 尺寸固定：AnimatedContent 默认会补间尺寸变化，
                    // 角标是固定大小，让它自己补间会抖。
                    modifier = Modifier.height(shieldBadgeSize),
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
                    label = "shield-badge-count"
                ) { count ->
                    if (count > 0) {
                        BasicText(
                            // 超过两位就写"99+"：写死 99 会让人以为只挡了 99 个。
                            text = if (count > 99) "99+" else count.toString(),
                            style = TextStyle(
                                color = Color.White,
                                fontSize = shieldBadgeFont.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(shieldGap))
        // 收起态与展开态之间做交叉过渡，而不是硬切：
        // 硬切会让整条地址栏的内容在收起瞬间"闪"一下，
        // 展开的两行（主机名 + 页面标题）也无法自然地收成一行。
        AnimatedContent(
            targetState = compact,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (fadeIn(animationSpec = MotionSpec.enter(reduced, Motion.Quick)) +
                    expandHorizontally(
                        animationSpec = MotionSpec.sizeValue(reduced),
                        expandFrom = Alignment.Start
                    )) togetherWith
                    (fadeOut(animationSpec = MotionSpec.exit(reduced, Motion.Quick)) +
                        shrinkHorizontally(
                            animationSpec = MotionSpec.sizeValue(reduced),
                            shrinkTowards = Alignment.Start
                        ))
            },
            label = "address-idle-body"
        ) { isCompact ->
            if (isCompact) {
                val compactInteraction = remember { MutableInteractionSource() }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        text = hostLabel,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable(
                                interactionSource = compactInteraction,
                                indication = null,
                                role = Role.Button,
                                onClickLabel = "展开工具栏",
                                onClick = onExpand
                            )
                            .pressScale(pressedScale = 0.97f, interactionSource = compactInteraction)
                            .padding(horizontal = 4.dp),
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    CompactExpandButton(onExpand)
                }
            } else {
                val urlInteraction = remember { MutableInteractionSource() }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                                text = hostLabel,
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
                            Visibility(
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
                        // 加载状态与页面标题之间交叉淡化：
                        // 直接换字会看到一行字"啪"地换成另一行，
                        // 页面刚打开那一瞬间最容易撞上。
                        AnimatedContent(
                            targetState = subtitle,
                            modifier = Modifier.fillMaxWidth(),
                            transitionSpec = {
                                fadeIn(animationSpec = MotionSpec.enter(reduced, Motion.Quick)) togetherWith
                                    fadeOut(animationSpec = MotionSpec.exit(reduced, Motion.Quick))
                            },
                            label = "address-subtitle"
                        ) { text ->
                            BasicText(
                                text = text,
                                style = TextStyle(color = colors.textTertiary, fontSize = 10.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
    }
}

@Composable
private fun CollapsedToolbarHandle(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLiuliColors.current
    val interaction = remember { MutableInteractionSource() }
    // 外层撑到 48dp 当点击区，内层才是那颗 24dp 的小把手：
    // 把手只有 24dp 高，按不中。
    Box(
        modifier = modifier
            .heightIn(min = Dimens.minTouch)
            .semantics {
                contentDescription = "显示工具栏"
                role = Role.Button
            }
            // 不用系统水波纹：这块是玻璃面，按压反馈由 pressScale 给，
            // 系统水波纹会直接画在折射层上面，很脏。
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "显示工具栏",
                onClick = onClick
            ),
        // 把手贴着底边：点击区往上长，视觉位置不动。
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .width(64.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .pressScale(pressedScale = 0.9f, interactionSource = interaction),
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
}

@Composable
private fun CompactExpandButton(onExpand: () -> Unit) {
    val colors = LocalLiuliColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            // 收起态的地址栏只有 42dp 高，48dp 的点击区撑不下；
            // 好在同一行的站点名整块都可点，这里只保证按下去有反馈。
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
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    // 聚焦时放大镜与占位文字一起转成强调色：
    // 地址栏和首页搜索框是同一块玻璃，焦点表现必须长得一样。
    val focused by interactionSource.collectIsFocusedAsState()
    val glyphTint by animateColorAsState(
        targetValue = if (focused) colors.accent else colors.textSecondary,
        animationSpec = MotionSpec.color(Motion.Quick),
        label = "address-glyph-tint"
    )
    val hintTint by animateColorAsState(
        targetValue = if (focused) colors.textSecondary else colors.textTertiary,
        animationSpec = MotionSpec.color(Motion.Quick),
        label = "address-hint-tint"
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphIcon(Glyph.Search, glyphTint, size = 19.dp)
            Spacer(Modifier.width(10.dp))
            // 选区底色与手柄跟随强调色，和首页搜索框保持一致。
            // 整个输入框都要裹进去：手柄是画在装饰框之外的兄弟节点，
            // 只包 decorationBox 的话手柄仍是默认蓝。
            CompositionLocalProvider(
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = colors.accent,
                    backgroundColor = colors.accent.copy(alpha = 0.28f)
                )
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .semantics {
                            contentDescription = "地址栏"
                            // 声明成网址字段：自动填充与密码管理器据此
                            // 才知道这里填的是网址而不是一段普通文字。
                            contentType = ContentType("url")
                        },
                    interactionSource = interactionSource,
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    singleLine = true,
                    // 地址栏是网址输入框：键盘给 URL 版面（带 / . : 键），
                    // 自动纠错与首字母大写都会把网址改坏，一律关掉。
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    // 回车键与「前往」按钮同一把尺：空内容同样不提交，
                    // 否则键盘上能走通、按钮却按不动，用户会以为按键坏了。
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (value.text.isNotBlank()) {
                                // 键盘的"前往"没有按下这一下，触感得自己给。
                                // 首页搜索框也是这么做的：两屏是同一块共享元素，
                                // 走键盘提交时的手感必须一致。
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSubmit()
                            }
                        }
                    ),
                    // 清空之后输入框是全空的，没有提示就不知道这里能输入什么。
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.text.isEmpty()) {
                                BasicText(
                                    text = "搜索或输入网址",
                                    style = TextStyle(color = hintTint, fontSize = 15.sp),
                                    maxLines = 1
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
            // 「清空」按钮淡入：有文字才出现，硬切会让输入框看起来在闪。
            Visibility(
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
                    val interaction = remember { MutableInteractionSource() }
                    // 选中与未选中之间用补间过渡：
                    // 此前是硬切底色与字色，整排引擎像被人换掉了一批。
                    val chipBackground by animateColorAsState(
                        targetValue = if (selected) {
                            colors.accent.copy(alpha = if (colors.isDark) 0.26f else 0.13f)
                        } else {
                            colors.textPrimary.copy(alpha = 0.06f)
                        },
                        animationSpec = MotionSpec.color(Motion.Quick),
                        label = "engine-chip-background"
                    )
                    val chipLabel by animateColorAsState(
                        targetValue = if (selected) colors.accent else colors.textSecondary,
                        animationSpec = MotionSpec.color(Motion.Quick),
                        label = "engine-chip-label"
                    )
                    // 外层撑到 48dp 当点击区，内层才是那颗小胶囊：
                    // 胶囊本身只有文字那么高，按不中。
                    Box(
                        Modifier
                            .heightIn(min = Dimens.minTouch)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Button,
                                onClick = { settings.updateSearchEngine(engine) }
                            )
                            .pressScale(pressedScale = 0.94f, interactionSource = interaction),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(chipBackground)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            BasicText(
                                text = engine.label,
                                style = TextStyle(
                                    color = chipLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            GlassPillButton(
                label = "前往",
                onClick = onSubmit,
                backdrop = com.kyant.backdrop.backdrops.emptyBackdrop(),
                accent = true,
                // 空内容没有可去的地方：按钮置灰，
                // 比按下去再弹一句"请输入网址"少一次往返。
                enabled = value.text.isNotBlank(),
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
                // 禁用态要念出来：只靠图标变灰，
                // 读屏用户根本不知道这一下按不动。
                if (!enabled) disabled()
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
                // 角标数量并进名字里：单独念一个"3"没有意义，
                // "标签页，3 个标签页"才是完整的一句。
                contentDescription = if (badge > 0) "$description，$badge 个标签页" else description
                role = Role.Button
                // 禁用态要念出来：只靠图标变灰，
                // 读屏用户根本不知道这一下按不动。
                if (!enabled) disabled()
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
                    .height(17.dp)
                    // 药丸而不是圆点：两位数（12、25）在圆里会被压扁看不清。
                    .widthIn(min = 17.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.accent)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // 数字变化时交叉淡入：11 → 12 是换字，不是整块角标闪一下。
                AnimatedContent(
                    targetState = badge,
                    modifier = Modifier.height(17.dp),
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
                            // 超过两位写"99+"：写死 99 会让人以为只有 99 个标签页。
                            text = if (count > 99) "99+" else count.toString(),
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            // 数量已经并进按钮的名字里，这里不要再单独播报一遍。
                            modifier = Modifier.clearAndSetSemantics {}
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
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.dockHeight)
            // 底栏同样浮在网页上，白底页面上也要有一条看得见的边。
            .glassHairline(color = colors.textPrimary.copy(alpha = 0.11f)),
        shape = RoundedCornerShape(Dimens.dockHeight / 2),
        tint = colors.dockTint,
        blurRadius = 24.dp,
        lensHeight = 18.dp,
        lensAmount = 24.dp,
        shadow = true
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = Space.xs)) {
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
    // WebView 的原始描述是给开发看的（net::ERR_XXX），
    // 直接摆给用户等于什么都没说。这里翻成"发生了什么 + 该怎么办"。
    val copy = remember(message) { explainError(message) }
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        shape = RoundedCornerShape(Radius.lg),
        tint = colors.glassTintStrong,
        blurRadius = 24.dp,
        lensHeight = 18.dp,
        lensAmount = 26.dp,
        shadow = true
    ) {
        Column(
            Modifier.padding(Space.xl),
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
            Spacer(Modifier.height(Space.md))
            BasicText(
                text = copy.title,
                modifier = Modifier.errorReveal(index = 1),
                style = Type.title.copy(color = colors.textPrimary, textAlign = TextAlign.Center)
            )
            Spacer(Modifier.height(Space.xs))
            BasicText(
                text = copy.detail,
                modifier = Modifier.errorReveal(index = 2),
                style = Type.bodySmall.copy(color = colors.textSecondary, textAlign = TextAlign.Center),
                // 错误文案来自 WebView，长度不受控：
                // 限三行并省略，否则超长报错会把整张卡片顶出屏幕。
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Space.lg))
            Row(
                modifier = Modifier.errorReveal(index = 3),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
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
 * 错误卡上给用户看的两句话：发生了什么、接下来怎么办。
 *
 * WebView 抛回来的描述是 net::ERR_NAME_NOT_RESOLVED 这类内部代号，
 * 直接展示等于什么都没解释。这里挑常见的几类翻成中文，
 * 认不出来的就给一句通用建议，别把机器码甩给用户。
 */
private data class ErrorCopy(val title: String, val detail: String)

private fun explainError(raw: String): ErrorCopy {
    val text = raw.trim()
    return when {
        text.isBlank() -> ErrorCopy("页面无法打开", "请稍后重试，或检查网络连接。")
        text.contains("HTTPS") ->
            ErrorCopy("已拦截不安全的连接", text)
        text.contains("ERR_NAME_NOT_RESOLVED") || text.contains("ERR_NAME_RESOLUTION_FAILED") ->
            ErrorCopy("找不到这个网站", "域名可能拼错了，或设备暂时没有联网。")
        text.contains("ERR_INTERNET_DISCONNECTED") || text.contains("ERR_NETWORK_CHANGED") ->
            ErrorCopy("网络已断开", "请打开 Wi-Fi 或移动数据后再试一次。")
        text.contains("ERR_CONNECTION_TIMED_OUT") || text.contains("ERR_TIMED_OUT") ->
            ErrorCopy("连接超时", "服务器没有及时响应，可以稍后重试。")
        text.contains("ERR_CONNECTION_REFUSED") || text.contains("ERR_CONNECTION_RESET") ->
            ErrorCopy("连接被中断", "对方没有接受这次请求，稍后再试一次。")
        text.contains("ERR_ADDRESS_UNREACHABLE") ->
            ErrorCopy("无法访问该地址", "网络或代理设置可能挡住了这次访问。")
        text.contains("ERR_ACCESS_DENIED") ->
            ErrorCopy("访问被拒绝", "该站点拒绝了这次访问请求。")
        text.contains("ERR_FILE_NOT_FOUND") || text.contains("ERR_FILE_ACCESS_DENIED") ->
            ErrorCopy("文件打不开", "本地文件可能已被移动或没有权限读取。")
        text.contains("ERR_CACHE_MISS") ->
            ErrorCopy("缓存已失效", "重新加载一次通常就能恢复。")
        text.contains("ERR_QUIC_PROTOCOL_ERROR") || text.contains("ERR_SSL_PROTOCOL_ERROR") ->
            ErrorCopy("安全连接失败", "这个站点的加密握手没有成功，可以稍后重试。")
        // 剩下的多半是应用自己写的中文提示，原样展示即可。
        text.contains("net::ERR") || text.contains("ERR_") ->
            ErrorCopy("页面无法打开", "请检查网络连接后重试一次。")
        else -> ErrorCopy("页面无法打开", text)
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
    // 面板可以往下甩掉：底栏手势是移动端的基本预期，
    // 只靠点遮罩关闭对单手操作太远了。
    val scope = rememberCoroutineScope()
    // 拖拽位移直接写状态，不用 Animatable：
    // 每个指针事件都起一个协程去 snapTo，事件一密就互相打断，
    // 面板会明显落后于手指，像是卡了一帧。
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // 松手后的回弹单独占一个协程句柄：
    // 新手势开始时必须先把它掐掉，否则回弹会和拖拽抢同一个位移量，面板会抖。
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val dismissDistance = with(LocalDensity.current) { 96.dp.toPx() }
    val flingSpeed = with(LocalDensity.current) { 900.dp.toPx() }
    val glideSpec = MotionSpec.glideSpring()
    val dragState = rememberDraggableState { delta ->
        settleJob?.cancel()
        settleJob = null
        dragOffset = (dragOffset + delta).coerceAtLeast(0f)
    }
    data class MenuEntry(
        val glyph: Glyph,
        val label: String,
        val onClick: () -> Unit,
        val trailing: String? = null,
        // 破坏性操作要和普通条目分开：
        // 手指一滑就点错代价太大，至少要让"关标签"看起来不一样。
        val destructive: Boolean = false,
        val dividerBefore: Boolean = false
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
        MenuEntry(
            Glyph.Trash,
            "关闭此标签页",
            onCloseTab,
            destructive = true,
            dividerBefore = true
        )
    )

    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .graphicsLayer {
                // 越往下拖越透明：面板"沉下去"的感觉比单纯位移更明确。
                alpha = 1f - (dragOffset / dismissDistance).coerceIn(0f, 1f) * 0.45f
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (dragOffset > dismissDistance || velocity > flingSpeed) {
                        onDismiss()
                    } else {
                        // 回弹从当前位移起算，起点必须在起协程前取好：
                        // 协程真正跑起来时 dragOffset 可能已经被改过。
                        val start = dragOffset
                        settleJob = scope.launch {
                            animate(
                                initialValue = start,
                                targetValue = 0f,
                                animationSpec = glideSpec
                            ) { value, _ -> dragOffset = value }
                        }
                    }
                }
            )
            .clip(RoundedCornerShape(Radius.xl))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { },
        shape = RoundedCornerShape(Radius.xl),
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
                .padding(Space.sm)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandMini(colors)
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    BasicText(
                        text = tab.pageTitle(),
                        style = Type.title.copy(color = colors.textPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    BasicText(
                        text = tab.addressLabel(),
                        style = Type.caption.copy(color = colors.textTertiary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                // 按下的底色：玻璃面板上只有缩放的话几乎看不出按到了哪一行，
                // 补一层淡淡的填充，手指落点才明确。
                val pressed by interaction.collectIsPressedAsState()
                val pressFill by animateColorAsState(
                    targetValue = if (pressed) {
                        colors.textPrimary.copy(alpha = 0.07f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = MotionSpec.color(Motion.Quick),
                    label = "menu-entry-press"
                )
                val entryTint = if (entry.destructive) colors.danger else colors.textSecondary
                if (entry.dividerBefore) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.md, vertical = Space.xs)
                            .height(1.dp)
                            .background(colors.textPrimary.copy(alpha = 0.10f))
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.minTouch)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(pressFill)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = entry.label
                        ) { entry.onClick() }
                        .pressScale(pressedScale = 0.97f, interactionSource = interaction)
                        .graphicsLayer {
                            this.alpha = alpha
                            translationX = (1f - alpha) * 14.dp.toPx()
                        }
                        .padding(horizontal = Space.md, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphIcon(entry.glyph, entryTint, size = 20.dp)
                    Spacer(Modifier.width(Space.md))
                    BasicText(
                        text = entry.label,
                        style = Type.body.copy(
                            color = if (entry.destructive) colors.danger else colors.textPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (entry.trailing != null) {
                        BasicText(
                            text = entry.trailing,
                            style = Type.caption.copy(color = colors.textTertiary)
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

/**
 * 浮在网页上的玻璃件需要一圈看得见的边。
 *
 * 玻璃自带的描边是半透明白色：白底页面上地址栏和底栏会像"印"在页面里，
 * 完全读不出浮起来的感觉。这圈按主题反色的细边把轮廓钉住，
 * 深浅主题下都能与任意底色拉开距离。
 *
 * 画在玻璃之后而不是另起一层：这样描边会跟着共享元素一起飞，
 * 首页与浏览器之间来回切换时不会留下一条掉队的线。
 *
 * 圆角按实际高度取一半：地址栏与底栏都是胶囊形，
 * 而且共享元素切换时高度会被重新测量，半高圆角在任何尺寸下都贴合。
 */
private fun Modifier.glassHairline(color: Color): Modifier =
    drawWithContent {
        drawContent()
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(size.height / 2f),
            style = Stroke(width = 1.dp.toPx())
        )
    }

/**
 * 地址栏要显示的站点名。
 *
 * [BrowserTab.displayHost] 对 data:、about: 这类没有主机名的地址会退回整条原始 URL，
 * 而 data: 的正文可能几十 KB，直接塞进文本框既撑爆地址栏又拖慢排版。
 * 这里统一换成可读的名字，正常网址仍然显示主机名。
 */
private fun BrowserTab.addressLabel(): String {
    val url = this.url
    if (url.isBlank()) return "新标签页"
    return when {
        url.startsWith("data:") -> "数据页面"
        url.startsWith("about:") -> "内部页面"
        url.startsWith("javascript:") -> "脚本页面"
        url.startsWith("file:") -> "本地文件"
        else -> {
            // 没有主机名的地址（超长的一串单词、被截断的链接）会整条落到这里。
            // 硬切会把词从中间劈开，读起来像乱码，补一个省略号才像"被截断了"。
            val host = displayHost
            if (host.length <= MaxAddressLabelLength) {
                host
            } else {
                host.take(MaxAddressLabelLength - 1) + "…"
            }
        }
    }
}

/** 地址栏里站点名的最大长度：再长就把主机名读不完了。 */
private const val MaxAddressLabelLength = 120
