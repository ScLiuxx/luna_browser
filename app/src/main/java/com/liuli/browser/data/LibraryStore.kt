package com.liuli.browser.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class LibraryEntry(
    val title: String,
    val url: String,
    val timestamp: Long
)

class LibraryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val bookmarks = mutableStateListOf<LibraryEntry>()
    val history = mutableStateListOf<LibraryEntry>()

    init {
        readEntries(KEY_BOOKMARKS, bookmarks, MAX_BOOKMARKS)
        readEntries(KEY_HISTORY, history, MAX_HISTORY)
    }

    fun isBookmarked(url: String): Boolean = bookmarks.any { it.url == url }

    fun toggleBookmark(title: String, url: String): Boolean {
        if (url.isBlank()) return false
        val existing = bookmarks.firstOrNull { it.url == url }
        return if (existing != null) {
            bookmarks.remove(existing)
            persist(KEY_BOOKMARKS, bookmarks)
            false
        } else {
            bookmarks.add(
                0,
                LibraryEntry(
                    title = title.ifBlank { url },
                    url = url,
                    timestamp = System.currentTimeMillis()
                )
            )
            trim(bookmarks, MAX_BOOKMARKS)
            persist(KEY_BOOKMARKS, bookmarks)
            true
        }
    }

    fun removeBookmark(url: String) {
        bookmarks.removeAll { it.url == url }
        persist(KEY_BOOKMARKS, bookmarks)
    }

    fun addHistory(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
        val entry = LibraryEntry(
            title = title.ifBlank { url },
            url = url,
            timestamp = System.currentTimeMillis()
        )
        history.removeAll { it.url == url }
        history.add(0, entry)
        trim(history, MAX_HISTORY)
        persist(KEY_HISTORY, history)
    }

    fun updateHistoryTitle(url: String, title: String) {
        val index = history.indexOfFirst { it.url == url }
        if (index >= 0 && title.isNotBlank()) {
            history[index] = history[index].copy(title = title)
            persist(KEY_HISTORY, history)
        }
    }

    fun removeHistory(entry: LibraryEntry) {
        history.remove(entry)
        persist(KEY_HISTORY, history)
    }

    fun clearHistory() {
        history.clear()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    fun clearAll() {
        bookmarks.clear()
        history.clear()
        prefs.edit().remove(KEY_BOOKMARKS).remove(KEY_HISTORY).apply()
    }

    private fun readEntries(key: String, target: MutableList<LibraryEntry>, limit: Int) {
        val raw = prefs.getString(key, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            repeat(minOf(array.length(), limit)) { index ->
                val item = array.getJSONObject(index)
                target.add(
                    LibraryEntry(
                        title = item.optString("title"),
                        url = item.optString("url"),
                        timestamp = item.optLong("timestamp")
                    )
                )
            }
        }
    }

    private fun persist(key: String, entries: List<LibraryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("title", entry.title)
                    .put("url", entry.url)
                    .put("timestamp", entry.timestamp)
            )
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun trim(entries: MutableList<LibraryEntry>, limit: Int) {
        while (entries.size > limit) entries.removeAt(entries.lastIndex)
    }

    companion object {
        private const val PREFS_NAME = "liuli_library"
        private const val KEY_BOOKMARKS = "bookmarks"
        private const val KEY_HISTORY = "history"
        private const val MAX_BOOKMARKS = 200
        private const val MAX_HISTORY = 200
    }
}
