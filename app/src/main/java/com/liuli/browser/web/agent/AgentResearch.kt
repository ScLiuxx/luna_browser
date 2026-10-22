package com.liuli.browser.web.agent

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.webkit.WebView
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

/**
 * A loaded tab that a read-only research request may read. Only tabs that
 * already own a WebView can be collected; the Agent never creates background
 * pages or navigates on its own while collecting.
 */
class AgentResearchPage(
    val tabId: Long,
    val isPrivate: Boolean,
    val webView: WebView
)

/** 来源台账中的一条记录，只保留引用所需的最小信息。 */
data class AgentSource(
    val id: Int,
    val title: String,
    val url: String,
    val host: String,
    val isPrivate: Boolean,
    val accessedAt: Long,
    val charCount: Int,
    val keyPoints: List<String>
)

/** 报告导出格式；HTML 与 CSV 完全在本机生成。 */
enum class AgentReportFormat(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    Markdown("Markdown", "md", "text/markdown"),
    Html("HTML", "html", "text/html"),
    Pdf("PDF", "pdf", "application/pdf"),
    Csv("CSV", "csv", "text/csv")
}

/** 本机启发式来源评估；不是事实核查，只提示协议、域名与篇幅特征。 */
data class SourceCredibility(
    val score: Int,
    val label: String,
    val notes: List<String>
)

/** 内存中的调研报告；保存位置由用户通过系统文件选择器决定。 */
data class AgentReport(
    val goal: String,
    val markdown: String,
    val sources: List<AgentSource>,
    val createdAt: Long,
    val mode: AgentMode,
    val note: String? = null
) {
    fun fileName(format: AgentReportFormat = AgentReportFormat.Markdown): String =
        ResearchEngine.fileName(goal, format)
}

/**
 * P4.1 local research helpers. Everything here is deterministic and works on
 * already-collected source excerpts, so reports can always be produced without
 * a cloud model. Every generated statement carries a source number.
 */
object ResearchEngine {

    const val MAX_SOURCES = 6
    const val MAX_REPORT_CHARS = 8_000

    private const val MAX_POINTS_PER_SOURCE = 3
    private const val MAX_SENTENCES = 400
    private const val MAX_POINT_CHARS = 220

    private val WHITESPACE = Regex("\\s+")
    private val SENTENCE_SPLIT = Regex("[。！？!?；;\\n]+")
    private val LATIN_WORD = Regex("[a-zA-Z0-9]{2,}")
    private val HAN_CHAR = Regex("[\\u4e00-\\u9fa5]")
    private val CITATION = Regex("\\[(\\d{1,2})\\]")
    private val FILE_ILLEGAL = Regex("[\\\\/:*?\"<>|\\s]+")
    private val ORDERED_ITEM = Regex("^\\d{1,2}\\.\\s+")
    private val LINK = Regex("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)")
    private val BOLD = Regex("\\*\\*([^*]+)\\*\\*")
    private val IP_HOST = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")

    private const val PDF_WIDTH = 595
    private const val PDF_HEIGHT = 842
    private const val PDF_MARGIN = 44f

    private val HTML_STYLE = """
        :root { color-scheme: light dark; }
        body { margin: 0; padding: 24px; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Noto Sans CJK SC", sans-serif; line-height: 1.75; color: #1d1d1f; background: #f7f7fa; }
        main { max-width: 760px; margin: 0 auto; background: #ffffff; border-radius: 16px; padding: 28px 30px; box-shadow: 0 8px 30px rgba(0, 0, 0, 0.06); }
        h1 { font-size: 26px; line-height: 1.3; margin: 0 0 12px; }
        h2 { font-size: 18px; margin: 26px 0 10px; }
        h3 { font-size: 15px; margin: 18px 0 8px; }
        p, li { font-size: 14px; }
        blockquote { margin: 0 0 8px; padding: 8px 14px; border-left: 3px solid #8e8e93; color: #636366; background: #f2f2f7; border-radius: 0 8px 8px 0; }
        ul, ol { padding-left: 22px; }
        a { color: #0a84ff; word-break: break-all; }
        @media (prefers-color-scheme: dark) {
            body { color: #f5f5f7; background: #111114; }
            main { background: #1c1c1e; box-shadow: none; }
            blockquote { background: #2c2c2e; color: #aeaeb2; }
            a { color: #409cff; }
        }
    """.trimIndent()

