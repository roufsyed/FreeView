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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
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
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: View

    private val adapter = HistoryAdapter(
        onOpen = ::openArticle,
        onSelectionStarted = ::enterSelectionUi,
        onSelectionChanged = ::onSelectionChanged,
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
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val selecting = adapter.selectionMode
        menu.findItem(R.id.action_delete_selected)?.isVisible = selecting
        menu.findItem(R.id.action_clear_history)?.isVisible = !selecting && adapter.itemCount > 0
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (adapter.selectionMode) exitSelection() else finish()
                true
            }
            R.id.action_delete_selected -> {
                confirmDeleteSelected()
                true
            }
            R.id.action_clear_history -> {
                store.clear()
                refresh()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refresh() {
        val items = store.items()
        adapter.submit(items)
        emptyView.isVisible = items.isEmpty()
        listView.isVisible = items.isNotEmpty()
        invalidateOptionsMenu()
    }

    // --- Selection mode (contextual bar reusing the app's toolbar) ---

    private fun enterSelectionUi() {
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

    /** Reopen a history entry: hand the URL back to MainActivity via its existing instance. */
    private fun openArticle(url: String) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_URL, url)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
        )
        finish()
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
            holder.title.text = deriveTitle(url)
            holder.url.text = url
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

        /** Best-effort readable title from a Medium slug (drops the trailing id, spaces the words). */
        private fun deriveTitle(url: String): String {
            val segment = runCatching { url.toUri().lastPathSegment }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: return url
            val parts = segment.split('-')
            val words = if (parts.size > 1) parts.dropLast(1) else parts
            val title = words.joinToString(" ").trim()
            return if (title.isBlank()) url else title.replaceFirstChar { it.uppercase() }
        }
    }
}
