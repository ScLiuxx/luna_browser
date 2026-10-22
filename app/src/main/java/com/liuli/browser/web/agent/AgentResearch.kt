package com.liuli.browser.web.agent

import android.webkit.WebView
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
    val accessedAt: Long,
    val charCount: Int,
    val keyPoints: List<String>
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
    val fileName: String get() = ResearchEngine.fileName(goal)
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

    fun fileName(goal: String): String {
        val stamp = LocalDateTime.now().format(FILE_TIME)
        val slug = goal.replace(FILE_ILLEGAL, "-").trim('-').take(40).ifBlank { "report" }
        return "liuli-$stamp-$slug.md"
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
