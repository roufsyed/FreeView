package com.rouf.freeview

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HistoryActivity : AppCompatActivity() {

    private lateinit var store: HistoryStore
    private val bookmarks by lazy { BookmarkStore(this) }
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: TextView

    /** Full list from the store; [applyFilter] narrows it to what the adapter shows. */
    private var allItems: List<String> = emptyList()
    private var query: String = ""
    private var searchItem: MenuItem? = null
    private var clearItem: MenuItem? = null

    /** Normalized keys of currently-bookmarked URLs, for the per-row bookmark indicator. */
    private var bookmarkedKeys: Set<String> = emptySet()

    private val adapter = HistoryAdapter(
        onOpen = ::openArticle,
        onSelectionStarted = ::enterSelectionUi,
        onSelectionChanged = ::onSelectionChanged,
        isBookmarked = { normalizeBookmarkUrl(it) in bookmarkedKeys },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        store = HistoryStore(this)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        listView = findViewById(R.id.history_list)
        emptyView = findViewById(R.id.empty_view)
        listView.layoutManager = LinearLayoutManager(this)
        listView.adapter = adapter

        // Back exits selection first, then leaves the screen.
        onBackPressedDispatcher.addCallback(this) {
            if (adapter.selectionMode) exitSelection() else finish()
        }

        applyWindowInsets()
        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.history_menu, menu)
        searchItem = menu.findItem(R.id.action_search)
        clearItem = menu.findItem(R.id.action_clear_history)
        (searchItem?.actionView as? SearchView)?.apply {
            queryHint = getString(R.string.search_hint)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(text: String?): Boolean = false
                override fun onQueryTextChange(text: String?): Boolean {
                    this@HistoryActivity.query = text.orEmpty()
                    applyFilter()
                    return true
                }
            })
        }
        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                clearItem?.isVisible = false // hide "Clear all" while the search field is open
                return true
            }
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                query = ""
                applyFilter()
                // Rebuild the menu so "Clear all" returns inline and the bar re-lays-out cleanly;
                // a direct isVisible flip can strand it in the overflow after a back-gesture collapse.
                invalidateOptionsMenu()
                return true
            }
        })
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val selecting = adapter.selectionMode
        val searching = searchItem?.isActionViewExpanded == true
        menu.findItem(R.id.action_search)?.isVisible = !selecting && allItems.isNotEmpty()
        menu.findItem(R.id.action_bookmark_selected)?.isVisible = selecting
        menu.findItem(R.id.action_delete_selected)?.isVisible = selecting
        // Hide "Clear all" while searching (the SearchView takes over the bar), like the title.
        menu.findItem(R.id.action_clear_history)?.isVisible = !selecting && !searching && allItems.isNotEmpty()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (adapter.selectionMode) exitSelection() else finish()
                true
            }
            R.id.action_bookmark_selected -> {
                bookmarkSelected()
                true
            }
            R.id.action_delete_selected -> {
                confirmDeleteSelected()
                true
            }
            R.id.action_clear_history -> {
                confirmClearAll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refresh() {
        allItems = store.items()
        reloadBookmarkedKeys()
        applyFilter()
        invalidateOptionsMenu()
    }

    /** A bookmark may have been added/removed elsewhere; re-sync the per-row indicators. */
    override fun onResume() {
        super.onResume()
        reloadBookmarkedKeys()
        applyFilter()
    }

    private fun reloadBookmarkedKeys() {
        bookmarkedKeys = bookmarks.items().mapTo(HashSet()) { normalizeBookmarkUrl(it) }
    }

    /** Narrows [allItems] by the current [query] (matching URL + derived title) and updates the UI. */
    private fun applyFilter() {
        // No invalidateOptionsMenu() here: it re-prepares the menu on every keystroke, which
        // collapses the expanded SearchView and closes the keyboard. Menu visibility depends only
        // on allItems (set in refresh()), not on the filtered results.
        val filtered = allItems.filter { matchesQuery(it, query) }
        adapter.submit(filtered)
        val empty = filtered.isEmpty()
        emptyView.setText(if (query.isBlank()) R.string.history_empty else R.string.search_empty)
        emptyView.isVisible = empty
        listView.isVisible = !empty
    }

    // --- Selection mode (contextual bar reusing the app's toolbar) ---

    private fun enterSelectionUi() {
        searchItem?.collapseActionView() // drop the filter so selection acts on the full list
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_24)
        updateSelectionTitle()
        invalidateOptionsMenu()
    }

    private fun onSelectionChanged() {
        if (adapter.selectedCount == 0) exitSelection() else updateSelectionTitle()
    }

    private fun updateSelectionTitle() {
        supportActionBar?.title = getString(R.string.history_selected_count, adapter.selectedCount)
    }

    private fun exitSelection() {
        adapter.exitSelection()
        supportActionBar?.setHomeAsUpIndicator(null)
        supportActionBar?.setTitle(R.string.history_title)
        invalidateOptionsMenu()
    }

    private fun confirmDeleteSelected() {
        val urls = adapter.selectedUrls()
        if (urls.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(resources.getQuantityString(R.plurals.history_delete_confirm, urls.size, urls.size))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                urls.forEach { store.remove(it) }
                exitSelection()
                refresh()
                Toast.makeText(
                    this,
                    resources.getQuantityString(R.plurals.history_deleted, urls.size, urls.size),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .show()
    }

    /** Bookmarks every selected history entry, then leaves selection mode. */
    private fun bookmarkSelected() {
        val urls = adapter.selectedUrls()
        if (urls.isEmpty()) return
        bookmarks.addAll(urls)
        reloadBookmarkedKeys()
        exitSelection()
        Toast.makeText(
            this,
            resources.getQuantityString(R.plurals.bookmarks_added, urls.size, urls.size),
            Toast.LENGTH_SHORT,
        ).show()
    }

    /** Confirms before clearing the whole history. */
    private fun confirmClearAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.history_clear_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.history_clear) { _, _ ->
                store.clear()
                refresh()
            }
            .show()
    }

    /** Open the article in a new view on top of this screen, so Back returns here (not exit). */
    private fun openArticle(url: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_URL, url),
        )
    }

    private fun applyWindowInsets() {
        val appBar = findViewById<View>(R.id.app_bar)
        val content = findViewById<View>(R.id.history_content)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.history_root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            content.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private class HistoryAdapter(
        private val onOpen: (String) -> Unit,
        private val onSelectionStarted: () -> Unit,
        private val onSelectionChanged: () -> Unit,
        private val isBookmarked: (String) -> Boolean,
    ) : RecyclerView.Adapter<HistoryAdapter.VH>() {

        private val items = mutableListOf<String>()
        private val selected = linkedSetOf<String>()

        var selectionMode = false
            private set

        val selectedCount: Int get() = selected.size

        fun selectedUrls(): List<String> = selected.toList()

        @Suppress("NotifyDataSetChanged")
        fun submit(newItems: List<String>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        @Suppress("NotifyDataSetChanged")
        private fun startSelection(url: String) {
            selectionMode = true
            selected.clear()
            selected.add(url)
            notifyDataSetChanged()
            onSelectionStarted()
        }

        private fun toggle(url: String) {
            if (!selected.add(url)) selected.remove(url)
            val index = items.indexOf(url)
            if (index >= 0) notifyItemChanged(index)
            onSelectionChanged()
        }

        @Suppress("NotifyDataSetChanged")
        fun exitSelection() {
            if (!selectionMode) return
            selectionMode = false
            selected.clear()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val url = items[position]
            holder.title.text = deriveArticleTitle(url)
            holder.url.text = url
            holder.bookmark.isVisible = isBookmarked(url)
            holder.setSelected(selected.contains(url))
            holder.itemView.setOnClickListener {
                if (selectionMode) toggle(url) else onOpen(url)
            }
            holder.itemView.setOnLongClickListener {
                if (selectionMode) toggle(url) else startSelection(url)
                true
            }
        }

        override fun getItemCount(): Int = items.size

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.item_title)
            val url: TextView = view.findViewById(R.id.item_url)
            val bookmark: ImageView = view.findViewById(R.id.item_bookmark)

            fun setSelected(isSelected: Boolean) {
                if (isSelected) {
                    itemView.setBackgroundColor(
                        MaterialColors.getColor(
                            itemView,
                            com.google.android.material.R.attr.colorSurfaceVariant,
                            Color.LTGRAY,
                        ),
                    )
                } else {
                    val tv = TypedValue()
                    itemView.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                    itemView.setBackgroundResource(tv.resourceId)
                }
            }
        }

    }
}
