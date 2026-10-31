package com.liuli.browser.web.agent

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** P4.3 创作类型：基于当前页面素材的写作、改写、大纲与分享卡片。 */
enum class AgentCreationKind(val label: String) {
    Write("写作"),
    Rewrite("改写"),
    Outline("大纲"),
    Card("分享卡片")
}

/** 创作产物的导出格式；全部在本机生成，不上传任何内容。 */
enum class AgentCreationFormat(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    Markdown("Markdown", "md", "text/markdown"),
    Html("HTML", "html", "text/html"),
    Text("纯文本", "txt", "text/plain")
}

/** 创作语气；本机模式只做可解释的删改，云端模式才真正改写语气。 */
enum class AgentTone(val label: String) {
    Neutral("中性"),
    Concise("简洁"),
    Formal("正式"),
    Casual("口语"),
    Detailed("详细")
}

/**
 * 创作产物：只保存在当前会话内存中，退出或清除对话即删除。
 * 保存位置由用户通过系统文件选择器决定，Agent 不主动落盘。
 */
data class AgentCreation(
    val kind: AgentCreationKind,
    val title: String,
    val body: String,
    val cardText: String,
    val sourceUrl: String,
    val sourceTitle: String,
    val createdAt: Long,
    val mode: AgentMode,
    val note: String? = null
) {
    fun fileName(format: AgentCreationFormat = AgentCreationFormat.Markdown): String =
        CreationEngine.fileName(title, format)
}

/** 一次创作请求：来自用户指令、当前页面快照与选中文本。 */
data class CreationRequest(
    val kind: AgentCreationKind,
    val goal: String,
    val tone: AgentTone,
    val sourceText: String,
    val pageTitle: String,
    val pageUrl: String,
    val pageText: String
) {
    val points: List<String>
        get() = CreationEngine.extractPoints(pageText, CreationEngine.MAX_POINTS)

    fun displayTitle(): String {
        val page = pageTitle.trim()
        return when (kind) {
            AgentCreationKind.Rewrite -> "改写稿"
            AgentCreationKind.Outline -> page.ifBlank { "内容大纲" }
            AgentCreationKind.Card -> page.ifBlank { "分享卡片" }
            AgentCreationKind.Write -> page.ifBlank { "写作稿" }
        }.take(80)
    }

    companion object {
        private val CARD_MARKERS = listOf(
            "分享卡片", "分享卡", "做一张卡", "做张卡", "卡片", "海报", "朋友圈",
            "分享文案", "配图文案"
        )
        private val OUTLINE_MARKERS = listOf(
            "大纲", "提纲", "框架", "思维导图", "目录结构", "结构化", "层级"
        )
        private val REWRITE_MARKERS = listOf(
            "改写", "润色", "换个说法", "换种说法", "精简", "缩写", "优化表述",
            "优化措辞", "改得更好", "改得更", "美化文字"
        )
        private val WRITE_MARKERS = listOf(
            "写一篇", "写一段", "写一些", "写个", "帮我写", "生成文章", "生成一段",
            "拟一篇", "撰写", "写文案", "起草", "草稿", "写成", "整理成文章"
        )

        /**
         * 从用户指令里判断创作类型与素材。不是创作指令时返回 null，交给原有问答流程。
         */
        fun parse(input: String, snapshot: AgentPageSnapshot, pageTitle: String): CreationRequest? {
            val kind = when {
                CARD_MARKERS.any { input.contains(it) } -> AgentCreationKind.Card
                OUTLINE_MARKERS.any { input.contains(it) } -> AgentCreationKind.Outline
                REWRITE_MARKERS.any { input.contains(it) } -> AgentCreationKind.Rewrite
                WRITE_MARKERS.any { input.contains(it) } -> AgentCreationKind.Write
                else -> return null
            }
            val tone = when {
                input.contains("简洁") || input.contains("精简") || input.contains("简短") ||
                    input.contains("短一点") -> AgentTone.Concise
                input.contains("正式") || input.contains("书面") || input.contains("专业") ||
                    input.contains("严肃") -> AgentTone.Formal
                input.contains("口语") || input.contains("轻松") || input.contains("活泼") ||
                    input.contains("接地气") -> AgentTone.Casual
                input.contains("详细") || input.contains("展开") || input.contains("长一点") ->
                    AgentTone.Detailed
                else -> AgentTone.Neutral
            }
            val explicit = sequenceOf("改写：", "改写:", "润色：", "润色:", "原文：", "原文:")
                .map { input.substringAfter(it, "") }
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                .orEmpty()
            val sourceText = when {
                explicit.isNotBlank() -> explicit
                snapshot.selection.isNotBlank() -> snapshot.selection.trim()
                input.contains("页面") -> snapshot.text.take(2_000)
                else -> ""
            }
            return CreationRequest(
                kind = kind,
                goal = input.trim(),
                tone = tone,
                sourceText = sourceText,
                pageTitle = snapshot.title.ifBlank { pageTitle },
                pageUrl = snapshot.url,
                pageText = snapshot.text
            )
        }
    }
}

