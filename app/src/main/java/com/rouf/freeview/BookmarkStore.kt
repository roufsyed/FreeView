package com.rouf.freeview

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores bookmarked Medium URLs in the default [SharedPreferences] under [KEY].
 *
 * Unlike [HistoryStore], bookmarks are deliberate saves, so they are:
 *  - **uncapped** (never silently evicted),
 *  - de-duplicated on a **normalized key** while the **original URL** is preserved for reopening,
 *  - written with `commit()` for durability, with the boolean surfaced so the UI can react to a
 *    failed write.
 *
 * All access is main-thread only (single-process, single write path), so the read-modify-write
 * in [add]/[remove]/[removeAll] cannot interleave. If I/O ever moves off the main thread this
 * needs an explicit synchronization guard.
 */
class BookmarkStore(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    /** All stored URLs, newest first. Tolerant of legacy (bare-array) and partially corrupt data. */
    fun items(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = if (raw.trimStart().startsWith("{")) {
                JSONObject(raw).optJSONArray(FIELD_ITEMS) ?: JSONArray()
            } else {
                JSONArray(raw) // legacy bare array (v0)
            }
            (0 until array.length()).mapNotNull { (array.opt(it) as? String)?.takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
    }

    /** True when [url] (normalized) is already bookmarked. */
    fun contains(url: String): Boolean {
        val key = normalizeBookmarkUrl(url)
        return items().any { normalizeBookmarkUrl(it) == key }
    }

    /** Adds [url] at the front, de-duplicated by normalized key. Returns whether the write committed. */
    fun add(url: String): Boolean {
        if (url.isBlank()) return false
        val key = normalizeBookmarkUrl(url)
        val list = items().toMutableList()
        list.removeAll { normalizeBookmarkUrl(it) == key }
        list.add(0, url)
        return save(list)
    }

    /** Adds every non-blank URL in [urls] at the front (de-duplicated by normalized key), in one write. */
    fun addAll(urls: Collection<String>): Boolean {
        val toAdd = urls.filter { it.isNotBlank() }
        if (toAdd.isEmpty()) return false
        val list = items().toMutableList()
        for (url in toAdd) {
            val key = normalizeBookmarkUrl(url)
            list.removeAll { normalizeBookmarkUrl(it) == key }
            list.add(0, url)
        }
        return save(list)
    }

    /** Removes the entry matching [url] by normalized key. Returns whether anything was removed and committed. */
    fun remove(url: String): Boolean {
        val key = normalizeBookmarkUrl(url)
        val list = items().toMutableList()
        return if (list.removeAll { normalizeBookmarkUrl(it) == key }) save(list) else false
    }

    /** Removes every entry whose normalized key matches one in [urls], in a single write. */
    fun removeAll(urls: Collection<String>): Boolean {
        if (urls.isEmpty()) return false
        val keys = urls.mapTo(HashSet()) { normalizeBookmarkUrl(it) }
        val list = items().toMutableList()
        return if (list.removeAll { normalizeBookmarkUrl(it) in keys }) save(list) else false
    }

    fun clear(): Boolean {
        backupIfCorrupt()
        return prefs.edit().remove(KEY).commit()
    }

    private fun save(list: List<String>): Boolean {
        backupIfCorrupt()
        val array = JSONArray().apply { list.forEach { put(it) } }
        val envelope = JSONObject().put(FIELD_VERSION, SCHEMA_VERSION).put(FIELD_ITEMS, array)
        return prefs.edit().putString(KEY, envelope.toString()).commit()
    }

    /**
     * Guards against permanent loss: if the stored blob is non-blank but unparseable, copy it
     * aside once (under [CORRUPT_KEY]) before it gets overwritten, so it stays recoverable.
     */
    private fun backupIfCorrupt() {
        val raw = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() } ?: return
        val parseable = runCatching {
            if (raw.trimStart().startsWith("{")) JSONObject(raw) else JSONArray(raw)
        }.isSuccess
        if (!parseable && !prefs.contains(CORRUPT_KEY)) {
            prefs.edit().putString(CORRUPT_KEY, raw).commit()
        }
    }

    companion object {
        private const val KEY = "bookmark_items"
        private const val CORRUPT_KEY = "bookmark_items_corrupt_backup"
        private const val FIELD_VERSION = "v"
        private const val FIELD_ITEMS = "items"
        private const val SCHEMA_VERSION = 1
    }
}
