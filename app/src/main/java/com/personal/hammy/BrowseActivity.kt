package com.personal.hammy

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/**
 * Native Fire TV / Leanback-style browse: focusable poster cards in a grid.
 * Listing metadata comes from official category/home HTML (window.initials).
 * Playback opens [PlayerActivity] with only that video's official page URL.
 */
class BrowseActivity : AppCompatActivity() {
    private lateinit var shortcutContainer: LinearLayout
    private lateinit var sectionTitle: TextView
    private lateinit var videoGrid: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var errorPanel: View
    private lateinit var errorMessage: TextView
    private lateinit var btnRetry: Button

    private val adapter = VideoAdapter { item -> openPlayer(item) }
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var currentUrl: String = ""
    private var currentLabel: String = "Home"
    private var loadGeneration = 0
    private var activeShortcut: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)

        shortcutContainer = findViewById(R.id.shortcutContainer)
        sectionTitle = findViewById(R.id.sectionTitle)
        videoGrid = findViewById(R.id.videoGrid)
        loading = findViewById(R.id.loading)
        errorPanel = findViewById(R.id.errorPanel)
        errorMessage = findViewById(R.id.errorMessage)
        btnRetry = findViewById(R.id.btnRetry)

        TvFocus.attach(btnRetry, scale = 1.1f)
        btnRetry.setOnClickListener { loadListing(currentUrl, currentLabel, activeShortcut) }

        videoGrid.layoutManager = GridLayoutManager(this, 5)
        videoGrid.adapter = adapter
        videoGrid.itemAnimator = null

        buildShortcuts()

        val orientation = Prefs.getOrientation(this)
        val startUrl = intent.getStringExtra(EXTRA_URL) ?: orientation.homeUrl
        val startLabel = intent.getStringExtra(EXTRA_LABEL) ?: getString(R.string.home)
        // Prefer Home shortcut if present
        val homeBtn = shortcutContainer.findViewWithTag<Button>("shortcut:home")
        loadListing(startUrl, startLabel, homeBtn)
    }

    private fun buildShortcuts() {
        shortcutContainer.removeAllViews()
        val orientation = Prefs.getOrientation(this)
        val slugs = Prefs.getSelectedSlugs(this)
        val catalog = Categories.forOrientation(orientation).associateBy { it.slug }

        addShortcut(getString(R.string.home), orientation.homeUrl, tag = "shortcut:home")
        addShortcut(getString(R.string.change_prefs), onClick = {
            Prefs.setSetupDone(this, false)
            startActivity(Intent(this, OrientationActivity::class.java))
            finish()
        }, tag = "shortcut:prefs")

        for (slug in slugs) {
            val name = catalog[slug]?.name ?: slug
            val url = orientation.categoryPrefix + slug
            addShortcut(name, url, tag = "shortcut:cat:$slug")
        }
    }

    private fun addShortcut(label: String, url: String, tag: String) {
        addShortcut(label, tag = tag) { btn ->
            loadListing(url, label, btn)
        }
    }

    private fun addShortcut(
        label: String,
        tag: String,
        onClick: (Button) -> Unit
    ) {
        val btn = Button(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            isAllCaps = false
            isFocusable = true
            background = getDrawable(R.drawable.btn_bg)
            setPadding(24, 8, 24, 8)
            this.tag = tag
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                marginStart = 6
                marginEnd = 6
                topMargin = 6
                bottomMargin = 6
            }
            setOnFocusChangeListener { v, hasFocus ->
                v.animate()
                    .scaleX(if (hasFocus) 1.08f else 1f)
                    .scaleY(if (hasFocus) 1.08f else 1f)
                    .setDuration(120)
                    .start()
            }
            setOnClickListener { onClick(this) }
        }
        shortcutContainer.addView(btn)
    }

    private fun loadListing(url: String, label: String, selectedBtn: Button?) {
        currentUrl = url
        currentLabel = label
        sectionTitle.text = label
        markActiveShortcut(selectedBtn)

        val gen = ++loadGeneration
        showLoading()
        io.execute {
            val result = ListingFetcher.fetch(url)
            main.post {
                if (gen != loadGeneration) return@post
                result.fold(
                    onSuccess = { items ->
                        adapter.submit(items)
                        showGrid()
                        videoGrid.post {
                            if (adapter.itemCount > 0) {
                                videoGrid.findViewHolderForAdapterPosition(0)
                                    ?.itemView?.requestFocus()
                            }
                        }
                    },
                    onFailure = { err ->
                        adapter.submit(emptyList())
                        showError(err.message ?: getString(R.string.load_failed))
                    }
                )
            }
        }
    }

    private fun markActiveShortcut(btn: Button?) {
        activeShortcut?.isSelected = false
        activeShortcut = btn
        btn?.isSelected = true
    }

    private fun showLoading() {
        loading.visibility = View.VISIBLE
        errorPanel.visibility = View.GONE
        videoGrid.visibility = View.INVISIBLE
    }

    private fun showGrid() {
        loading.visibility = View.GONE
        errorPanel.visibility = View.GONE
        videoGrid.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        loading.visibility = View.GONE
        videoGrid.visibility = View.INVISIBLE
        errorPanel.visibility = View.VISIBLE
        errorMessage.text = message
        btnRetry.requestFocus()
    }

    private fun openPlayer(item: VideoItem) {
        startActivity(
            Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_URL, item.pageUrl)
                putExtra(PlayerActivity.EXTRA_TITLE, item.title)
            }
        )
    }

    override fun onDestroy() {
        loadGeneration++
        io.shutdownNow()
        super.onDestroy()
    }

    private class VideoAdapter(
        private val onClick: (VideoItem) -> Unit
    ) : RecyclerView.Adapter<VideoAdapter.VH>() {
        private val items = mutableListOf<VideoItem>()

        fun submit(list: List<VideoItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val root: View = view.findViewById(R.id.videoCardRoot)
            val thumb: ImageView = view.findViewById(R.id.thumb)
            val title: TextView = view.findViewById(R.id.title)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_video_card, parent, false)
            TvFocus.attach(view.findViewById(R.id.videoCardRoot), scale = 1.1f)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            ThumbLoader.load(holder.thumb, item.thumbUrl)
            holder.root.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_LABEL = "extra_label"
    }
}
