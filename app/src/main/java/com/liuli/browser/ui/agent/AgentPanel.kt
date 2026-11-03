package com.liuli.browser.ui.agent

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.liuli.browser.ui.components.EmptyState
import com.liuli.browser.ui.components.GlassIconButton
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
import com.liuli.browser.web.agent.AgentActionStatus
import com.liuli.browser.web.agent.AgentController
import com.liuli.browser.web.agent.AgentCreation
import com.liuli.browser.web.agent.AgentCreationFormat
import com.liuli.browser.web.agent.AgentCreationKind
import com.liuli.browser.web.agent.AgentMessage
import com.liuli.browser.web.agent.AgentMessageRole
import com.liuli.browser.web.agent.AgentMode
import com.liuli.browser.web.agent.AgentPlan
import com.liuli.browser.web.agent.AgentPlanCommand
import com.liuli.browser.web.agent.AgentPlanStatus
import com.liuli.browser.web.agent.AgentPlanStepKind
import com.liuli.browser.web.agent.AgentReport
import com.liuli.browser.web.agent.AgentReportFormat
import com.liuli.browser.web.agent.AgentSiteMode
import com.liuli.browser.web.agent.CreationEngine
import com.liuli.browser.web.agent.ResearchEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 提问框的字数上限。
 *
 * 超出部分会被丢掉，所以必须在接近上限时给出提示，
 * 否则用户粘贴一段长文后只看到尾巴没了，却不知道是被截断的。
 */
private const val AgentInputLimit = 500

@Composable
fun AgentOrb(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLiuliColors.current
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .semantics {
                contentDescription = "打开琉璃 Agent"
                role = Role.Button
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "打开琉璃 Agent",
                onClick = onClick
            )
            .pressScale(pressedScale = 0.90f, interactionSource = interaction),
        shape = CircleShape,
        tint = colors.glassTintStrong,
        blurRadius = 20.dp,
        lensHeight = 14.dp,
        lensAmount = 20.dp,
        shadow = true,
        contentPadding = PaddingValues(0.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(colors.accent, colors.accentSecondary, colors.accentTertiary)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.Sparkle, Color.White, size = 21.dp)
        }
    }
}

