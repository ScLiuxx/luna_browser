package com.liuli.browser.web.agent

import com.liuli.browser.data.LibraryEntry
import java.security.MessageDigest

/**
 * 页面关注：只保存 URL、标题、正文指纹与数字片段，不保存正文，
 * 也不写入磁盘。监控范围仅限当前已加载的标签页，不创建后台任务。
 */
data class AgentWatch(
    val id: Long,
    val url: String,
    val host: String,
    val title: String,
    val baselineHash: String,
    val baselineNumbers: List<String>,
    val createdAt: Long
)

data class WatchUpdate(
    val watch: AgentWatch,
    val changed: Boolean,
    val newNumbers: List<String>,
    val goneNumbers: List<String>
)

/** 用户录制宏：只保存语义步骤，不保存元素 ref，回放前仍需重新批准。 */
data class AgentMacro(
    val id: Long,
    val name: String,
    val host: String,
    val steps: List<AgentPlanStep>,
    val createdAt: Long
)

object AgentAutomation {

    const val MAX_WATCHES = 12
    const val MAX_MACROS = 10
    const val MAX_MACRO_STEPS = 5

    private val WHITESPACE = Regex("\\s+")
    private val PRICE = Regex(
        "(?:¥|￥|\\$|€|£)\\s?\\d+(?:[.,]\\d{1,2})?|" +
            "\\d+(?:\\.\\d{1,2})?\\s?(?:元|美元|欧元|GBP|USD|CNY)"
    )
    private val HAN = Regex("[\\u4e00-\\u9fa5]")
    private val LATIN = Regex("[a-zA-Z0-9]{2,}")

    fun normalize(text: String): String = text.replace(WHITESPACE, " ").trim().lowercase()

    /** 正文指纹：只用于判断“是否变化”，无法还原正文。 */
    fun fingerprint(text: String): String {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { byte -> "%02x".format(byte) }
    }

    /** 只抽取带货币标记的数字，避免把普通计数当成价格提醒。 */
    fun numbers(text: String): List<String> = PRICE
        .findAll(text.replace(WHITESPACE, " "))
        .map { it.value.replace(" ", "") }
        .distinct()
        .take(30)
        .toList()

    fun compare(watch: AgentWatch, text: String): WatchUpdate {
        val hash = fingerprint(text)
        val current = numbers(text)
        return WatchUpdate(
            watch = watch.copy(
                baselineHash = hash,
                baselineNumbers = current
            ),
            changed = hash != watch.baselineHash,
            newNumbers = current.filter { it !in watch.baselineNumbers },
            goneNumbers = watch.baselineNumbers.filter { it !in current }
        )
    }

    /** 近 N 天浏览摘要，只在本机内存中生成。 */
    fun digest(entries: List<LibraryEntry>, days: Long = 1): String {
        val since = System.currentTimeMillis() - days * 86_400_000L
        val recent = entries.filter { it.timestamp >= since }
        if (recent.isEmpty()) {
            return "近 $days 天没有本机浏览记录。历史与书签仅保存在本机。"
        }
        val hostCounts = recent.groupingBy { hostOf(it.url) }.eachCount()
        val topHosts = hostCounts.entries
            .sortedByDescending { it.value }
            .take(5)
        return buildString {
            appendLine("本机浏览日报（近 $days 天，未上传）")
            appendLine("- 访问 ${recent.size} 次，涉及 ${hostCounts.size} 个站点。")
            topHosts.forEach { (host, count) -> appendLine("- $host · $count 次") }
            appendLine()
            appendLine("最近访问：")
            recent.take(8).forEach { entry ->
                appendLine("- ${entry.title.take(60).ifBlank { entry.url.take(60) }}")
            }
        }.trim()
    }

    /** 本地关键词检索：只匹配标题与 URL，返回最近的相关条目。 */
    fun search(entries: List<LibraryEntry>, query: String): List<LibraryEntry> {
        val terms = termsOf(query)
        if (terms.isEmpty()) return entries.take(8)
        return entries
            .map { entry ->
                val title = entry.title.lowercase()
                val url = entry.url.lowercase()
                var score = 0
                terms.forEach { term ->
                    if (title.contains(term)) score += 3
                    if (url.contains(term)) score += 1
                }
                entry to score
            }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<LibraryEntry, Int>> { it.second }
                    .thenByDescending { it.first.timestamp }
            )
            .map { it.first }
            .distinctBy { it.url }
            .take(8)
    }

    private fun termsOf(text: String): List<String> {
        val lower = text.lowercase()
        val latin = LATIN.findAll(lower).map { it.value }.filter { it.length in 2..24 }
        val han = HAN.findAll(lower).map { it.value }.toList()
        return (latin.toList() + han.windowed(2, 1).map { it[0] + it[1] })
            .distinct()
            .take(8)
    }

    private fun hostOf(url: String): String = runCatching {
        java.net.URI(url).host?.removePrefix("www.")
    }.getOrNull().orEmpty().ifBlank { "其他站点" }
}
