package com.liuli.browser.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.liuli.browser.ui.components.GlassIconButton
import com.liuli.browser.ui.components.GlassSurface
import com.liuli.browser.ui.components.Glyph
import com.liuli.browser.ui.components.GlyphIcon
import com.liuli.browser.ui.theme.Dimens
import com.liuli.browser.ui.theme.LocalLiuliColors
import com.liuli.browser.web.agent.AgentActionStatus
import com.liuli.browser.web.agent.AgentController
import com.liuli.browser.web.agent.AgentMessage
import com.liuli.browser.web.agent.AgentMessageRole
import com.liuli.browser.web.agent.AgentMode
import com.liuli.browser.web.agent.AgentPlan
import com.liuli.browser.web.agent.AgentPlanCommand
import com.liuli.browser.web.agent.AgentPlanStatus
import com.liuli.browser.web.agent.AgentReport
import com.liuli.browser.web.agent.AgentSiteMode

@Composable
fun AgentOrb(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLiuliColors.current
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
                role = Role.Button,
                onClickLabel = "打开琉璃 Agent",
                onClick = onClick
            ),
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
    onExportReport: (AgentReport) -> Unit
) {
    val colors = LocalLiuliColors.current
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

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

    LaunchedEffect(agent.messages.size, agent.busy, agent.plan != null, agent.report?.createdAt) {
        val target = agent.messages.size +
            (if (agent.plan != null) 1 else 0) +
            (if (agent.report != null) 1 else 0) +
            (if (agent.busy) 1 else 0)
        if (target == 0) return@LaunchedEffect
        listState.animateScrollToItem(target - 1)
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
        Box(
            Modifier
                .fillMaxSize()
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
                .padding(end = 16.dp, top = 10.dp, bottom = 10.dp)
                .width(400.dp)
                .fillMaxHeight()
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .fillMaxHeight(0.72f)
        }

        GlassSurface(
            backdrop = backdrop,
            modifier = panelModifier
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
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
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
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            text = "琉璃 Agent",
                            style = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        BasicText(
                            text = subtitle,
                            style = TextStyle(color = colors.textTertiary, fontSize = 10.sp),
                            maxLines = 1
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(modeTint.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        BasicText(
                            text = modeLabel,
                            style = TextStyle(
                                color = modeTint,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    GlassIconButton(
                        glyph = Glyph.Close,
                        contentDescription = "关闭 Agent",
                        onClick = onClose,
                        backdrop = backdrop,
                        size = 40.dp,
                        glyphSize = 15.dp
                    )
                }

                Spacer(Modifier.height(8.dp))
                BasicText(
                    text = "只读取可见正文与元素名称，不读取输入框已有内容、密码、Cookie 或剪贴板。",
                    style = TextStyle(color = colors.textTertiary, fontSize = 10.sp, lineHeight = 15.sp)
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickChip("总结此页") { submit("总结此页") }
                    QuickChip("页面信息") { submit("页面信息") }
                    QuickChip("页内查找") { prepareFind() }
                    QuickChip("向下滚动") { submit("向下滚动一屏") }
                    QuickChip("调研标签页") { submit("调研这些标签页并生成报告") }
                    QuickChip("这个页面讲了什么？") { submit("这个页面讲了什么？") }
                }

                Spacer(Modifier.height(6.dp))

                val currentPlan = agent.plan
                val currentReport = agent.report
                if (currentPlan == null && agent.messages.isEmpty()) {
                    EmptyState(
                        tabTitle = tabTitle,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(agent.messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                onConfirm = { onConfirmAction(message.id) },
                                onCancel = { onCancelAction(message.id) }
                            )
                        }
                        if (currentPlan != null) {
                            item(key = "plan") {
                                PlanCard(plan = currentPlan, onCommand = onPlanCommand)
                            }
                        }
                        if (currentReport != null) {
                            item(key = "report-${currentReport.createdAt}") {
                                ReportCard(
                                    report = currentReport,
                                    onExport = { onExportReport(currentReport) },
                                    onDismiss = agent::dismissReport
                                )
                            }
                        }
                        if (agent.busy) {
                            item(key = "thinking") { ThinkingRow() }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(colors.textPrimary.copy(alpha = 0.06f))
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it.take(500) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .focusRequester(focusRequester),
                            textStyle = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submit(input) }),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (input.isEmpty()) {
                                        BasicText(
                                            text = "询问本页内容…",
                                            style = TextStyle(
                                                color = colors.textTertiary,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    val canSend = input.isNotBlank() && !agent.busy
                    Box(
                        Modifier
                            .size(Dimens.minTouch)
                            .clip(CircleShape)
                            .background(
                                if (canSend) colors.accent
                                else colors.textPrimary.copy(alpha = 0.06f)
                            )
                            .semantics {
                                contentDescription = "发送"
                                role = Role.Button
                            }
                            .clickable(
                                enabled = canSend,
                                role = Role.Button,
                                onClickLabel = "发送",
                                onClick = { submit(input) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(
                            glyph = Glyph.Sparkle,
                            tint = if (canSend) Color.White else colors.textTertiary,
                            size = 20.dp
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                BasicText(
                    text = "受控操作：单步动作需逐条确认；多步任务需先批准计划，执行中可暂停或取消，不会自动提交表单。",
                    style = TextStyle(color = colors.textTertiary, fontSize = 9.sp, lineHeight = 13.sp)
                )
                if (agent.audit.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    BasicText(
                        text = "审计 ${agent.audit.size} 条 · 仅本机内存，清除对话或退出时删除",
                        style = TextStyle(
                            color = colors.textTertiary,
                            fontSize = 9.sp,
                            lineHeight = 13.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    val colors = LocalLiuliColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.textPrimary.copy(alpha = 0.06f))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = colors.textSecondary, fontSize = 11.sp)
        )
    }
}

@Composable
private fun EmptyState(tabTitle: String, modifier: Modifier = Modifier) {
    val colors = LocalLiuliColors.current
    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.accent.copy(alpha = 0.20f),
                            colors.accentSecondary.copy(alpha = 0.12f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.Sparkle, colors.accent, size = 26.dp)
        }
        Spacer(Modifier.height(10.dp))
        BasicText(
            text = "向当前页面提问",
            style = TextStyle(
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = tabTitle.take(48).ifBlank { "当前页面" },
            style = TextStyle(color = colors.textTertiary, fontSize = 11.sp),
            maxLines = 1
        )
        Spacer(Modifier.height(12.dp))
        BasicText(
            text = "速览、查找与问答默认在本机完成。\n也可以让我滚动、点击或填写，每次都会先确认。",
            style = TextStyle(
                color = colors.textTertiary,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        )
    }
}

@Composable
private fun PlanCard(
    plan: AgentPlan,
    onCommand: (AgentPlanCommand) -> Unit
) {
    val colors = LocalLiuliColors.current
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
            .clip(RoundedCornerShape(20.dp))
            .background(colors.accent.copy(alpha = if (colors.isDark) 0.12f else 0.07f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.Sparkle, colors.accent, size = 14.dp)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = "任务计划",
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                BasicText(
                    text = "${plan.steps.size} 步 · " +
                        "${if (plan.source == AgentMode.Cloud) "云端规划" else "本地规划"}",
                    style = TextStyle(color = colors.textTertiary, fontSize = 9.sp)
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(statusTint.copy(alpha = 0.14f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                BasicText(
                    text = statusLabel,
                    style = TextStyle(
                        color = statusTint,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = plan.goal,
            style = TextStyle(color = colors.textTertiary, fontSize = 10.sp, lineHeight = 15.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(stepTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "${index + 1}",
                        style = TextStyle(
                            color = stepTint,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                Spacer(Modifier.width(6.dp))
                BasicText(
                    text = step.description,
                    style = TextStyle(
                        color = if (completed) colors.textTertiary else colors.textSecondary,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                when {
                    completed -> GlyphIcon(Glyph.Check, colors.success, size = 12.dp)
                    failed -> GlyphIcon(Glyph.Info, colors.danger, size = 12.dp)
                    current -> GlyphIcon(Glyph.ChevronRight, colors.accent, size = 12.dp)
                    else -> Unit
                }
            }
        }
        if (!plan.failure.isNullOrBlank()) {
            Spacer(Modifier.height(5.dp))
            BasicText(
                text = plan.failure,
                style = TextStyle(color = colors.danger, fontSize = 10.sp, lineHeight = 15.sp)
            )
        }
        Spacer(Modifier.height(8.dp))
        when (plan.status) {
            AgentPlanStatus.Pending -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("批准并执行", accent = true) { onCommand(AgentPlanCommand.Approve) }
                    ActionButton("取消", accent = false) { onCommand(AgentPlanCommand.Cancel) }
                }
                Spacer(Modifier.height(5.dp))
                BasicText(
                    text = "批准后按顺序执行；点击可能跳转或提交表单，填写不会自动提交。",
                    style = TextStyle(color = colors.textTertiary, fontSize = 9.sp, lineHeight = 13.sp)
                )
            }
            AgentPlanStatus.Running -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("暂停", accent = false) { onCommand(AgentPlanCommand.Pause) }
                    ActionButton("取消任务", accent = false) { onCommand(AgentPlanCommand.Cancel) }
                }
            }
            AgentPlanStatus.Paused -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("继续执行", accent = true) { onCommand(AgentPlanCommand.Resume) }
                    ActionButton("取消任务", accent = false) { onCommand(AgentPlanCommand.Cancel) }
                }
            }
            AgentPlanStatus.Failed -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("重试", accent = true) { onCommand(AgentPlanCommand.Retry) }
                    ActionButton("取消", accent = false) { onCommand(AgentPlanCommand.Cancel) }
                }
            }
            AgentPlanStatus.Done, AgentPlanStatus.Cancelled -> Unit
        }
    }
}

@Composable
private fun ReportCard(
    report: AgentReport,
    onExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalLiuliColors.current
    val clipboard = LocalClipboardManager.current
    var expanded by rememberSaveable(report.createdAt) { mutableStateOf(false) }
    var copied by rememberSaveable(report.createdAt) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.success.copy(alpha = if (colors.isDark) 0.12f else 0.07f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(colors.success.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.History, colors.success, size = 14.dp)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = "调研报告",
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                BasicText(
                    text = "${report.sources.size} 个来源 · ${report.mode.label}整理 · 全部带引用",
                    style = TextStyle(color = colors.textTertiary, fontSize = 9.sp)
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.success.copy(alpha = 0.14f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                BasicText(
                    text = "只读",
                    style = TextStyle(
                        color = colors.success,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = report.goal,
            style = TextStyle(color = colors.textTertiary, fontSize = 10.sp, lineHeight = 15.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!report.note.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = report.note,
                style = TextStyle(color = colors.textTertiary, fontSize = 9.sp, lineHeight = 13.sp)
            )
        }
        Spacer(Modifier.height(7.dp))
        BasicText(
            text = report.markdown,
            style = TextStyle(color = colors.textSecondary, fontSize = 11.sp, lineHeight = 17.sp),
            maxLines = if (expanded) 80 else 8,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = if (expanded) "收起全文" else "展开全文",
            style = TextStyle(
                color = colors.accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "收起全文" else "展开全文"
                ) { expanded = !expanded }
        )
        Spacer(Modifier.height(7.dp))
        report.sources.forEach { source ->
            BasicText(
                text = "[${source.id}] ${source.title} · ${source.host}",
                style = TextStyle(color = colors.textTertiary, fontSize = 9.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("保存 Markdown", accent = true, onClick = onExport)
            ActionButton(if (copied) "已复制" else "复制", accent = false) {
                clipboard.setText(AnnotatedString(report.markdown))
                copied = true
            }
            ActionButton("收起", accent = false, onClick = onDismiss)
        }
        Spacer(Modifier.height(5.dp))
        BasicText(
            text = "报告保存在本机内存，只有你选择保存位置后才会写入文件。",
            style = TextStyle(color = colors.textTertiary, fontSize = 9.sp, lineHeight = 13.sp)
        )
    }
}

@Composable
private fun MessageBubble(
    message: AgentMessage,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    if (message.action != null) {
        ActionCard(message, onConfirm, onCancel)
        return
    }
    val colors = LocalLiuliColors.current
    val isUser = message.role == AgentMessageRole.User
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            Modifier
                .fillMaxWidth(if (isUser) 0.88f else 0.96f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 5.dp,
                        bottomEnd = if (isUser) 5.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) colors.accent.copy(alpha = 0.16f)
                    else colors.textPrimary.copy(alpha = 0.05f)
                )
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            BasicText(
                text = message.text,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            )
        }
        if (!isUser) {
            BasicText(
                text = "${message.mode.label} · 只读",
                style = TextStyle(color = colors.textTertiary, fontSize = 9.sp),
                modifier = Modifier.padding(start = 5.dp, top = 3.dp)
            )
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
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.accent.copy(alpha = if (colors.isDark) 0.14f else 0.08f))
                .padding(13.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(Glyph.Sparkle, colors.accent, size = 14.dp)
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = proposal.title,
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.accent.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        BasicText(
                            text = proposal.riskLabel,
                            style = TextStyle(
                                color = colors.accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                BasicText(
                    text = proposal.detail,
                    style = TextStyle(
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                )
                Spacer(Modifier.height(9.dp))
                when (message.actionStatus) {
                    AgentActionStatus.Pending -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton("允许执行", accent = true, onClick = onConfirm)
                            ActionButton("取消", accent = false, onClick = onCancel)
                        }
                    }
                    AgentActionStatus.Running -> BasicText(
                        text = "正在执行…",
                        style = TextStyle(color = colors.accent, fontSize = 11.sp)
                    )
                    AgentActionStatus.Done -> BasicText(
                        text = "已执行",
                        style = TextStyle(color = colors.success, fontSize = 11.sp)
                    )
                    AgentActionStatus.Failed -> BasicText(
                        text = "执行失败，请查看下方说明",
                        style = TextStyle(color = colors.danger, fontSize = 11.sp)
                    )
                    AgentActionStatus.Cancelled -> BasicText(
                        text = "已取消",
                        style = TextStyle(color = colors.textTertiary, fontSize = 11.sp)
                    )
                    AgentActionStatus.None -> Unit
                }
            }
        }
        BasicText(
            text = "仅执行此一步，不会自动连续操作",
            style = TextStyle(color = colors.textTertiary, fontSize = 9.sp),
            modifier = Modifier.padding(start = 5.dp, top = 3.dp)
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    accent: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalLiuliColors.current
    Box(
        Modifier
            .heightIn(min = Dimens.minTouch)
            .clip(RoundedCornerShape(50))
            .background(if (accent) colors.accent else colors.textPrimary.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (accent) Color.White else colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun ThinkingRow() {
    val colors = LocalLiuliColors.current
    Row(
        Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(Glyph.Sparkle, colors.accent, size = 13.dp)
        Spacer(Modifier.width(6.dp))
        BasicText(
            text = "正在读取页面或与页面交互…",
            style = TextStyle(color = colors.textTertiary, fontSize = 11.sp)
        )
    }
}