@Composable
fun AgentPanel(
    agent: AgentController,
    tabTitle: String,
    pageUrl: String,
    isTablet: Boolean,
    isPrivate: Boolean,
    backdrop: Backdrop,
    onClose: () -> Unit,
    onSubmit: (String) -> Unit,
    onConfirmAction: (Long) -> Unit,
    onCancelAction: (Long) -> Unit,
    onPlanCommand: (AgentPlanCommand) -> Unit,
    onExportReport: (AgentReport, AgentReportFormat) -> Unit,
    onExportCreation: (AgentCreation, AgentCreationFormat) -> Unit,
    onShareCreation: (AgentCreation) -> Unit,
    onShareCreationCard: (AgentCreation) -> Unit,
    onOpenSource: (String, Boolean) -> Unit,
    onSharePage: () -> Unit
) {
    val colors = LocalLiuliColors.current
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // 开关型操作（语音回复）靠轻触反馈告知状态翻转。
    val haptics = LocalHapticFeedback.current
    // 已经演过入场动画的消息：滚出去再回来不重播。
    val revealedMessageIds = remember { mutableStateListOf<Long>() }
    // "减少动态效果"开关：AnimatedContent 的 transitionSpec 不是 @Composable 上下文，
    // 只能在这里把开关读出来，交给非组合版本的时间轴。
    val reduced = reduceMotion
    // 规格先在组合作用域算好：fadeIn / scaleIn 的参数位不是
    // @Composable 上下文，不能直接调用 MotionSpec 的组合版本。
    val fadeInSpec = MotionSpec.enter()
    val fadeOutSpec = MotionSpec.exit()
    val scaleInSpec = MotionSpec.pageSpring()
    val slideSpec = MotionSpec.pageSpring(androidx.compose.ui.unit.IntOffset.Zero)
    // 高度变化（审计行、会话行出现/消失）用带可见阈值的弹簧。
    val expandSpec = MotionSpec.pageSpring(IntSize.Zero)
    // 尺寸补间：卡片收起时的高度收缩。
    val sizeValueSpec = MotionSpec.sizeValue()
    // 滚动跟随用的位移曲线。
    val glideSpec = MotionSpec.glideSpring()
    // 顶部免责声明与底部说明文案：面板展开时淡入，不要整块压在标题下面。
    var notesVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { notesVisible = true }

    // P7 语音回复：只在用户开启后朗读最新一条只读回答，不上传音频、不请求录音权限。
    val context = LocalContext.current
    var voiceReplies by rememberSaveable { mutableStateOf(false) }
    var lastSpokenId by remember { mutableStateOf(-1L) }
    val replySpeech = remember(context) { TextToSpeech(context.applicationContext) { } }
    DisposableEffect(replySpeech) {
        onDispose {
            replySpeech.stop()
            replySpeech.shutdown()
        }
    }
    fun speakLatest() {
        val reply = agent.messages.lastOrNull {
            it.role == AgentMessageRole.Agent && it.action == null && it.text.isNotBlank()
        } ?: return
        // 只在出现新的 Agent 回答时朗读；用户新发消息不会重读上一条。
        if (reply.id == lastSpokenId) return
        lastSpokenId = reply.id
        replySpeech.language = Locale.SIMPLIFIED_CHINESE
        replySpeech.stop()
        replySpeech.speak(reply.text, TextToSpeech.QUEUE_FLUSH, null, "liuli-agent-reply")
    }
    val lastMessageId = agent.messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId, voiceReplies) {
        if (voiceReplies) speakLatest()
    }

    // 这几项在列表区域之外也要用（自动滚动、回到底部按钮），
    // 提前读一次快照，避免同一份状态在两个作用域里各读一遍。
    val currentPlan = agent.plan
    val currentReport = agent.report
    val currentCreation = agent.creation
    // 面板空空如也时显示占位页，而不是空列表。
    val emptyPanel = currentPlan == null &&
        currentReport == null &&
        currentCreation == null &&
        agent.messages.isEmpty()
    // 列表里的内容条数：消息 + 计划/报告/创作卡片。末尾恒有一行思考位。
    val contentItems = agent.messages.size +
        (if (currentPlan != null) 1 else 0) +
        (if (currentReport != null) 1 else 0) +
        (if (currentCreation != null) 1 else 0)
    // 滚动落点：忙碌时停在思考行，否则停在最后一条内容上。
    // 思考行常驻列表末尾，收起后紧跟最后一条内容。
    val scrollIndex = if (agent.busy) contentItems else (contentItems - 1).coerceAtLeast(0)
    // 卡片收起时先播出场动画，动画播完才真正移除：
    // 直接置空会让卡片瞬间消失，少一个收尾。
    val cardExitMillis = if (reduced) Motion.Fast + 60 else Motion.Normal + 60
    // 内容条数会变，派生状态里必须读到最新值：
    // remember 里的派生状态捕获的是第一次组合时的条数，之后一直是旧值。
    val contentCount = rememberUpdatedState(contentItems)
    // 用户往上翻阅旧回答时，新内容不该把他拽回底部。
    // 只在滚动位置变化时重算：新消息到达会让列表重排，
    // 那不是"翻阅旧消息"，不该把自动跟随关掉。
    var scrolledAway by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect {
                val info = listState.layoutInfo
                // 末尾恒有一行思考位，最后一条内容排在它前面。
                val lastContent = info.totalItemsCount - 2
                val visibleContent = info.visibleItemsInfo.lastOrNull { it.index <= lastContent }
                // 还没排版时什么都判断不了，按"仍在底部"处理，
                // 否则面板打开时第一次自动滚动会被自己挡掉。
                scrolledAway = visibleContent != null && visibleContent.index < lastContent
            }
    }

    val modeLabel = when {
        isPrivate -> "本机"
        agent.cloudConfigured -> "云端"
        else -> "本地"
    }
    val modeTint = when {
        isPrivate -> colors.accentSecondary
        agent.cloudConfigured -> colors.accent
        else -> colors.success
    }
    val sitePolicy = agent.sitePolicyFor(pageUrl)
    val subtitle = when (sitePolicy.mode) {
        AgentSiteMode.Blocked -> "本站已禁止 · 不会读取页面"
        AgentSiteMode.ReadOnly -> "本站只读 · 不会执行动作"
        AgentSiteMode.Allowed -> when {
            isPrivate -> "隐私标签 · 仅本机处理"
            sitePolicy.trustLowRisk -> "本站已批准低风险步骤"
            else -> "读取页面；操作逐条确认"
        }
    }

    LaunchedEffect(
        agent.messages.size,
        agent.busy,
        agent.plan != null,
        agent.report?.createdAt,
        agent.creation?.createdAt
    ) {
        // 空面板还没有列表可滚；用户正在翻旧消息时也不打断他。
        if (emptyPanel || scrolledAway) return@LaunchedEffect
        listState.scrollToLatest(scrollIndex, glideSpec)
    }

    fun submit(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || agent.busy) return
        onSubmit(trimmed)
        input = ""
        keyboard?.hide()
    }

    fun prepareFind() {
        input = "查找 "
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Box(Modifier.fillMaxSize()) {
        // 遮罩淡入：面板展开时背景不该瞬间变暗。
        var scrimVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { scrimVisible = true }
        val scrimAlpha by animateFloatAsState(
            targetValue = if (scrimVisible) 1f else 0f,
            animationSpec = MotionSpec.value(Motion.Quick),
            label = "agent-scrim"
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrimAlpha }
                .background(Color.Black.copy(alpha = 0.20f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() }
        )

        val panelModifier = if (isTablet) {
            Modifier
                .align(Alignment.CenterEnd)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(end = Space.lg, top = Space.md, bottom = Space.md)
                .width(400.dp)
                .fillMaxHeight()
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(start = Space.md, end = Space.md, bottom = Space.sm)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .fillMaxHeight(0.72f)
        }

        GlassSurface(
            backdrop = backdrop,
            modifier = panelModifier
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
                    .fillMaxSize()
                    .padding(horizontal = Space.lg, vertical = Space.lg)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(colors.accent, colors.accentSecondary, colors.accentTertiary)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(Glyph.Sparkle, Color.White, size = 19.dp)
                    }
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            text = "琉璃 Agent",
                            // 面板标题：走 title 档，字号保留 16sp 的紧凑感。
                            style = Type.title.copy(
                                color = colors.textPrimary,
                                fontSize = 16.sp
                            )
                        )
                        // 站点策略变化时副标题淡入淡出，而不是整行硬切。
                        AnimatedContent(
                            targetState = subtitle,
                            transitionSpec = {
                                fadeIn(animationSpec = MotionSpec.enter(reduced, Motion.Normal))
                                    .togetherWith(fadeOut(animationSpec = MotionSpec.exit(reduced, Motion.Quick)))
                                    .using(SizeTransform(clip = false))
                            },
                            label = "agent-subtitle"
                        ) { text ->
                            BasicText(
                                text = text,
                                style = Type.caption.copy(color = colors.textTertiary),
                                maxLines = 1
                            )
                        }
                    }
                    // 模式徽标："本机 / 云端 / 本地"切换时底色与字色交叉过渡。
                    val modeBadgeBg by animateColorAsState(
                        targetValue = modeTint.copy(alpha = 0.14f),
                        animationSpec = MotionSpec.color(),
                        label = "mode-badge-bg"
                    )
                    val modeBadgeTint by animateColorAsState(
                        targetValue = modeTint,
                        animationSpec = MotionSpec.color(),
                        label = "mode-badge-tint"
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(modeBadgeBg)
                            .padding(horizontal = Space.sm, vertical = Space.xs)
                    ) {
                        AnimatedContent(
                            targetState = modeLabel,
                            transitionSpec = {
                                fadeIn(animationSpec = MotionSpec.enter(reduced, Motion.Quick))
                                    .togetherWith(fadeOut(animationSpec = MotionSpec.exit(reduced, Motion.Quick)))
                            },
                            label = "mode-badge-label"
                        ) { label ->
                            BasicText(
                                text = label,
                                style = Type.micro.copy(color = modeBadgeTint)
                            )
                        }
                    }
                    Spacer(Modifier.width(Space.xs))
                    GlassIconButton(
                        glyph = Glyph.Close,
                        contentDescription = "关闭 Agent",
                        onClick = onClose,
                        backdrop = backdrop,
                        // 与隐私说明、隐私护盾面板的关闭键同一规格：
                        // 三个浮层此前一个 15dp、一个 16dp。
                        size = 40.dp,
                        glyphSize = 16.dp
                    )
                }

                Spacer(Modifier.height(Space.sm))
                // 免责声明淡入：面板展开时不要整块"啪"地压在标题下面，
                // 高度也跟着过渡，快捷标签不会被顶下去。
                AnimatedVisibility(
                    visible = notesVisible,
                    enter = fadeIn(animationSpec = fadeInSpec) +
                        expandVertically(animationSpec = expandSpec),
                    exit = fadeOut(animationSpec = fadeOutSpec) +
                        shrinkVertically(animationSpec = expandSpec)
                ) {
                    BasicText(
                        text = "只读取可见正文与元素名称，不读取输入框已有内容、密码、Cookie 或剪贴板。",
                        style = Type.caption.copy(color = colors.textTertiary)
                    )
                }

                Spacer(Modifier.height(Space.md))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    // 忙碌时快捷标签整体退场：点了没反应比明确变灰更让人困惑。
                    QuickChip("总结此页", enabled = !agent.busy) { submit("总结此页") }
                    QuickChip("页面信息", enabled = !agent.busy) { submit("页面信息") }
                    QuickChip("页内查找") { prepareFind() }
                    QuickChip("监控此页", enabled = !agent.busy) { submit("监控此页") }
                    QuickChip("检查关注", enabled = !agent.busy) { submit("检查关注") }
                    QuickChip("页面审计", enabled = !agent.busy) { submit("审计此页") }
                    QuickChip("查看关注", enabled = !agent.busy) { submit("查看关注") }
                    QuickChip("调研标签页", enabled = !agent.busy) { submit("调研这些标签页并生成报告") }
                    QuickChip("开始录制", enabled = !agent.busy) { submit("开始录制") }
                    QuickChip("结束录制", enabled = !agent.busy) { submit("结束录制") }
                    QuickChip("查看宏", enabled = !agent.busy) { submit("查看宏") }
                    QuickChip("浏览日报", enabled = !agent.busy) { submit("浏览日报") }
                    QuickChip("分享此页") { onSharePage() }
                    QuickChip(
                        label = if (voiceReplies) "停止语音" else "语音回复",
                        active = voiceReplies
                    ) {
                        // 开关型操作给一次轻触反馈：与设置页的开关、手风琴一致，
                        // 用户不看字也知道状态翻过去了。
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        voiceReplies = !voiceReplies
                        if (!voiceReplies) replySpeech.stop()
                    }
                }

                Spacer(Modifier.height(Space.sm))

                val currentPlan = agent.plan
                val currentReport = agent.report
                val currentCreation = agent.creation
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // 空面板用占位页填满，否则只是一片空白，看不出这里能做什么。
                    // 第一条内容到达时占位页淡出，而不是被瞬间抽走。
                    Visibility(
                        visible = emptyPanel,
                        enter = fadeIn(animationSpec = fadeInSpec),
                        exit = fadeOut(animationSpec = fadeOutSpec)
                    ) {
                        AgentEmptyState(
                            tabTitle = tabTitle,
                            modifier = Modifier
                                .fillMaxSize()
                        )
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Space.xxs),
                        contentPadding = PaddingValues(vertical = Space.xs)
                    ) {
                        itemsIndexed(agent.messages, key = { _, item -> item.id }) { index, message ->
                            // 逐条浮现：新消息到达时轻轻滑入。
                            // 用户消息从右侧滑入，Agent 回答从下方升起，方向与来源一致。
                            val stagger = MotionSpec.staggerDelay(index, stepMillis = 18)
                            val alreadyShown = message.id in revealedMessageIds
                            var appeared by remember(message.id) {
                                mutableStateOf(alreadyShown)
                            }
                            LaunchedEffect(message.id) {
                                if (!alreadyShown) {
                                    if (stagger > 0) delay(stagger.toLong())
                                    revealedMessageIds.add(message.id)
                                }
                                appeared = true
                            }
                            val isUserMessage = message.role == AgentMessageRole.User
                            val enterTransition: EnterTransition = if (isUserMessage) {
                                fadeIn(animationSpec = fadeInSpec) +
                                    slideInHorizontally(
                                        animationSpec = slideSpec,
                                        initialOffsetX = { it / 4 }
                                    )
                            } else {
                                fadeIn(animationSpec = fadeInSpec) +
                                    slideInVertically(
                                        animationSpec = slideSpec,
                                        initialOffsetY = { it / 6 }
                                    )
                            }
                            Visibility(
                                visible = appeared,
                                enter = enterTransition,
                                exit = fadeOut(animationSpec = fadeOutSpec)
                            ) {
                                MessageBubble(
                                    message = message,
                                    // 标注跟随这一条消息自己的来路：
                                    // 云端规划失败改用本机汇总时，这条就该写"本机"。
                                    modeLabel = bubbleModeLabel(message.mode, isPrivate),
                                    onConfirm = { onConfirmAction(message.id) },
                                    onCancel = { onCancelAction(message.id) }
                                )
                            }
                        }
                        if (currentPlan != null) {
                            item(key = "plan") {
                                // 计划没有独立 id，用目标文本作为身份标识：
                                // 换了目标就是新计划，重新播一次入场动画。
                                var appeared by remember(currentPlan.goal) {
                                    mutableStateOf(false)
                                }
                                LaunchedEffect(currentPlan.goal) { appeared = true }
                                Visibility(
                                    visible = appeared,
                                    enter = fadeIn(animationSpec = fadeInSpec) +
                                        scaleIn(
                                            animationSpec = scaleInSpec,
                                            initialScale = 0.94f
                                        ),
                                    exit = fadeOut(animationSpec = fadeOutSpec) +
                                        scaleOut(
                                            animationSpec = scaleInSpec,
                                            targetScale = 0.96f
                                        )
                                ) {
                                    PlanCard(plan = currentPlan, onCommand = onPlanCommand)
                                }
                            }
                        }
                        if (currentReport != null) {
                            item(key = "report-${currentReport.createdAt}") {
                                var appeared by remember(currentReport.createdAt) {
                                    mutableStateOf(false)
                                }
                                // 收起：先播出场动画，播完才真正把报告清空。
                                // 直接置空会让卡片瞬间消失，列表少一个收尾。
                                var closing by remember(currentReport.createdAt) {
                                    mutableStateOf(false)
                                }
                                LaunchedEffect(currentReport.createdAt) { appeared = true }
                                LaunchedEffect(closing) {
                                    if (closing) {
                                        delay(cardExitMillis.toLong())
                                        // 期间若换成了新报告，别把新报告一起清掉。
                                        if (agent.report?.createdAt == currentReport.createdAt) {
                                            agent.dismissReport()
                                        }
                                    }
                                }
                                Visibility(
                                    visible = appeared && !closing,
                                    enter = fadeIn(animationSpec = fadeInSpec) +
                                        scaleIn(
                                            animationSpec = scaleInSpec,
                                            initialScale = 0.94f
                                        ),
                                    exit = fadeOut(animationSpec = fadeOutSpec) +
                                        shrinkVertically(animationSpec = sizeValueSpec)
                                ) {
                                    ReportCard(
                                        report = currentReport,
                                        onExport = { format -> onExportReport(currentReport, format) },
                                        onOpenSource = onOpenSource,
                                        onDismiss = { closing = true }
                                    )
                                }
                            }
                        }
                        if (currentCreation != null) {
                            item(key = "creation-${currentCreation.createdAt}") {
                                var appeared by remember(currentCreation.createdAt) {
                                    mutableStateOf(false)
                                }
                                var closing by remember(currentCreation.createdAt) {
                                    mutableStateOf(false)
                                }
                                LaunchedEffect(currentCreation.createdAt) { appeared = true }
                                LaunchedEffect(closing) {
                                    if (closing) {
                                        delay(cardExitMillis.toLong())
                                        // 期间若换成了新产物，别把新产物一起清掉。
                                        if (agent.creation?.createdAt == currentCreation.createdAt) {
                                            agent.dismissCreation()
                                        }
                                    }
                                }
                                Visibility(
                                    visible = appeared && !closing,
                                    enter = fadeIn(animationSpec = fadeInSpec) +
                                        scaleIn(
                                            animationSpec = scaleInSpec,
                                            initialScale = 0.94f
                                        ),
                                    exit = fadeOut(animationSpec = fadeOutSpec) +
                                        shrinkVertically(animationSpec = sizeValueSpec)
                                ) {
                                    CreationCard(
                                        creation = currentCreation,
                                        onExport = { format -> onExportCreation(currentCreation, format) },
                                        onShare = { onShareCreation(currentCreation) },
                                        onShareCard = { onShareCreationCard(currentCreation) },
                                        onDismiss = { closing = true }
                                    )
                                }
                            }
                        }
                        // 思考行常驻列表末尾：请求结束时先收起来再消失，
                        // 否则它会被瞬间移除，面板内容"啪"地少一块。
                        item(key = "thinking") {
                            Visibility(
                                visible = agent.busy,
                                enter = fadeIn(animationSpec = fadeInSpec) +
                                    expandVertically(animationSpec = expandSpec),
                                exit = fadeOut(animationSpec = fadeOutSpec) +
                                    shrinkVertically(animationSpec = expandSpec)
                            ) {
                                ThinkingRow()
                            }
                        }
                    }
                    // 往上翻阅旧回答时，右下角浮出"回到最新"：
                    // 没有它，用户只能一直往下拖才知道有新消息来了。
                    // 条数取最新值：末尾的思考行不算内容，它不忙时是收着的。
                    val showJumpToLatest by remember {
                        derivedStateOf {
                            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                            last != null && last.index < contentCount.value - 1
                        }
                    }
                    Visibility(
                        visible = showJumpToLatest && !emptyPanel,
                        enter = fadeIn(MotionSpec.enter(Motion.Quick)) + scaleIn(
                            animationSpec = MotionSpec.enter(Motion.Quick),
                            initialScale = 0.7f
                        ),
                        exit = fadeOut(MotionSpec.exit(Motion.Quick)) + scaleOut(
                            animationSpec = MotionSpec.exit(Motion.Quick),
                            targetScale = 0.7f
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = Space.md, bottom = Space.sm)
                    ) {
                        val jumpInteraction = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (colors.isDark) {
                                        Color(0xFF1B2130)
                                    } else {
                                        Color.White.copy(alpha = 0.92f)
                                    }
                                )
                                .semantics {
                                    contentDescription = "回到最新"
                                    role = Role.Button
                                }
                                .clickable(
                                    interactionSource = jumpInteraction,
                                    indication = null,
                                    role = Role.Button,
                                    onClickLabel = "回到最新",
                                    onClick = {
                                        scope.launch {
                                            listState.scrollToLatest(scrollIndex, glideSpec)
                                        }
                                    }
                                )
                                .pressScale(pressedScale = 0.86f, interactionSource = jumpInteraction),
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(Glyph.ChevronDown, colors.textSecondary, size = 18.dp)
                        }
                    }
                }

                Spacer(Modifier.height(Space.sm))
                // 接近上限时提示剩余字数：粘贴长文被截断却毫无提示，
                // 用户只会觉得"尾巴怎么没了"。
                Visibility(
                    visible = input.length >= AgentInputLimit * 4 / 5,
                    enter = fadeIn(MotionSpec.enter(Motion.Quick)) +
                        expandVertically(animationSpec = MotionSpec.sizeValue(Motion.Quick)),
                    exit = fadeOut(MotionSpec.exit(Motion.Quick)) +
                        shrinkVertically(animationSpec = MotionSpec.sizeValue(Motion.Quick))
                ) {
                    BasicText(
                        text = "还可输入 ${AgentInputLimit - input.length} 字",
                        style = Type.micro.copy(
                            color = colors.textTertiary,
                            textAlign = TextAlign.End
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 输入框聚焦时描一圈主色：光标之外还有一处"正在输入"的提示。
                    val inputInteraction = remember { MutableInteractionSource() }
                    val inputFocused by inputInteraction.collectIsFocusedAsState()
                    val inputBorder by animateColorAsState(
                        targetValue = if (inputFocused) {
                            colors.accent.copy(alpha = 0.55f)
                        } else {
                            Color.Transparent
                        },
                        animationSpec = MotionSpec.color(),
                        label = "input-border"
                    )
                    // 聚焦时底色也偏向强调色：与设置页的输入框同一套反馈，
                    // 只描边不换底色时两者看起来像两种控件。
                    val inputBackground by animateColorAsState(
                        targetValue = if (inputFocused) {
                            colors.accent.copy(alpha = 0.10f)
                        } else {
                            colors.textPrimary.copy(alpha = 0.06f)
                    },
                        animationSpec = MotionSpec.color(),
                        label = "input-background"
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(inputBackground)
                            .border(1.dp, inputBorder, RoundedCornerShape(Radius.md))
                            .padding(horizontal = Space.md, vertical = Space.xxs),
                        contentAlignment = Alignment.CenterStart
                    ) {
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
                                value = input,
                                // 超出上限的字符会被丢掉。上面有字数提示兜底，
                                // 用户至少知道内容被截断了。
                                onValueChange = { input = it.take(AgentInputLimit) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 40.dp)
                                    .focusRequester(focusRequester)
                                    .semantics { contentDescription = "向助手提问" },
                                interactionSource = inputInteraction,
                                textStyle = Type.body.copy(
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                ),
                                cursorBrush = SolidColor(colors.accent),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(onSend = { submit(input) }),
                                decorationBox = { innerTextField ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.weight(1f)) {
                                            if (input.isEmpty()) {
                                                BasicText(
                                                    text = "询问本页内容…",
                                                    style = Type.body.copy(
                                                        color = colors.textTertiary,
                                                        fontSize = 13.sp
                                                    )
                                                )
                                            }
                                            innerTextField()
                                        }
                                        // 清空入口：逐字删太慢，
                                        // 给一个能直接擦掉的按钮。
                                        Visibility(
                                            visible = input.isNotEmpty(),
                                            enter = fadeIn(MotionSpec.enter(Motion.Quick)),
                                            exit = fadeOut(MotionSpec.exit(Motion.Quick))
                                        ) {
                                            val clearInteraction = remember { MutableInteractionSource() }
                                            Box(
                                                Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .semantics {
                                                        contentDescription = "清空提问"
                                                        role = Role.Button
                                                    }
                                                    .clickable(
                                                        interactionSource = clearInteraction,
                                                        indication = null,
                                                        role = Role.Button,
                                                        onClickLabel = "清空"
                                                    ) { input = "" }
                                                    .pressScale(
                                                        pressedScale = 0.82f,
                                                        interactionSource = clearInteraction
                                                    ),
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
                    Spacer(Modifier.width(Space.sm))
                    val canSend = input.isNotBlank() && !agent.busy
                    val sendInteraction = remember { MutableInteractionSource() }
                    val sendBackground by animateColorAsState(
                        targetValue = if (canSend) colors.accent
                        else colors.textPrimary.copy(alpha = 0.06f),
                        animationSpec = MotionSpec.color(),
                        label = "send-bg"
                    )
                    val sendTint by animateColorAsState(
                        targetValue = if (canSend) Color.White else colors.textTertiary,
                        animationSpec = MotionSpec.color(),
                        label = "send-tint"
                    )
                    Box(
                        Modifier
                            .size(Dimens.minTouch)
                            .clip(CircleShape)
                            .background(sendBackground)
                            .semantics {
                                contentDescription = "发送"
                                role = Role.Button
                            }
                            .clickable(
                                interactionSource = sendInteraction,
                                indication = null,
                                enabled = canSend,
                                role = Role.Button,
                                onClickLabel = "发送",
                                onClick = { submit(input) }
                            )
                            .pressScale(pressedScale = 0.88f, interactionSource = sendInteraction),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(
                            glyph = Glyph.Sparkle,
                            tint = sendTint,
                            size = 20.dp
                        )
                    }
                }

                Spacer(Modifier.height(Space.sm))
                // 底部说明文案与面板一起淡入。
                AnimatedVisibility(
                    visible = notesVisible,
                    enter = fadeIn(animationSpec = fadeInSpec) +
                        expandVertically(animationSpec = expandSpec),
                    exit = fadeOut(animationSpec = fadeOutSpec) +
                        shrinkVertically(animationSpec = expandSpec)
                ) {
                    BasicText(
                        text = "受控操作：单步动作需逐条确认；多步任务需先批准计划，执行中可暂停或取消，不会自动提交表单。",
                        // 这是承诺条款，不是角标：走 caption 档才读得下去。
                        style = Type.caption.copy(color = colors.textTertiary)
                    )
                }
                // 审计行与会话行：出现/消失都带高度过渡，输入框不会被顶来顶去。
                AnimatedVisibility(
                    visible = agent.audit.isNotEmpty(),
                    enter = fadeIn(animationSpec = fadeInSpec) +
                        expandVertically(animationSpec = expandSpec),
                    exit = fadeOut(animationSpec = fadeOutSpec) +
                        shrinkVertically(animationSpec = expandSpec)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(Space.xxs))
                        BasicText(
                            text = "审计 ${agent.audit.size} 条 · 仅本机内存，清除对话或退出时删除",
                            style = Type.caption.copy(color = colors.textTertiary)
                        )
                    }
                }
                AnimatedVisibility(
                    visible = agent.watches.isNotEmpty() || agent.macros.isNotEmpty() || agent.recordingMacro,
                    enter = fadeIn(animationSpec = fadeInSpec) +
                        expandVertically(animationSpec = expandSpec),
                    exit = fadeOut(animationSpec = fadeOutSpec) +
                        shrinkVertically(animationSpec = expandSpec)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(Space.xxs))
                        BasicText(
                            text = buildString {
                                append("本机会话：关注 ${agent.watches.size} 个页面 · 宏 ${agent.macros.size} 个")
                                if (agent.recordingMacro) append(" · 正在录制宏")
                                append("（不后台运行）")
                            },
                            style = Type.caption.copy(color = colors.textTertiary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickChip(
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val reduced = reduceMotion
    val interaction = remember { MutableInteractionSource() }
    // 忙碌时标签退成灰色，开着的开关（语音回复）用主色标出来：
    // 状态一眼能分清，不必去猜哪个还生效。
    val background by animateColorAsState(
        targetValue = when {
            active -> colors.accent.copy(alpha = 0.18f)
            enabled -> colors.textPrimary.copy(alpha = 0.06f)
            else -> colors.textPrimary.copy(alpha = 0.03f)
        },
        animationSpec = MotionSpec.color(),
        label = "chip-bg"
    )
    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> colors.textTertiary.copy(alpha = 0.55f)
            active -> colors.accent
            else -> colors.textSecondary
        },
        animationSpec = MotionSpec.color(),
        label = "chip-tint"
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            )
            // 禁用时 interactionSource 不会发出按下事件，按压形变自然不出现。
            .pressScale(pressedScale = 0.90f, interactionSource = interaction)
            .padding(horizontal = Space.md, vertical = Space.sm)
    ) {
        // 文案会变（"语音回复"↔"停止语音"），交叉淡入淡出而不是硬切。
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                fadeIn(animationSpec = MotionSpec.enter(reduced, Motion.Quick))
                    .togetherWith(fadeOut(animationSpec = MotionSpec.exit(reduced, Motion.Quick)))
                    .using(SizeTransform(clip = false))
            },
            label = "chip-label"
        ) { text ->
            BasicText(
                text = text,
                style = Type.label.copy(color = tint)
            )
        }
    }
}