    private val REPORT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
    private val REPORT_ZONE = ZoneId.systemDefault()

    private val STOP_TERMS = setOf(
        "我们", "你们", "他们", "自己", "这个", "那个", "这些", "那些", "一个",
        "没有", "已经", "可以", "因为", "所以", "但是", "如果", "以及", "并且",
        "或者", "不是", "就是", "进行", "通过", "使用", "对于", "关于", "什么",
        "怎么", "如何", "为什么", "是否", "哪些", "哪个", "请问", "帮我", "告诉",
        "一下", "页面", "文章", "内容", "本站", "点击", "查看", "更多",
        "the", "and", "for", "with", "that", "this", "from", "are", "was", "were",
        "you", "your", "have", "has", "will", "can", "not", "but"
    )

    /** 从来源正文中抽取少量可引用的句子，保持原文顺序。 */
    fun keyPoints(text: String): List<String> {
        val sentences = text.split(SENTENCE_SPLIT)
            .map { it.replace(WHITESPACE, " ").trim() }
            .filter { it.length >= 16 }
            .map { if (it.length > MAX_POINT_CHARS) it.take(MAX_POINT_CHARS - 1) + "…" else it }
            .filter { sentence ->
                HAN_CHAR.findAll(sentence).count() >= 8 ||
                    LATIN_WORD.findAll(sentence).count() >= 4
            }
            .take(MAX_SENTENCES)
        if (sentences.size <= MAX_POINTS_PER_SOURCE) return sentences

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
            .take(MAX_POINTS_PER_SOURCE)
            .sortedBy { it.first }
            .map { it.second }
    }

    /**
     * 只做协议、域名类型与篇幅的启发式评估，帮助用户判断“先看哪里”，
     * 不推断内容真伪，也不替代跨来源核对。
     */
    fun assessSource(url: String, title: String, textLength: Int): SourceCredibility {
        var score = 50
        val notes = ArrayList<String>()
        val normalized = url.trim()
        when {
            normalized.startsWith("https://") -> {
                score += 18
                notes += "HTTPS"
            }
            normalized.startsWith("http://") -> {
                score -= 18
                notes += "未使用 HTTPS"
            }
            else -> {
                score -= 10
                notes += "协议异常"
            }
        }
        val host = runCatching { java.net.URI(normalized).host }.getOrNull()
            ?.lowercase()
            .orEmpty()
        when {
            host.endsWith(".gov") || host.endsWith(".gov.cn") -> {
                score += 18
                notes += "政府域名"
            }
            host.endsWith(".edu") || host.endsWith(".edu.cn") ||
                host.endsWith(".ac.cn") -> {
                score += 15
                notes += "教育/科研域名"
            }
            host.endsWith(".org") -> {
                score += 5
                notes += "非营利域名"
            }
        }
        if (IP_HOST.matches(host)) {
            score -= 15
            notes += "直接使用 IP 地址"
        }
        if (host.contains("xn--")) {
            score -= 5
            notes += "国际化域名，注意仿冒"
        }
        when {
            textLength >= 3_000 -> {
                score += 10
                notes += "正文较长"
            }
            textLength >= 800 -> {
                score += 5
                notes += "正文篇幅一般"
            }
            textLength > 0 -> {
                score -= 10
                notes += "正文过短"
            }
            else -> {
                score -= 15
                notes += "未读取到正文"
            }
        }
        if (title.trim().length < 4) {
            score -= 5
            notes += "标题过短"
        }
        val bounded = score.coerceIn(0, 100)
        val label = when {
            bounded >= 75 -> "较高"
            bounded >= 55 -> "中等"
            else -> "偏低"
        }
        return SourceCredibility(bounded, label, notes)
    }

    /** 去掉 Markdown 标记，供语音朗读与 PDF 逐行排版使用。 */
    fun plainText(markdown: String): String = markdown.lineSequence()
        .map { raw ->
            val line = raw.trim()
            val content = when {
                line.startsWith("#") -> line.trimStart('#').trim()
                line.startsWith(">") -> line.trimStart('>').trim()
                line.startsWith("- ") || line.startsWith("* ") -> line.drop(2)
                ORDERED_ITEM.containsMatchIn(line) -> line.replaceFirst(ORDERED_ITEM, "")
                else -> line
            }
            plainInline(content)
        }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .replace(WHITESPACE, " ")
        .trim()

