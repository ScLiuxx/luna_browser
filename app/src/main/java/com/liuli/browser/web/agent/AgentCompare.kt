package com.liuli.browser.web.agent

/**
 * 多标签页只读对比。只接收各标签页在本机抽取的词频与价格片段，
 * 不保存正文原文，也不调用云端。
 */
data class AgentComparePage(
    val title: String,
    val host: String,
    val charCount: Int,
    val isPrivate: Boolean,
    val terms: Map<String, Int>,
    val numbers: List<String>
)

object TabCompareEngine {

    const val MAX_PAGES = 4

    private const val MAX_TOP_TERMS = 6
    private const val MAX_COMMON_TERMS = 8
    private const val MAX_UNIQUE_TERMS = 5
    private const val MAX_NUMBERS_PER_PAGE = 4
    private const val MAX_OUTPUT_CHARS = 3_200

    fun render(pages: List<AgentComparePage>): String {
        val adapted = pages.take(MAX_PAGES)
        if (adapted.isEmpty()) return "没有可对比的页面。"

        val common = commonTerms(adapted)
        val text = buildString {
            appendLine("标签页对比 · 本地只读 · ${adapted.size} 页")
            appendLine()
            adapted.forEachIndexed { index, page ->
                appendLine("${index + 1}. 《${page.title.ifBlank { page.host }}》")
                appendLine(
                    "   ${page.host} · ${page.charCount} 字" +
                        if (page.isPrivate) " · 隐私标签" else ""
                )
                val top = page.terms.entries
                    .sortedByDescending { it.value }
                    .take(MAX_TOP_TERMS)
                    .joinToString("、") { it.key }
                if (top.isNotBlank()) appendLine("   高频词：$top")
            }
            appendLine()
            if (common.isEmpty()) {
                appendLine("共同主题：没有识别到所有页面都重复出现的关键词。")
            } else {
                appendLine("共同主题：${common.joinToString("、")}")
            }

            val uniqueLines = adapted.mapIndexedNotNull { index, page ->
                val unique = page.terms.entries
                    .filter { entry ->
                        entry.value >= 2 &&
                            adapted.indices.none { other ->
                                other != index && adapted[other].terms.containsKey(entry.key)
                            }
                    }
                    .sortedByDescending { it.value }
                    .take(MAX_UNIQUE_TERMS)
                    .map { it.key }
                if (unique.isEmpty()) {
                    null
                } else {
                    "仅《${page.title.ifBlank { page.host }}》出现：" +
                        unique.joinToString("、")
                }
            }
            if (uniqueLines.isNotEmpty()) {
                appendLine()
                appendLine("内容差异")
                uniqueLines.forEach { appendLine("- $it") }
            }

            lengthLine(adapted)?.let { line ->
                appendLine()
                appendLine(line)
            }

            val numberLines = numberLines(adapted)
            if (numberLines.isNotEmpty()) {
                appendLine()
                appendLine("价格/数字差异")
                numberLines.forEach { appendLine("- $it") }
            }

            appendLine()
            append(
                "对比只基于本机词频与数字片段，不代表语义结论；" +
                    "未打开或设为禁止的页面不会参与。"
            )
        }.trim()
        return if (text.length > MAX_OUTPUT_CHARS) {
            text.take(MAX_OUTPUT_CHARS) + "…"
        } else {
            text
        }
    }

    private fun commonTerms(pages: List<AgentComparePage>): List<String> {
        if (pages.size < 2) return emptyList()
        return pages.first().terms.keys
            .filter { term -> pages.all { it.terms.containsKey(term) } }
            .sortedWith(
                compareByDescending<String> { term ->
                    pages.minOf { it.terms[term] ?: 0 }
                }.thenByDescending { term ->
                    pages.sumOf { it.terms[term] ?: 0 }
                }
            )
            .take(MAX_COMMON_TERMS)
    }

    private fun lengthLine(pages: List<AgentComparePage>): String? {
        val longest = pages.maxByOrNull { it.charCount } ?: return null
        val shortest = pages.minByOrNull { it.charCount } ?: return null
        val diff = longest.charCount - shortest.charCount
        if (diff < 200) return null
        return "篇幅：最长《${longest.title.ifBlank { longest.host }}》" +
            "${longest.charCount} 字，最短《${shortest.title.ifBlank { shortest.host }}》" +
            "${shortest.charCount} 字，相差 $diff 字。"
    }

    private fun numberLines(pages: List<AgentComparePage>): List<String> {
        val withNumbers = pages.filter { it.numbers.isNotEmpty() }
        if (withNumbers.size < 2) return emptyList()
        val lines = ArrayList<String>()
        pages.forEachIndexed { index, page ->
            if (page.numbers.isEmpty()) return@forEachIndexed
            val exclusive = page.numbers.filter { number ->
                pages.indices.none { other ->
                    other != index && number in pages[other].numbers
                }
            }.take(MAX_NUMBERS_PER_PAGE)
            if (exclusive.isNotEmpty()) {
                lines += "仅《${page.title.ifBlank { page.host }}》出现：" +
                    exclusive.joinToString("、")
            }
        }
        val shared = withNumbers.first().numbers.filter { number ->
            withNumbers.all { number in it.numbers }
        }.take(MAX_NUMBERS_PER_PAGE)
        if (shared.isNotEmpty()) {
            lines += "各页均出现：${shared.joinToString("、")}"
        }
        return lines
    }
}