@Composable
private fun AgentEmptyState(tabTitle: String, modifier: Modifier = Modifier) {
    val colors = LocalLiuliColors.current
    // 面板打开时内容淡入上移，避免"啪"地一下出现。
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val emptyAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MotionSpec.value(Motion.Slow),
        label = "agent-empty-alpha"
    )
    Column(
        modifier.graphicsLayer {
            alpha = emptyAlpha
            translationY = (1f - emptyAlpha) * 12.dp.toPx()
        },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 与其它空状态共用一套外观：底座尺寸、配色与标题层级都一致，
        // 面板内外看着才像同一个产品。
        EmptyState(
            glyph = Glyph.Sparkle,
            title = "向当前页面提问",
            message = tabTitle.take(48).ifBlank { "当前页面" },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Space.md))
        BasicText(
            text = "速览、查找与问答默认在本机完成。\n也可以让我滚动、点击或填写，每次都会先确认。",
            style = Type.bodySmall.copy(
                color = colors.textTertiary,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 卡片头部的小图标圆。
 *
 * 卡片本身已经整体缩放入场，图标再从 0.82 长出来一点，
 * 两层错开，卡片才像"落位"而不是被"贴"上去。
 *
 * [entranceKey] 用来记住入场状态：卡片滑出屏幕再滑回来时不重播动画。
 */
@Composable
private fun CardHeaderIcon(
    glyph: Glyph,
    tint: Color,
    entranceKey: Any
) {
    var entered by rememberSaveable(entranceKey) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.82f,
        animationSpec = MotionSpec.pageSpring(),
        label = "card-icon-scale"
    )
    LaunchedEffect(entranceKey) { entered = true }
    Box(
        Modifier
            .size(26.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, tint, size = 14.dp)
    }
}

@Composable
private fun PlanCard(
    plan: AgentPlan,
    onCommand: (AgentPlanCommand) -> Unit
) {
    val colors = LocalLiuliColors.current
    val reduced = reduceMotion
    val statusLabel = when (plan.status) {
        AgentPlanStatus.Pending -> "待批准"
        AgentPlanStatus.Running -> "执行中"
        AgentPlanStatus.Paused -> "已暂停"
        AgentPlanStatus.Done -> "已完成"
        AgentPlanStatus.Failed -> "失败"
        AgentPlanStatus.Cancelled -> "已取消"
    }
    val statusTint = when (plan.status) {
        AgentPlanStatus.Pending, AgentPlanStatus.Running -> colors.accent
        AgentPlanStatus.Paused -> colors.textSecondary
        AgentPlanStatus.Done -> colors.success
        AgentPlanStatus.Failed -> colors.danger
        AgentPlanStatus.Cancelled -> colors.textTertiary
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(colors.accent.copy(alpha = if (colors.isDark) 0.12f else 0.07f))
            .padding(Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardHeaderIcon(Glyph.Sparkle, colors.accent, entranceKey = plan.goal)
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = "任务计划",
                    style = Type.sectionTitle.copy(color = colors.textPrimary)
                )
                BasicText(
                    text = "${plan.steps.size} 步 · " +
                        "${if (plan.source == AgentMode.Cloud) "云端规划" else "本地规划"}",
                    // 卡片副信息走 caption 档：micro 是留给角标数字的，
                    // 9sp 排一行元信息几乎读不出来。
                    style = Type.caption.copy(color = colors.textTertiary)
                )
            }
            // 状态徽标：待批准→执行中→已完成，颜色交叉过渡而不是瞬时换色。
            val badgeTint by animateColorAsState(
                targetValue = statusTint,
                animationSpec = MotionSpec.color(),
                label = "plan-status-tint"
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(badgeTint.copy(alpha = 0.14f))
                    .padding(horizontal = Space.sm, vertical = Space.xs)
            ) {
                // 状态文案与颜色同步交叉过渡，换状态时徽标是"化"过去的。
                AnimatedContent(
                    targetState = statusLabel,
                    transitionSpec = {
                        fadeIn(animationSpec = MotionSpec.enter(reduced, Motion.Quick))
                            .togetherWith(fadeOut(animationSpec = MotionSpec.exit(reduced, Motion.Quick)))
                    },
                    label = "plan-status-label"
                ) { label ->
                    BasicText(
                        text = label,
                        style = Type.micro.copy(color = badgeTint)
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
        // 目标：用户批准前要判断的正是这一句，
        // 压成 caption 档几乎读不出来，等于没写。
        BasicText(
            text = plan.goal,
            style = Type.body.copy(color = colors.textPrimary),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(Space.sm))
        // 执行进度：步骤列表只能看出"哪些做完了"，
        // 看不出"现在走到第几步"，执行中需要后者。
        Visibility(
            visible = plan.status == AgentPlanStatus.Running ||
                plan.status == AgentPlanStatus.Paused,
            enter = fadeIn(animationSpec = MotionSpec.enter(Motion.Quick)) +
                expandVertically(animationSpec = MotionSpec.sizeValue(Motion.Quick)),
            exit = fadeOut(animationSpec = MotionSpec.exit(Motion.Quick)) +
                shrinkVertically(animationSpec = MotionSpec.sizeValue(Motion.Quick))
        ) {
            PlanProgress(
                done = plan.currentStep.coerceIn(0, plan.steps.size),
                total = plan.steps.size,
                current = (plan.currentStep + 1).coerceAtMost(plan.steps.size.coerceAtLeast(1)),
                tint = statusTint
            )
        }
        Spacer(Modifier.height(Space.sm))
        plan.steps.forEachIndexed { index, step ->
            val completed = index < plan.currentStep
            val current = index == plan.currentStep &&
                (plan.status == AgentPlanStatus.Running || plan.status == AgentPlanStatus.Paused)
            val failed = index == plan.currentStep && plan.status == AgentPlanStatus.Failed
            val stepTint = when {
                completed -> colors.success
                failed -> colors.danger
                current -> colors.accent
                else -> colors.textTertiary
            }
            // 步骤状态在"待办→执行中→已完成"之间流转，颜色交叉过渡而不是瞬时跳变。
            val tint by animateColorAsState(
                targetValue = stepTint,
                animationSpec = MotionSpec.color(),
                label = "plan-step-tint"
            )
            // 当前步骤的箭头轻轻脉动，提示"卡在这里"。
            val markerScale by animateFloatAsState(
                targetValue = if (current) 1f else 0f,
                animationSpec = MotionSpec.pageSpring(),
                label = "plan-step-marker"
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "${index + 1}",
                        style = Type.micro.copy(
                            color = tint,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                Spacer(Modifier.width(Space.xs))
                // 步骤类型单独一列：滚动、点击、输入的风险完全不同，
                // 批准之前必须一眼看出哪几步会点页面、哪几步会打字。
                BasicText(
                    text = step.kind.label(),
                    style = Type.caption.copy(
                        color = if (completed) colors.textTertiary.copy(alpha = 0.7f)
                        else colors.textTertiary
                    ),
                    modifier = Modifier.width(26.dp)
                )
                Spacer(Modifier.width(Space.xs))
                BasicText(
                    text = step.description,
                    style = Type.bodySmall.copy(
                        color = if (completed) colors.textTertiary else colors.textSecondary,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.weight(1f),
                    // 一步的描述常常是半句话，截成一行就只剩动作名，
                    // 看不出要点在哪个元素上。
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                when {
                    completed -> GlyphIcon(Glyph.Check, colors.success, size = 12.dp)
                    failed -> GlyphIcon(Glyph.Info, colors.danger, size = 12.dp)
                    current -> GlyphIcon(
                        glyph = Glyph.ChevronRight,
                        tint = colors.accent,
                        modifier = Modifier.graphicsLayer {
                            scaleX = markerScale
                            scaleY = markerScale
                        },
                        size = 12.dp
                    )
                    else -> Unit
                }
            }
        }
        if (!plan.failure.isNullOrBlank()) {
            Spacer(Modifier.height(Space.xs))
            BasicText(
                text = plan.failure,
                style = Type.caption.copy(color = colors.danger)
            )
        }
        Spacer(Modifier.height(Space.sm))
        // 状态推进时按钮组整组交叉淡入淡出，不再"啪"地换掉。
        AnimatedContent(
            targetState = plan.status,
            transitionSpec = {
                fadeIn(animationSpec = MotionSpec.enter(reduced, Motion.Quick))
                    .togetherWith(fadeOut(animationSpec = MotionSpec.exit(reduced, Motion.Quick)))
                    .using(SizeTransform(clip = false))
            },
            label = "plan-actions"
        ) { status ->
            when (status) {
                AgentPlanStatus.Pending -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        ActionButton("批准并执行", accent = true) { onCommand(AgentPlanCommand.Approve) }
                        ActionButton("取消", accent = false) { onCommand(AgentPlanCommand.Cancel) }
                    }
                    Spacer(Modifier.height(Space.xs))
                    BasicText(
                        text = "批准后按顺序执行；点击可能跳转或提交表单，填写不会自动提交。",
                        style = Type.caption.copy(color = colors.textTertiary)
                    )
                }
                AgentPlanStatus.Running -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        ActionButton("暂停", accent = false) { onCommand(AgentPlanCommand.Pause) }
                        ActionButton("取消任务", accent = false) { onCommand(AgentPlanCommand.Cancel) }
                    }
                }
                AgentPlanStatus.Paused -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        ActionButton("继续执行", accent = true) { onCommand(AgentPlanCommand.Resume) }
                        ActionButton("取消任务", accent = false) { onCommand(AgentPlanCommand.Cancel) }
                    }
                }
                AgentPlanStatus.Failed -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        ActionButton("重试", accent = true) { onCommand(AgentPlanCommand.Retry) }
                        ActionButton("取消", accent = false) { onCommand(AgentPlanCommand.Cancel) }
                    }
                }
                AgentPlanStatus.Done, AgentPlanStatus.Cancelled -> Unit
            }
        }
    }
}

