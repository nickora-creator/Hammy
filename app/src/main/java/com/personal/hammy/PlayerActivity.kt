package com.personal.hammy

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
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
 * then injects lite player assist (gentle play + Skip Ads / age CTA soft focus) plus
 * the focus-ring / Center play-pause helpers.
 *
 * No CookieManager age seeding and no early localStorage/cookie age-bypass inject —
 * those blanked the page. Age / Skip CTAs are soft-focused once; hardClick / native
 * tap run only when the user presses OK (__hammyActivateFocusedOrBlockingCta).
 *
 * Always-on Close (and Back / Menu) finish() back to the browse grid so the user can
 * escape ad traps even if the page WebView is stuck. Close stays focusable for escape.
 *
 * DPAD_CENTER always tries __hammyActivateFocusedOrBlockingCta (native tapAt then
 * hard-click age/skip or focused CTA) before play-pause. When ageGateVisible /
 * skipVisible, LEFT/RIGHT do not seek.
 */
class PlayerActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var rootLayout: FrameLayout
    private lateinit var closeButton: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var injectGeneration = 0

    @Volatile
    private var skipAdsVisible = false

    @Volatile
    private var ageGateVisible = false

    private var playerPageUrl: String = ""
    private var initialWebFocusDone = false

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullScreenContainer: FrameLayout? = null

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * Bridge from injected JS → Kotlin so key handling can gate on age-gate /
     * Skip Ads visibility without racing evaluateJavascript round-trips.
     */
    inner class HammyBridge {
        @JavascriptInterface
        fun skipVisible(visible: Boolean) {
            skipAdsVisible = visible
        }

        @JavascriptInterface
        fun ageGateVisible(visible: Boolean) {
            ageGateVisible = visible
            mainHandler.post { onAgeGateVisibleChanged(visible) }
        }

        /**
         * Aggregate blocking CTA signal (age or skip). Specific
         * [ageGateVisible] / [skipVisible] remain authoritative for key routing.
         */
        @JavascriptInterface
        fun blockingCtaVisible(visible: Boolean) {
            // Mirror only when specifics have not already reported; prefer age/skip hooks.
            if (visible && !ageGateVisible && !skipAdsVisible) {
                skipAdsVisible = true
            }
        }

        /**
         * Native tap at CSS viewport coords (from getBoundingClientRect center).
         * Converts to WebView view pixels via [WebView.getScale] and dispatches
         * ACTION_DOWN then ACTION_UP on the UI thread — stronger than JS click
         * for age-gate / Skip Ads CTAs that ignore synthetic events.
         * Invoked only from user OK path in lite assist (no auto-tap loops).
         */
        @JavascriptInterface
        fun tapAt(x: Double, y: Double) {
            mainHandler.post {
                if (!::webView.isInitialized) return@post
                try {
                    val scale = webView.scale.toDouble().coerceAtLeast(0.01)
                    // CSS client pixels → WebView widget coords. Scale covers zoom /
                    // overview; getBoundingClientRect is viewport-relative (no scroll add).
                    val viewX = (x * scale).toFloat()
                    val viewY = (y * scale).toFloat()
                    val downTime = SystemClock.uptimeMillis()
                    val down = MotionEvent.obtain(
                        downTime, downTime, MotionEvent.ACTION_DOWN, viewX, viewY, 0
                    )
                    val upTime = downTime + 50L
                    val up = MotionEvent.obtain(
                        downTime, upTime, MotionEvent.ACTION_UP, viewX, viewY, 0
                    )
                    webView.dispatchTouchEvent(down)
                    webView.dispatchTouchEvent(up)
                    down.recycle()
                    up.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

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
        playerPageUrl = pageUrl

        rootLayout = findViewById(R.id.playerRoot)
        webView = findViewById(R.id.playerWebView)
        closeButton = findViewById(R.id.playerCloseButton)
        closeButton.setOnClickListener { exitPlayer() }
        closeButton.isFocusable = true
        closeButton.isFocusableInTouchMode = true
        closeButton.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                if (ageGateVisible) {
                    activateFocusedOrBlockingCtaThenMaybeToggle()
                    true
                } else {
                    exitPlayer()
                    true
                }
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
        webView.visibility = View.VISIBLE
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.addJavascriptInterface(HammyBridge(), "HammyBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // No age-storage/cookie inject — that blanked the page.
                view?.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.visibility = View.VISIBLE
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
                if (view == null) {
                    // Null custom view would leave a black frame — ignore
                    try { callback?.onCustomViewHidden() } catch (_: Exception) {}
                    return
                }
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
                // Hide WebView only while a real custom view is showing; restored on hide
                webView.visibility = View.GONE
                applyImmersiveFullscreen()
            }

            override fun onHideCustomView() {
                hideCustomViewOnly()
            }
        }

        // Focus WebView on open; Close stays focusable for escape (UP from WebView).
        webView.requestFocus()
        rootLayout.post { focusWebViewNotClose() }

        webView.post {
            if (!::webView.isInitialized) return@post
            webView.visibility = View.VISIBLE
            webView.loadUrl(playerPageUrl)
            focusWebViewNotClose()
        }
    }

    private fun onAgeGateVisibleChanged(visible: Boolean) {
        // Keep WebView painting; do not disable Close (user needs escape).
        if (::webView.isInitialized) {
            webView.visibility = View.VISIBLE
        }
        if (visible) {
            focusWebViewNotClose()
        }
    }

    private fun focusWebViewNotClose() {
        if (!::webView.isInitialized) return
        if (::closeButton.isInitialized && closeButton.isFocused) {
            closeButton.clearFocus()
        }
        webView.requestFocus()
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
        // Never leave a black customView covering the player
        if (::webView.isInitialized) {
            webView.visibility = View.VISIBLE
            webView.bringToFront()
        }
        if (::closeButton.isInitialized) {
            closeButton.bringToFront()
        }
        applyImmersiveFullscreen()
    }

    /** Always leave the player and return to the grid — never trapped by customView/ads. */
    private fun exitPlayer() {
        if (customView != null) {
            hideCustomViewOnly()
        }
        if (::webView.isInitialized) {
            webView.visibility = View.VISIBLE
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
        if (hasFocus) {
            applyImmersiveFullscreen()
            if (::webView.isInitialized) {
                webView.visibility = View.VISIBLE
            }
            if (!initialWebFocusDone && ::webView.isInitialized) {
                initialWebFocusDone = true
                focusWebViewNotClose()
            }
        }
    }

    private fun schedulePlayerInjection() {
        val gen = ++injectGeneration
        val delays = longArrayOf(0L, 400L, 1200L, 2500L, 4500L, 7000L)
        for (delay in delays) {
            mainHandler.postDelayed({
                if (gen != injectGeneration) return@postDelayed
                if (!::webView.isInitialized) return@postDelayed
                webView.visibility = View.VISIBLE
                webView.evaluateJavascript(PlayerChromeJs.SCRIPT, null)
                webView.evaluateJavascript(FocusInjectJs.SCRIPT, null)
            }, delay)
        }
    }

    /**
     * Intercept keys before the WebView (which consumes DPAD for scroll/focus).
     * Seek / play-pause / exit / age-gate / Skip Ads must run here — onKeyDown is too late.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            // Back / Escape / Menu / Guide always leave the player
            if (keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_ESCAPE ||
                keyCode == KeyEvent.KEYCODE_MENU ||
                keyCode == KeyEvent.KEYCODE_GUIDE
            ) {
                exitPlayer()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                // Age gate: prefer CTA activate over Close exit when overlay is up.
                if (ageGateVisible) {
                    if (closeButton.isFocused) {
                        closeButton.clearFocus()
                        webView.requestFocus()
                    }
                    activateFocusedOrBlockingCtaThenMaybeToggle()
                    return true
                }
                if (closeButton.isFocused) {
                    exitPlayer()
                    return true
                }
                // Always try hard-click age/skip/focused CTA first (user OK only).
                // Toggle only if activate returns false.
                activateFocusedOrBlockingCtaThenMaybeToggle()
                return true
            }
            // Close focused: let Left/Right/Up/Down move focus normally (no seek)
            if (closeButton.isFocused &&
                (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
            ) {
                return super.dispatchKeyEvent(event)
            }
            // Age gate or Skip Ads visible: do not seek — keep JS focus on CTA
            if ((ageGateVisible || skipAdsVisible) &&
                (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
            ) {
                webView.evaluateJavascript(
                    "(function(){try{var el=document.querySelector('.hammy-escape-btn');" +
                        "if(el){try{el.focus({preventScroll:true});}catch(e){el.focus();}}}" +
                        "catch(e){}})();",
                    null
                )
                return true
            }
            if (isSeekBackKey(keyCode)) {
                seekVideo(-10)
                return true
            }
            if (isSeekForwardKey(keyCode)) {
                seekVideo(10)
                return true
            }
            // Up from WebView can move focus to Close for leanback escape
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && webView.hasFocus()) {
                closeButton.requestFocus()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Always run activate CTA JS on OK. Nested callback toggles play/pause only when
     * activate returns false (no age/skip/focused blocking control found).
     */
    private fun activateFocusedOrBlockingCtaThenMaybeToggle() {
        if (!::webView.isInitialized) return
        webView.evaluateJavascript(
            "(function(){try{" +
                "if(window.__hammyActivateFocusedOrBlockingCta){" +
                "return window.__hammyActivateFocusedOrBlockingCta();}" +
                "if(window.__hammyClickBlockingCta){return window.__hammyClickBlockingCta();}" +
                "if(window.__hammyClickAgeGate){return window.__hammyClickAgeGate();}" +
                "if(window.__hammyClickSkip){return window.__hammyClickSkip();}" +
                "}catch(e){}return false;})();"
        ) { result ->
            val activated = result == "true"
            if (!activated && ::webView.isInitialized && !ageGateVisible) {
                webView.evaluateJavascript(
                    "(function(){try{if(window.__hammyToggleVideo){" +
                        "return window.__hammyToggleVideo('native');}}catch(e){}return false;})();",
                    null
                )
            }
        }
    }

    private fun isSeekBackKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_MEDIA_REWIND ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS

    private fun isSeekForwardKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ||
            keyCode == KeyEvent.KEYCODE_MEDIA_NEXT

    private fun seekVideo(deltaSeconds: Int) {
        if (!::webView.isInitialized) return
        webView.evaluateJavascript(
            "(function(){try{if(window.__hammySeekVideo){return window.__hammySeekVideo($deltaSeconds);}}catch(e){}return false;})();",
            null
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        exitPlayer()
    }

    override fun onDestroy() {
        injectGeneration++
        skipAdsVisible = false
        ageGateVisible = false
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
