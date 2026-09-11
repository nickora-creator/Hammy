package com.personal.hammy

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/**
 * Dedicated player: loads only the selected video's official page URL in a WebView.
 * Keeps the v0.1.1 focus-ring injection for terms/consent + controls.
 */
class PlayerActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var injectGeneration = 0

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val pageUrl = intent.getStringExtra(EXTRA_URL)
        if (pageUrl.isNullOrBlank()) {
            finish()
            return
        }

        webView = findViewById(R.id.playerWebView)
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

        webView.setBackgroundColor(0xFF000000.toInt())
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                scheduleFocusInjection()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                scheduleFocusInjection()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress >= 85) scheduleFocusInjection()
            }
        }

        webView.loadUrl(pageUrl)
    }

    private fun scheduleFocusInjection() {
        val gen = ++injectGeneration
        val delays = longArrayOf(0L, 400L, 1200L, 2500L, 4500L)
        for (delay in delays) {
            mainHandler.postDelayed({
                if (gen != injectGeneration) return@postDelayed
                if (!::webView.isInitialized) return@postDelayed
                webView.evaluateJavascript(FocusInjectJs.SCRIPT, null)
            }, delay)
        }
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
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            webView.evaluateJavascript(
                "(function(){try{if(window.__hammyToggleVideo){return window.__hammyToggleVideo('native');}}catch(e){}return false;})();",
                null
            )
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
        injectGeneration++
        mainHandler.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
    }
}
