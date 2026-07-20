package com.rouf.freediumcfd

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray

class HistoryStore(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    /** All stored URLs, newest first. */
    fun items(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    /** Records [url] at the front (de-duplicated), trimmed to [maxSize]. No-op when maxSize <= 0. */
    fun add(url: String, maxSize: Int) {
        if (maxSize <= 0) return
        val list = items().toMutableList()
        list.remove(url)
        list.add(0, url)
        save(if (list.size > maxSize) list.subList(0, maxSize).toList() else list)
    }

    fun remove(url: String) {
        val list = items().toMutableList()
        if (list.remove(url)) save(list)
    }

    /** Trims to [maxSize]; clears entirely when maxSize <= 0. */
    fun trimTo(maxSize: Int) {
        if (maxSize <= 0) {
            clear()
            return
        }
        val list = items()
        if (list.size > maxSize) save(list.subList(0, maxSize).toList())
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun save(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "history_items"
    }
}
