package com.personal.hammy

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var injectGeneration = 0

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

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                scheduleFocusInjection()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                // Catch SPA / soft navigations and late overlays after redirects
                scheduleFocusInjection()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress >= 85) {
                    scheduleFocusInjection()
                }
            }
        }

        buildShortcuts()

        val orientation = Prefs.getOrientation(this)
        val startUrl = intent.getStringExtra(EXTRA_URL) ?: orientation.homeUrl
        webView.loadUrl(startUrl)
    }

    private fun scheduleFocusInjection() {
        val gen = ++injectGeneration
        // Immediate + delayed retries for consent modals / late DOM
        val delays = longArrayOf(0L, 400L, 1200L, 2500L, 4500L)
        for (delay in delays) {
            mainHandler.postDelayed({
                if (gen != injectGeneration) return@postDelayed
                if (!::webView.isInitialized) return@postDelayed
                injectFocusHelpers()
            }, delay)
        }
    }

    private fun injectFocusHelpers() {
        webView.evaluateJavascript(FOCUS_INJECT_JS, null)
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
            setOnFocusChangeListener { v, hasFocus ->
                v.animate()
                    .scaleX(if (hasFocus) 1.08f else 1f)
                    .scaleY(if (hasFocus) 1.08f else 1f)
                    .setDuration(120)
                    .start()
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
        // Ask page to toggle play/pause when video/player is focused (debounced in JS)
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
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"

        // language=javascript
        private val FOCUS_INJECT_JS: String = buildFocusJs()

        private fun buildFocusJs(): String {
            // Built without fragile escapes; newlines via String.fromCharCode(10)
            return """
(function(){
  if (window.__hammyFocusV2) {
    try { window.__hammyFocusRefresh && window.__hammyFocusRefresh(); } catch(e) {}
    return;
  }
  window.__hammyFocusV2 = true;

  var STYLE_ID = 'hammy-focus-css';
  var RING_ID = 'hammy-focus-ring';
  var HINT_ID = 'hammy-video-hint';
  var PAD = 6;
  var lastToggleAt = 0;

  function cssText() {
    var nl = String.fromCharCode(10);
    return [
      '*:focus, *:focus-visible {',
      '  outline: 5px solid #39FF14 !important;',
      '  outline-offset: 3px !important;',
      '  box-shadow: 0 0 0 3px #00E5FF, 0 0 18px 4px rgba(57,255,20,0.85) !important;',
      '}',
      'a:focus, button:focus, [role="button"]:focus, input:focus, select:focus, textarea:focus,',
      '[tabindex]:focus, .btn:focus, [class*="button"]:focus, [class*="Button"]:focus,',
      '[class*="consent"]:focus, [class*="cookie"]:focus, [class*="Accept"]:focus {',
      '  outline: 6px solid #00E5FF !important;',
      '  outline-offset: 4px !important;',
      '  box-shadow: 0 0 0 4px #39FF14, 0 0 22px 6px rgba(0,229,255,0.9) !important;',
      '  position: relative; z-index: 2147483646 !important;',
      '}',
      'video:focus, video:focus-visible {',
      '  outline: 6px solid #39FF14 !important;',
      '  outline-offset: 4px !important;',
      '  box-shadow: 0 0 0 4px #00E5FF, 0 0 28px 8px rgba(57,255,20,0.9) !important;',
      '}',
      'video::-webkit-media-controls-play-button:focus,',
      'video::-webkit-media-controls-fullscreen-button:focus,',
      'video::-webkit-media-controls-mute-button:focus,',
      'video::-webkit-media-controls-timeline:focus,',
      'video::-webkit-media-controls-volume-slider:focus {',
      '  outline: 5px solid #39FF14 !important;',
      '  box-shadow: 0 0 16px 4px rgba(0,229,255,0.95) !important;',
      '  transform: scale(1.25);',
      '}',
      '[class*="play"]:focus, [class*="Play"]:focus, [aria-label*="play" i]:focus,',
      '[aria-label*="pause" i]:focus, [class*="control"]:focus, [class*="Control"]:focus,',
      '[class*="player"] button:focus, .xh-player button:focus, .player-container button:focus {',
      '  outline: 5px solid #39FF14 !important;',
      '  outline-offset: 3px !important;',
      '  box-shadow: 0 0 0 3px #00E5FF, 0 0 20px 5px rgba(57,255,20,0.9) !important;',
      '  transform: scale(1.15);',
      '}',
      '#' + RING_ID + ' {',
      '  position: fixed !important; pointer-events: none !important;',
      '  z-index: 2147483647 !important; box-sizing: border-box !important;',
      '  border: 5px solid #39FF14 !important; border-radius: 10px !important;',
      '  box-shadow: 0 0 0 3px #00E5FF, 0 0 24px 6px rgba(57,255,20,0.85) !important;',
      '  background: transparent !important; display: none;',
      '  transition: top 0.05s linear, left 0.05s linear, width 0.05s linear, height 0.05s linear;',
      '}',
      '#' + HINT_ID + ' {',
      '  position: fixed !important; left: 50% !important; bottom: 12% !important;',
      '  transform: translateX(-50%) !important; z-index: 2147483647 !important;',
      '  pointer-events: none !important; padding: 14px 28px !important;',
      '  background: rgba(0,0,0,0.82) !important; color: #fff !important;',
      '  font: 600 20px/1.3 system-ui,sans-serif !important; border-radius: 12px !important;',
      '  border: 3px solid #39FF14 !important; box-shadow: 0 0 18px rgba(0,229,255,0.7) !important;',
      '  display: none; white-space: nowrap !important;',
      '}'
    ].join(nl);
  }

  function ensureStyle() {
    var s = document.getElementById(STYLE_ID);
    if (!s) {
      s = document.createElement('style');
      s.id = STYLE_ID;
      (document.head || document.documentElement).appendChild(s);
    }
    s.textContent = cssText();
  }

  function ensureRing() {
    var r = document.getElementById(RING_ID);
    if (!r) {
      r = document.createElement('div');
      r.id = RING_ID;
      r.setAttribute('aria-hidden', 'true');
      (document.body || document.documentElement).appendChild(r);
    }
    return r;
  }

  function ensureHint() {
    var h = document.getElementById(HINT_ID);
    if (!h) {
      h = document.createElement('div');
      h.id = HINT_ID;
      h.setAttribute('aria-hidden', 'true');
      h.textContent = 'OK / Enter — Play / Pause';
      (document.body || document.documentElement).appendChild(h);
    }
    return h;
  }

  function isVisible(el) {
    if (!el || el === document.body || el === document.documentElement) return false;
    var rect = el.getBoundingClientRect();
    if (rect.width < 2 && rect.height < 2) return false;
    var st = window.getComputedStyle(el);
    if (st.visibility === 'hidden' || st.display === 'none' || st.opacity === '0') return false;
    return true;
  }

  function findRelatedVideo(el) {
    if (!el) return null;
    if (el.tagName && el.tagName.toLowerCase() === 'video') return el;
    if (el.closest) {
      var host = el.closest('video, [class*="player"], [class*="Player"], .xh-player, .player-container');
      if (host) {
        if (host.tagName && host.tagName.toLowerCase() === 'video') return host;
        return host.querySelector('video');
      }
    }
    return null;
  }

  function updateRing() {
    var el = document.activeElement;
    var ring = ensureRing();
    var hint = ensureHint();
    if (!isVisible(el)) {
      ring.style.display = 'none';
      hint.style.display = 'none';
      return;
    }
    var rect = el.getBoundingClientRect();
    ring.style.display = 'block';
    ring.style.top = Math.max(0, rect.top - PAD) + 'px';
    ring.style.left = Math.max(0, rect.left - PAD) + 'px';
    ring.style.width = Math.max(8, rect.width + PAD * 2) + 'px';
    ring.style.height = Math.max(8, rect.height + PAD * 2) + 'px';

    var v = findRelatedVideo(el);
    if (v) {
      hint.style.display = 'block';
      hint.textContent = v.paused ? 'OK / Enter — Play' : 'OK / Enter — Pause';
    } else {
      hint.style.display = 'none';
    }
  }

  function prepVideos() {
    var videos = document.querySelectorAll('video');
    for (var i = 0; i < videos.length; i++) {
      var v = videos[i];
      if (!v.hasAttribute('tabindex')) v.setAttribute('tabindex', '0');
      v.setAttribute('data-hammy-focus', '1');
    }
  }

  window.__hammyToggleVideo = function(source) {
    var now = Date.now();
    if (now - lastToggleAt < 350) return false;
    var el = document.activeElement;
    var v = findRelatedVideo(el);
    if (!v) return false;
    lastToggleAt = now;
    try {
      if (v.paused) { v.play(); } else { v.pause(); }
      updateRing();
      return true;
    } catch (e) { return false; }
  };

  function onKey(e) {
    updateRing();
    var code = e.keyCode || e.which;
    // 13 Enter, 23 DPAD_CENTER (Android WebView), 32 Space
    if (code === 13 || code === 23 || code === 32) {
      var el = document.activeElement;
      if (findRelatedVideo(el)) {
        if (window.__hammyToggleVideo('js')) {
          e.preventDefault();
          e.stopPropagation();
        }
      }
    }
  }

  function refresh() {
    ensureStyle();
    if (document.body) {
      ensureRing();
      ensureHint();
    }
    prepVideos();
    updateRing();
  }
  window.__hammyFocusRefresh = refresh;

  ensureStyle();
  if (document.body) {
    ensureRing();
    ensureHint();
  } else {
    document.addEventListener('DOMContentLoaded', function() {
      ensureRing();
      ensureHint();
      refresh();
    });
  }

  document.addEventListener('focusin', updateRing, true);
  document.addEventListener('focusout', function(){ setTimeout(updateRing, 0); }, true);
  document.addEventListener('keydown', onKey, true);
  document.addEventListener('keyup', updateRing, true);
  window.addEventListener('scroll', updateRing, true);
  window.addEventListener('resize', updateRing, true);

  try {
    var mo = new MutationObserver(function() {
      prepVideos();
      ensureStyle();
      updateRing();
    });
    mo.observe(document.documentElement, { childList: true, subtree: true });
  } catch (e) {}

  prepVideos();
  updateRing();
  setTimeout(refresh, 300);
  setTimeout(refresh, 1000);
  setTimeout(refresh, 2500);
})();
            """.trimIndent()
        }
    }
}