/**
 * P4.3 本机创作引擎。所有输出都由页面已有句子拼装，不新增事实、不调用网络；
 * 语气改写只做可解释的删词与替换，避免本机"假装"生成了新观点。
 */
object CreationEngine {

    const val MAX_BODY_CHARS = 6_000
    const val MAX_POINTS = 10

    private const val MAX_POINT_CHARS = 180
    private const val MAX_SHORT_POINTS = 3
    private const val MAX_LONG_POINTS = MAX_POINTS
    private const val MIN_POINT_CHARS = 16
    private const val MAX_CARD_CHARS = 60

    private val WHITESPACE = Regex("\\s+")
    private val SENTENCE_SPLIT = Regex("[。！？!?；;\\n]+")
    private val CODE_FENCE = Regex("```[\\s\\S]*?```")
    private val HTML_TAG = Regex("<[^>]+>")
    private val HEADING = Regex("^#{1,6}\\s*", RegexOption.MULTILINE)
    private val BULLET = Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE)
    private val ORDERED = Regex("^\\s*\\d{1,2}\\.\\s+", RegexOption.MULTILINE)
    private val FILE_ILLEGAL = Regex("[\\\\/:*?\"<>|\\s]+")
    private val LATIN_WORD = Regex("[a-zA-Z0-9]{2,}")
    private val HAN_CHAR = Regex("[\\u4e00-\\u9fa5]")
    private val LEADING_CLAUSE = Regex("[，,、：:；;。！!？?]")

    private val FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    private val STOP_TERMS = setOf(
        "我们", "你们", "他们", "这个", "那个", "这些", "那些", "一个", "没有",
        "已经", "可以", "因为", "所以", "但是", "如果", "以及", "并且", "或者",
        "进行", "通过", "使用", "对于", "关于", "什么", "怎么", "如何", "为什么",
        "是否", "哪些", "请问", "帮我", "告诉", "一下", "页面", "本站",
        "the", "and", "for", "with", "that", "this", "from", "are", "was", "were",
        "you", "your", "have", "has", "will", "can", "not", "but"
    )

    /** 口语词与书面词的最小替换表；本机改写只做这些确定性替换。 */
    private val TO_FORMAL = mapOf(
        "然后" to "随后",
        "所以" to "因此",
        "挺" to "较为",
        "特别特别" to "尤其",
        "特别" to "尤其",
        "搞" to "开展",
        "弄" to "完成",
        " lots of" to "大量",
        "差不多" to "大致",
        " basically" to "基本上"
    )

    private val TO_CASUAL = mapOf(
        "因此" to "所以",
        "随后" to "然后",
        "此外" to "另外",
        "综上" to "总的来说",
        "该页面" to "这个页面"
    )

    private val FILLER_WORDS = listOf(
        "其实", "基本上", "真的", "非常非常", "我觉得", "说实话", "怎么说呢",
        "然后就是", "反正", "大家", "的话"
    )

    private val CASUAL_TAILS = listOf("吧", "呢", "啊", "哦", "嗯", "哈")

    // ---------------------------------------------------------------------
    // 素材抽取
    // ---------------------------------------------------------------------

    /** 从正文里挑出可复用的句子，按出现顺序返回。 */
    fun extractPoints(text: String, limit: Int): List<String> {
        val sentences = text.split(SENTENCE_SPLIT)
            .map { it.replace(WHITESPACE, " ").trim() }
            .filter { it.length >= MIN_POINT_CHARS }
            .filter { sentence ->
                HAN_CHAR.findAll(sentence).count() >= 8 ||
                    LATIN_WORD.findAll(sentence).count() >= 4
            }
            .map { if (it.length > MAX_POINT_CHARS) it.take(MAX_POINT_CHARS - 1) + "…" else it }
        if (sentences.size <= limit) return sentences

        val frequency = HashMap<String, Int>()
        sentences.forEach { sentence ->
            termsOf(sentence).toSet().forEach { term ->
                frequency[term] = (frequency[term] ?: 0) + 1
            }
        }
        return sentences.mapIndexed { index, sentence ->
            val unique = termsOf(sentence).toSet()
            val score = unique.sumOf { (frequency[it] ?: 0).toDouble() }
            val position = (2.4 - index * 0.14).coerceAtLeast(0.0)
            Triple(index, score + position, sentence)
        }.sortedByDescending { it.second }
            .take(limit)
            .sortedBy { it.first }
            .map { it.third }
    }

    // ---------------------------------------------------------------------
    // 本机生成
    // ---------------------------------------------------------------------

    /** 本机创作：只拼装页面已有句子，不新增事实。 */
    fun compose(request: CreationRequest): AgentCreation {
        val title = request.displayTitle()
        val points = request.points
        val body = when (request.kind) {
            AgentCreationKind.Write -> writeBody(request, title, points)
            AgentCreationKind.Rewrite -> rewriteBody(request)
            AgentCreationKind.Outline -> outlineBody(request, title, points)
            AgentCreationKind.Card -> cardBody(request, title, points)
        }
        return AgentCreation(
            kind = request.kind,
            title = title,
            body = body,
            cardText = cardText(request, title, points),
            sourceUrl = request.pageUrl,
            sourceTitle = request.pageTitle.trim().ifBlank { title },
            createdAt = System.currentTimeMillis(),
            mode = AgentMode.Local,
            note = "本机整理：内容只来自页面已有句子，没有新增事实。"
        )
    }

    private fun writeBody(
        request: CreationRequest,
        title: String,
        points: List<String>
    ): String {
        if (points.isEmpty()) {
            return buildString {
                appendLine("# $title")
                appendLine()
                appendLine("页面正文为空或尚未加载完成，暂时没有可整理的素材。")
            }
        }
        val limit = when (request.tone) {
            AgentTone.Concise -> MAX_SHORT_POINTS
            AgentTone.Detailed -> MAX_LONG_POINTS
            else -> 6
        }
        val picked = points.take(limit)
        return buildString {
            appendLine("# $title")
            appendLine()
            appendLine("以下文字整理自《${request.pageTitle.trim().ifBlank { title }}》，只保留页面已有信息。")
            appendLine()
            picked.forEach { point ->
                appendLine(point.trimEnd('。', '！', '？', '.', '!', '?') + "。")
                appendLine()
            }
            append("原文：${hostOf(request.pageUrl) ?: request.pageUrl}")
        }
    }

    private fun rewriteBody(request: CreationRequest): String {
        val source = request.sourceText.trim()
        if (source.isEmpty()) return "没有可改写的原文。"
        val sentences = splitSentences(source)
        if (sentences.isEmpty()) return source
        val rewritten = when (request.tone) {
            AgentTone.Concise -> concise(sentences)
            AgentTone.Formal -> formal(sentences)
            AgentTone.Casual -> casual(sentences)
            AgentTone.Detailed -> detailed(sentences)
            AgentTone.Neutral -> sentences
        }
        return rewritten.joinToString("\n\n") { it.trim() }
    }

    private fun outlineBody(
        request: CreationRequest,
        title: String,
        points: List<String>
    ): String {
        if (points.isEmpty()) {
            return buildString {
                appendLine("# $title")
                appendLine()
                appendLine("页面正文为空或尚未加载完成，暂时没有可整理的要点。")
            }
        }
        val groups = points.chunked(if (points.size <= 4) 2 else 3)
        return buildString {
            appendLine("# $title")
            appendLine()
            groups.forEachIndexed { index, group ->
                appendLine("## ${groupLabel(group, index)}")
                group.forEach { point ->
                    appendLine("- ${point.trimEnd('。', '！', '？', '.', '!', '?')}")
                }
                appendLine()
            }
            append("素材来源：${hostOf(request.pageUrl) ?: request.pageUrl}")
        }
    }

    private fun cardBody(
        request: CreationRequest,
        title: String,
        points: List<String>
    ): String {
        val summary = points.firstOrNull()?.take(MAX_CARD_CHARS) ?: "页面正文为空。"
        return buildString {
            appendLine("# $title")
            appendLine()
            appendLine(summary.trimEnd('。', '！', '？', '.', '!', '?') + "。")
            appendLine()
            append(hostOf(request.pageUrl) ?: request.pageUrl)
        }
    }

    private fun cardText(
        request: CreationRequest,
        title: String,
        points: List<String>
    ): String {
        val summary = points.firstOrNull()?.take(MAX_CARD_CHARS)
            ?: request.pageTitle.trim().take(MAX_CARD_CHARS)
        val host = hostOf(request.pageUrl) ?: return summary
        return "$summary —— $host"
    }

    // ---------------------------------------------------------------------
    // 语气处理
    // ---------------------------------------------------------------------

    private fun concise(sentences: List<String>): List<String> {
        val kept = sentences.take(MAX_SHORT_POINTS)
        return kept.map { sentence ->
            var text = sentence
            FILLER_WORDS.forEach { text = text.replace(it, "") }
            text.replace(WHITESPACE, " ").trim()
        }.filter { it.isNotBlank() }
    }

    private fun formal(sentences: List<String>): List<String> {
        return sentences.map { sentence ->
            var text = sentence
            TO_FORMAL.forEach { (from, to) -> text = text.replace(from, to) }
            CASUAL_TAILS.forEach { tail -> text = text.replace("$tail ", " ") }
            text.replace(WHITESPACE, " ").trim()
        }.filter { it.isNotBlank() }
    }

    private fun casual(sentences: List<String>): List<String> {
        return sentences.map { sentence ->
            var text = sentence
            TO_CASUAL.forEach { (from, to) -> text = text.replace(from, to) }
            text.replace(WHITESPACE, " ").trim()
        }.filter { it.isNotBlank() }
    }

    private fun detailed(sentences: List<String>): List<String> {
        val lead = sentences.firstOrNull()?.take(MAX_POINT_CHARS)
        val rest = sentences.drop(1)
        return buildList {
            lead?.let { add(it.trim()) }
            addAll(rest)
        }.filter { it.isNotBlank() }
    }

    // ---------------------------------------------------------------------
    // 导出
    // ---------------------------------------------------------------------

    fun render(creation: AgentCreation, format: AgentCreationFormat): ByteArray = when (format) {
        AgentCreationFormat.Markdown -> creation.body.toByteArray(Charsets.UTF_8)
        AgentCreationFormat.Html -> toHtml(creation).toByteArray(Charsets.UTF_8)
        AgentCreationFormat.Text -> plainText(creation).toByteArray(Charsets.UTF_8)
    }

    /** 自包含 HTML：内联样式 + CSP，禁止任何外部资源。 */
    fun toHtml(creation: AgentCreation): String {
        val title = escapeHtml(creation.title.take(80))
        return buildString {
            appendLine("<!doctype html>")
            appendLine("<html lang=\"zh-CN\">")
            appendLine("<head>")
            appendLine("<meta charset=\"utf-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            appendLine(
                "<meta http-equiv=\"Content-Security-Policy\" " +
                    "content=\"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'\">"
            )
            appendLine("<title>$title</title>")
            appendLine("<style>")
            appendLine(HTML_STYLE)
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("<main>")
            append(markdownToHtml(creation.body))
            appendLine("</main>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }

    /** 纯文本：去掉 Markdown 标记，便于直接分享或朗读。 */
    fun plainText(creation: AgentCreation): String = plainText(creation.body)

    /** 纯文本：去掉 Markdown 标记，便于直接分享或朗读。 */
    fun plainText(body: String): String {
        val text = body
            .replace(CODE_FENCE, "")
            .replace(HEADING, "")
            .replace(BULLET, "· ")
            .replace(ORDERED, "· ")
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        return text
    }

    fun fileName(title: String, format: AgentCreationFormat): String {
        val safe = title.replace(FILE_ILLEGAL, "_").trim('_').take(40)
            .ifBlank { "创作产物" }
        val stamp = LocalDateTime.now().format(FILE_TIME)
        return "琉璃-$safe-$stamp.${format.extension}"
    }

    /** 清理模型输出：去代码块、去 HTML 标签、限长。 */
    fun sanitize(raw: String): String {
        val text = raw.trim()
            .replace(CODE_FENCE, "")
            .replace(HTML_TAG, "")
            .replace(Regex("[ \\t]+"), " ")
            .trim()
        return text.take(MAX_BODY_CHARS)
    }

    // ---------------------------------------------------------------------
    // 工具方法
    // ---------------------------------------------------------------------

    private fun splitSentences(text: String): List<String> =
        text.split(SENTENCE_SPLIT)
            .map { it.replace(WHITESPACE, " ").trim() }
            .filter { it.isNotEmpty() }

    private fun groupLabel(group: List<String>, index: Int): String {
        val head = group.firstOrNull().orEmpty()
        val clause = LEADING_CLAUSE.split(head).firstOrNull()?.trim().orEmpty()
        val label = clause.ifBlank { head }.take(16)
        return if (label.length >= 4) label else "要点 ${index + 1}"
    }

    private fun termsOf(sentence: String): List<String> =
        sentence.split(Regex("[^\\u4e00-\\u9fa5a-zA-Z0-9]+"))
            .map { it.lowercase() }
            .filter { it.length >= 2 && it !in STOP_TERMS }

    private fun hostOf(url: String): String? = runCatching {
        java.net.URI(url.trim()).host?.lowercase()
    }.getOrNull()?.ifBlank { null }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun markdownToHtml(markdown: String): String {
        val out = StringBuilder()
        var listTag: String? = null
        fun closeList() {
            val open = listTag ?: return
            out.appendLine("</$open>")
            listTag = null
        }
        markdown.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> closeList()
                line.startsWith("# ") -> {
                    closeList()
                    out.appendLine("<h1>${escapeHtml(line.removePrefix("# "))}</h1>")
                }
                line.startsWith("## ") -> {
                    closeList()
                    out.appendLine("<h2>${escapeHtml(line.removePrefix("## "))}</h2>")
                }
                line.startsWith("### ") -> {
                    closeList()
                    out.appendLine("<h3>${escapeHtml(line.removePrefix("### "))}</h3>")
                }
                ORDERED.containsMatchIn(line) -> {
                    if (listTag != "ol") {
                        closeList()
                        out.appendLine("<ol>")
                        listTag = "ol"
                    }
                    out.appendLine("<li>${escapeHtml(line.replace(ORDERED, ""))}</li>")
                }
                BULLET.containsMatchIn(line) -> {
                    if (listTag != "ul") {
                        closeList()
                        out.appendLine("<ul>")
                        listTag = "ul"
                    }
                    out.appendLine("<li>${escapeHtml(line.replace(BULLET, ""))}</li>")
                }
                else -> {
                    closeList()
                    out.appendLine("<p>${escapeHtml(line)}</p>")
                }
            }
        }
        closeList()
        return out.toString()
    }

    private const val HTML_STYLE = """
        :root { color-scheme: light dark; }
        body { margin: 0; padding: 24px; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Noto Sans CJK SC", sans-serif; line-height: 1.8; color: #1d1d1f; background: #f7f7fa; }
        main { max-width: 720px; margin: 0 auto; background: #ffffff; border-radius: 16px; padding: 28px 30px; box-shadow: 0 8px 30px rgba(0, 0, 0, 0.06); }
        h1 { font-size: 24px; line-height: 1.35; margin: 0 0 14px; }
        h2 { font-size: 17px; margin: 24px 0 10px; }
        h3 { font-size: 15px; margin: 18px 0 8px; }
        p, li { font-size: 14px; }
        ul, ol { padding-left: 22px; }
        @media (prefers-color-scheme: dark) {
            body { color: #f5f5f7; background: #111114; }
            main { background: #1c1c1e; box-shadow: none; }
        }
    """.trimIndent()
}
