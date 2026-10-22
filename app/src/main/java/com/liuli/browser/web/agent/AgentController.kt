package com.liuli.browser.web.agent

import android.net.Uri
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liuli.browser.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.sqrt

enum class AgentMessageRole {
    User,
    Agent
}

enum class AgentMode(val label: String) {
    Local("本地"),
    Cloud("云端")
}

enum class AgentActionStatus {
    None,
    Pending,
    Running,
    Done,
    Failed,
    Cancelled
}

data class AgentActionProposal(
    val kind: AgentActionKind,
    val title: String,
    val detail: String,
    val riskLabel: String,
    val pageUrl: String,
    val tabId: Long,
    val ref: String? = null,
    val text: String? = null,
    val direction: String? = null
) {
    fun toPageAction(): AgentPageAction = AgentPageAction(
        kind = kind,
        ref = ref,
        text = text,
        direction = direction
    )
}

data class AgentMessage(
    val id: Long,
    val role: AgentMessageRole,
    val text: String,
    val mode: AgentMode = AgentMode.Local,
    val action: AgentActionProposal? = null,
    val actionStatus: AgentActionStatus = AgentActionStatus.None
)

enum class AgentPlanStatus {
    Pending,
    Running,
    Paused,
    Done,
    Failed,
    Cancelled
}

enum class AgentPlanCommand {
    Approve,
    Pause,
    Resume,
    Retry,
    Cancel
}

enum class AgentPlanStepKind {
    Scroll,
    Click,
    Type
}

data class AgentPlanStep(
    val kind: AgentPlanStepKind,
    val description: String,
    val targetQuery: String? = null,
    val text: String? = null,
    val direction: String? = null,
    val resultIndex: Int? = null
)

data class AgentPlan(
    val tabId: Long,
    val pageUrl: String,
    val goal: String,
    val steps: List<AgentPlanStep>,
    val status: AgentPlanStatus = AgentPlanStatus.Pending,
    val currentStep: Int = 0,
    val failure: String? = null
)

/**
 * P1/P2 agent. It reads the current page through [AgentPageBridge] and answers
 * locally by default. Cloud inference is opt-in and only ever receives scrubbed
 * page text. Page actions are parsed locally and require one explicit
 * confirmation per action; nothing is executed automatically.
 */
class AgentController(private val settings: SettingsStore) {

    val messages = mutableStateListOf<AgentMessage>()

    var plan by mutableStateOf<AgentPlan?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var nextId = 1L
    private var pauseRequested = false

    val cloudConfigured: Boolean
        get() = settings.agentCloudEnabled &&
            settings.agentEndpoint.trim().startsWith("https://") &&
            settings.agentApiKey.isNotBlank()

    fun clear() {
        job?.cancel()
        job = null
        messages.clear()
        plan = null
        pauseRequested = false
        busy = false
    }

    fun dispose() {
        scope.cancel()
    }