    private fun plainInline(markdown: String): String {
        var value = markdown.replace("**", "")
        value = LINK.replace(value) { it.groupValues[1] }
        return value
    }

    /** 本机报告主体：逐条摘录并标注来源编号，同时给出跨来源比对。 */
    fun localBody(sources: List<AgentSource>): String = buildString {
        val pointCount = sources.sumOf { it.keyPoints.size }
        appendLine("## 摘要")
        appendLine("- 只读整理了 ${sources.size} 个来源，共提取 $pointCount 条可引用摘录。")
        appendLine("- 内容按来源编号摘录原文，未对跨来源说法做事实裁决。")
        appendLine()
        appendLine("## 关键发现")
        sources.forEach { source ->
            if (source.keyPoints.isEmpty()) {
                appendLine("- 来源 [${source.id}] 正文过短或尚未加载完成，未提取到可引用要点。")
            } else {
                source.keyPoints.forEach { point ->
                    appendLine("- $point [${source.id}]")
                }
            }
        }
        appendLine()
        appendLine("## 来源差异")
        differences(sources).forEach { appendLine(it) }
        appendLine()
        appendLine("## 待核实")
        numericClaims(sources).forEach { appendLine(it) }
    }.trim()

    /** 跨来源用词比对：只声明“用词重合”，不推断结论一致。 */
    private fun differences(sources: List<AgentSource>): List<String> {
        if (sources.size < 2) return listOf("- 只有一个来源，无法做跨来源用词比对。")
        val termsBySource = sources.associate { source ->
            source.id to termsOf(source.keyPoints.joinToString(" ")).distinct()
        }
        val sourcesByTerm = HashMap<String, MutableSet<Int>>()
        termsBySource.forEach { (id, terms) ->
            terms.forEach { term ->
                sourcesByTerm.getOrPut(term) { HashSet() }.add(id)
            }
        }
        val shared = sourcesByTerm.entries
            .filter { it.value.size >= 2 }
            .sortedWith(
                compareByDescending<Map.Entry<String, MutableSet<Int>>> { it.value.size }
                    .thenBy { it.key }
            )
            .take(6)
            .map { it.key }
        val lines = ArrayList<String>()
        if (shared.isEmpty()) {
            lines += "- 未发现明显的跨来源共同用词。"
        } else {
            lines += "- 多来源共同出现的词：${shared.joinToString("、")}（仅表示用词重合，不代表结论一致）。"
        }
        termsBySource.forEach { (id, terms) ->
            val unique = terms.filter { sourcesByTerm[it]?.size == 1 }.take(3)
            if (unique.isNotEmpty()) {
                lines += "- 仅来源 [$id] 出现：${unique.joinToString("、")}。"
            }
        }
        return lines
    }

    /** 列出含数字的摘录，提醒用户核对数字、日期与价格。 */
    private fun numericClaims(sources: List<AgentSource>): List<String> {
        val claims = ArrayList<String>()
        for (source in sources) {
            for (point in source.keyPoints) {
                if (claims.size >= 6) break
                if (point.any { it.isDigit() }) {
                    claims += "- [${source.id}] ${point.take(MAX_POINT_CHARS)}"
                }
            }
        }
        if (claims.isEmpty()) {
            return listOf("- 未自动识别到明显数字型断言；仍建议打开来源核对原文。")
        }
        return claims
    }

    /** 统一拼装标题、来源台账与核对提示，模型只能提供中间正文。 */
    fun compose(
        goal: String,
        body: String,
        sources: List<AgentSource>,
        mode: AgentMode,
        note: String? = null
    ): String {
        val title = goal.replace(WHITESPACE, " ").trim().ifBlank { "网页调研" }.take(80)
        val now = formatTime(System.currentTimeMillis())
        return buildString {
            appendLine("# $title")
            appendLine()
            appendLine("> ${mode.label}整理 · $now · ${sources.size} 个来源")
            if (!note.isNullOrBlank()) appendLine("> $note")
            appendLine()
            append(body.trim())
            appendLine()
            appendLine()
            appendLine("## 来源")
            sources.forEach { source ->
                appendLine("${source.id}. [${escapeLabel(source.title.ifBlank { source.host })}](${source.url})")
                appendLine(
                    "   ${source.host} · 访问于 ${formatTime(source.accessedAt)} · " +
                        "${source.charCount} 字"
                )
            }
            appendLine()
            appendLine("## 核对建议")
            appendLine("- 报告只整理来源原文，不替用户做事实裁决；数字、日期、价格与结论请打开来源核对。")
            appendLine("- 正文中的 [n] 对应上方来源编号。")
        }.trim().take(MAX_REPORT_CHARS)
    }

