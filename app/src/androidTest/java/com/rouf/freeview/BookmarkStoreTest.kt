package com.rouf.freeview

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumentation tests for [BookmarkStore] against real SharedPreferences. */
@RunWith(AndroidJUnit4::class)
class BookmarkStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var store: BookmarkStore
    private lateinit var history: HistoryStore

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        store = BookmarkStore(context)
        history = HistoryStore(context)
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test
    fun add_thenContains_byNormalizedKey() {
        store.add("https://medium.com/@u/post-abc?source=rss")
        assertTrue(store.contains("https://medium.com/@u/post-abc?sk=other"))
        assertTrue(store.contains("http://medium.com/@u/post-abc/"))
        assertFalse(store.contains("https://medium.com/@u/different-xyz"))
    }

    @Test
    fun add_deduplicatesButKeepsLatestOriginalAtFront() {
        store.add("https://medium.com/@u/read-9f2?source=a")
        store.add("https://medium.com/@u/read-9f2?sk=b")
        val items = store.items()
        assertEquals(1, items.size)
        // Original (token-bearing) URL preserved; latest add wins and moves to front.
        assertEquals("https://medium.com/@u/read-9f2?sk=b", items[0])
    }

    @Test
    fun add_newestFirst() {
        store.add("https://medium.com/@u/one-1")
        store.add("https://medium.com/@u/two-2")
        assertEquals(
            listOf("https://medium.com/@u/two-2", "https://medium.com/@u/one-1"),
            store.items(),
        )
    }

    @Test
    fun add_blankIsRejected() {
        assertFalse(store.add("   "))
        assertTrue(store.items().isEmpty())
    }

    @Test
    fun remove_byNormalizedKey() {
        store.add("https://medium.com/@u/post-abc?source=a")
        assertTrue(store.remove("https://medium.com/@u/post-abc?sk=b"))
        assertTrue(store.items().isEmpty())
    }

    @Test
    fun removeAll_deletesBatchInOneWrite() {
        store.add("https://medium.com/@u/a-1")
        store.add("https://medium.com/@u/b-2")
        store.add("https://medium.com/@u/c-3")
        store.removeAll(listOf("https://medium.com/@u/a-1?x=1", "https://medium.com/@u/c-3"))
        assertEquals(listOf("https://medium.com/@u/b-2"), store.items())
    }

    @Test
    fun clear_emptiesBookmarks() {
        store.add("https://medium.com/@u/post-1")
        store.clear()
        assertTrue(store.items().isEmpty())
    }

    @Test
    fun bookmarksAreIsolatedFromHistory() {
        store.add("https://medium.com/@u/bookmarked-1")
        history.add("https://medium.com/@u/browsed-2", maxSize = 50)

        // Clearing one store must never touch the other.
        history.clear()
        assertEquals(listOf("https://medium.com/@u/bookmarked-1"), store.items())

        history.add("https://medium.com/@u/browsed-2", maxSize = 50)
        store.clear()
        assertEquals(listOf("https://medium.com/@u/browsed-2"), history.items())
    }

    @Test
    fun corruptBlobIsBackedUpBeforeOverwrite() {
        prefs.edit().putString("bookmark_items", "{ this is not valid json").commit()
        // A write must preserve the unparseable blob rather than silently erasing it.
        store.add("https://medium.com/@u/fresh-1")
        assertEquals("{ this is not valid json", prefs.getString("bookmark_items_corrupt_backup", null))
        assertEquals(listOf("https://medium.com/@u/fresh-1"), store.items())
    }

    @Test
    fun readsLegacyBareArray() {
        prefs.edit().putString(
            "bookmark_items",
            "[\"https://medium.com/@u/legacy-1\",\"https://medium.com/@u/legacy-2\"]",
        ).commit()
        assertEquals(
            listOf("https://medium.com/@u/legacy-1", "https://medium.com/@u/legacy-2"),
            store.items(),
        )
    }
}