/**
 * 计划执行进度条。
 *
 * 步骤列表只能看出"哪些做完了"，看不出"现在第几步、还剩几步"。
 * 执行中的多步任务最怕这一点：用户不知道它是在跑还是卡住了。
 * 这里补一条进度轨与"第 N / M 步"，两者一起回答同一个问题。
 */
@Composable
private fun PlanProgress(
    done: Int,
    total: Int,
    current: Int,
    tint: Color
) {
    val colors = LocalLiuliColors.current
    val fraction = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    // 进度是连续量，插值让步与步之间的推进平滑，而不是一跳一跳。
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = MotionSpec.value(Motion.Slow),
        label = "plan-progress"
    )
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = "第 $current / $total 步",
                style = Type.caption.copy(color = colors.textSecondary)
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                text = "已完成 $done 步",
                style = Type.caption.copy(color = colors.textTertiary)
            )
        }
        Spacer(Modifier.height(Space.xs))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(colors.textPrimary.copy(alpha = 0.08f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(tint)
            )
        }
    }
}

/**
 * 步骤类型的中文标签。
 *
 * 滚动、点击、输入三种动作的风险差别很大：
 * 滚动几乎无害，点击会跳转，输入会往页面里写字。
 * 批准之前必须让用户一眼看出这一步属于哪一类。
 */