    /**
     * 云端正文必须带来源编号，且不得超出本次台账范围或自行添加链接。
     */
    fun citationsValid(body: String, sourceCount: Int): Boolean {
        if (sourceCount <= 0) return false
        val numbers = CITATION.findAll(body)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toList()
        if (numbers.size < 3) return false
        if (numbers.any { it !in 1..sourceCount }) return false
        if (sourceCount >= 2 && numbers.toSet().size < 2) return false
        if (body.contains("http://") || body.contains("https://")) return false
        return true
    }

    /** 按指定格式渲染报告；全部在本机完成，不访问网络。 */
    fun render(report: AgentReport, format: AgentReportFormat): ByteArray = when (format) {
        AgentReportFormat.Markdown -> report.markdown.toByteArray(Charsets.UTF_8)
        AgentReportFormat.Html -> toHtml(report).toByteArray(Charsets.UTF_8)
        AgentReportFormat.Pdf -> toPdf(report)
        AgentReportFormat.Csv -> toCsv(report.sources).toByteArray(Charsets.UTF_8)
    }

    /**
     * 本机 PDF 排版：A4 纵向、纯文本分页，使用系统 PdfDocument，
     * 不加载外部图片、不执行脚本，也不上传任何内容。
     */
    private fun toPdf(report: AgentReport): ByteArray {
        val document = PdfDocument()
        val contentWidth = (PDF_WIDTH - PDF_MARGIN * 2).toInt()
        val body = TextPaint().apply {
            isAntiAlias = true
            color = Color.rgb(29, 29, 31)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val heading = TextPaint(body).apply {
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subheading = TextPaint(body).apply {
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val quote = TextPaint(body).apply {
            color = Color.rgb(110, 110, 115)
        }
        val footer = TextPaint(body).apply {
            textSize = 8f
            color = Color.rgb(142, 142, 147)
        }

        var pageNumber = 1
        var page: PdfDocument.Page = document.startPage(
            PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, pageNumber).create()
        )
        var canvas = page.canvas
        var y = PDF_MARGIN

        fun finishCurrentPage() {
            canvas.drawText(
                "第 $pageNumber 页 · 琉璃浏览器 · 本机生成",
                PDF_MARGIN,
                PDF_HEIGHT - PDF_MARGIN / 2,
                footer
            )
            document.finishPage(page)
        }

        fun nextPage() {
            finishCurrentPage()
            pageNumber++
            page = document.startPage(
                PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, pageNumber).create()
            )
            canvas = page.canvas
            y = PDF_MARGIN
        }

        fun draw(text: String, paint: TextPaint, before: Float, after: Float) {
            if (text.isBlank()) {
                y += after
                return
            }
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.4f)
                .setIncludePad(false)
                .build()
            val height = layout.height.toFloat()
            if (y + before + height > PDF_HEIGHT - PDF_MARGIN && y > PDF_MARGIN) {
                nextPage()
            }
            y += before
            canvas.save()
            canvas.translate(PDF_MARGIN, y)
            layout.draw(canvas)
            canvas.restore()
            y += height + after
        }

        report.markdown.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> y += 5f
                line.startsWith("# ") -> draw(line.removePrefix("# "), heading, 2f, 10f)
                line.startsWith("## ") -> draw(line.removePrefix("## "), subheading, 10f, 5f)
                line.startsWith("### ") -> draw(line.removePrefix("### "), subheading, 6f, 4f)
                line.startsWith("> ") -> draw(line.removePrefix("> "), quote, 1f, 3f)
                line.startsWith("- ") || line.startsWith("* ") ->
                    draw("• " + line.drop(2), body, 0f, 2f)
                else -> draw(line, body, 1f, 2f)
            }
        }
        finishCurrentPage()
        val output = ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    /** 自包含 HTML：内联样式、CSP 禁止外部资源，不执行脚本。 */
    private fun toHtml(report: AgentReport): String {
        val title = escapeHtml(report.goal.take(80).ifBlank { "网页调研" })
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
            append(markdownToHtml(report.markdown))
            appendLine("</main>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }

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
            val ordered = ORDERED_ITEM.find(line)
            when {
                line.isBlank() -> closeList()
                line.startsWith("# ") -> {
                    closeList()
                    out.appendLine("<h1>${inlineHtml(line.removePrefix("# "))}</h1>")
                }
                line.startsWith("## ") -> {
                    closeList()
                    out.appendLine("<h2>${inlineHtml(line.removePrefix("## "))}</h2>")
                }
                line.startsWith("### ") -> {
                    closeList()
                    out.appendLine("<h3>${inlineHtml(line.removePrefix("### "))}</h3>")
                }
                line.startsWith("> ") -> {
                    closeList()
                    out.appendLine("<blockquote>${inlineHtml(line.removePrefix("> "))}</blockquote>")
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    if (listTag != "ul") {
                        closeList()
                        out.appendLine("<ul>")
                        listTag = "ul"
                    }
                    out.appendLine("<li>${inlineHtml(line.drop(2))}</li>")
                }
                ordered != null -> {
                    if (listTag != "ol") {
                        closeList()
                        out.appendLine("<ol>")
                        listTag = "ol"
                    }
                    out.appendLine("<li>${inlineHtml(line.substring(ordered.range.last + 1))}</li>")
                }
                else -> {
                    closeList()
                    out.appendLine("<p>${inlineHtml(line.trim())}</p>")
                }
            }
        }
        closeList()
        return out.toString()
    }

    private fun inlineHtml(markdown: String): String {
        var html = escapeHtml(markdown)
        html = LINK.replace(html) { match ->
            val label = match.groupValues[1]
            val href = match.groupValues[2]
            // 只允许 http(s) 链接，避免页面文本注入 javascript: 等协议。
            if (href.startsWith("http://") || href.startsWith("https://")) {
                "<a href=\"$href\" rel=\"noreferrer noopener\">$label</a>"
            } else {
                match.value
            }
        }
        html = BOLD.replace(html) { "<strong>${it.groupValues[1]}</strong>" }
        return html
    }

    private fun escapeHtml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** 来源台账 CSV，带 UTF-8 BOM 以便表格软件识别中文。 */
    private fun toCsv(sources: List<AgentSource>): String {
        val out = StringBuilder("\uFEFF")
        out.appendLine("id,title,url,host,private,accessedAt,charCount,keyPoints")
        sources.forEach { source ->
            val cells = listOf(
                source.id.toString(),
                source.title,
                source.url,
                source.host,
                if (source.isPrivate) "1" else "0",
                formatTime(source.accessedAt),
                source.charCount.toString(),
                source.keyPoints.joinToString(" | ")
            )
            out.appendLine(cells.joinToString(",") { csvCell(it) })
        }
        return out.toString()
    }

    private fun csvCell(raw: String): String {
        val value = raw.replace("\r", " ").replace("\n", " ")
        // 防止表格软件把页面标题当作公式执行。
        val first = value.firstOrNull()
        val guarded = if (first == '=' || first == '+' || first == '-' ||
            first == '@' || first == '\t'
        ) {
            "'$value"
        } else {
            value
        }
        return if (guarded.contains(',') || guarded.contains('"')) {
            "\"${guarded.replace("\"", "\"\"")}\""
        } else {
            guarded
        }
    }

    fun fileName(
        goal: String,
        format: AgentReportFormat = AgentReportFormat.Markdown
    ): String {
        val stamp = LocalDateTime.now().format(FILE_TIME)
        val slug = goal.replace(FILE_ILLEGAL, "-").trim('-').take(40).ifBlank { "report" }
        return "liuli-$stamp-$slug.${format.extension}"
    }

    fun formatTime(timestamp: Long): String = runCatching {
        Instant.ofEpochMilli(timestamp).atZone(REPORT_ZONE).format(REPORT_TIME)
    }.getOrDefault("")

    private fun escapeLabel(raw: String): String =
        raw.replace("[", "(").replace("]", ")").take(120)

    private fun termsOf(text: String): List<String> {
        val lower = text.lowercase()
        val latin = LATIN_WORD.findAll(lower).map { it.value }.filter { it.length in 2..24 }
        val han = HAN_CHAR.findAll(lower).map { it.value }.toList()
        val bigrams = han.windowed(2, 1).map { it[0] + it[1] }
        return (latin.toList() + bigrams).filter { it !in STOP_TERMS }
    }
}
