package com.liuli.browser.ui.screens

import android.os.Build
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.liuli.browser.BuildConfig
import com.liuli.browser.data.SearchEngine
import com.liuli.browser.data.SettingsStore
import com.liuli.browser.ui.components.DangerConfirmDialog
import com.liuli.browser.ui.components.GlassIconButton
import com.liuli.browser.ui.components.GlassPillButton
import com.liuli.browser.ui.components.GlassSurface
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.GlyphIcon
import com.liuli.browser.ui.components.HeaderSubtitle
import com.liuli.browser.ui.components.PrivacyPolicyDialog
import com.liuli.browser.ui.components.ScreenHeader
import com.liuli.browser.ui.liquid.LiquidSlider
import com.liuli.browser.ui.liquid.LiquidToggle
import com.liuli.browser.ui.liquid.Visibility
import com.liuli.browser.ui.liquid.pressScale
import com.liuli.browser.ui.theme.AccentPalettes
import com.liuli.browser.ui.theme.DarkModeOption
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.ui.theme.Motion
import com.liuli.browser.ui.theme.MotionSpec
import com.liuli.browser.ui.theme.Radius
import com.liuli.browser.ui.theme.Space
import com.liuli.browser.ui.theme.Type
import com.liuli.browser.ui.theme.reduceMotion
import com.liuli.browser.web.BrowserViewModel
import com.liuli.browser.web.agent.AgentAuditEntry
import com.liuli.browser.web.agent.AgentController
import com.liuli.browser.web.agent.AgentPolicyStore
import com.liuli.browser.web.agent.AgentSiteMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    settings: SettingsStore,
    vm: BrowserViewModel,
    agent: AgentController,
    agentPolicies: AgentPolicyStore,
    backdrop: LayerBackdrop,
    isTablet: Boolean,
    onClose: () -> Unit,
    onRevokePrivacy: () -> Unit
) {
    val colors = LocalLiuliColors.current
    // 破坏性操作点下去时给一次轻触反馈：
    // 玻璃按钮按下时只有形变，"这一步会抹掉东西"这句话听不见也摸不着。
    val haptics = LocalHapticFeedback.current
    // 标签页上限的数字是补间上去的：拖动滑块时能看清自己走了几格。
    val maxTabs by animateIntAsState(
        targetValue = settings.maxTabs,
        animationSpec = MotionSpec.intValue(Motion.Quick),
        label = "max-tabs"
    )
    val activePolicyHost = AgentPolicyStore.normalizeHost(vm.activeTab?.url)
    val activePolicy = agentPolicies.policyFor(activePolicyHost)
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmRevoke by rememberSaveable { mutableStateOf(false) }
    // 清空站点策略与清空对话同样是"一键抹掉全部记录"的操作，
    // 此前一点就删、没有确认，与同页的"清除浏览数据"对不上。
    var confirmClearPolicies by rememberSaveable { mutableStateOf(false) }
    var confirmClearAgent by rememberSaveable { mutableStateOf(false) }
    // 页面左右留白走间距阶梯：平板 Space.xl，手机 Space.lg。
    val horizontal = if (isTablet) Space.xl else Space.lg
    val webViewPackage = remember {
        runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()
    }
    val webViewVersion = webViewPackage?.versionName
    val webViewProvider = webViewPackage?.packageName ?: "系统 WebView"
    val chromiumMajor = webViewVersion
        ?.let { Regex("^([0-9]+)\\.").find(it)?.groupValues?.getOrNull(1) }
    val kernelRows = remember(webViewProvider, webViewVersion) {
        listOf(
            "WebView 提供方" to webViewProvider,
            "WebView 版本" to (webViewVersion ?: "未知"),
            "Chromium 主版本" to (chromiumMajor ?: "未知"),
            "内核形态" to "系统内置 WebView（APK 不打包 Chromium）"
        )
    }

    BackHandler(enabled = showPrivacy) { showPrivacy = false }
    BackHandler(enabled = confirmClear) { confirmClear = false }
    BackHandler(enabled = confirmRevoke) { confirmRevoke = false }
    BackHandler(enabled = confirmClearPolicies) { confirmClearPolicies = false }
    BackHandler(enabled = confirmClearAgent) { confirmClearAgent = false }
    BackHandler(
        enabled = !showPrivacy && !confirmClear && !confirmRevoke &&
            !confirmClearPolicies && !confirmClearAgent,
        onBack = onClose
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = Dimens.pageContentWidth)
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontal)
                .padding(top = Space.sm)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScreenHeader(
                    title = "设置",
                    isTablet = isTablet,
                    modifier = Modifier.weight(1f),
                    subtitle = { HeaderSubtitle("外观、搜索、AI 与隐私合规") }
                )
                GlassIconButton(
                    glyph = Glyph.Close,
                    contentDescription = "关闭设置",
                    onClick = onClose,
                    backdrop = backdrop,
                    size = 46.dp,
                    glyphSize = 18.dp
                )
            }

            Spacer(Modifier.height(Space.lg))

            SettingsSection(title = "外观", backdrop = backdrop, index = 0) {
                SettingsLabel("主题色", "液态玻璃的折射色调")
                Spacer(Modifier.height(Space.md))
                // 色板整排等宽：五个色块按可用宽度平分，
                // 窄屏上不会挤成一团，宽屏上也不会散得找不着对应关系。
                Row(
                    Modifier.widthIn(max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    AccentPalettes.forEachIndexed { index, palette ->
                        val selected = settings.accentIndex == index
                        val interaction = remember { MutableInteractionSource() }
                        // 选中圈缩放淡入，未选中淡出：切换主题色时有一眼可见的反馈。
                        val markScale by animateFloatAsState(
                            targetValue = if (selected) 1f else 0.4f,
                            animationSpec = MotionSpec.pageSpring(),
                            label = "accent-mark"
                        )
                        val markAlpha by animateFloatAsState(
                            targetValue = if (selected) 1f else 0f,
                            animationSpec = MotionSpec.value(Motion.Quick),
                            label = "accent-mark-alpha"
                        )
                        // 外圈跟着选中态亮起：勾号只有 16dp，
                        // 光靠它认出当前用的是哪套颜色还是太吃力。
                        val ringAlpha by animateFloatAsState(
                            targetValue = if (selected) 1f else 0f,
                            animationSpec = MotionSpec.value(Motion.Quick),
                            label = "accent-ring-alpha"
                        )
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(Dimens.minTouch)
                                    .clip(CircleShape)
                                    // 色块是纯图形，读屏软件只会念出"按钮"，
                                    // 得自己报出色名，并说明当前是否已选中。
                                    .semantics {
                                        contentDescription = if (selected) {
                                            "${palette.label}主题色，已选中"
                                        } else {
                                            "${palette.label}主题色"
                                        }
                                        role = Role.Button
                                    }
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = null,
                                        role = Role.Button,
                                        onClickLabel = palette.label
                                    ) { settings.updateAccentIndex(index) }
                                    .pressScale(pressedScale = 0.86f, interactionSource = interaction),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .border(
                                            1.5.dp,
                                            colors.accent.copy(alpha = ringAlpha),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                androidx.compose.ui.graphics.Brush.linearGradient(
                                                    listOf(palette.primary, palette.secondary, palette.tertiary)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        GlyphIcon(
                                            glyph = Glyph.Check,
                                            tint = Color.White.copy(alpha = markAlpha),
                                            modifier = Modifier.graphicsLayer {
                                                scaleX = markScale
                                                scaleY = markScale
                                            },
                                            size = 16.dp
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(Space.xxs))
                            // 色名写在色块下面：光有一排颜色分不出是哪一套，
                            // 名字写出来才认得出当前用的是哪个。
                            BasicText(
                                text = palette.label,
                                style = Type.caption.copy(
                                    color = if (selected) colors.accent else colors.textTertiary,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                                ),
                                maxLines = 1,
                                // 色名已经报进色块的语义里，这里不要再念一遍。
                                modifier = Modifier.clearAndSetSemantics { }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Space.md))
                SettingsLabel("深色模式", null)
                Spacer(Modifier.height(Space.sm))
                DarkModeSegmented(
                    selected = settings.darkMode,
                    onSelect = { settings.updateDarkMode(it) }
                )
                Spacer(Modifier.height(Space.md))
                ToggleRow(
                    title = "动态流光背景",
                    // 说明跟着状态走：写死一句"关闭后保持静态"，
                    // 用户关了也看不出现在到底是不是静态的。
                    subtitle = if (settings.dynamicBackground) {
                        "低功耗地缓慢流动"
                    } else {
                        "已关闭：背景保持静态"
                    },
                    checked = settings.dynamicBackground,
                    onCheckedChange = { settings.updateDynamicBackground(it) },
                    backdrop = backdrop
                )
                Divider()
                ToggleRow(
                    title = "减弱动效",
                    subtitle = "尊重无障碍偏好，关闭一镜到底的位移动画",
                    checked = settings.reduceMotion,
                    onCheckedChange = { settings.updateReduceMotion(it) },
                    backdrop = backdrop
                )
            }

            Spacer(Modifier.height(Space.lg))

            SettingsSection(title = "搜索", backdrop = backdrop, index = 1) {
                SettingsLabel("默认搜索引擎", "地址栏输入非网址内容时使用")
                Spacer(Modifier.height(Space.md))
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    SearchEngine.entries.forEach { engine ->
                        val selected = settings.searchEngine == engine
                        val interaction = remember { MutableInteractionSource() }
                        // 勾选标记缩放淡入，行文字颜色交叉过渡。
                        val markScale by animateFloatAsState(
                            targetValue = if (selected) 1f else 0.5f,
                            animationSpec = MotionSpec.pageSpring(),
                            label = "engine-mark"
                        )
                        val markAlpha by animateFloatAsState(
                            targetValue = if (selected) 1f else 0f,
                            animationSpec = MotionSpec.value(Motion.Quick),
                            label = "engine-mark-alpha"
                        )
                        // 空心圈是"未选中"的那一半，得单独一条透明度：
                        // 复用勾号的透明度的话，未选中时圈也跟着透明，等于没画。
                        val ringAlpha by animateFloatAsState(
                            targetValue = if (selected) 0f else 1f,
                            animationSpec = MotionSpec.value(Motion.Quick),
                            label = "engine-ring-alpha"
                        )
                        val labelColor by animateColorAsState(
                            targetValue = if (selected) colors.accent else colors.textPrimary,
                            animationSpec = MotionSpec.color(),
                            label = "engine-label"
                        )
                        // 按下时给一行浅色高亮：玻璃件没有水波纹，
                        // 手指按下去时没有这层提示就完全没反应。
                        val pressed by interaction.collectIsPressedAsState()
                        val highlight by animateColorAsState(
                            targetValue = if (pressed) {
                                colors.textPrimary.copy(alpha = 0.06f)
                            } else {
                                Color.Transparent
                            },
                            animationSpec = MotionSpec.color(Motion.Quick),
                            label = "engine-row-highlight"
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Dimens.minTouch)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(highlight)
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    role = Role.RadioButton
                                ) { settings.updateSearchEngine(engine) }
                                // 读屏要能听出当前用的是哪个引擎：
                                // 光有 Role.RadioButton 不够，选中状态也得报上去。
                                .semantics(mergeDescendants = true) {
                                    contentDescription = if (selected) {
                                        "${engine.label}，已选中"
                                    } else {
                                        engine.label
                                    }
                                }
                                .pressScale(pressedScale = 0.98f, interactionSource = interaction)
                                // 横向不再自带留白：卡片里每一行的文字都要落在同一条竖线上。
                                .padding(vertical = Space.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(17.dp), contentAlignment = Alignment.Center) {
                                if (selected) {
                                    GlyphIcon(
                                        glyph = Glyph.Check,
                                        tint = colors.accent.copy(alpha = markAlpha),
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = markScale
                                            scaleY = markScale
                                        },
                                        size = 17.dp
                                    )
                                } else {
                                    // 未选中时画一个空心圈：这一行的语义是单选，
                                    // 放大镜图标会让人以为按下去是去搜索。
                                    Box(
                                        Modifier
                                            .size(13.dp)
                                            .clip(CircleShape)
                                            .border(
                                                1.5.dp,
                                                colors.textTertiary.copy(alpha = ringAlpha),
                                                CircleShape
                                            )
                                    )
                                }
                            }
                            Spacer(Modifier.width(Space.sm))
                            BasicText(
                                text = engine.label,
                                style = Type.body.copy(
                                    color = labelColor,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.lg))

            SettingsSection(title = "浏览", backdrop = backdrop, index = 2) {
                SettingsLabel("标签页上限", "达到上限后自动回收最久未使用的标签，可选 4–12 个")
                Spacer(Modifier.height(Space.xs))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidSlider(
                        value = { settings.maxTabs.toFloat() },
                        onValueChange = {
                            settings.updateMaxTabs(kotlin.math.round(it).toInt())
                        },
                        valueRange = 4f..12f,
                        visibilityThreshold = 1f,
                        backdrop = backdrop,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Space.lg),
                        contentDescription = "标签页上限"
                    )
                    BasicText(
                        // 数字也是补间上去的：拖动滑块时能看清自己走了几格。
                        text = "$maxTabs 个",
                        style = Type.bodySmall.copy(
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End
                        ),
                        maxLines = 1,
                        // 读数宽度固定：数字从 8 变成 12 时滑块不能跟着变窄，
                        // 否则拖到一半轨道会缩，拇指会在眼前自己往前挪一点。
                        // 但字号放大后"12 个"要放得下：给它下限而不是死宽度。
                        modifier = Modifier.widthIn(min = 52.dp)
                    )
                }
                Divider()
                ToggleRow(
                    title = "新标签使用桌面版网站",
                    subtitle = if (settings.desktopModeDefault) {
                        "对需要完整布局的网站更友好"
                    } else {
                        "已关闭：新标签按站点的移动版布局打开"
                    },
                    checked = settings.desktopModeDefault,
                    onCheckedChange = { settings.updateDesktopModeDefault(it) },
                    backdrop = backdrop
                )
                Divider()
                ToggleRow(
                    title = "阻止第三方 Cookie",
                    subtitle = if (settings.blockThirdPartyCookies) {
                        "默认开启，减少跨站跟踪"
                    } else {
                        "已关闭：跨站 Cookie 会被放行"
                    },
                    checked = settings.blockThirdPartyCookies,
                    onCheckedChange = {
                        settings.updateBlockThirdPartyCookies(it)
                        vm.applyCookiePolicy()
                    },
                    backdrop = backdrop
                )
                Divider()
                ToggleRow(
                    title = "滚动时收起工具栏",
                    subtitle = if (settings.autoHideToolbar) {
                        "向下滚动时压缩顶栏并隐藏底栏，向上滚动恢复"
                    } else {
                        "已关闭：顶栏与底栏始终保持展开"
                    },
                    checked = settings.autoHideToolbar,
                    onCheckedChange = {
                        settings.updateAutoHideToolbar(it)
                        if (!it) vm.expandToolbar()
                    },
                    backdrop = backdrop
                )
            }

            Spacer(Modifier.height(Space.lg))

            SettingsSection(title = "AI 页面助手", backdrop = backdrop, index = 3) {
                ToggleRow(
                    title = "启用 Agent 浮球",
                    subtitle = "读取正文与页面结构，可提议受控操作",
                    checked = settings.agentEnabled,
                    onCheckedChange = { settings.updateAgentEnabled(it) },
                    backdrop = backdrop
                )
                Divider()
                ToggleRow(
                    title = "允许提议页面操作",
                    subtitle = "单步操作逐条确认；多步任务需批准计划后执行，可暂停或取消",
                    checked = settings.agentActionsEnabled,
                    onCheckedChange = { settings.updateAgentActionsEnabled(it) },
                    backdrop = backdrop
                )
                Divider()
                SettingsLabel(
                    title = "当前站点策略",
                    subtitle = if (activePolicyHost != null) {
                        "$activePolicyHost · 仅保存在本机"
                    } else {
                        "先打开一个网页，再回到这里设置"
                    }
                )
                Spacer(Modifier.height(Space.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    AgentSiteMode.entries.forEach { mode ->
                        GlassPillButton(
                            label = mode.label,
                            onClick = { agentPolicies.setMode(activePolicyHost, mode) },
                            backdrop = backdrop,
                            accent = activePolicy.mode == mode,
                            enabled = activePolicyHost != null,
                            contentPadding = PaddingValues(horizontal = Space.md)
                        )
                    }
                }
                Spacer(Modifier.height(Space.xs))
                BasicText(
                    text = if (activePolicyHost == null) {
                        "打开网页后可选择禁止、只读或允许；策略按域名保存。"
                    } else {
                        activePolicy.mode.hint
                    },
                    style = Type.caption.copy(color = colors.textTertiary)
                )
                // 低风险免确认只在"允许"站点出现，出现与收起都有过渡。
                Visibility(
                    visible = activePolicyHost != null && activePolicy.mode == AgentSiteMode.Allowed,
                    enter = fadeIn(MotionSpec.enter()) + expandVertically(
                        animationSpec = MotionSpec.sizeValue()
                    ),
                    exit = fadeOut(MotionSpec.exit()) + shrinkVertically(
                        animationSpec = MotionSpec.sizeValue()
                    )
                ) {
                    ToggleRow(
                        title = "本站低风险步骤免确认",
                        subtitle = "只自动执行滚动，点击与填写仍需逐条确认",
                        checked = activePolicy.trustLowRisk,
                        onCheckedChange = { agentPolicies.setTrustLowRisk(activePolicyHost, it) },
                        backdrop = backdrop
                    )
                }
                // 站点策略存在时才出现这个按钮：
                // 硬切会让按钮在内容中间突然冒出来，
                // 淡入淡出接住它，过渡才不生硬。
                Visibility(
                    visible = agentPolicies.policies.isNotEmpty(),
                    enter = fadeIn(MotionSpec.enter(Motion.Quick)) +
                        expandVertically(animationSpec = MotionSpec.sizeValue(Motion.Quick)),
                    exit = fadeOut(MotionSpec.exit(Motion.Quick)) +
                        shrinkVertically(animationSpec = MotionSpec.sizeValue(Motion.Quick))
                ) {
                    Column {
                        Spacer(Modifier.height(Space.sm))
                        GlassPillButton(
                            label = "清除全部站点策略",
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                confirmClearPolicies = true
                            },
                            backdrop = backdrop,
                            glyph = Glyph.Trash,
                            // 破坏性操作：文字与图标染上危险色，
                            // 并先问一句再删。
                            danger = true
                        )
                    }
                }
                Divider()
                ToggleRow(
                    title = "允许云端推理",
                    subtitle = "默认关闭；开启后脱敏正文可能发送到你配置的接口",
                    checked = settings.agentCloudEnabled,
                    onCheckedChange = { settings.updateAgentCloudEnabled(it) },
                    backdrop = backdrop
                )
                // 云端配置区：展开云端推理后才出现，展开/收起有过渡。
                Visibility(
                    visible = settings.agentCloudEnabled,
                    enter = fadeIn(MotionSpec.enter()) + expandVertically(
                        animationSpec = MotionSpec.sizeValue()
                    ),
                    exit = fadeOut(MotionSpec.exit()) + shrinkVertically(
                        animationSpec = MotionSpec.sizeValue()
                    )
                ) {
                    Column {
                        Spacer(Modifier.height(Space.md))
                        SettingsField(
                            label = "接口地址（OpenAI 兼容）",
                            value = settings.agentEndpoint,
                            placeholder = SettingsStore.DEFAULT_AGENT_ENDPOINT,
                            onValueChange = { settings.updateAgentEndpoint(it) },
                            keyboardType = KeyboardType.Uri
                        )
                        Spacer(Modifier.height(Space.sm))
                        SettingsField(
                            label = "模型名称",
                            value = settings.agentModel,
                            placeholder = SettingsStore.DEFAULT_AGENT_MODEL,
                            onValueChange = { settings.updateAgentModel(it) },
                            // 模型名（gpt-4o 之类）不是网址：URL 键盘会塞进
                            // 一堆斜杠点号键位，反而碍事。
                            keyboardType = KeyboardType.Text
                        )
                        Spacer(Modifier.height(Space.sm))
                        SettingsField(
                            label = "API Key（仅本次会话）",
                            value = settings.agentApiKey,
                            placeholder = "不写入磁盘，退出应用即失效",
                            onValueChange = { settings.updateAgentApiKey(it) },
                            keyboardType = KeyboardType.Password,
                            password = true
                        )
                    }
                }
                Spacer(Modifier.height(Space.sm))
                val cloudState = when {
                    !settings.agentCloudEnabled -> "本地模式"
                    settings.agentApiKey.isBlank() -> "云端已开启，未填写 API Key"
                    !settings.agentEndpoint.trim().startsWith("https://") ->
                        "云端接口必须使用 HTTPS"
                    else -> "云端模式已就绪，可生成并验证多步计划"
                }
                BasicText(
                    text = buildString {
                        append("当前模式：")
                        append(cloudState)
                        agent.lastCloudFailure?.takeIf { it.isNotBlank() }?.let {
                            append("；最近失败：")
                            append(it)
                        }
                    },
                    style = Type.caption.copy(color = colors.textTertiary)
                )
                Spacer(Modifier.height(Space.md))
                BasicText(
                    text = "Agent 默认在本机完成页面速览、页内查找与关键词问答。页面操作只在本地执行，" +
                        "单步动作需要你逐条确认；多步任务会先生成计划，批准后按顺序执行，执行中可暂停、重试或取消。" +
                        "云端开启后，多步任务先由模型生成 JSON 计划，本地校验通过后才进入计划卡；" +
                        "点击或填写后会回读页面并调用模型验证。" +
                        "不会自动提交表单，密码与文件字段始终拒绝。" +
                        "站点策略按域名保存在本机：禁止站点完全不读取，只读站点不执行动作，" +
                        "允许站点可单独开启低风险步骤免确认。" +
                        "云端模式必须手动开启，且仅支持 HTTPS；发送前会脱敏邮箱、手机号、证件号与长数字串。" +
                        "隐私标签始终只在本机处理。",
                    style = Type.caption.copy(color = colors.textTertiary, lineHeight = 16.sp)
                )
                // 审计记录整块淡入淡出：
                // 硬切会让分隔线与几行文字突然冒出来，
                // 而它是随 Agent 动作不断变化的，出现得并不频繁。
                Visibility(
                    visible = agent.audit.isNotEmpty(),
                    enter = fadeIn(MotionSpec.enter(Motion.Quick)) +
                        expandVertically(animationSpec = MotionSpec.sizeValue(Motion.Quick)),
                    exit = fadeOut(MotionSpec.exit(Motion.Quick)) +
                        shrinkVertically(animationSpec = MotionSpec.sizeValue(Motion.Quick))
                ) {
                    Column {
                        Spacer(Modifier.height(Space.md))
                        Divider()
                        SettingsLabel(
                            title = "审计记录",
                            subtitle = "仅保存在本机内存，退出或清除对话时删除"
                        )
                        Spacer(Modifier.height(Space.xs))
                        agent.audit.takeLast(6).reversed().forEach { entry ->
                            BasicText(
                                text = entry.displayLine(),
                                style = Type.caption.copy(color = colors.textTertiary),
                                // 审计行由主机名、动作、结果拼成，长度不受控。
                                // 限一行并省略，一行审计不该把设置页撑开。
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Space.md))
                GlassPillButton(
                    label = "清除对话与审计",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        confirmClearAgent = true
                    },
                    backdrop = backdrop,
                    glyph = Glyph.Trash,
                    // 破坏性操作：文字与图标染上危险色，
                    // 并先问一句再删。
                    danger = true
                )
            }

            Spacer(Modifier.height(Space.lg))

            SettingsSection(title = "隐私护盾", backdrop = backdrop, index = 4) {
                ToggleRow(
                    title = "隐私护盾",
                    subtitle = "拦截跟踪器、去除跟踪参数、使用通用 User-Agent",
                    checked = settings.privacyShield,
                    onCheckedChange = {
                        settings.updatePrivacyShield(it)
                        vm.applyPrivacySettings()
                    },
                    backdrop = backdrop
                )
                Divider()
                ToggleRow(
                    title = "防浏览器指纹",
                    subtitle = "统一 Canvas/WebGL2/Client Hints/硬件/时区指纹，阻止 WebRTC、媒体与 DNS 预取，防止跨站关联",
                    checked = settings.fingerprintProtection,
                    onCheckedChange = {
                        settings.updateFingerprintProtection(it)
                        vm.applyPrivacySettings()
                    },
                    backdrop = backdrop
                )
                Divider()
                ToggleRow(
                    title = "退出即清除数据",
                    subtitle = if (settings.clearOnExit) {
                        "关闭应用时清除 Cookie、缓存、历史与标签会话"
                    } else {
                        "已关闭：本地数据会保留，直到手动清除"
                    },
                    checked = settings.clearOnExit,
                    onCheckedChange = { settings.updateClearOnExit(it) },
                    backdrop = backdrop
                )
                Divider()
                ToggleRow(
                    title = "禁止截屏与最近任务预览",
                    subtitle = "阻止系统截屏、录屏与最近任务缩略图读取页面内容",
                    checked = settings.secureScreen,
                    onCheckedChange = {
                        settings.updateSecureScreen(it)
                        vm.applyPrivacySettings()
                    },
                    backdrop = backdrop
                )
                Spacer(Modifier.height(Space.md))
                BasicText(
                    text = "隐私护盾在本机运行，不上传网址或任何标识符。隐私标签关闭时清除该站点 Cookie。" +
                        "网页无法读取剪贴板、通讯录、定位、相机或麦克风。",
                    style = Type.caption.copy(color = colors.textTertiary, lineHeight = 16.sp)
                )
            }

            Spacer(Modifier.height(Space.lg))

            SettingsSection(title = "金标联盟 · 隐私与合规", backdrop = backdrop, index = 5) {
                // 十条条目平铺在一起会像一堵墙：拆成三组，
                // 每一组的用途一眼可见，滑动时也有段落感。
                GroupLabel("数据与权限")
                ComplianceRow("权限最小化", "仅 INTERNET，无存储、定位、相机、通讯录等敏感权限")
                ComplianceRow("隐私首启同意", "首次启动展示说明，同意后才创建 WebView 与联网")
                ComplianceRow("无第三方 SDK", "零统计、零广告、零账号；书签与历史仅存本机")
                GroupLabel("防护")
                ComplianceRow("隐私护盾", "本机拦截追踪器、去除跟踪参数、退出即清除数据")
                ComplianceRow("安全浏览", "SSL 错误阻断、混合内容阻止、文件访问禁用、Safe Browsing")
                GroupLabel("体验")
                ComplianceRow("64 位兼容", "仅打包 arm64-v8a / x86_64 64 位 ABI，无 32 位原生库")
                ComplianceRow("Android 17", "targetSdk 37，适配新版前台与内存策略")
                ComplianceRow("大屏与折叠屏", "响应式侧栏、多列标签、可调整窗口与旋转")
                ComplianceRow("一镜到底", "搜索框与标签卡片共享元素/容器，弹簧曲线驱动")
                ComplianceRow("深色与无障碍", "深色模式、48dp 触控、语义标签、字体缩放、减弱动效")
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    GlassPillButton(
                        label = "隐私说明",
                        onClick = { showPrivacy = true },
                        backdrop = backdrop,
                        glyph = Glyph.Shield
                    )
                    GlassPillButton(
                        label = "清除浏览数据",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            confirmClear = true
                        },
                        backdrop = backdrop,
                        glyph = Glyph.Trash,
                        // 破坏性操作：文字与图标染上危险色。
                        danger = true
                    )
                }
            }

            Spacer(Modifier.height(Space.lg))

            SettingsSection(title = "关于", backdrop = backdrop, index = 6) {
                GroupLabel("版本")
                AboutRow("应用版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                GroupLabel("内核")
                kernelRows.forEach { (label, value) -> AboutRow(label, value) }
                GroupLabel("平台与实现")
                AboutRow("运行平台", "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
                AboutRow("设计语言", "Liquid Glass · Backdrop (Apache-2.0)")
                AboutRow("界面框架", "Jetpack Compose 1.12 · Kotlin 2.4")
                Spacer(Modifier.height(Space.md))
                BasicText(
                    text = "琉璃浏览器使用系统 Chromium WebView 作为内核，内核随系统或 WebView 应用更新，应用本身不内置 Chromium 运行时，因此 APK 约 2 MB。",
                    // 说明段落用 caption：与 AI 分区里的长说明同一档，
                    // 此前这一段用的是 label（半粗），两段正文看起来不是一套东西。
                    style = Type.caption.copy(color = colors.textTertiary, lineHeight = 16.sp)
                )
                Spacer(Modifier.height(Space.xs))
                BasicText(
                    text = "琉璃浏览器不提供云同步。所有浏览数据均保存在本机，可在上方一键清除。",
                    style = Type.caption.copy(color = colors.textTertiary, lineHeight = 16.sp)
                )
                Spacer(Modifier.height(Space.md))
                GlassPillButton(
                    label = "撤回隐私同意",
                    onClick = {
                        // 撤回同意会清掉本地全部数据，同样是破坏性操作。
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        confirmRevoke = true
                    },
                    backdrop = backdrop,
                    glyph = Glyph.Info
                )
            }

            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(Space.xl))
        }
    }

    PrivacyPolicyDialog(visible = showPrivacy, onDismiss = { showPrivacy = false })
    DangerConfirmDialog(
        visible = confirmClear,
        title = "清除浏览数据？",
        message = "将删除 Cookie、缓存、书签与历史记录，此操作不可撤销。",
        confirmLabel = "确认清除",
        glyph = Glyph.Trash,
        onConfirm = {
            confirmClear = false
            vm.clearBrowsingData()
        },
        onCancel = { confirmClear = false }
    )
    DangerConfirmDialog(
        visible = confirmRevoke,
        title = "撤回隐私同意？",
        message = "撤回后将清除本地书签、历史、Cookie、缓存与设置，并返回隐私告知页。此操作不可撤销。",
        confirmLabel = "撤回并清除",
        glyph = Glyph.Shield,
        onConfirm = {
            confirmRevoke = false
            onRevokePrivacy()
        },
        onCancel = { confirmRevoke = false }
    )
    DangerConfirmDialog(
        visible = confirmClearPolicies,
        title = "清除全部站点策略？",
        message = "将删除本机保存的全部站点策略，各站点回到默认行为。此操作不可撤销。",
        confirmLabel = "确认清除",
        glyph = Glyph.Trash,
        onConfirm = {
            confirmClearPolicies = false
            agentPolicies.clear()
        },
        onCancel = { confirmClearPolicies = false }
    )
    DangerConfirmDialog(
        visible = confirmClearAgent,
        title = "清除对话与审计？",
        message = "将删除当前对话与审计记录，此操作不可撤销。",
        confirmLabel = "确认清除",
        glyph = Glyph.Trash,
        onConfirm = {
            confirmClearAgent = false
            agent.clear()
        },
        onCancel = { confirmClearAgent = false }
    )
}

