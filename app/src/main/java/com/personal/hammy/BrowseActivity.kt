package com.personal.hammy

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class BrowseActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var shortcutContainer: LinearLayout

    // Desktop Chrome UA — site serves a full layout that works better on TV WebView
    // than the mobile m-site. Noted in README.
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)

        webView = findViewById(R.id.webView)
        shortcutContainer = findViewById(R.id.shortcutContainer)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.userAgentString = userAgent
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false
        }
        webView.webChromeClient = WebChromeClient()

        buildShortcuts()

        val orientation = Prefs.getOrientation(this)
        val startUrl = intent.getStringExtra(EXTRA_URL) ?: orientation.homeUrl
        webView.loadUrl(startUrl)
    }

    private fun buildShortcuts() {
        shortcutContainer.removeAllViews()
        val orientation = Prefs.getOrientation(this)
        val slugs = Prefs.getSelectedSlugs(this)
        val catalog = Categories.forOrientation(orientation).associateBy { it.slug }

        addShortcut("Home", orientation.homeUrl)
        addShortcut("Change prefs") {
            Prefs.setSetupDone(this, false)
            startActivity(Intent(this, OrientationActivity::class.java))
            finish()
        }

        for (slug in slugs) {
            val name = catalog[slug]?.name ?: slug
            val url = orientation.categoryPrefix + slug
            addShortcut(name, url)
        }
    }

    private fun addShortcut(label: String, url: String) {
        addShortcut(label) { webView.loadUrl(url) }
    }

    private fun addShortcut(label: String, onClick: () -> Unit) {
        val btn = Button(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            isAllCaps = false
            isFocusable = true
            background = getDrawable(R.drawable.btn_bg)
            setPadding(24, 8, 24, 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                marginStart = 6
                marginEnd = 6
                topMargin = 6
                bottomMargin = 6
            }
            setOnClickListener { onClick() }
        }
        shortcutContainer.addView(btn)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
    }
}