private fun AgentPlanStepKind.label(): String = when (this) {
    AgentPlanStepKind.Scroll -> "滚动"
    AgentPlanStepKind.Click -> "点击"
    AgentPlanStepKind.Type -> "输入"
}

@Composable
private fun ReportCard(
    report: AgentReport,
    onExport: (AgentReportFormat) -> Unit,
    onOpenSource: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val clipboard = LocalClipboardManager.current
    val reduced = reduceMotion
    var expanded by rememberSaveable(report.createdAt) { mutableStateOf(false) }
    // 动作按钮的瞬时反馈：点完把文案换成"已复制/已导出"，1.6 秒后复原，
    // 避免用户误以为状态卡住。
    var flash by rememberSaveable(report.createdAt) { mutableStateOf("") }
    LaunchedEffect(flash) {
        if (flash.isNotEmpty()) {
            delay(1600)
            flash = ""
        }
    }
    val context = LocalContext.current
    var speaking by rememberSaveable(report.createdAt) { mutableStateOf(false) }
    val speech = remember(context) { TextToSpeech(context.applicationContext) { } }
    DisposableEffect(speech) {
        onDispose {
            speech.stop()
            speech.shutdown()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(colors.success.copy(alpha = if (colors.isDark) 0.12f else 0.07f))
            .padding(Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardHeaderIcon(Glyph.History, colors.success, entranceKey = report.createdAt)
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = "调研报告",
                    style = Type.sectionTitle.copy(color = colors.textPrimary)
                )
                BasicText(
                    text = "${report.sources.size} 个来源 · ${report.mode.label}整理 · 全部带引用",
                    style = Type.caption.copy(color = colors.textTertiary)
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(colors.success.copy(alpha = 0.14f))
                    .padding(horizontal = Space.sm, vertical = Space.xs)
            ) {
                BasicText(
                    text = "只读",
                    style = Type.micro.copy(color = colors.success)
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        BasicText(
            text = report.goal,
            style = Type.caption.copy(color = colors.textTertiary),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!report.note.isNullOrBlank()) {
            Spacer(Modifier.height(Space.xs))
            BasicText(
                text = report.note,
                style = Type.caption.copy(color = colors.textTertiary)
            )
        }
        Spacer(Modifier.height(Space.sm))
        // 行数用动画插值：展开/收起时正文连续生长，而不是"啪"地跳一下。
        val maxLines by animateIntAsState(
            targetValue = if (expanded) 80 else 8,
            animationSpec = MotionSpec.intValue(Motion.Slow),
            label = "report-expand"
        )
        BasicText(
            text = report.markdown,
            style = Type.bodySmall.copy(color = colors.textSecondary, fontSize = 11.sp),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        val expandInteraction = remember { MutableInteractionSource() }
        // "展开全文 / 收起全文"切换时交叉淡入淡出，文字不硬切。
        Box(
            Modifier
                .padding(top = Space.xs)
                .clickable(
                    interactionSource = expandInteraction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (expanded) "收起全文" else "展开全文"
                ) { expanded = !expanded }
                .pressScale(pressedScale = 0.94f, interactionSource = expandInteraction)
        ) {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    fadeIn(animationSpec = MotionSpec.enter(reduced, Motion.Quick))
                        .togetherWith(fadeOut(animationSpec = MotionSpec.exit(reduced, Motion.Quick)))
                        .using(SizeTransform(clip = false))
                },
                label = "report-expand-label"
            ) { isExpanded ->
                BasicText(
                    text = if (isExpanded) "收起全文" else "展开全文",
                    style = Type.caption.copy(
                        color = colors.accent,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        report.sources.forEach { source ->
            val credibility = ResearchEngine.assessSource(source.url, source.title, source.charCount)
            val sourceInteraction = remember { MutableInteractionSource() }
            BasicText(
                text = "[${source.id}] ${source.title} · ${source.host}" +
                    " · 可信度 ${credibility.label} ${credibility.score}" +
                    (if (source.isPrivate) " · 隐私" else ""),
                style = Type.caption.copy(color = colors.textTertiary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xs))
                    .clickable(
                        interactionSource = sourceInteraction,
                        // 不用系统水波纹：来源行坐在玻璃卡上，
                        // 水波纹会直接画在折射层上面。
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "在新标签页打开来源 ${source.id}"
                    ) { onOpenSource(source.url, source.isPrivate) }
                    // 来源行也是能点的：没有按压反馈，用户不知道手指按到了没有。
                    .pressScale(pressedScale = 0.97f, interactionSource = sourceInteraction)
                    .padding(vertical = Space.xxs)
            )
        }
        BasicText(
            text = "点按来源可在新标签页打开；隐私来源保持为隐私标签。",
            style = Type.caption.copy(color = colors.textTertiary),
            modifier = Modifier.padding(top = Space.xxs)
        )
        Spacer(Modifier.height(Space.sm))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            ActionButton(if (flash == "md") "已导出" else "保存 Markdown", accent = true) {
                onExport(AgentReportFormat.Markdown)
                flash = "md"
            }
            ActionButton(if (flash == "html") "已导出" else "保存 HTML", accent = false) {
                onExport(AgentReportFormat.Html)
                flash = "html"
            }
            ActionButton(if (flash == "csv") "已导出" else "保存来源 CSV", accent = false) {
                onExport(AgentReportFormat.Csv)
                flash = "csv"
            }
            ActionButton(if (flash == "pdf") "已导出" else "保存 PDF", accent = false) {
                onExport(AgentReportFormat.Pdf)
                flash = "pdf"
            }
            ActionButton(if (speaking) "停止朗读" else "朗读", accent = false) {
                if (speaking) {
                    speech.stop()
                    speaking = false
                } else {
                    speech.language = Locale.SIMPLIFIED_CHINESE
                    val spoken = speech.speak(
                        ResearchEngine.plainText(report.markdown),
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "liuli-report"
                    )
                    speaking = spoken == TextToSpeech.SUCCESS
                }
            }
            ActionButton(if (flash == "copy") "已复制" else "复制", accent = false) {
                clipboard.setText(AnnotatedString(report.markdown))
                flash = "copy"
            }
            ActionButton("收起", accent = false, onClick = onDismiss)
        }
        Spacer(Modifier.height(Space.xs))
        BasicText(
            text = "报告保存在本机内存，只有你选择保存位置后才会写入文件。",
            style = Type.caption.copy(color = colors.textTertiary)
        )
    }
}

/** P4.3 创作产物：展示写作/改写/大纲/分享卡片，并提供复制、导出与分享动作。 */
@Composable
private fun CreationCard(
    creation: AgentCreation,
    onExport: (AgentCreationFormat) -> Unit,
    onShare: () -> Unit,
    onShareCard: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val clipboard = LocalClipboardManager.current
    // 动作按钮的瞬时反馈：点完换成"已复制/已导出/已分享"，1.6 秒后复原。
    var flash by rememberSaveable(creation.createdAt) { mutableStateOf("") }
    LaunchedEffect(flash) {
        if (flash.isNotEmpty()) {
            delay(1600)
            flash = ""
        }
    }
    // 正文预览：去掉 Markdown 标记后取前几行。
    val preview = remember(creation.body) {
        creation.body.lineSequence()
            .map { raw ->
                val line = raw.trim()
                val content = when {
                    line.startsWith("#") || line.startsWith(">") -> line.trimStart('#', '>').trim()
                    line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") ->
                        line.drop(2).trim()
                    else -> line
                }
                content.replace("**", "").replace("`", "").trim()
            }
            .filter { it.isNotBlank() }
            .take(6)
            .joinToString("\n")
    }
    val kindGlyph = when (creation.kind) {
        AgentCreationKind.Outline -> Glyph.History
        AgentCreationKind.Card -> Glyph.Share
        AgentCreationKind.Write, AgentCreationKind.Rewrite -> Glyph.Edit
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(colors.accentSecondary.copy(alpha = if (colors.isDark) 0.12f else 0.07f))
            .padding(Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardHeaderIcon(kindGlyph, colors.accentSecondary, entranceKey = creation.createdAt)
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = creation.kind.label,
                    style = Type.sectionTitle.copy(color = colors.textPrimary)
                )
                BasicText(
                    text = creation.title,
                    style = Type.caption.copy(color = colors.textTertiary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(colors.accentSecondary.copy(alpha = 0.14f))
                    .padding(horizontal = Space.sm, vertical = Space.xs)
            ) {
                BasicText(
                    text = creation.mode.label,
                    style = Type.micro.copy(color = colors.accentSecondary)
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        BasicText(
            text = preview,
            style = Type.bodySmall.copy(color = colors.textSecondary, fontSize = 11.sp),
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
        if (!creation.note.isNullOrBlank()) {
            Spacer(Modifier.height(Space.xs))
            BasicText(
                text = creation.note,
                style = Type.caption.copy(color = colors.textTertiary)
            )
        }
        Spacer(Modifier.height(Space.sm))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            ActionButton(if (flash == "copy") "已复制" else "复制", accent = true) {
                clipboard.setText(AnnotatedString(CreationEngine.plainText(creation)))
                flash = "copy"
            }
            ActionButton(if (flash == "md") "已导出" else "Markdown", accent = false) {
                onExport(AgentCreationFormat.Markdown)
                flash = "md"
            }
            ActionButton(if (flash == "html") "已导出" else "HTML", accent = false) {
                onExport(AgentCreationFormat.Html)
                flash = "html"
            }
            ActionButton(if (flash == "text") "已导出" else "纯文本", accent = false) {
                onExport(AgentCreationFormat.Text)
                flash = "text"
            }
            ActionButton(if (flash == "share") "已分享" else "分享", accent = false) {
                onShare()
                flash = "share"
            }
            ActionButton(if (flash == "card") "已生成" else "卡片", accent = false) {
                onShareCard()
                flash = "card"
            }
            ActionButton("收起", accent = false, onClick = onDismiss)
        }
        Spacer(Modifier.height(Space.xs))
        BasicText(
            text = "创作产物保存在本机内存，导出或分享前不会写入文件。",
            style = Type.caption.copy(color = colors.textTertiary)
        )
    }
}

@Composable
private fun MessageBubble(
    message: AgentMessage,
    modeLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    if (message.action != null) {
        ActionCard(message, onConfirm, onCancel)
        return
    }
    val colors = LocalLiuliColors.current
    val isUser = message.role == AgentMessageRole.User
    // 气泡标注的淡入规格：比气泡本身晚一点，两层错开。
    val captionSpec = MotionSpec.enter(Motion.Normal)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xxs),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // 气泡贴着文字收紧：整行铺满会让一句短话变成一条大色块，
        // 用户与 Agent 的气泡也就看不出差别了。
        if (message.text.isNotBlank()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .widthIn(max = maxWidth * if (isUser) 0.86f else 0.94f)
                        .wrapContentWidth()
                        .clip(
                            RoundedCornerShape(
                                // 气泡主体用小圆角，尾巴那一角再收小一档。
                                topStart = Radius.sm,
                                topEnd = Radius.sm,
                                bottomStart = if (isUser) Radius.sm else Radius.xs,
                                bottomEnd = if (isUser) Radius.xs else Radius.sm
                            )
                        )
                        .background(
                            if (isUser) colors.accent.copy(alpha = 0.16f)
                            else colors.textPrimary.copy(alpha = 0.06f)
                        )
                        .padding(horizontal = Space.md, vertical = Space.sm)
                ) {
                    BasicText(
                        text = message.text,
                        style = Type.body.copy(
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    )
                }
            }
        }
        if (!isUser) {
            // "本机 · 只读"标注晚于气泡一点点淡入：两层错开，不一次性砸下来。
            // 用 rememberSaveable 记住是否已经演过，滚出屏幕再回来不重播。
            var captionVisible by rememberSaveable(message.id) { mutableStateOf(false) }
            LaunchedEffect(message.id) {
                delay(120L)
                captionVisible = true
            }
            AnimatedVisibility(
                visible = captionVisible,
                enter = fadeIn(animationSpec = captionSpec)
            ) {
                BasicText(
                    // 措辞跟右上角徽标同源（"本机"/"云端"/"本地"），
                    // 但取值看这一条消息自己的来路：
                    // 云端失败改用本机汇总的那一条必须写"本机"。
                    text = "$modeLabel · 只读",
                    // 标注是承诺，不是角标：走 caption 档并用次级色，
                    // 9sp 的三级色在玻璃上几乎读不出来。
                    style = Type.caption.copy(color = colors.textSecondary),
                    modifier = Modifier.padding(start = Space.xs, top = Space.xxs)
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    message: AgentMessage,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val proposal = message.action ?: return
    val pending = message.actionStatus == AgentActionStatus.Pending
    // 待确认时卡片轻微呼吸：让用户知道"球在用户这边"，别把确认请求漏掉。
    val breath = rememberBreath(pending)
    val cardBackground by animateColorAsState(
        targetValue = colors.accent.copy(
            alpha = (if (colors.isDark) 0.14f else 0.08f) + 0.05f * breath
        ),
        animationSpec = MotionSpec.color(Motion.Normal),
        label = "action-card-bg"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xxs),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .background(cardBackground)
                .padding(Space.md)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardHeaderIcon(Glyph.Sparkle, colors.accent, entranceKey = message.id)
                    Spacer(Modifier.width(Space.sm))
                    BasicText(
                        text = proposal.title,
                        style = Type.sectionTitle.copy(color = colors.textPrimary),
                        modifier = Modifier.weight(1f)
                    )
                    // 风险徽标：入场时先是无色，随后落到对应风险色，
                    // 底色与字色一起交叉过渡，而不是硬切。
                    var riskSettled by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { riskSettled = true }
                    val riskTint = if (proposal.riskLabel.contains("低风险")) {
                        colors.success
                    } else {
                        colors.accent
                    }
                    val riskBadgeTint by animateColorAsState(
                        targetValue = if (riskSettled) riskTint else colors.textTertiary,
                        animationSpec = MotionSpec.color(Motion.Normal),
                        label = "action-risk-tint"
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(riskBadgeTint.copy(alpha = 0.14f))
                            .padding(horizontal = Space.sm, vertical = Space.xs)
                    ) {
                        BasicText(
                            text = proposal.riskLabel,
                            style = Type.micro.copy(color = riskBadgeTint)
                        )
                    }
                }
                Spacer(Modifier.height(Space.sm))
                BasicText(
                    text = proposal.detail,
                    style = Type.bodySmall.copy(
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                )
                Spacer(Modifier.height(Space.sm))
                // 状态行交叉淡入：待确认→执行中→已执行，切换时不是硬切。
                val statusLabel = when (message.actionStatus) {
                    AgentActionStatus.Running -> "正在执行…"
                    AgentActionStatus.Done -> "已执行"
                    AgentActionStatus.Failed -> "执行失败，请查看下方说明"
                    AgentActionStatus.Cancelled -> "已取消"
                    else -> null
                }
                val statusTint = when (message.actionStatus) {
                    AgentActionStatus.Running -> colors.accent
                    AgentActionStatus.Done -> colors.success
                    AgentActionStatus.Failed -> colors.danger
                    else -> colors.textTertiary
                }
                AnimatedVisibility(
                    visible = statusLabel != null,
                    enter = fadeIn(animationSpec = MotionSpec.enter(Motion.Quick)),
                    exit = fadeOut(animationSpec = MotionSpec.exit(Motion.Quick))
                ) {
                    BasicText(
                        text = statusLabel.orEmpty(),
                        style = Type.label.copy(color = statusTint)
                    )
                }
                // 确认行出现/消失都带高度过渡：卡片不会硬切一块下去。
                Visibility(
                    visible = pending,
                    enter = fadeIn(animationSpec = MotionSpec.enter(Motion.Quick)) +
                        expandVertically(animationSpec = MotionSpec.pageSpring(IntSize.Zero)),
                    exit = fadeOut(animationSpec = MotionSpec.exit(Motion.Quick)) +
                        shrinkVertically(animationSpec = MotionSpec.pageSpring(IntSize.Zero))
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        ActionButton("允许执行", accent = true, onClick = onConfirm)
                        ActionButton("取消", accent = false, onClick = onCancel)
                    }
                }
                if (message.actionStatus == AgentActionStatus.Running) {
                    // 执行中把确认键置灰：用户一眼看出这一步已经在跑，不会重复点。
                    ActionButton("执行中…", accent = true, enabled = false) { }
                }
            }
        }
        BasicText(
            text = "仅执行此一步，不会自动连续操作",
            style = Type.caption.copy(color = colors.textTertiary),
            modifier = Modifier.padding(start = Space.xs, top = Space.xxs)
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    accent: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val interaction = remember { MutableInteractionSource() }
    val reduced = reduceMotion
    // 禁用态：底色与字色一起褪下去，和可用态一眼分得清。
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> colors.textPrimary.copy(alpha = 0.04f)
            accent -> colors.accent
            else -> colors.textPrimary.copy(alpha = 0.08f)
        },
        animationSpec = MotionSpec.color(),
        label = "action-bg"
    )
    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> colors.textTertiary.copy(alpha = 0.6f)
            accent -> Color.White
            else -> colors.textSecondary
        },
        animationSpec = MotionSpec.color(),
        label = "action-tint"
    )
    Box(
        Modifier
            .heightIn(min = Dimens.minTouch)
            .clip(RoundedCornerShape(Radius.pill))
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            )
            // 禁用时 interactionSource 不发按下事件，按压形变自然不出现。
            .pressScale(pressedScale = 0.92f, interactionSource = interaction)
            .padding(horizontal = Space.lg),
        contentAlignment = Alignment.Center
    ) {
        // 按钮文案会变（"复制"→"已复制"、"朗读"→"停止朗读"），交叉淡入淡出而不是硬切。
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                fadeIn(animationSpec = MotionSpec.enter(reduced, Motion.Quick))
                    .togetherWith(fadeOut(animationSpec = MotionSpec.exit(reduced, Motion.Quick)))
                    .using(SizeTransform(clip = false))
            },
            label = "action-label"
        ) { text ->
            BasicText(
                text = text,
                style = Type.label.copy(color = tint)
            )
        }
    }
}

