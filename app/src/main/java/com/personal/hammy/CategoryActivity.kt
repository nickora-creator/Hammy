package com.personal.hammy

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CategoryActivity : AppCompatActivity() {
    private val selected = linkedSetOf<String>()
    private lateinit var countLabel: TextView
    private lateinit var items: List<Category>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category)

        val orientation = Prefs.getOrientation(this)
        items = Categories.shuffled(orientation)
        selected.addAll(Prefs.getSelectedSlugs(this).filter { slug ->
            items.any { it.slug == slug }
        }.take(10))

        countLabel = findViewById(R.id.countLabel)
        updateCount()

        val btnSkip = findViewById<Button>(R.id.btnSkip)
        val btnContinue = findViewById<Button>(R.id.btnContinue)
        TvFocus.attach(btnSkip, scale = 1.1f)
        TvFocus.attach(btnContinue, scale = 1.1f)

        val grid = findViewById<RecyclerView>(R.id.categoryGrid)
        grid.layoutManager = GridLayoutManager(this, 4)
        grid.adapter = CategoryAdapter(items, selected) { category, nowSelected ->
            if (nowSelected) {
                if (selected.size >= 10) {
                    Toast.makeText(this, "Maximum 10 categories", Toast.LENGTH_SHORT).show()
                    false
                } else {
                    selected.add(category.slug)
                    updateCount()
                    true
                }
            } else {
                selected.remove(category.slug)
                updateCount()
                true
            }
        }

        btnSkip.setOnClickListener {
            finishSetup(emptySet())
        }
        btnContinue.setOnClickListener {
            finishSetup(selected.toSet())
        }
    }

    private fun updateCount() {
        countLabel.text = getString(R.string.selected_count, selected.size)
    }

    private fun finishSetup(slugs: Set<String>) {
        Prefs.setSelectedSlugs(this, slugs)
        Prefs.setSetupDone(this, true)

        val orientation = Prefs.getOrientation(this)
        val ordered = Prefs.getOrderedSelectedSlugs(this)
        val mixUrl = orientation.combinedSearchUrl(ordered)

        val intent = Intent(this, BrowseActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            if (mixUrl != null) {
                putExtra(BrowseActivity.EXTRA_URL, mixUrl)
                putExtra(BrowseActivity.EXTRA_LABEL, getString(R.string.all_selected))
            }
        }
        startActivity(intent)
        finish()
    }

    private class CategoryAdapter(
        private val items: List<Category>,
        private val selected: Set<String>,
        private val onToggle: (Category, Boolean) -> Boolean
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {

        class VH(val button: Button) : RecyclerView.ViewHolder(button)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false) as Button
            TvFocus.attach(view, scale = 1.1f)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.button.text = item.name
            holder.button.isSelected = selected.contains(item.slug)
            holder.button.setOnClickListener {
                val wantSelected = !holder.button.isSelected
                if (onToggle(item, wantSelected)) {
                    holder.button.isSelected = wantSelected
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