@Composable
private fun SettingsSection(
    title: String,
    backdrop: LayerBackdrop,
    index: Int = 0,
    content: @Composable () -> Unit
) {
    val colors = LocalLiuliColors.current
    // 整屏七段设置分区逐段浮现：一次全亮会让人找不到起点。
    val stagger = MotionSpec.staggerDelay(index, stepMillis = 26)
    // "减少动态效果"下位移动画要整个去掉：
    // 淡入可以留，位移正是这套设定里被要求省掉的那部分。
    val reduced = reduceMotion
    var appeared by remember(title) { mutableStateOf(false) }
    LaunchedEffect(title) {
        if (stagger > 0) kotlinx.coroutines.delay(stagger.toLong())
        appeared = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = MotionSpec.value(Motion.Normal),
        label = "settings-section-alpha"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                translationY = if (reduced) 0f else (1f - alpha) * 16.dp.toPx()
            }
    ) {
        BasicText(
            text = title,
            // 分区名用强调色：卡片里的字段名比它还大也更深，
            // 不给分区名一点色彩，整屏就分不出组。
            style = Type.sectionTitle.copy(color = colors.accent),
            // 分区名与卡片左边缘对齐：之前多缩进 4dp，
            // 和卡片、以及卡片里的文字都不在同一条竖线上。
            modifier = Modifier.padding(bottom = Space.sm)
        )
        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.lg),
            tint = colors.glassTint,
            blurRadius = 20.dp,
            lensHeight = 16.dp,
            lensAmount = 22.dp,
            contentPadding = PaddingValues(Space.lg)
        ) {
            Column(Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

/**
 * 深色模式分段控件。
 *
 * 指示器是一块独立底色，位置与宽度按分段实测值滑动：
 * 每段各自带底色、靠淡入淡出换人，看上去是"闪"而不是"滑"。
 */
@Composable
private fun DarkModeSegmented(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val colors = LocalLiuliColors.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    // 每个分段的真实位置：指示器按实际尺寸滑过去，不能靠估算。
    val segmentBounds = remember { mutableStateMapOf<Int, Rect>() }
    val selectedBounds = segmentBounds[selected]
    val indicatorLeft by animateDpAsState(
        targetValue = selectedBounds?.left?.let { with(density) { it.toDp() } } ?: 0.dp,
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "dark-indicator-left"
    )
    val indicatorTop by animateDpAsState(
        targetValue = selectedBounds?.top?.let { with(density) { it.toDp() } } ?: 0.dp,
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "dark-indicator-top"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = selectedBounds?.width?.let { with(density) { it.toDp() } } ?: 0.dp,
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "dark-indicator-width"
    )
    val indicatorHeight by animateDpAsState(
        targetValue = selectedBounds?.height?.let { with(density) { it.toDp() } } ?: 0.dp,
        animationSpec = MotionSpec.pageSpring(0.01.dp),
        label = "dark-indicator-height"
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(Radius.pill))
            // 深色下 6% 的白几乎看不见轨道：与书签页的分段控件取同一档，
            // 浅色主题仍保持轻一点的分量。
            .background(
                colors.textPrimary.copy(alpha = if (colors.isDark) 0.10f else 0.06f)
            )
    ) {
        // 指示器画在文字下面，切换时底色滑过去，文字始终压在最上层。
        if (selectedBounds != null) {
            Box(
                Modifier
                    .offset(x = indicatorLeft, y = indicatorTop)
                    .width(indicatorWidth)
                    .height(indicatorHeight)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(colors.glassTintStrong)
            )
        }
        Row(
            // 三段平分整条宽度：内容宽度的分段在字体放大后会顶出卡片，
            // 均分之后每段各自省略，控件始终是满宽的一条。
            Modifier.fillMaxWidth().padding(Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            DarkModeOption.entries.forEachIndexed { index, option ->
                val isSelected = index == selected
                val interaction = remember { MutableInteractionSource() }
                val labelColor by animateColorAsState(
                    targetValue = if (isSelected) colors.textPrimary else colors.textSecondary,
                    animationSpec = MotionSpec.color(),
                    label = "dark-mode-label"
                )
                // 字重也平滑过渡：选中瞬间换字重，文字会明显"跳"一下。
                val weight by animateFloatAsState(
                    targetValue = if (isSelected) 600f else 400f,
                    animationSpec = MotionSpec.value(Motion.Quick),
                    label = "dark-mode-weight"
                )
                Box(
                    Modifier
                        // 三段各占一份：均分之后字体放大也不会把
                        // 控件从卡片里挤出去。
                        .weight(1f)
                        .onGloballyPositioned {
                            val origin = it.positionInParent()
                            segmentBounds[index] = Rect(
                                offset = origin,
                                size = it.size.toSize()
                            )
                        }
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.RadioButton
                        ) {
                            // 换主题会立刻重染整屏，给一次轻触反馈确认改动生效了。
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        }
                        // 读屏要能听出当前选中的是哪一项：
                        // 光有 Role.RadioButton 不够，选中状态也得报上去。
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (isSelected) {
                                "${option.label}，已选中"
                            } else {
                                option.label
                            }
                        }
                        .pressScale(pressedScale = 0.92f, interactionSource = interaction)
                        // 胶囊本身只有一行字高，补足最小触控高度。
                        .heightIn(min = Dimens.minTouch)
                        .padding(horizontal = Space.sm, vertical = Space.sm),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = option.label,
                        style = Type.bodySmall.copy(
                            color = labelColor,
                            fontWeight = FontWeight(weight.toInt())
                        ),
                        // 字体放大到 1.3 以上时"跟随系统"会顶出一段：
                        // 限一行并省略，不让文字压到邻段上。
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsLabel(title: String, subtitle: String?) {
    val colors = LocalLiuliColors.current
    Column {
        BasicText(
            text = title,
            style = Type.body.copy(
                color = colors.textPrimary,
                // 字段名比开关名重一档：两者字号一样、只差半个字重时，
                // 卡片里分不清哪行是分组、哪行是条目。
                fontWeight = FontWeight.SemiBold
            )
        )
        if (subtitle != null) {
            Spacer(Modifier.height(Space.xxs))
            BasicText(
                text = subtitle,
                // 与开关行的说明同一档：卡片里的说明文字层级要统一，
                // 一半 caption 一半 bodySmall，看不出哪条更重要。
                style = Type.bodySmall.copy(color = colors.textSecondary)
            )
        }
    }
}

/**
 * 卡片内的小组标题。
 *
 * 同一张卡片里塞进十条以上的行时，整屏看上去没有尽头。
 * 拆成几组、每组给一个标题，长列表才有段落感。
 */
@Composable
private fun GroupLabel(text: String) {
    val colors = LocalLiuliColors.current
    BasicText(
        text = text,
        // 小节标题要压得住下面那几行说明：
        // 11sp 的 label 在高密度屏上太小，看不出这是一组标题。
        style = Type.bodySmall.copy(
            color = colors.textSecondary,
            fontWeight = FontWeight.SemiBold
        ),
        modifier = Modifier.padding(top = Space.sm, bottom = Space.xxs)
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop
) {
    val colors = LocalLiuliColors.current
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    // 按下时给一行浅色高亮：玻璃件没有水波纹，
    // 手指按下去时没有这层提示就完全没反应。
    val pressed by interaction.collectIsPressedAsState()
    val highlight by animateColorAsState(
        targetValue = if (pressed) {
            colors.textPrimary.copy(alpha = 0.06f)
        } else {
            Color.Transparent
        },
        animationSpec = MotionSpec.color(Motion.Quick),
        label = "toggle-row-highlight"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch),
        // 开关顶对齐：说明文字一行还是两行，各行开关的高度都不一样，
        // 顶对齐才能让所有开关落在同一条竖线上。
        verticalAlignment = Alignment.Top
    ) {
        // 标题整块都是开关的点击区：既要点得中，也要报得出开/关。
        // 竖向内边距从外层挪到这里，点击区才真正覆盖到 48dp。
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = Dimens.minTouch)
                .clip(RoundedCornerShape(Radius.sm))
                .background(highlight)
                // 标题与说明并成一个语义节点：读屏不会把同一行念两遍，
                // 开关的开合状态与它的说明一起报出来。
                .semantics(mergeDescendants = true) {
                    role = Role.Switch
                    toggleableState = ToggleableState(checked)
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Switch
                ) {
                    // 点标题等于拨开关：这里也给一次轻触反馈，
                    // 否则点标题没震动、点开关有震动，同一行两种手感。
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(!checked)
                }
                .pressScale(pressedScale = 0.97f, interactionSource = interaction)
                // 横向不再自带留白：卡片里各行的文字都落在同一条竖线上。
                .padding(end = Space.md, top = Space.sm, bottom = Space.sm)
        ) {
            BasicText(
                text = title,
                // 行高写死：开关要拿它当基准对齐标题，不能随字体度量漂移。
                style = Type.body.copy(color = colors.textPrimary, lineHeight = 20.sp)
            )
            Spacer(Modifier.height(Space.xxs))
            BasicText(
                text = subtitle,
                // 说明文字给到 bodySmall：10sp 的 caption 在高密度屏上
                // 几乎读不出来，而这行字正是"这个开关现在是什么状态"的唯一说明。
                style = Type.bodySmall.copy(color = colors.textSecondary)
            )
        }
        // 开关与标题行中心对齐：标题行 20dp、开关 28dp，
        // 往下挪 4dp 两者的中心才在同一条基线上。
        Box(
            Modifier.offset(y = Space.sm - 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // 开关自身的语义被上面那一行接管：两处都报"开关"，
            // 读屏会把这一行念两遍。这里只留下图形与手势。
            Box(Modifier.clearAndSetSemantics { }) {
                LiquidToggle(
                    selected = { checked },
                    onSelect = onCheckedChange,
                    backdrop = backdrop,
                    contentDescription = title
                )
            }
        }
    }
}

@Composable
private fun ComplianceRow(title: String, detail: String) {
    val colors = LocalLiuliColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.sm),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = Space.xxs)
                .size(18.dp)
                .clip(CircleShape)
                .background(colors.success.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.Check, colors.success, size = 10.dp)
        }
        Spacer(Modifier.width(Space.sm))
        Column {
            BasicText(
                text = title,
                style = Type.sectionTitle.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(Modifier.height(Space.xxs))
            BasicText(
                text = detail,
                // 合规条目是给人读的解释，不是脚注：
                // 给到 bodySmall，浅色主题下才不至于糊成一片。
                style = Type.bodySmall.copy(color = colors.textSecondary)
            )
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    val colors = LocalLiuliColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            style = Type.bodySmall.copy(color = colors.textSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // 值可能很长（WebView 版本号、包名），限一行并省略。
        // 它也分一份权重，否则超长值会把左侧标签整个挤没。
        BasicText(
            text = value,
            style = Type.bodySmall.copy(
                color = colors.textPrimary,
                textAlign = TextAlign.End
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Divider() {
    val colors = LocalLiuliColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.sm)
            .height(1.dp)
            // 深色下 7% 的白几乎看不见分隔线：与书签页的行描边取同一档。
            .background(
                colors.textPrimary.copy(alpha = if (colors.isDark) 0.10f else 0.07f)
            )
    )
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Uri,
    password: Boolean = false
) {
    val colors = LocalLiuliColors.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // 聚焦时底色偏向强调色并描出细边：玻璃件没有光标提示，
    // 不给这层反馈，用户不知道键盘落在哪个框里。
    val fieldBackground by animateColorAsState(
        targetValue = if (focused) {
            colors.accent.copy(alpha = 0.10f)
        } else {
            colors.textPrimary.copy(alpha = 0.06f)
        },
        animationSpec = MotionSpec.color(),
        label = "field-background"
    )
    val fieldBorder by animateColorAsState(
        targetValue = if (focused) colors.accent.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = MotionSpec.color(),
        label = "field-border"
    )
    val hintTint by animateColorAsState(
        targetValue = if (focused) colors.textSecondary else colors.textTertiary,
        animationSpec = MotionSpec.color(),
        label = "field-hint-tint"
    )
    Column {
        BasicText(
            text = label,
            // 框名与卡片里的其它说明同一档：标签与输入内容的层级
            // 不能反着来（框名比框里的字还小）。
            style = Type.bodySmall.copy(color = colors.textSecondary)
        )
        Spacer(Modifier.height(Space.xs))
        // 选区底色与手柄跟随强调色，和地址栏、首页搜索框一致。
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
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label }
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(fieldBackground)
                    .border(1.dp, fieldBorder, RoundedCornerShape(Radius.sm))
                    .padding(horizontal = Space.md, vertical = Space.sm),
                textStyle = Type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                visualTransformation = if (password) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                // 这些框里填的全是地址、模型名、密钥这类"术语"，
                // 不是句子：自动纠错和首字母大写只会把内容改坏，一律关掉。
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done
                ),
                // 键盘上的"完成"键：收起键盘而不是留一个光标在框里，
                // 否则设置页会被键盘挡掉一半。
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                decorationBox = { innerTextField ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty()) {
                                BasicText(
                                    text = placeholder,
                                    style = Type.body.copy(color = hintTint)
                                )
                            }
                            innerTextField()
                        }
                        // 清除按钮：全选删除太慢，长按也不够直觉，
                        // 给一个能直接擦掉的入口。
                        Visibility(
                            visible = value.isNotEmpty(),
                            enter = fadeIn(animationSpec = MotionSpec.enter(Motion.Quick)),
                            exit = fadeOut(animationSpec = MotionSpec.exit(Motion.Quick))
                        ) {
                            val clearInteraction = remember { MutableInteractionSource() }
                            Box(
                                Modifier
                                    // 清除键原先只有 26dp：在输入框里戳不准，
                                    // 手指一偏就变成选中文本。放大到 36dp，
                                    // 图形本身仍保持 13dp，不喧宾夺主。
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .semantics {
                                        contentDescription = "清除$label"
                                        role = Role.Button
                                    }
                                    .clickable(
                                        interactionSource = clearInteraction,
                                        indication = null,
                                        role = Role.Button,
                                        onClickLabel = "清除"
                                    ) { onValueChange("") }
                                    .pressScale(pressedScale = 0.82f, interactionSource = clearInteraction),
                                contentAlignment = Alignment.Center
                            ) {
                                GlyphIcon(Glyph.Close, colors.textTertiary, size = 13.dp)
                            }
                        }
                    }
                }
            )
        }
    }
}

private val AUDIT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

private fun AgentAuditEntry.displayLine(): String {
    val time = runCatching {
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(AUDIT_TIME_FORMAT)
    }.getOrDefault("")
    val detailPart = if (detail.isBlank()) "" else " · ${detail.take(28)}"
    return "$time · $host · ${action.take(24)} · ${outcome.label}$detailPart"
}