    fun send(
        rawInput: String,
        webView: WebView?,
        pageTitle: String,
        isPrivate: Boolean,
        tabId: Long
    ) {
        if (busy) return
        val input = rawInput.trim().take(MAX_INPUT_CHARS)
        if (input.isEmpty()) return
        // A new instruction replaces any pending plan and invalidates the
        // current element registry.
        plan = null
        pauseRequested = false
        cancelPendingProposals()
        messages.add(AgentMessage(nextId++, AgentMessageRole.User, input))
        busy = true
        job = scope.launch {
            try {
                val snapshot = webView?.let { view ->
                    withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) { AgentPageBridge.snapshot(view) }
                }
                if (snapshot == null) {
                    addReply(
                        "暂时无法读取当前页面。请等待页面加载完成后再试；PDF、本地页面或渲染进程异常时不可用。",
                        AgentMode.Local
                    )
                    return@launch
                }
                handle(input, snapshot, pageTitle, isPrivate, tabId)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                addReply("Agent 处理时遇到问题：${error.message ?: "未知错误"}", AgentMode.Local)
            } finally {
                busy = false
            }
        }
    }

    fun confirmAction(messageId: Long, webView: WebView?, tabId: Long) {
        if (busy) return
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val message = messages[index]
        val proposal = message.action ?: return
        if (message.actionStatus != AgentActionStatus.Pending) return
        if (proposal.tabId != tabId) {
            messages[index] = message.copy(actionStatus = AgentActionStatus.Cancelled)
            addReply("该动作属于其他标签页，已取消。请在原标签页重新发起。", AgentMode.Local)
            return
        }
        if (!settings.agentActionsEnabled) {
            messages[index] = message.copy(actionStatus = AgentActionStatus.Cancelled)
            addReply("页面操作已在设置中关闭，动作已取消。", AgentMode.Local)
            return
        }
        if (webView == null) {
            messages[index] = message.copy(actionStatus = AgentActionStatus.Failed)
            addReply("无法执行：当前页面不可用。", AgentMode.Local)
            return
        }
        val currentHost = hostOf(webView.url)
        val proposalHost = hostOf(proposal.pageUrl)
        if (proposalHost != null && currentHost != proposalHost) {
            messages[index] = message.copy(actionStatus = AgentActionStatus.Failed)
            addReply("页面已经跳转到其他站点，动作已取消。请重新读取页面后再发起。", AgentMode.Local)
            return
        }

        messages[index] = message.copy(actionStatus = AgentActionStatus.Running)
        cancelPendingProposals()
        busy = true
        job = scope.launch {
            try {
                val result = withTimeoutOrNull(ACTION_TIMEOUT_MS) {
                    AgentPageBridge.execute(webView, proposal.toPageAction())
                }
                val currentIndex = messages.indexOfFirst { it.id == messageId }
                when {
                    result == null -> {
                        if (currentIndex >= 0) {
                            messages[currentIndex] = messages[currentIndex]
                                .copy(actionStatus = AgentActionStatus.Failed)
                        }
                        addReply(
                            if (proposal.kind == AgentActionKind.Click) {
                                "执行结果未返回，页面可能已经跳转。请重新读取页面后再继续。"
                            } else {
                                "执行超时，页面可能没有响应。"
                            },
                            AgentMode.Local
                        )
                    }
                    result.ok -> {
                        if (currentIndex >= 0) {
                            messages[currentIndex] = messages[currentIndex]
                                .copy(actionStatus = AgentActionStatus.Done)
                        }
                        addReply(result.message, AgentMode.Local)
                    }
                    else -> {
                        if (currentIndex >= 0) {
                            messages[currentIndex] = messages[currentIndex]
                                .copy(actionStatus = AgentActionStatus.Failed)
                        }
                        addReply("未能执行：${result.message}", AgentMode.Local)
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                addReply("执行失败：${error.message ?: "未知错误"}", AgentMode.Local)
            } finally {
                busy = false
            }
        }
    }

    fun cancelAction(messageId: Long) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val message = messages[index]
        if (message.actionStatus != AgentActionStatus.Pending) return
        messages[index] = message.copy(actionStatus = AgentActionStatus.Cancelled)
        addReply("已取消，页面未发生变化。", AgentMode.Local)
    }

    private fun cancelPendingProposals() {
        for (index in messages.indices) {
            val message = messages[index]
            if (message.actionStatus == AgentActionStatus.Pending) {
                messages[index] = message.copy(actionStatus = AgentActionStatus.Cancelled)
            }
        }
    }

    /**
     * Pending proposals belong to one tab's element registry. Switching tabs
     * invalidates them, so cancel anything that is not for the new tab.
     */
    fun onTabChanged(tabId: Long) {
        for (index in messages.indices) {
            val message = messages[index]
            val proposal = message.action
            if (message.actionStatus == AgentActionStatus.Pending &&
                proposal != null &&
                proposal.tabId != tabId
            ) {
                messages[index] = message.copy(actionStatus = AgentActionStatus.Cancelled)
            }
        }
        val activePlan = plan
        if (activePlan != null && activePlan.tabId != tabId) {
            job?.cancel()
            plan = null
            pauseRequested = false
        }
    }

    fun handlePlanCommand(command: AgentPlanCommand, webView: WebView?, tabId: Long) {
        when (command) {
            AgentPlanCommand.Approve -> approvePlan(webView, tabId)
            AgentPlanCommand.Pause -> pausePlan()
            AgentPlanCommand.Resume -> resumePlan(webView, tabId)
            AgentPlanCommand.Retry -> retryPlan(webView, tabId)
            AgentPlanCommand.Cancel -> cancelPlan()
        }
    }

    private fun approvePlan(webView: WebView?, tabId: Long) {
        val current = plan ?: return
        if (current.status != AgentPlanStatus.Pending) return
        if (current.tabId != tabId) {
            plan = null
            return
        }
        if (!settings.agentActionsEnabled) {
            plan = current.copy(
                status = AgentPlanStatus.Cancelled,
                failure = "页面操作已关闭"
            )
            addReply("页面操作已在设置中关闭，任务计划已取消。", AgentMode.Local)
            return
        }
        if (webView == null) {
            plan = current.copy(
                status = AgentPlanStatus.Failed,
                failure = "当前页面不可用"
            )
            addReply("无法开始任务：当前页面不可用。", AgentMode.Local)
            return
        }
        if (!sameSite(webView.url, current.pageUrl)) {
            plan = current.copy(
                status = AgentPlanStatus.Failed,
                failure = "页面已跳转"
            )
            addReply("页面已经跳转，任务计划已停止。请重新读取页面后再发起。", AgentMode.Local)
            return
        }
        startPlan(webView, tabId)
    }

    private fun resumePlan(webView: WebView?, tabId: Long) {
        val current = plan ?: return
        if (current.status != AgentPlanStatus.Paused || current.tabId != tabId) return
        if (webView == null) {
            plan = current.copy(status = AgentPlanStatus.Failed, failure = "当前页面不可用")
            addReply("无法继续任务：当前页面不可用。", AgentMode.Local)
            return
        }
        startPlan(webView, tabId)
    }

    private fun retryPlan(webView: WebView?, tabId: Long) {
        val current = plan ?: return
        if (current.status != AgentPlanStatus.Failed || current.tabId != tabId) return
        if (webView == null) {
            plan = current.copy(status = AgentPlanStatus.Failed, failure = "当前页面不可用")
            addReply("无法重试任务：当前页面不可用。", AgentMode.Local)
            return
        }
        startPlan(webView, tabId)
    }

    private fun pausePlan() {
        val current = plan ?: return
        if (current.status != AgentPlanStatus.Running || pauseRequested) return
        pauseRequested = true
        addReply("将在当前步骤完成后暂停。", AgentMode.Local)
    }

    private fun cancelPlan() {
        val current = plan ?: return
        if (current.status == AgentPlanStatus.Done || current.status == AgentPlanStatus.Cancelled) return
        job?.cancel()
        job = null
        pauseRequested = false
        plan = current.copy(status = AgentPlanStatus.Cancelled)
        addReply("任务已取消，后续步骤不会再执行。", AgentMode.Local)
        busy = false
    }

    private fun startPlan(webView: WebView, tabId: Long) {
        val current = plan ?: return
        if (current.tabId != tabId || busy) return
        plan = current.copy(status = AgentPlanStatus.Running, failure = null)
        pauseRequested = false
        busy = true
        job = scope.launch {
            try {
                runPlan(webView, tabId)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                failPlan("任务执行异常：${error.message ?: "未知错误"}")
            } finally {
                busy = false
            }
        }
    }

    private suspend fun runPlan(webView: WebView, tabId: Long) {
        var current = plan ?: return
        while (current.currentStep < current.steps.size) {
            if (pauseRequested) {
                plan = current.copy(status = AgentPlanStatus.Paused)
                pauseRequested = false
                addReply(
                    "任务已暂停，可继续执行第 ${current.currentStep + 1} 步。",
                    AgentMode.Local
                )
                return
            }
            if (current.tabId != tabId) {
                failPlan("标签页已切换，任务停止")
                return
            }
            if (!sameSite(webView.url, current.pageUrl)) {
                failPlan("页面已跳转到其他站点，任务停止")
                return
            }
            val step = current.steps[current.currentStep]
            val stepNumber = current.currentStep + 1
            val snapshot = snapshotForStep(webView)
            if (snapshot == null) {
                failPlan("第 $stepNumber 步无法读取页面")
                return
            }
            if (!sameSite(snapshot.url, current.pageUrl)) {
                failPlan("第 $stepNumber 步执行前页面已跳转")
                return
            }
            val action = resolvePlanStep(step, snapshot)
            if (action == null) {
                failPlan("第 $stepNumber 步找不到目标：${step.description}")
                return
            }
            val result = withTimeoutOrNull(ACTION_TIMEOUT_MS) {
                AgentPageBridge.execute(webView, action)
            }
            if (result == null) {
                failPlan("第 $stepNumber 步执行超时")
                return
            }
            if (!result.ok) {
                failPlan("第 $stepNumber 步失败：${result.message}")
                return
            }
            current = current.copy(currentStep = current.currentStep + 1)
            plan = current
            addReply("步骤 $stepNumber/${current.steps.size} · ${result.message}", AgentMode.Local)
            if (current.currentStep < current.steps.size) {
                delay(if (step.kind == AgentPlanStepKind.Click) 700L else 300L)
            }
        }
        plan = current.copy(status = AgentPlanStatus.Done)
        addReply("任务完成，共执行 ${current.steps.size} 步。", AgentMode.Local)
    }

    private suspend fun snapshotForStep(webView: WebView): AgentPageSnapshot? {
        repeat(SNAPSHOT_ATTEMPTS) { attempt ->
            val snapshot = withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) {
                AgentPageBridge.snapshot(webView)
            }
            if (snapshot != null) return snapshot
            if (attempt < SNAPSHOT_ATTEMPTS - 1) delay(SNAPSHOT_RETRY_DELAY_MS)
        }
        return null
    }

    private fun failPlan(reason: String) {
        val current = plan ?: return
        if (current.status == AgentPlanStatus.Cancelled) return
        plan = current.copy(status = AgentPlanStatus.Failed, failure = reason)
        addReply("任务未完成：$reason", AgentMode.Local)
    }

    private fun resolvePlanStep(
        step: AgentPlanStep,
        snapshot: AgentPageSnapshot
    ): AgentPageAction? = when (step.kind) {
        AgentPlanStepKind.Scroll -> AgentPageAction(
            kind = AgentActionKind.Scroll,
            direction = step.direction ?: "down"
        )
        AgentPlanStepKind.Click -> {
            val element = if (step.resultIndex != null) {
                snapshot.elements
                    .filter { it.tag == "a" && it.name.length >= 2 }
                    .getOrNull(step.resultIndex)
            } else {
                bestMatch(
                    snapshot.elements.filter { it.isClickable() },
                    step.targetQuery.orEmpty()
                )
            }
            element?.let { AgentPageAction(kind = AgentActionKind.Click, ref = it.ref) }
        }
        AgentPlanStepKind.Type -> {
            val candidates = snapshot.elements.filter { it.isTextInput() }
            val element = if (step.targetQuery.isNullOrBlank()) {
                candidates.firstOrNull { it.looksLikeSearchField() }
            } else {
                bestMatch(candidates, cleanTypeTarget(step.targetQuery))
            }
            if (element == null ||
                element.isForbiddenInput() ||
                isSensitiveInputName(element.name)
            ) {
                null
            } else {
                AgentPageAction(
                    kind = AgentActionKind.Type,
                    ref = element.ref,
                    text = step.text.orEmpty()
                )
            }
        }
    }

    private fun sameSite(currentUrl: String?, planUrl: String): Boolean {
        val currentHost = hostOf(currentUrl) ?: return false
        val planHost = hostOf(planUrl) ?: return true
        return currentHost == planHost
    }

    private fun hostOf(url: String?): String? =
        runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()

    private suspend fun handle(
        input: String,
        snapshot: AgentPageSnapshot,
        pageTitle: String,
        isPrivate: Boolean,
        tabId: Long
    ) {
        val findQuery = findQuery(input)
        if (findQuery != null) {
            addReply(findInPage(findQuery, snapshot), AgentMode.Local)
            return
        }
        if (isPageInfoRequest(input)) {
            addReply(pageInfo(snapshot), AgentMode.Local)
            return
        }

        if (settings.agentActionsEnabled) {
            if (tryOfferPlan(input, snapshot, tabId)) return
            if (tryOfferAction(input, snapshot, tabId)) return
        } else if (looksLikeActionRequest(input) || looksLikePlanRequest(input)) {
            addReply(
                "页面操作已在“设置 > AI 页面助手”中关闭。开启后可让我滚动、点击、填写，" +
                    "也可以批准多步任务计划。",
                AgentMode.Local
            )
            return
        }

        val summary = isSummaryRequest(input)
        val instruction = if (summary) {
            "请用 5 条要点总结这个页面的主要内容，只依据正文，不要编造。"
        } else {
            input
        }

        var note: String? = null
        if (isPrivate && settings.agentCloudEnabled) {
            note = "隐私标签仅在本机处理，本次回答没有离开设备。"
        } else if (cloudReady(isPrivate)) {
            val result = runCatching { requestCloud(snapshot, instruction) }
            val answer = result.getOrNull()
            if (!answer.isNullOrBlank()) {
                addReply(answer, AgentMode.Cloud)
                return
            }
            val reason = result.exceptionOrNull()?.message?.take(60)
            note = if (reason.isNullOrBlank()) {
                "云端请求失败，已使用本地模式。"
            } else {
                "云端请求失败（$reason），已使用本地模式。"
            }
        } else if (settings.agentCloudEnabled) {
            note = when {
                settings.agentApiKey.isBlank() -> "云端模式已开启但未填写 API Key，本次使用本地模式。"
                settings.agentEndpoint.trim().startsWith("http://") ->
                    "云端接口仅支持 HTTPS，本次使用本地模式。"
                else -> "云端模式未就绪，本次使用本地模式。"
            }
        }

        val local = if (summary) summarize(snapshot, pageTitle) else answerLocally(input, snapshot)
        addReply(local, AgentMode.Local, note)
    }

    // ---------------------------------------------------------------------
    // Confirmation-gated page actions (single step, local only)
    // ---------------------------------------------------------------------

    private fun tryOfferPlan(
        input: String,
        snapshot: AgentPageSnapshot,
        tabId: Long
    ): Boolean {
        val segments = splitPlanSegments(input)
        if (segments.size < 2) return false
        val parsed = segments.map { parseSingleStep(it) }
        val steps = parsed.filterNotNull()
        if (steps.size < 2) {
            if (steps.isNotEmpty()) {
                addReply(invalidPlanMessage(), AgentMode.Local)
                return true
            }
            return false
        }
        if (parsed.any { it == null }) {
            addReply(invalidPlanMessage(), AgentMode.Local)
            return true
        }
        if (steps.size > MAX_PLAN_STEPS) {
            addReply("计划最多支持 $MAX_PLAN_STEPS 步，请拆分成多次任务。", AgentMode.Local)
            return true
        }
        offerPlan(input, steps, snapshot.url, tabId)
        return true
    }

    private fun offerPlan(
        goal: String,
        steps: List<AgentPlanStep>,
        pageUrl: String,
        tabId: Long
    ) {
        cancelPendingProposals()
        plan = AgentPlan(
            tabId = tabId,
            pageUrl = pageUrl,
            goal = goal.take(200),
            steps = steps
        )
        addReply("已生成 ${steps.size} 步任务计划，请批准后执行。", AgentMode.Local)
    }

    private fun invalidPlanMessage(): String =
        "计划中有无法识别的步骤。请把每一步写成“点击…” “在…输入…”或“滚动…”，用“然后”连接。"

    private fun splitPlanSegments(input: String): List<String> {
        return input.split(PLAN_SEPARATOR)
            .flatMap { part ->
                val commaParts = part.split(COMMA_SEPARATOR)
                if (commaParts.size > 1 && commaParts.all { parseSingleStep(it) != null }) {
                    commaParts
                } else {
                    listOf(part)
                }
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseSingleStep(segment: String): AgentPlanStep? {
        val direction = parseScroll(segment)
        if (direction != null) {
            return AgentPlanStep(
                kind = AgentPlanStepKind.Scroll,
                description = scrollTitle(direction),
                direction = direction
            )
        }
        val clickTarget = parseClickTarget(segment)
        if (clickTarget != null) {
            val resultIndex = parseResultIndex(clickTarget)
            val target = cleanClickTarget(clickTarget)
            if (resultIndex == null && target.isBlank()) return null
            return if (resultIndex != null) {
                AgentPlanStep(
                    kind = AgentPlanStepKind.Click,
                    description = "点击第 ${resultIndex + 1} 个搜索结果",
                    resultIndex = resultIndex
                )
            } else {
                AgentPlanStep(
                    kind = AgentPlanStepKind.Click,
                    description = "点击“$target”",
                    targetQuery = target
                )
            }
        }
        val typeIntent = parseTypeIntent(segment)
        if (typeIntent != null) {
            val (target, text) = typeIntent
            if (text.isBlank()) return null
            val label = target.ifBlank { "搜索框" }
            return AgentPlanStep(
                kind = AgentPlanStepKind.Type,
                description = "在“${label.take(24)}”输入“${text.take(24)}”",
                targetQuery = target,
                text = text
            )
        }
        return null
    }

    private fun parseResultIndex(target: String): Int? {
        val raw = RESULT_PATTERN.find(target)?.groupValues?.getOrNull(1) ?: return null
        val value = raw.toIntOrNull() ?: CHINESE_NUMBERS[raw] ?: return null
        return (value - 1).takeIf { it in 0..9 }
    }

    private fun looksLikePlanRequest(input: String): Boolean {
        val parts = splitPlanSegments(input)
        return parts.size >= 2 && parts.any { parseSingleStep(it) != null }
    }

    private fun tryOfferAction(
        input: String,
        snapshot: AgentPageSnapshot,
        tabId: Long
    ): Boolean {
        val direction = parseScroll(input)
        if (direction != null) {
            offerAction(
                AgentActionProposal(
                    kind = AgentActionKind.Scroll,
                    title = scrollTitle(direction),
                    detail = "只改变页面滚动位置，不会点击或提交任何内容。",
                    riskLabel = "低风险",
                    pageUrl = snapshot.url,
                    tabId = tabId,
                    direction = direction
                )
            )
            return true
        }

        val clickTarget = parseClickTarget(input)
        if (clickTarget != null) {
            val resultIndex = parseResultIndex(clickTarget)
            val target = cleanClickTarget(clickTarget)
            if (resultIndex == null && target.isBlank()) {
                addReply("请告诉我点击哪个元素，例如“点击登录”。", AgentMode.Local)
                return true
            }
            val element = if (resultIndex != null) {
                snapshot.elements
                    .filter { it.tag == "a" && it.name.length >= 2 }
                    .getOrNull(resultIndex)
            } else {
                bestMatch(snapshot.elements.filter { it.isClickable() }, target)
            }
            if (element == null) {
                val label = if (resultIndex != null) {
                    "第 ${resultIndex + 1} 个搜索结果"
                } else {
                    "“${target.take(40)}”"
                }
                addReply(
                    "没有找到可点击的$label。可以先发“页面信息”查看当前页面的可交互元素。",
                    AgentMode.Local
                )
                return true
            }
            offerAction(
                AgentActionProposal(
                    kind = AgentActionKind.Click,
                    title = if (resultIndex != null) {
                        "点击第 ${resultIndex + 1} 个搜索结果"
                    } else {
                        "点击${element.roleLabel()}“${element.name.take(40)}”"
                    },
                    detail = if (isSubmitLike(element)) {
                        "该元素可能触发提交、登录或跳转。只会执行这一次点击，请确认目标无误。"
                    } else {
                        "只会执行这一次点击；页面可能展开、跳转或提交表单。"
                    },
                    riskLabel = "需确认",
                    pageUrl = snapshot.url,
                    tabId = tabId,
                    ref = element.ref
                )
            )
            return true
        }

        val typeIntent = parseTypeIntent(input)
        if (typeIntent != null) {
            val (targetQuery, text) = typeIntent
            if (text.isBlank()) {
                addReply("请告诉我需要填写的内容，例如“在搜索框输入琉璃浏览器”。", AgentMode.Local)
                return true
            }
            val candidates = snapshot.elements.filter { it.isTextInput() }
            val element = if (targetQuery.isBlank()) {
                candidates.firstOrNull { it.looksLikeSearchField() }
            } else {
                bestMatch(candidates, cleanTypeTarget(targetQuery))
            }
            if (element == null) {
                addReply(
                    if (targetQuery.isBlank()) {
                        "当前页面没有找到搜索输入框。可以先发“页面信息”查看可交互元素。"
                    } else {
                        "没有找到可填写的“${targetQuery.take(40)}”。可以先发“页面信息”查看可交互元素。"
                    },
                    AgentMode.Local
                )
                return true
            }
            if (element.isForbiddenInput() || isSensitiveInputName(element.name)) {
                addReply("出于隐私保护，Agent 不填写密码、隐藏或文件字段。", AgentMode.Local)
                return true
            }
            val preview = text.take(60)
            offerAction(
                AgentActionProposal(
                    kind = AgentActionKind.Type,
                    title = "填写${element.roleLabel()}“${element.name.take(40)}”",
                    detail = "将输入 ${text.length} 个字符：$preview${if (text.length > 60) "…" else ""}。" +
                        "只填入文本，不会自动提交表单。",
                    riskLabel = "需确认",
                    pageUrl = snapshot.url,
                    tabId = tabId,
                    ref = element.ref,
                    text = text
                )
            )
            return true
        }
        return false
    }

    private fun looksLikeActionRequest(input: String): Boolean =
        parseScroll(input) != null ||
            parseClickTarget(input) != null ||
            parseTypeIntent(input) != null

    private fun offerAction(proposal: AgentActionProposal) {
        cancelPendingProposals()
        while (messages.size >= MAX_MESSAGES) messages.removeAt(0)
        messages.add(
            AgentMessage(
                id = nextId++,
                role = AgentMessageRole.Agent,
                text = "我准备执行一个页面动作，请确认：",
                mode = AgentMode.Local,
                action = proposal,
                actionStatus = AgentActionStatus.Pending
            )
        )
    }

    private fun parseScroll(input: String): String? {
        val lower = input.lowercase()
        val mentions = input.contains("滚动") || input.contains("滚到") ||
            input.contains("滑到") || lower.contains("scroll")
        if (!mentions) return null
        return when {
            input.contains("底") || lower.contains("bottom") -> "bottom"
            input.contains("顶") || lower.contains("top") -> "top"
            input.contains("上") || lower.contains("up") -> "up"
            else -> "down"
        }
    }

    private fun parseClickTarget(input: String): String? =
        CLICK_PATTERN.find(input)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseTypeIntent(input: String): Pair<String, String>? {
        TYPE_IN_PATTERN.find(input)?.let { match ->
            return match.groupValues[1].trim() to cleanTypedText(match.groupValues[2])
        }
        TYPE_TO_PATTERN.find(input)?.let { match ->
            return match.groupValues[2].trim() to cleanTypedText(match.groupValues[1])
        }
        TYPE_EN_PATTERN.find(input)?.let { match ->
            return match.groupValues[2].trim() to cleanTypedText(match.groupValues[1])
        }
        SEARCH_PATTERN.find(input)?.let { match ->
            val text = cleanTypedText(match.groupValues[1]).removePrefix("一下").trim()
            if (text.isNotEmpty()) return "" to text
        }
        return null
    }

    private fun cleanTypedText(raw: String): String =
        raw.trim().trim('：', ':', '“', '”', '"', '\'').trim()

    private fun scrollTitle(direction: String): String = when (direction) {
        "bottom" -> "滚动到页面底部"
        "top" -> "滚动到页面顶部"
        "up" -> "向上滚动约一屏"
        else -> "向下滚动约一屏"
    }

    private fun cleanClickTarget(raw: String): String =
        stripSuffixes(raw, listOf("按钮", "链接", "选项", "图标"))

    private fun cleanTypeTarget(raw: String): String =
        stripSuffixes(raw, listOf("输入框", "文本框", "搜索框"))

    private fun stripSuffixes(raw: String, suffixes: List<String>): String {
        var value = raw.trim().trim('“', '”', '"', '\'', '《', '》', ' ')
        suffixes.forEach { suffix ->
            if (value.endsWith(suffix) && value.length > suffix.length) {
                value = value.removeSuffix(suffix).trim()
            }
        }
        return value.trim()
    }

    private fun bestMatch(candidates: List<AgentElement>, query: String): AgentElement? {
        val target = query.lowercase().trim()
        if (target.isEmpty()) return null
        return candidates.map { element ->
            val name = element.name.lowercase().trim()
            val score = when {
                name.isNotEmpty() && name == target -> 100
                name.isNotEmpty() && name.startsWith(target) -> 82
                name.isNotEmpty() && name.contains(target) -> 68
                name.length >= 2 && target.contains(name) -> 56
                else -> 0
            } + when {
                element.tag == "button" || element.role == "button" -> 6
                element.tag == "a" || element.role == "link" -> 5
                element.tag == "input" -> 3
                else -> 0
            }
            score to element
        }.filter { it.first > 0 }
            .maxByOrNull { it.first }
            ?.second
    }

    private fun AgentElement.isClickable(): Boolean = when {
        tag == "a" || tag == "button" -> true
        role == "button" || role == "link" || role == "tab" ||
            role == "checkbox" || role == "radio" -> true
        tag == "input" && inputType in setOf("submit", "button", "reset", "checkbox", "radio") -> true
        else -> false
    }

    private fun AgentElement.isTextInput(): Boolean = when {
        tag == "textarea" -> true
        editable -> true
        tag == "input" && inputType in setOf("", "text", "search", "email", "url", "tel", "number") -> true
        else -> false
    }

    private fun AgentElement.isForbiddenInput(): Boolean =
        tag == "input" && inputType in setOf("password", "hidden", "file")

    private fun isSensitiveInputName(name: String): Boolean {
        val lower = name.lowercase()
        return name.contains("密码") || lower.contains("password")
    }

    private fun AgentElement.looksLikeSearchField(): Boolean {
        val lower = name.lowercase()
        return inputType == "search" ||
            lower.contains("搜索") ||
            lower.contains("search") ||
            lower.contains("查询")
    }

    private fun AgentElement.roleLabel(): String = when {
        tag == "a" || role == "link" -> "链接"
        role == "tab" -> "标签"
        role == "checkbox" -> "复选框"
        role == "radio" -> "单选项"
        tag == "input" && inputType == "submit" -> "提交按钮"
        tag == "input" && inputType in setOf("button", "reset") -> "按钮"
        tag == "input" || tag == "textarea" || editable -> "输入框"
        else -> "按钮"
    }

    private fun isSubmitLike(element: AgentElement): Boolean {
        if (element.tag == "input" && element.inputType in setOf("submit", "image")) return true
        val riskyWords = listOf("提交", "登录", "注册", "支付", "购买", "下单", "确认", "发送", "删除", "注销")
        return riskyWords.any { element.name.contains(it) }
    }

    private fun cloudReady(isPrivate: Boolean): Boolean =
        settings.agentCloudEnabled &&
            !isPrivate &&
            settings.agentApiKey.isNotBlank() &&
            settings.agentEndpoint.trim().startsWith("https://")

    private suspend fun requestCloud(
        snapshot: AgentPageSnapshot,
        instruction: String
    ): String {
        val endpoint = normalizeEndpoint(settings.agentEndpoint)
        val model = settings.agentModel.trim().ifBlank { DEFAULT_MODEL }
        val apiKey = settings.agentApiKey.trim()
        val pageText = PrivacyScrubber.scrub(snapshot.text.take(CLOUD_TEXT_LIMIT))
        val title = PrivacyScrubber.scrub(snapshot.title.take(200))
        val address = PrivacyScrubber.safeUrl(snapshot.url)

        return withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("model", model)
                put("temperature", 0.2)
                put("max_tokens", 800)
                put(
                    "messages",
                    JSONArray().apply {
                        put(
                            JSONObject()
                                .put("role", "system")
                                .put("content", SYSTEM_PROMPT)
                        )
                        put(
                            JSONObject()
                                .put("role", "user")
                                .put(
                                    "content",
                                    buildString {
                                        appendLine("页面标题：$title")
                                        if (address.isNotBlank()) appendLine("页面地址：$address")
                                        appendLine("页面正文：")
                                        appendLine(pageText)
                                        append("任务：$instruction")
                                    }
                                )
                        )
                    }
                )
            }

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "LiuliBrowser/1.0.5")
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }
            try {
                val body = payload.toString().toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(body) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val detail = runCatching {
                        JSONObject(text).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    throw IllegalStateException(detail?.take(100) ?: "HTTP $code")
                }
                val content = JSONObject(text)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                    .trim()
                if (content.isEmpty()) throw IllegalStateException("响应为空")
                content.take(MAX_ANSWER_CHARS)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun normalizeEndpoint(raw: String): String {
        val base = raw.trim().ifBlank { DEFAULT_ENDPOINT }.trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    private fun addReply(text: String, mode: AgentMode, note: String? = null) {
        val body = if (note.isNullOrBlank()) text else "$text\n\n$note"
        while (messages.size >= MAX_MESSAGES) {
            messages.removeAt(0)
        }
        messages.add(AgentMessage(nextId++, AgentMessageRole.Agent, body, mode))
    }

    // ---------------------------------------------------------------------
    // Local, on-device capabilities
    // ---------------------------------------------------------------------

    private fun isSummaryRequest(input: String): Boolean {
        if (input.length > 40) return false
        val lower = input.lowercase()
        return input.contains("总结") ||
            input.contains("速览") ||
            input.contains("摘要") ||
            input.contains("概括") ||
            input.contains("讲了什么") ||
            input.contains("讲什么") ||
            lower.contains("summary") ||
            lower.contains("summarize")
    }

    private fun isPageInfoRequest(input: String): Boolean {
        val lower = input.lowercase().trim()
        return input.contains("页面信息") ||
            input.contains("页面统计") ||
            input.contains("页面结构") ||
            lower == "info" ||
            lower == "page info"
    }

    private fun findQuery(input: String): String? {
        val prefixes = listOf("查找", "查找：", "查找:", "find ", "/find ")
        val lower = input.lowercase()
        val matched = prefixes.firstOrNull { prefix ->
            if (prefix.endsWith(" ")) lower.startsWith(prefix) else input.startsWith(prefix)
        } ?: return null
        return input.drop(matched.length).trim().trim('：', ':', '“', '”', '"', '\'')
    }

    private fun summarize(snapshot: AgentPageSnapshot, pageTitle: String): String {
        val sentences = splitSentences(snapshot.text)
        if (sentences.isEmpty()) {
            return "页面正文为空或尚未加载完成，暂时无法速览。"
        }
        val picked = rankSentences(sentences)
        val title = snapshot.title.ifBlank { pageTitle }.take(120)
        val heading = if (title.isNotBlank()) "《$title》\n" else ""
        val body = picked.joinToString("\n") { "• ${it.second}" }
        val stat = buildString {
            append("共 ${snapshot.text.length} 字")
            if (snapshot.elements.isNotEmpty()) append(" · ${snapshot.elements.size} 个可交互元素")
            if (snapshot.truncated) append(" · 正文已截断")
        }
        return "页面速览 · 本地\n$heading$body\n\n$stat"
    }

    private fun answerLocally(question: String, snapshot: AgentPageSnapshot): String {
        val sentences = splitSentences(snapshot.text)
        if (sentences.isEmpty()) return "页面正文为空，暂时无法回答。"
        val queryTerms = termsOf(question).toSet()
        if (queryTerms.isEmpty()) {
            return "请把问题说得更具体一些，例如“这个页面的核心结论是什么？”"
        }
        val ranked = sentences.map { sentence ->
            val terms = termsOf(sentence).toSet()
            val overlap = terms.count { it in queryTerms }
            Ranked(sentence, overlap, overlap.toDouble() / sqrt(terms.size.coerceAtLeast(1).toDouble()))
        }.filter { it.overlap > 0 }
            .sortedWith(compareByDescending<Ranked> { it.overlap }.thenByDescending { it.density })
            .take(3)
        if (ranked.isEmpty()) {
            return "页面中没有找到与“${question.take(60)}”直接相关的内容。试试用“查找 关键词”定位一个更短的词。"
        }
        val body = ranked.joinToString("\n") { "• ${it.sentence}" }
        return "根据页面内容：\n$body\n\n本地回答基于关键词匹配，可能不完整。"
    }

    private fun findInPage(query: String, snapshot: AgentPageSnapshot): String {
        if (query.isBlank()) {
            return "用法：输入“查找 关键词”，我会在正文和按钮文字中查找，但不会改动页面。"
        }
        val text = snapshot.text
        val lowerText = text.lowercase()
        val needle = query.lowercase()
        val contexts = mutableListOf<String>()
        var total = 0
        var index = lowerText.indexOf(needle)
        while (index >= 0 && total < 200) {
            total++
            if (contexts.size < 5) {
                val start = (index - 30).coerceAtLeast(0)
                val end = (index + needle.length + 30).coerceAtMost(text.length)
                val context = text.substring(start, end).replace(WHITESPACE, " ").trim()
                contexts += buildString {
                    if (start > 0) append("…")
                    append(context)
                    if (end < text.length) append("…")
                }
            }
            index = lowerText.indexOf(needle, index + needle.length)
        }
        val elementHits = snapshot.elements
            .filter { it.name.contains(query, ignoreCase = true) }
            .take(5)

        if (total == 0 && elementHits.isEmpty()) {
            return "页面中没有找到“$query”。"
        }
        return buildString {
            appendLine("页内查找 · “$query”")
            if (total > 0) {
                append("正文命中 ${total} 处")
                if (total >= 200) append("（仅统计前 200 处）")
                appendLine("：")
                contexts.forEachIndexed { position, context ->
                    appendLine("${position + 1}. $context")
                }
            }
            if (elementHits.isNotEmpty()) {
                if (total > 0) appendLine()
                appendLine("可交互元素：")
                elementHits.forEach { appendLine("• ${it.name}（${it.role}）") }
            }
        }.trim()
    }

    private fun pageInfo(snapshot: AgentPageSnapshot): String {
        return buildString {
            appendLine("页面信息 · 本地")
            if (snapshot.title.isNotBlank()) appendLine("标题：${snapshot.title}")
            val address = displayUrl(snapshot.url)
            if (address.isNotBlank()) appendLine("地址：$address")
            appendLine("正文：${snapshot.text.length} 字${if (snapshot.truncated) "（已截断）" else ""}")
            appendLine(
                "结构：链接 ${snapshot.linkCount} · 按钮 ${snapshot.buttonCount} · " +
                    "表单 ${snapshot.formCount} · 图片 ${snapshot.imageCount}"
            )
            if (snapshot.language.isNotBlank()) appendLine("语言：${snapshot.language}")
            if (snapshot.selection.isNotBlank()) {
                appendLine("已选中：${snapshot.selection.take(120)}")
            }
            if (snapshot.elements.isNotEmpty()) {
                append("可交互：")
                append(snapshot.elements.take(6).joinToString("、") { it.name })
                if (snapshot.elements.size > 6) append(" 等 ${snapshot.elements.size} 个")
            }
        }.trim()
    }

    private fun displayUrl(raw: String): String = runCatching {
        val uri = Uri.parse(raw)
        if (uri.host.isNullOrBlank()) {
            raw.take(300)
        } else {
            buildString {
                append(uri.scheme ?: "https").append("://").append(uri.host)
                val path = uri.path.orEmpty()
                if (path.isNotBlank() && path != "/") append(path.take(120))
            }
        }
    }.getOrDefault(raw.take(300))

    private fun splitSentences(text: String): List<String> {
        return text.split(SENTENCE_SPLIT)
            .map { it.replace(WHITESPACE, " ").trim() }
            .filter { it.length >= 12 }
            .map { if (it.length > 260) it.take(260) + "…" else it }
            .filter { sentence ->
                HAN_CHAR.findAll(sentence).count() >= 8 ||
                    LATIN_WORD.findAll(sentence).count() >= 4
            }
            .take(400)
    }

    private data class Ranked(val sentence: String, val overlap: Int, val density: Double)

    private fun rankSentences(sentences: List<String>): List<Pair<Int, String>> {
        val documentFrequency = HashMap<String, Int>()
        sentences.forEach { sentence ->
            termsOf(sentence).toSet().forEach { term ->
                documentFrequency[term] = (documentFrequency[term] ?: 0) + 1
            }
        }
        return sentences.mapIndexed { index, sentence ->
            val unique = termsOf(sentence).toSet()
            val frequency = unique.sumOf { (documentFrequency[it] ?: 0).toDouble() }
            val density = if (unique.isEmpty()) 0.0 else frequency / sqrt(unique.size.toDouble())
            val positionBoost = (2.4 - index * 0.16).coerceAtLeast(0.0)
            val lengthBoost = 1.0 + minOf(sentence.length, 120) / 240.0
            Triple(index, sentence, (density + positionBoost) * lengthBoost)
        }.sortedByDescending { it.third }
            .take(5)
            .sortedBy { it.first }
            .map { it.first to it.second }
    }

    private fun termsOf(text: String): List<String> {
        val lower = text.lowercase()
        val latin = LATIN_WORD.findAll(lower).map { it.value }.filter { it.length in 2..24 }
        val han = HAN_CHAR.findAll(lower).map { it.value }.toList()
        val bigrams = han.windowed(2, 1).map { it[0] + it[1] }
        return (latin.toList() + bigrams).filter { it !in STOP_TERMS }
    }

    private object PrivacyScrubber {

        private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        private val MOBILE = Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)")
        private val ID_CARD = Regex("(?<!\\d)\\d{17}[\\dXx](?!\\d)")
        private val LONG_DIGITS = Regex("(?<!\\d)\\d{12,}(?!\\d)")
        private val BEARER = Regex("(?i)(bearer\\s+)[A-Za-z0-9._-]{8,}")
        private val SECRET_FIELD =
            Regex("(?i)(api[_-]?key|token|password|secret|authorization)\\s*[:=]\\s*\\S+")

        fun scrub(text: String): String = text
            .replace(EMAIL, "[邮箱已隐藏]")
            .replace(MOBILE, "[手机号已隐藏]")
            .replace(ID_CARD, "[证件号已隐藏]")
            .replace(LONG_DIGITS, "[数字已隐藏]")
            .replace(BEARER, "${'$'}1[凭证已隐藏]")
            .replace(SECRET_FIELD, "[凭证已隐藏]")

        fun safeUrl(raw: String): String = runCatching {
            val uri = Uri.parse(raw)
            if (uri.host.isNullOrBlank()) "" else "${uri.scheme ?: "https"}://${uri.host}"
        }.getOrDefault("")
    }

    companion object {
        private const val SNAPSHOT_TIMEOUT_MS = 5_000L
        private const val SNAPSHOT_ATTEMPTS = 3
        private const val SNAPSHOT_RETRY_DELAY_MS = 500L
        private const val MAX_INPUT_CHARS = 500
        private const val MAX_ANSWER_CHARS = 4_000
        private const val MAX_MESSAGES = 60
        private const val CLOUD_TEXT_LIMIT = 8_000
        private const val ACTION_TIMEOUT_MS = 4_000L
        private const val MAX_PLAN_STEPS = 5
        private const val DEFAULT_ENDPOINT = "https://api.openai.com/v1"
        private const val DEFAULT_MODEL = "gpt-4o-mini"

        private val WHITESPACE = Regex("\\s+")
        private val SENTENCE_SPLIT = Regex("[。！？!?；;\\n]+")
        private val LATIN_WORD = Regex("[a-zA-Z0-9]{2,}")
        private val HAN_CHAR = Regex("[\\u4e00-\\u9fa5]")

        private val CLICK_PATTERN = Regex(
            "^(?:请|帮我|替我)?\\s*(?:点击|点开|打开|click|tap)\\s*(.+)$",
            RegexOption.IGNORE_CASE
        )
        private val TYPE_IN_PATTERN = Regex(
            "^(?:请|帮我|替我)?\\s*在\\s*(.+?)\\s*(?:中|里|内)?\\s*(?:输入|填入|填写|键入)\\s*(.+)$"
        )
        private val TYPE_TO_PATTERN = Regex(
            "^(?:请|帮我|替我)?\\s*(?:输入|填入|填写|键入)\\s*(.+?)\\s*(?:到|至|在)\\s*(.+)$"
        )
        private val TYPE_EN_PATTERN = Regex(
            "^(?:type|fill)\\s+(.+?)\\s+(?:into|in)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
        private val SEARCH_PATTERN = Regex(
            "^(?:请|帮我|替我)?\\s*搜索\\s*(.+)$"
        )
        private val PLAN_SEPARATOR = Regex("\\s*(?:然后|接着|随后|；|;|\\n)\\s*")
        private val COMMA_SEPARATOR = Regex("\\s*[，,]\\s*")
        private val RESULT_PATTERN = Regex("第([0-9一二三四五六七八九十]+)个?(?:搜索)?(?:结果|链接|条目)")
        private val CHINESE_NUMBERS = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5,
            "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10
        )

        private val STOP_TERMS = setOf(
            "我们", "你们", "他们", "自己", "这个", "那个", "这些", "那些", "一个",
            "没有", "已经", "可以", "因为", "所以", "但是", "如果", "以及", "并且",
            "或者", "不是", "就是", "进行", "通过", "使用", "对于", "关于", "什么",
            "怎么", "如何", "为什么", "是否", "哪些", "哪个", "请问", "帮我", "告诉",
            "一下", "页面", "文章", "内容",
            "the", "and", "for", "with", "that", "this", "from", "are", "was", "were",
            "you", "your", "have", "has", "will", "can", "not", "but"
        )

        private val SYSTEM_PROMPT = """
            你是琉璃浏览器内置的只读页面助手。只依据用户提供的页面正文回答，
            不要编造正文中没有的信息；如果正文不足以回答，请明确说明。
            使用简体中文，回答简洁，必要时使用短列表。不要执行任何页面操作。
        """.trimIndent()
    }
}
