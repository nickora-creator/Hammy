package com.personal.hammy

/**
 * Lite player assist injected into the official video page WebView.
 *
 * Goal: let the site's own player work like a normal WebView. Find the largest
 * <video>, gently try play() + scroll-into-view, and make visible Skip/Close-ad
 * controls focusable with a high z-index outline. Does NOT reparent the video,
 * hide body siblings, force fullscreen CSS, or run an aggressive ad classifier
 * that can mark real content as ads.
 *
 * Focus-ring / Center play-pause stay in [FocusInjectJs]. Native Close + Back/Menu
 * always finish() from PlayerActivity.
 */
object PlayerChromeJs {
    val SCRIPT: String = buildScript()

    private fun buildScript(): String {
        return """
(function(){
  if (window.__hammyChromeV4) {
    try { window.__hammyChromeRefresh && window.__hammyChromeRefresh(); } catch(e) {}
    return;
  }
  window.__hammyChromeV4 = true;
  // Clear stale aggressive chrome flags so older injects cannot fight the lite assist
  try { delete window.__hammyChromeV3; } catch(e) {}
  try { delete window.__hammyChromeV2; } catch(e) {}

  var STYLE_ID = 'hammy-chrome-lite-css';
  var unmuted = false;
  var skipAutoClicked = false;
  var lastSkipHelpAt = 0;
  var lastPlayAt = 0;
  var moTimer = null;

  function ensureLiteStyle() {
    var s = document.getElementById(STYLE_ID);
    if (!s) {
      s = document.createElement('style');
      s.id = STYLE_ID;
      (document.head || document.documentElement).appendChild(s);
    }
    var nl = String.fromCharCode(10);
    s.textContent = [
      /* Keep focus helpers above page chrome; no body-sibling hide, no forced FS */
      '#hammy-focus-ring, #hammy-video-hint { z-index: 2147483647 !important; }',
      /* Visible Skip / Close-ad controls: focusable outline + high z-index */
      '.hammy-escape-btn {',
      '  outline: 4px solid #00E5FF !important;',
      '  outline-offset: 3px !important;',
      '  box-shadow: 0 0 0 2px #39FF14, 0 0 16px 4px rgba(0,229,255,0.75) !important;',
      '  position: relative !important;',
      '  z-index: 2147483600 !important;',
      '  pointer-events: auto !important;',
      '}'
    ].join(nl);
  }

  function stripAggressiveChrome() {
    try {
      document.documentElement.classList.remove('hammy-fs');
      if (document.body) document.body.classList.remove('hammy-fs');
    } catch (e0) {}
    try {
      var old = document.getElementById('hammy-chrome-css');
      if (old && old.parentNode) old.parentNode.removeChild(old);
    } catch (e1) {}
    try {
      var mains = document.querySelectorAll('.hammy-main-video, .hammy-player-root');
      for (var i = 0; i < mains.length; i++) {
        try {
          mains[i].classList.remove('hammy-main-video');
          mains[i].classList.remove('hammy-player-root');
        } catch (e2) {}
      }
    } catch (e3) {}
  }

  function videoArea(v) {
    if (!v || !v.getBoundingClientRect) return 0;
    try {
      var r = v.getBoundingClientRect();
      var w = Math.max(r.width || 0, v.videoWidth || 0);
      var h = Math.max(r.height || 0, v.videoHeight || 0);
      return w * h;
    } catch (e) {
      return 0;
    }
  }

  function findLargestVideo() {
    var videos = document.querySelectorAll('video');
    var best = null;
    var bestArea = 0;
    for (var i = 0; i < videos.length; i++) {
      var v = videos[i];
      var area = videoArea(v);
      // Prefer ready / sourced videos when areas are similar
      var bonus = 0;
      try {
        if (v.readyState >= 2) bonus += 50000;
        else if (v.currentSrc && v.currentSrc.length > 0) bonus += 20000;
        if (!v.paused && !v.ended) bonus += 10000;
      } catch (e0) {}
      var score = area + bonus;
      if (score > bestArea) {
        bestArea = score;
        best = v;
      }
    }
    return best;
  }

  function tryPlay(video) {
    if (!video) return;
    var now = Date.now();
    if (now - lastPlayAt < 1500) return;
    lastPlayAt = now;
    try {
      video.setAttribute('playsinline', 'true');
      video.setAttribute('webkit-playsinline', 'true');
      if (!video.hasAttribute('tabindex')) video.setAttribute('tabindex', '0');
      try {
        video.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'smooth' });
      } catch (eScroll) {
        try { video.scrollIntoView(true); } catch (eScroll2) {}
      }
      var wasMuted = video.muted;
      if (video.paused) {
        video.muted = true;
        var p = video.play();
        if (p && p.then) {
          p.then(function() {
            if (!unmuted) {
              setTimeout(function() {
                try {
                  video.muted = false;
                  video.volume = 1;
                  unmuted = true;
                } catch (e2) {}
              }, 400);
            }
          }).catch(function() {
            try { video.muted = wasMuted; } catch (e3) {}
          });
        } else if (!unmuted) {
          setTimeout(function() {
            try { video.muted = false; video.volume = 1; unmuted = true; } catch (e4) {}
          }, 400);
        }
      } else if (!unmuted && video.muted) {
        try { video.muted = false; video.volume = 1; unmuted = true; } catch (e5) {}
      }
    } catch (e) {}
  }

  function normalizeLabel(el) {
    if (!el) return '';
    var bits = [];
    try {
      bits.push(el.innerText || el.textContent || '');
      bits.push(el.getAttribute('aria-label') || '');
      bits.push(el.getAttribute('title') || '');
      bits.push(el.getAttribute('value') || '');
      bits.push(el.id || '');
      bits.push((el.className && el.className.toString) ? el.className.toString() : '');
    } catch (e) {}
    return bits.join(' ').replace(/\s+/g, ' ').trim().toLowerCase();
  }

  function isVisibleClickable(el) {
    if (!el || !el.getBoundingClientRect) return false;
    try {
      var r = el.getBoundingClientRect();
      if (r.width < 6 || r.height < 6) return false;
      if (r.bottom < 0 || r.right < 0 || r.top > (window.innerHeight + 20) || r.left > (window.innerWidth + 20)) return false;
      var st = window.getComputedStyle(el);
      if (st.display === 'none' || st.visibility === 'hidden' || parseFloat(st.opacity) === 0) return false;
      if (st.pointerEvents === 'none') return false;
      return true;
    } catch (e) {
      return false;
    }
  }

  function findEscapeControls() {
    var out = [];
    var candidates = [];
    try {
      candidates = document.querySelectorAll(
        'button, a, [role="button"], input[type="button"], div[tabindex], span[tabindex], [class*="skip" i], [id*="skip" i]'
      );
    } catch (e) {
      return out;
    }
    var skipRe = /^(skip(\s+ad)?|skip\s+advertisement|close(\s+ad)?|continue|dismiss|not\s+now|\u00d7|x)$/i;
    var softSkipRe = /\b(skip(\s+ad)?|skip\s+advertisement|close(\s+ad)?|continue to video|dismiss)\b/i;
    for (var i = 0; i < candidates.length; i++) {
      var el = candidates[i];
      if (!isVisibleClickable(el)) continue;
      var label = normalizeLabel(el);
      if (!label) continue;
      var exact = skipRe.test(label.trim()) || label.trim() === 'x' || label.trim() === '\u00d7';
      var soft = softSkipRe.test(label);
      var clsIdHint = /skip|close-ad|ad-close|dismiss/i.test(label);
      if (!(exact || soft || clsIdHint)) continue;
      if (/\b(accept all|agree|cookie|subscribe|sign in|login|register)\b/i.test(label) && !/\bskip\b/i.test(label)) continue;
      var clearlySkipAd = /\bskip(\s+ad|\s+advertisement)?\b/i.test(label);
      out.push({ el: el, exact: exact || clsIdHint, clearlySkipAd: clearlySkipAd, label: label });
    }
    return out;
  }

  function helpEscapeAd() {
    var now = Date.now();
    if (now - lastSkipHelpAt < 800) return;
    lastSkipHelpAt = now;
    var controls = findEscapeControls();
    if (!controls.length) return;

    var best = controls[0];
    for (var i = 0; i < controls.length; i++) {
      if (controls[i].clearlySkipAd) { best = controls[i]; break; }
      if (controls[i].exact) best = controls[i];
    }

    try {
      best.el.setAttribute('tabindex', '0');
      best.el.classList.add('hammy-escape-btn');
      best.el.focus();
    } catch (e1) {}

    // Optional: auto-click once only when clearly labeled Skip Ad
    if (!skipAutoClicked && best.clearlySkipAd) {
      skipAutoClicked = true;
      try {
        best.el.click();
      } catch (e2) {
        try {
          best.el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
        } catch (e3) {}
      }
    }
  }

  function apply() {
    ensureLiteStyle();
    stripAggressiveChrome();

    var video = findLargestVideo();
    if (video) {
      tryPlay(video);
    }

    // Always surface Skip/Close-ad if visible — no aggressive classifier
    helpEscapeAd();
  }

  window.__hammyChromeRefresh = apply;
  window.__hammyHelpEscapeAd = helpEscapeAd;

  apply();
  setTimeout(apply, 400);
  setTimeout(apply, 1200);
  setTimeout(apply, 2500);
  setTimeout(apply, 5000);

  try {
    var mo = new MutationObserver(function() {
      if (moTimer) clearTimeout(moTimer);
      moTimer = setTimeout(function() {
        moTimer = null;
        apply();
      }, 700);
    });
    mo.observe(document.documentElement, { childList: true, subtree: true });
  } catch (e) {}
})();
        """.trimIndent()
    }
}