/**
 * 思考中的指示器。
 *
 * 原来只有一个静态的星形图标，用户看不出 Agent 是否还活着。
 * 现在用三点依次起伏的波纹表示"正在进行"，并让星形图标轻微呼吸。
 * "减少动态效果"开启时退化为静态图标。
 */
@Composable
private fun ThinkingRow() {
    val colors = LocalLiuliColors.current
    val reduced = reduceMotion
    // 三点波纹：用无限循环的相位偏移错开，模拟依次点亮的节奏。
    val wave = remember { Animatable(0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            wave.snapTo(0f)
        } else {
            while (true) {
                wave.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1100, easing = Motion.Enter)
                )
                wave.snapTo(0f)
            }
        }
    }
    val phase = wave.value

    Row(
        Modifier.padding(start = Space.xs, top = Space.sm, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(
            glyph = Glyph.Sparkle,
            tint = colors.accent.copy(alpha = 0.55f + 0.45f * phase),
            size = 13.dp
        )
        Spacer(Modifier.width(Space.xs))
        BasicText(
            text = "正在读取页面或与页面交互…",
            style = Type.label.copy(color = colors.textTertiary)
        )
        Spacer(Modifier.width(Space.sm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                // 每个点落后前一个 1/3 个周期，形成从左到右的流动感。
                val offset = (phase - index / 3f).let { if (it < 0f) it + 1f else it }
                val dotAlpha = 0.25f + 0.75f * (1f - offset)
                val dotScale = 0.72f + 0.42f * (1f - offset)
                Box(
                    Modifier
                        .size(5.dp)
                        .graphicsLayer {
                            alpha = dotAlpha
                            scaleX = dotScale
                            scaleY = dotScale
                        }
                        .clip(CircleShape)
                        .background(colors.accent)
                )
            }
        }
    }
}

