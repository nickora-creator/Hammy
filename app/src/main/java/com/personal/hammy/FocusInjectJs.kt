package com.personal.hammy

/** Shared WebView focus-ring / play-pause / seek helpers for the player screen. */
object FocusInjectJs {
    val SCRIPT: String = buildFocusJs()

    private fun buildFocusJs(): String {
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
  var lastSeekAt = 0;
  var hintHideTimer = null;
  var HINT_HIDE_MS = 3000;
  var TOAST_HIDE_MS = 1000;
  var DEFAULT_HINT = 'OK / Enter — Play / Pause';

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
      h.textContent = DEFAULT_HINT;
      (document.body || document.documentElement).appendChild(h);
    }
    return h;
  }

  function clearHintTimer() {
    if (hintHideTimer) {
      clearTimeout(hintHideTimer);
      hintHideTimer = null;
    }
  }

  function hideHint() {
    clearHintTimer();
    var h = document.getElementById(HINT_ID);
    if (h) h.style.display = 'none';
  }

  function showHintBrief(text, hideMs) {
    var h = ensureHint();
    h.textContent = text || DEFAULT_HINT;
    h.style.display = 'block';
    clearHintTimer();
    hintHideTimer = setTimeout(function() {
      hintHideTimer = null;
      h.style.display = 'none';
    }, hideMs || HINT_HIDE_MS);
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

  function findMainVideo() {
    var videos = document.querySelectorAll('video');
    var best = null;
    var bestArea = 0;
    for (var i = 0; i < videos.length; i++) {
      var v = videos[i];
      var rect = v.getBoundingClientRect();
      var area = Math.max(0, rect.width) * Math.max(0, rect.height);
      if (area < 4) continue;
      if (!best || area > bestArea) {
        best = v;
        bestArea = area;
      }
    }
    if (best) return best;
    return videos.length ? videos[0] : null;
  }

  function updateRing() {
    var el = document.activeElement;
    var ring = ensureRing();
    ensureHint();
    if (!isVisible(el)) {
      ring.style.display = 'none';
      return;
    }
    var rect = el.getBoundingClientRect();
    ring.style.display = 'block';
    ring.style.top = Math.max(0, rect.top - PAD) + 'px';
    ring.style.left = Math.max(0, rect.left - PAD) + 'px';
    ring.style.width = Math.max(8, rect.width + PAD * 2) + 'px';
    ring.style.height = Math.max(8, rect.height + PAD * 2) + 'px';
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
    var v = findRelatedVideo(el) || findMainVideo();
    if (!v) return false;
    lastToggleAt = now;
    try {
      if (v.paused) { v.play(); } else { v.pause(); }
      updateRing();
      showHintBrief(v.paused ? 'OK / Enter — Play' : 'OK / Enter — Pause', HINT_HIDE_MS);
      return true;
    } catch (e) { return false; }
  };

  window.__hammySeekVideo = function(deltaSeconds) {
    var now = Date.now();
    if (now - lastSeekAt < 120) return false;
    var delta = Number(deltaSeconds);
    if (!isFinite(delta) || delta === 0) return false;
    var v = findRelatedVideo(document.activeElement) || findMainVideo();
    if (!v) return false;
    var dur = v.duration;
    if (!isFinite(dur) || dur <= 0) dur = Number.MAX_VALUE;
    var next = v.currentTime + delta;
    if (next < 0) next = 0;
    if (next > dur) next = dur;
    try {
      v.currentTime = next;
      lastSeekAt = now;
      updateRing();
      var label = (delta > 0 ? '+' : '') + Math.round(delta) + 's';
      showHintBrief(label, TOAST_HIDE_MS);
      return true;
    } catch (e) { return false; }
  };

  function softLockPageScroll() {
    try {
      if (!findMainVideo()) return;
      if (document.body) document.body.style.overflow = 'hidden';
      if (document.documentElement) document.documentElement.style.overflow = 'hidden';
    } catch (e) {}
  }

  function onKey(e) {
    updateRing();
    var code = e.keyCode || e.which;
    var key = e.key || '';
    // While site Skip Ads is visible, do not seek / toggle — keep focus on skip
    if (window.__hammySkipVisible) {
      if (code === 37 || code === 39 || key === 'ArrowLeft' || key === 'ArrowRight') {
        try {
          var skipEl = document.querySelector('.hammy-escape-btn');
          if (skipEl) {
            try { skipEl.focus({ preventScroll: true }); } catch (eF) { skipEl.focus(); }
          }
        } catch (eS) {}
        e.preventDefault();
        e.stopPropagation();
        return;
      }
      if (code === 13 || code === 23 || code === 32) {
        try {
          if (window.__hammyClickSkip && window.__hammyClickSkip()) {
            e.preventDefault();
            e.stopPropagation();
          }
        } catch (eC) {}
        return;
      }
    }
    // ArrowLeft / ArrowRight: seek when a video exists (stop page scroll)
    if (code === 37 || code === 39 || key === 'ArrowLeft' || key === 'ArrowRight') {
      if (findRelatedVideo(document.activeElement) || findMainVideo()) {
        e.preventDefault();
        e.stopPropagation();
        var delta = (code === 37 || key === 'ArrowLeft') ? -10 : 10;
        window.__hammySeekVideo(delta);
      }
      return;
    }
    // 13 Enter, 23 DPAD_CENTER (Android WebView), 32 Space
    if (code === 13 || code === 23 || code === 32) {
      var el = document.activeElement;
      if (findRelatedVideo(el) || findMainVideo()) {
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
    softLockPageScroll();
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
  softLockPageScroll();
  updateRing();
  // Brief initial OK/Enter hint, then auto-hide
  showHintBrief(DEFAULT_HINT, HINT_HIDE_MS);
  setTimeout(refresh, 300);
  setTimeout(refresh, 1000);
  setTimeout(refresh, 2500);
})();
            
        """.trimIndent()
    }
}
