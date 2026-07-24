package com.rouf.freeview

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The History list shows the bookmark indicator only on rows whose URL is bookmarked. Seeds the
 * app's own history + bookmark stores, launches HistoryActivity, and inspects each laid-out row.
 * Note: this resets on-device history and bookmarks, like the other store-backed tests.
 */
@RunWith(AndroidJUnit4::class)
class HistoryBookmarkIconTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val bookmarkedUrl = "https://medium.com/@u/bookmarked-post-abc123"
    private val plainUrl = "https://medium.com/@u/plain-post-def456"

    @Before
    fun seed() {
        HistoryStore(context).clear()
        BookmarkStore(context).clear()
        HistoryStore(context).add(plainUrl, 50)
        HistoryStore(context).add(bookmarkedUrl, 50) // both in history
        BookmarkStore(context).add(bookmarkedUrl)    // only this one bookmarked
    }

    @After
    fun cleanup() {
        HistoryStore(context).clear()
        BookmarkStore(context).clear()
    }

    @Test
    fun bookmarkIcon_showsOnlyOnBookmarkedRows() {
        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            // Wait until both rows are laid out.
            val deadline = System.currentTimeMillis() + 5000
            var rows = 0
            while (rows < 2 && System.currentTimeMillis() < deadline) {
                scenario.onActivity { rows = it.findViewById<RecyclerView>(R.id.history_list).childCount }
                if (rows < 2) Thread.sleep(50)
            }
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.history_list)
                var bookmarkedIcon: Boolean? = null
                var plainIcon: Boolean? = null
                for (i in 0 until list.childCount) {
                    val row = list.getChildAt(i)
                    val url = row.findViewById<TextView>(R.id.item_url).text.toString()
                    val shown = row.findViewById<View>(R.id.item_bookmark).visibility == View.VISIBLE
                    if (url == bookmarkedUrl) bookmarkedIcon = shown
                    if (url == plainUrl) plainIcon = shown
                }
                assertEquals(true, bookmarkedIcon)
                assertEquals(false, plainIcon)
            }
        }
    }

    @Test
    fun collapsingSearch_keepsClearAllInline() {
        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            // Expand then collapse the search (the same MenuItem collapse path the back gesture uses).
            scenario.onActivity {
                it.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
                    .menu.findItem(R.id.action_search).expandActionView()
            }
            scenario.onActivity {
                it.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
                    .menu.findItem(R.id.action_search).collapseActionView()
            }
            // "Clear all" must come back inline (displayed), not be stranded in the overflow.
            onView(withText(R.string.history_clear)).check(matches(isDisplayed()))
        }
    }
}
