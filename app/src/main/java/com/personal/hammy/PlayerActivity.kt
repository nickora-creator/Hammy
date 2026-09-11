package com.personal.hammy

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Dedicated player: loads only the selected video's official page URL in a WebView,
 * then injects lite player assist (gentle play + Skip/Close focus) plus the v0.1.1
 * focus-ring / Center play-pause helpers.
 *
 * Always-on Close (and Back / Menu) finish() back to the browse grid so the user can
 * escape ad traps even if the page WebView is stuck.
 */
class PlayerActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var rootLayout: FrameLayout
    private lateinit var closeButton: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var injectGeneration = 0

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullScreenContainer: FrameLayout? = null

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        applyImmersiveFullscreen()

        val pageUrl = intent.getStringExtra(EXTRA_URL)
        if (pageUrl.isNullOrBlank()) {
            finish()
            return
        }

        rootLayout = findViewById(R.id.playerRoot)
        webView = findViewById(R.id.playerWebView)
        closeButton = findViewById(R.id.playerCloseButton)
        closeButton.setOnClickListener { exitPlayer() }
        closeButton.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                exitPlayer()
                true
            } else {
                false
            }
        }

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.userAgentString = userAgent
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.setSupportZoom(false)

        webView.setBackgroundColor(Color.BLACK)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                schedulePlayerInjection()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                schedulePlayerInjection()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress >= 70) schedulePlayerInjection()
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    // Tear down prior custom view without finishing the activity
                    hideCustomViewOnly()
                }
                customView = view
                customViewCallback = callback
                val container = FrameLayout(this@PlayerActivity).apply {
                    setBackgroundColor(Color.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
                fullScreenContainer = container
                rootLayout.addView(container)
                // Keep Close above the custom-view layer
                closeButton.bringToFront()
                webView.visibility = View.GONE
                applyImmersiveFullscreen()
            }

            override fun onHideCustomView() {
                hideCustomViewOnly()
            }
        }

        webView.loadUrl(pageUrl)
    }

    private fun hideCustomViewOnly() {
        fullScreenContainer?.let { rootLayout.removeView(it) }
        fullScreenContainer = null
        customView = null
        try {
            customViewCallback?.onCustomViewHidden()
        } catch (_: Exception) {
        }
        customViewCallback = null
        webView.visibility = View.VISIBLE
        closeButton.bringToFront()
        applyImmersiveFullscreen()
    }

    /** Always leave the player and return to the grid — never trapped by customView/ads. */
    private fun exitPlayer() {
        if (customView != null) {
            hideCustomViewOnly()
        }
        finish()
    }

    private fun applyImmersiveFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveFullscreen()
    }

    private fun schedulePlayerInjection() {
        val gen = ++injectGeneration
        val delays = longArrayOf(0L, 400L, 1200L, 2500L, 4500L, 7000L)
        for (delay in delays) {
            mainHandler.postDelayed({
                if (gen != injectGeneration) return@postDelayed
                if (!::webView.isInitialized) return@postDelayed
                webView.evaluateJavascript(PlayerChromeJs.SCRIPT, null)
                webView.evaluateJavascript(FocusInjectJs.SCRIPT, null)
            }, delay)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Back / Escape always leave the player (no customView hide-only loop)
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            exitPlayer()
            return true
        }
        // Menu / Guide as alternate leanback escape hatch
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_GUIDE) {
            exitPlayer()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            // If Close has focus, let its click listener handle it
            if (closeButton.isFocused) {
                exitPlayer()
                return true
            }
            webView.evaluateJavascript(
                "(function(){try{if(window.__hammyToggleVideo){return window.__hammyToggleVideo('native');}}catch(e){}return false;})();",
                null
            )
        }
        // Up from WebView can move focus to Close for leanback users
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && webView.hasFocus()) {
            closeButton.requestFocus()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        exitPlayer()
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
