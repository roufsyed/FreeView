package com.rouf.freeview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

/** A simple, themed FAQ screen: a list of questions that expand to reveal their answers. */
class FaqActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faq)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<RecyclerView>(R.id.faq_list).apply {
            layoutManager = LinearLayoutManager(this@FaqActivity)
            adapter = FaqAdapter(FAQ_ITEMS)
        }

        applyWindowInsets()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun applyWindowInsets() {
        val appBar = findViewById<View>(R.id.app_bar)
        val list = findViewById<View>(R.id.faq_list)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.faq_root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            list.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private data class Faq(@StringRes val question: Int, @StringRes val answer: Int)

    /** Accordion adapter: tapping an item toggles its answer. State is by position (list is static). */
    private class FaqAdapter(private val items: List<Faq>) : RecyclerView.Adapter<FaqAdapter.VH>() {

        private val expanded = mutableSetOf<Int>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_faq, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.question.setText(item.question)
            holder.answer.setText(item.answer)
            bindExpanded(holder, position in expanded)
            holder.itemView.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val nowExpanded = pos !in expanded
                if (nowExpanded) expanded.add(pos) else expanded.remove(pos)
                // Toggle this row in place (no notifyItemChanged) so the change animation doesn't
                // briefly render two cross-fading copies of the row.
                bindExpanded(holder, nowExpanded)
            }
        }

        private fun bindExpanded(holder: VH, isExpanded: Boolean) {
            holder.answer.isVisible = isExpanded
            holder.chevron.rotation = if (isExpanded) 180f else 0f
        }

        override fun getItemCount(): Int = items.size

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val question: TextView = view.findViewById(R.id.faq_question)
            val answer: TextView = view.findViewById(R.id.faq_answer)
            val chevron: ImageView = view.findViewById(R.id.faq_chevron)
        }
    }

    companion object {
        private val FAQ_ITEMS = listOf(
            Faq(R.string.faq_q_about, R.string.faq_a_about),
            Faq(R.string.faq_q_affiliation, R.string.faq_a_affiliation),
            Faq(R.string.faq_q_open, R.string.faq_a_open),
            Faq(R.string.faq_q_share, R.string.faq_a_share),
            Faq(R.string.faq_q_domains, R.string.faq_a_domains),
            Faq(R.string.faq_q_service, R.string.faq_a_service),
            Faq(R.string.faq_q_wontload, R.string.faq_a_wontload),
            Faq(R.string.faq_q_bookmark, R.string.faq_a_bookmark),
            Faq(R.string.faq_q_default, R.string.faq_a_default),
            Faq(R.string.faq_q_wrongapp, R.string.faq_a_wrongapp),
            Faq(R.string.faq_q_data, R.string.faq_a_data),
            Faq(R.string.faq_q_account, R.string.faq_a_account),
            Faq(R.string.faq_q_clipboard, R.string.faq_a_clipboard),
            Faq(R.string.faq_q_disclaimer, R.string.faq_a_disclaimer),
        )
    }
}
