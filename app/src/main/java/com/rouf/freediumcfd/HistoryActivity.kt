package com.rouf.freediumcfd

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

class HistoryActivity : AppCompatActivity() {

    private lateinit var store: HistoryStore
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: View
    private val adapter = HistoryAdapter(::openArticle)

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

        applyWindowInsets()
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.history_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_clear_history)?.isVisible = adapter.itemCount > 0
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
        private val onClick: (String) -> Unit,
    ) : RecyclerView.Adapter<HistoryAdapter.VH>() {

        private val items = mutableListOf<String>()

        @Suppress("NotifyDataSetChanged")
        fun submit(newItems: List<String>) {
            items.clear()
            items.addAll(newItems)
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
            holder.itemView.setOnClickListener { onClick(url) }
        }

        override fun getItemCount(): Int = items.size

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.item_title)
            val url: TextView = view.findViewById(R.id.item_url)
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