/**
 * 呼吸强度：待确认的动作卡片靠它轻轻起伏，提醒用户"该你点头了"。
 *
 * 不需要脉动或开启"减少动态效果"时恒为 0，视觉上等价于静止。
 */
@Composable
private fun rememberBreath(active: Boolean): Float {
    val reduced = reduceMotion
    val breath = remember { Animatable(0f) }
    LaunchedEffect(active, reduced) {
        if (!active || reduced) {
            breath.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            breath.animateTo(1f, tween(durationMillis = 900, easing = Motion.Enter))
            breath.animateTo(0f, tween(durationMillis = 900, easing = Motion.Enter))
        }
    }
    return breath.value
}

/**
 * 气泡下方的"来路"标注。
 *
 * 面板右上角的徽标按面板整体状态显示（隐私标签一律"本机"），
 * 气泡则按这一条消息真正的处理位置显示：
 * 云端规划失败改用本机汇总的那一条，就该写"本机"而不是"云端"。
 * 两种说法混在一起会让用户分不清哪条消息真的出过本机。
 */
private fun bubbleModeLabel(mode: AgentMode, isPrivate: Boolean): String = when {
    mode == AgentMode.Cloud -> "云端"
    isPrivate -> "本机"
    else -> "本地"
}

/**
 * 把最新一条贴到视口底部。
 *
 * animateScrollToItem 会把条目顶到视口上沿，下面空出一大块；
 * 对话式列表里新内容应该贴着底部出现，才像"接着往下说"。
 */
private suspend fun LazyListState.scrollToLatest(
    index: Int,
    spec: FiniteAnimationSpec<Float>
) {
    animateScrollToItem(index)
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewport = info.viewportEndOffset - info.viewportStartOffset
    val overflow = viewport - item.size
    if (overflow > 0) {
        animateScrollBy(overflow.toFloat(), spec)
    }
}
