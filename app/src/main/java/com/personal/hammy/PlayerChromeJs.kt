package com.personal.hammy

/**
 * Injected into the official video page WebView: hide site chrome, scale the main
 * player/video to fill the viewport, attempt autoplay + fullscreen. Idempotent with
 * retries (same pattern as [FocusInjectJs]). Does not scrape CDNs or change media URLs.
 *
 * Ad-aware: never promotes preroll/IMA/VAST ad videos to fullscreen, never hides
 * Skip/Close controls, and can undo chrome-hide if locked onto an ad.
 */
object PlayerChromeJs {
    val SCRIPT: String = buildScript()

    private fun buildScript(): String {
        return """
(function(){
  if (window.__hammyChromeV3) {
    try { window.__hammyChromeRefresh && window.__hammyChromeRefresh(); } catch(e) {}
    return;
  }
  window.__hammyChromeV3 = true;
  // Drop stale v2 flag so a re-inject of older script cannot fight us
  try { delete window.__hammyChromeV2; } catch(e) {}

  var STYLE_ID = 'hammy-chrome-css';
  var lastFsAt = 0;
  var unmuted = false;
  var playAttempts = 0;
  var moTimer = null;
  var reparented = false;
  var lockedContentVideo = null;
  var skipAutoClicked = false;
  var lastSkipHelpAt = 0;
  var applyCount = 0;
  var moQuietUntil = 0;

  var AD_HINT_RE = /(?:^|[^a-z])(ad|ads|advert|advertisement|preroll|pre-roll|midroll|ima|vast|vmap|adsbygoogle|sponsor|promo|promoted|google_ads|adcontainer|ad-container|ad_wrapper|adwrapper|videoad|video-ad)(?:[^a-z]|$)/i;

  function haystackFor(el) {
    if (!el) return '';
    var parts = [];
    try {
      if (el.id) parts.push(String(el.id));
      if (el.className) parts.push(String(el.className));
      if (el.getAttribute) {
        var attrs = ['data-ad', 'data-ads', 'data-ima', 'data-vast', 'aria-label', 'name', 'title', 'src', 'data-src'];
        for (var i = 0; i < attrs.length; i++) {
          var v = el.getAttribute(attrs[i]);
          if (v) parts.push(String(v));
        }
      }
      if (el.tagName === 'VIDEO' || el.tagName === 'SOURCE') {
        try { if (el.currentSrc) parts.push(String(el.currentSrc)); } catch (e1) {}
        try { if (el.src) parts.push(String(el.src)); } catch (e2) {}
      }
    } catch (e) {}
    return parts.join(' ').toLowerCase();
  }

  function looksLikeAdNode(el) {
    if (!el || el === document.body || el === document.documentElement) return false;
    try {
      if (el.classList && (el.classList.contains('hammy-player-root') || el.classList.contains('hammy-main-video'))) {
        return false;
      }
      var h = haystackFor(el);
      if (h && AD_HINT_RE.test(h)) return true;
      // Common IMA / Google ad iframe hosts
      if (el.tagName === 'IFRAME') {
        var src = '';
        try { src = String(el.src || el.getAttribute('src') || '').toLowerCase(); } catch (e0) {}
        if (src.indexOf('doubleclick') >= 0 || src.indexOf('googlesyndication') >= 0 ||
            src.indexOf('imasdk') >= 0 || src.indexOf('/ads') >= 0 ||
            src.indexOf('pagead') >= 0 || src.indexOf('adservice') >= 0) {
          return true;
        }
      }
    } catch (e) {}
    return false;
  }

  function isInsideAdContainer(el) {
    if (!el) return false;
    var cur = el;
    for (var d = 0; d < 12 && cur && cur !== document.body; d++) {
      if (looksLikeAdNode(cur)) return true;
      cur = cur.parentElement;
    }
    return false;
  }

  function isAdVideo(video) {
    if (!video) return true;
    try {
      if (isInsideAdContainer(video)) return true;
      var h = haystackFor(video);
      if (h && AD_HINT_RE.test(h)) return true;
      // Short looping clips are typical prerolls when a longer content video also exists
      var dur = 0;
      try { dur = video.duration || 0; } catch (e1) { dur = 0; }
      if (isFinite(dur) && dur > 0 && dur < 45 && video.loop) return true;
    } catch (e) {}
    return false;
  }

  function adOverlayPresent() {
    var sels = [
      '[class*="skip" i]', '[id*="skip" i]',
      '[class*="ima-" i]', '[id*="ima-" i]', '[class*="ima_" i]',
      '[class*="vast" i]', '[id*="vast" i]',
      '[class*="preroll" i]', '[id*="preroll" i]',
      '[class*="ad-overlay" i]', '[class*="adoverlay" i]',
      '[class*="videoAd" i]', '[class*="video-ad" i]',
      '[class*="adsbygoogle" i]', 'ins.adsbygoogle',
      'iframe[src*="doubleclick" i]', 'iframe[src*="imasdk" i]',
      'iframe[src*="googlesyndication" i]', 'iframe[src*="pagead" i]'
    ];
    for (var i = 0; i < sels.length; i++) {
      try {
        var nodes = document.querySelectorAll(sels[i]);
        for (var j = 0; j < nodes.length; j++) {
          var el = nodes[j];
          if (!el || !el.getBoundingClientRect) continue;
          if (el.id === 'hammy-focus-ring' || el.id === 'hammy-video-hint') continue;
          if (el.classList && el.classList.contains('hammy-player-root')) continue;
          var r = el.getBoundingClientRect();
          if (r.width < 8 || r.height < 8) continue;
          var st = window.getComputedStyle(el);
          if (st.display === 'none' || st.visibility === 'hidden' || parseFloat(st.opacity) === 0) continue;
          return true;
        }
      } catch (e) {}
    }
    // Also treat any currently playing short ad-like video as overlay
    try {
      var vids = document.querySelectorAll('video');
      for (var k = 0; k < vids.length; k++) {
        if (isAdVideo(vids[k])) {
          var vv = vids[k];
          var rr = vv.getBoundingClientRect();
          if (rr.width > 40 && rr.height > 40 && (!vv.paused || vv.readyState >= 2)) return true;
        }
      }
    } catch (e2) {}
    return false;
  }

  function isConsentVisible() {
    // Softened: only cookie/consent/gdpr/age-gate style nodes, never bare .modal/.overlay
    var sels = [
      '[id*="cookie" i]', '[class*="cookie" i]',
      '[id*="consent" i]', '[class*="consent" i]',
      '[id*="gdpr" i]', '[class*="gdpr" i]',
      '[class*="age-gate" i]', '[class*="agegate" i]', '[class*="AgeGate" i]',
      '[id*="age-verification" i]', '[class*="age-verification" i]',
      '[id*="agegate" i]', '[id*="age-gate" i]'
    ];
    for (var i = 0; i < sels.length; i++) {
      try {
        var nodes = document.querySelectorAll(sels[i]);
        for (var j = 0; j < nodes.length; j++) {
          var el = nodes[j];
          if (!el || !el.getBoundingClientRect) continue;
          if (el.id === 'hammy-focus-ring' || el.id === 'hammy-video-hint') continue;
          if (el.classList && el.classList.contains('hammy-player-root')) continue;
          var r = el.getBoundingClientRect();
          if (r.width < 80 || r.height < 40) continue;
          var st = window.getComputedStyle(el);
          if (st.display === 'none' || st.visibility === 'hidden' || parseFloat(st.opacity) === 0) continue;
          var txt = (el.textContent || '').toLowerCase();
          var looksLikeConsent =
            txt.indexOf('cookie') >= 0 || txt.indexOf('consent') >= 0 ||
            txt.indexOf('gdpr') >= 0 || txt.indexOf('accept') >= 0 ||
            txt.indexOf('agree') >= 0 || txt.indexOf('18') >= 0 ||
            txt.indexOf('age') >= 0 || txt.indexOf('terms') >= 0;
          if (!looksLikeConsent) continue;
          if (r.width * r.height > (window.innerWidth * window.innerHeight * 0.12)) return true;
        }
      } catch (e) {}
    }
    return false;
  }

  function findMainVideo() {
    // Prefer a previously locked non-ad content video if still in DOM
    if (lockedContentVideo && lockedContentVideo.isConnected && !isAdVideo(lockedContentVideo)) {
      return lockedContentVideo;
    }

    var videos = document.querySelectorAll('video');
    var best = null;
    var bestScore = -1;
    var bestNonAd = null;
    var bestNonAdScore = -1;

    for (var i = 0; i < videos.length; i++) {
      var v = videos[i];
      var ad = isAdVideo(v);
      var r = v.getBoundingClientRect();
      var w = Math.max(r.width, v.videoWidth || 0);
      var h = Math.max(r.height, v.videoHeight || 0);
      var area = w * h;
      var readyBonus = (v.readyState >= 1 || (v.currentSrc && v.currentSrc.length > 0)) ? 100000 : 0;
      var dur = 0;
      try { dur = v.duration || 0; } catch (e0) { dur = 0; }
      // Prefer longer videos (content) over short prerolls
      var durBonus = 0;
      if (isFinite(dur) && dur > 0) {
        if (dur >= 60) durBonus = 50000 + Math.min(dur, 3600);
        else if (dur >= 45) durBonus = 20000;
        else durBonus = Math.max(0, dur);
      }
      var playingBonus = (!v.paused && !v.ended) ? 25000 : 0;
      var score = area + readyBonus + durBonus + playingBonus;

      if (score > bestScore) {
        bestScore = score;
        best = v;
      }
      if (!ad && score > bestNonAdScore) {
        bestNonAdScore = score;
        bestNonAd = v;
      }
    }

    if (bestNonAd) {
      // Lock once we have a reasonably ready non-ad video
      if (videoIsReal(bestNonAd) || bestNonAdScore > 50000) {
        lockedContentVideo = bestNonAd;
      }
      return bestNonAd;
    }
    // No non-ad candidate — return null so we do NOT promote an ad
    return null;
  }

  function videoIsReal(video) {
    if (!video) return false;
    try {
      if (video.readyState >= 2) return true;
      if (video.currentSrc && video.currentSrc.length > 0) return true;
      if (!video.paused && !video.ended) return true;
      if (video.networkState >= 2 && video.videoWidth > 0) return true;
    } catch (e) {}
    return false;
  }

  function findPlayerContainer(video) {
    if (!video) return null;
    var sels = [
      '.xh-player', '.player-container', '#player-container', '#player',
      '[class*="vp-player"]', '[class*="video-player"]', '[class*="VideoPlayer"]',
      '[class*="player-wrap"]', '[class*="player_container"]', '[id*="video-player"]',
      '.plyr', '[data-player]', '[class*="html5-player"]'
    ];
    for (var i = 0; i < sels.length; i++) {
      try {
        var c = video.closest(sels[i]);
        if (c && !isInsideAdContainer(c)) return c;
      } catch (e) {}
    }
    var el = video.parentElement;
    for (var d = 0; d < 8 && el && el !== document.body; d++) {
      if (isInsideAdContainer(el)) {
        el = el.parentElement;
        continue;
      }
      var cls = (el.className && el.className.toString) ? el.className.toString().toLowerCase() : '';
      var id = (el.id || '').toLowerCase();
      if (cls.indexOf('player') >= 0 || id.indexOf('player') >= 0 ||
          cls.indexOf('video') >= 0 || id.indexOf('video') >= 0) {
        return el;
      }
      el = el.parentElement;
    }
    return video.parentElement || video;
  }

  function chromeCss(hideChrome) {
    var nl = String.fromCharCode(10);
    var parts = [
      'html.hammy-fs, html.hammy-fs body {',
      '  background: #000 !important; overflow: hidden !important; margin: 0 !important; padding: 0 !important;',
      '  width: 100% !important; height: 100% !important;',
      '}',
      'html.hammy-fs video.hammy-main-video {',
      '  position: fixed !important; top: 0 !important; left: 0 !important;',
      '  width: 100vw !important; height: 100vh !important;',
      '  max-width: 100vw !important; max-height: 100vh !important;',
      '  object-fit: contain !important; z-index: 2147483000 !important;',
      '  background: #000 !important; margin: 0 !important; padding: 0 !important;',
      '  border: none !important; border-radius: 0 !important;',
      '}',
      'html.hammy-fs .hammy-player-root {',
      '  position: fixed !important; top: 0 !important; left: 0 !important;',
      '  width: 100vw !important; height: 100vh !important;',
      '  max-width: 100vw !important; max-height: 100vh !important;',
      '  z-index: 2147482990 !important; background: #000 !important;',
      '  margin: 0 !important; padding: 0 !important; border: none !important;',
      '  transform: none !important; inset: 0 !important;',
      '}',
      'html.hammy-fs .hammy-player-root video {',
      '  width: 100% !important; height: 100% !important; object-fit: contain !important;',
      '}',
      /* Keep focus helpers + escape UI above chrome-hide */
      '#hammy-focus-ring, #hammy-video-hint { z-index: 2147483647 !important; }',
      /* Never hide skip/close/dismiss style controls even under aggressive hide */
      'html.hammy-fs [class*="skip" i], html.hammy-fs [id*="skip" i],',
      'html.hammy-fs [aria-label*="skip" i], html.hammy-fs [aria-label*="Skip" i],',
      'html.hammy-fs [class*="close-ad" i], html.hammy-fs [class*="ad-close" i],',
      'html.hammy-fs [class*="dismiss" i], html.hammy-fs button[title*="close" i],',
      'html.hammy-fs [class*="ima-controls" i], html.hammy-fs .ima-controls-div {',
      '  display: block !important; visibility: visible !important; opacity: 1 !important;',
      '  pointer-events: auto !important; z-index: 2147483600 !important;',
      '}'
    ];
    if (hideChrome) {
      parts = parts.concat([
        'html.hammy-fs body > *:not(.hammy-player-root):not(#hammy-focus-ring):not(#hammy-video-hint):not(#hammy-chrome-css):not(script):not(style):not(link) {',
        '  display: none !important; visibility: hidden !important; pointer-events: none !important;',
        '}',
        'html.hammy-fs header, html.hammy-fs footer, html.hammy-fs nav, html.hammy-fs aside,',
        'html.hammy-fs [class*="header" i], html.hammy-fs [class*="Header" i],',
        'html.hammy-fs [class*="footer" i], html.hammy-fs [id*="footer" i],',
        'html.hammy-fs [class*="sidebar" i], html.hammy-fs [class*="side-bar" i],',
        'html.hammy-fs [class*="comment" i], html.hammy-fs [id*="comment" i],',
        'html.hammy-fs [class*="related" i], html.hammy-fs [class*="Recommended" i],',
        'html.hammy-fs [class*="recommend" i], html.hammy-fs [class*="suggestions" i],',
        'html.hammy-fs [class*="advert" i], html.hammy-fs [class*="adsby" i],',
        'html.hammy-fs [id*="ad-" i], html.hammy-fs [class*="banner-ad" i],',
        'html.hammy-fs [class*="top-menu" i], html.hammy-fs [class*="main-menu" i],',
        'html.hammy-fs [class*="search-bar" i], html.hammy-fs [class*="breadcrumb" i],',
        'html.hammy-fs [class*="under-player" i], html.hammy-fs [class*="below-player" i],',
        'html.hammy-fs [class*="video-info" i], html.hammy-fs [class*="video-title" i],',
        'html.hammy-fs [class*="right-col" i], html.hammy-fs [class*="left-col" i],',
        'html.hammy-fs .related-videos, html.hammy-fs #related, html.hammy-fs #comments {',
        '  display: none !important; visibility: hidden !important; height: 0 !important;',
        '  max-height: 0 !important; overflow: hidden !important; pointer-events: none !important;',
        '}',
        /* Re-assert skip visibility after aggressive rules */
        'html.hammy-fs [class*="skip" i], html.hammy-fs [id*="skip" i],',
        'html.hammy-fs [aria-label*="skip" i], html.hammy-fs [aria-label*="Skip" i],',
        'html.hammy-fs [class*="close-ad" i], html.hammy-fs [class*="ad-close" i],',
        'html.hammy-fs [class*="dismiss" i] {',
        '  display: block !important; visibility: visible !important; opacity: 1 !important;',
        '  pointer-events: auto !important; z-index: 2147483600 !important;',
        '  height: auto !important; max-height: none !important; overflow: visible !important;',
        '}'
      ]);
    }
    return parts.join(nl);
  }

  function ensureStyle(hideChrome) {
    var s = document.getElementById(STYLE_ID);
    if (!s) {
      s = document.createElement('style');
      s.id = STYLE_ID;
      (document.head || document.documentElement).appendChild(s);
    }
    s.textContent = chromeCss(hideChrome);
  }

  function undoChromeHide() {
    try {
      document.documentElement.classList.remove('hammy-fs');
      if (document.body) document.body.classList.remove('hammy-fs');
      ensureStyle(false);
      var mains = document.querySelectorAll('.hammy-main-video, .hammy-player-root');
      for (var i = 0; i < mains.length; i++) {
        try {
          mains[i].classList.remove('hammy-main-video');
          // Keep hammy-player-root only if we still intend to use it later; strip for undo
          mains[i].classList.remove('hammy-player-root');
        } catch (e1) {}
      }
      reparented = false;
    } catch (e) {}
  }

  function reparentToBody(node) {
    if (!node || !document.body) return;
    try {
      if (node.parentElement === document.body) {
        reparented = true;
        return;
      }
      document.body.appendChild(node);
      reparented = true;
    } catch (e) {}
  }

  function promotePlayer(video) {
    if (!video || isAdVideo(video)) return null;
    video.classList.add('hammy-main-video');
    var container = findPlayerContainer(video);
    if (container && container !== video) {
      container.classList.add('hammy-player-root');
      // Reparent player root onto body BEFORE sibling-hide CSS runs, so
      // body > *:not(.hammy-player-root) cannot hide a nested ancestor chain.
      reparentToBody(container);
    } else {
      // Fallback: promote the video element itself as the root
      video.classList.add('hammy-player-root');
      reparentToBody(video);
      container = video;
    }
    return container;
  }

  function tryFullscreen(video) {
    if (!video || isAdVideo(video)) return;
    var now = Date.now();
    if (now - lastFsAt < 2000) return;
    lastFsAt = now;
    try {
      var docFs = document.fullscreenElement || document.webkitFullscreenElement;
      if (docFs) return;
      var target = video || document.documentElement;
      if (target.requestFullscreen) {
        target.requestFullscreen().catch(function(){});
      } else if (target.webkitRequestFullscreen) {
        target.webkitRequestFullscreen();
      } else if (video && video.webkitEnterFullscreen) {
        video.webkitEnterFullscreen();
      } else if (video && video.webkitEnterFullScreen) {
        video.webkitEnterFullScreen();
      }
    } catch (e) {}
  }

  function tryPlay(video) {
    if (!video || isAdVideo(video)) return;
    playAttempts++;
    try {
      video.setAttribute('playsinline', 'true');
      video.setAttribute('webkit-playsinline', 'true');
      if (!video.hasAttribute('tabindex')) video.setAttribute('tabindex', '0');
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
      // Exact-ish match preferred; soft match for longer labels
      var exact = skipRe.test(label.trim()) || label.trim() === 'x' || label.trim() === '\u00d7';
      var soft = softSkipRe.test(label);
      var clsIdHint = /skip|close-ad|ad-close|dismiss/i.test(label);
      if (!(exact || soft || clsIdHint)) continue;
      // Avoid nav / cookie accept that aren't skip
      if (/\b(accept all|agree|cookie|subscribe|sign in|login|register)\b/i.test(label) && !/\bskip\b/i.test(label)) continue;
      out.push({ el: el, exact: exact || clsIdHint, label: label });
    }
    return out;
  }

  function helpEscapeAd() {
    var now = Date.now();
    if (now - lastSkipHelpAt < 800) return;
    lastSkipHelpAt = now;
    var controls = findEscapeControls();
    if (!controls.length) return;
    // Focus the best candidate
    var best = controls[0];
    for (var i = 0; i < controls.length; i++) {
      if (controls[i].exact) { best = controls[i]; break; }
    }
    try {
      best.el.setAttribute('tabindex', '0');
      best.el.focus();
    } catch (e1) {}

    // Auto-click Skip once per page when clearly an ad skip control
    if (!skipAutoClicked && best.exact && /\bskip\b/i.test(best.label)) {
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
    applyCount++;
    var consent = isConsentVisible();
    var adLike = adOverlayPresent();
    var video = findMainVideo();
    var real = videoIsReal(video);
    var videoIsAd = video ? isAdVideo(video) : false;

    // If we somehow locked onto / promoted an ad earlier, undo
    if (videoIsAd || (!video && adLike)) {
      undoChromeHide();
      helpEscapeAd();
      return;
    }

    // Never hide chrome while consent OR ad overlay is present
    // Never hide until a real NON-ad video exists
    var hideChrome = !consent && !adLike && real && !!video && !videoIsAd;

    if (!hideChrome && document.documentElement.classList.contains('hammy-fs')) {
      // Soft undo of aggressive sibling hide while keeping mild fs styling optional
      if (adLike || consent || !real) {
        undoChromeHide();
        if (adLike) helpEscapeAd();
        // Still try to style lightly without sibling hide if we have content video
        if (video && real && !adLike && !consent) {
          ensureStyle(false);
          document.documentElement.classList.add('hammy-fs');
          if (document.body) document.body.classList.add('hammy-fs');
          promotePlayer(video);
        }
        return;
      }
    }

    ensureStyle(hideChrome);
    if (hideChrome || (video && real && !adLike)) {
      document.documentElement.classList.add('hammy-fs');
      if (document.body) document.body.classList.add('hammy-fs');
    }

    if (video && !videoIsAd) {
      promotePlayer(video);
      if (!consent && !adLike && real) {
        tryPlay(video);
        tryFullscreen(video);
        try { video.focus(); } catch (e) {}
        // Quiet the observer after a successful content lock so we don't re-chase ads
        moQuietUntil = Date.now() + 4000;
      }
    }

    if (adLike) helpEscapeAd();
  }

  window.__hammyChromeRefresh = apply;
  window.__hammyHelpEscapeAd = helpEscapeAd;

  apply();
  setTimeout(apply, 300);
  setTimeout(apply, 800);
  setTimeout(apply, 1500);
  setTimeout(apply, 3000);
  setTimeout(apply, 5000);
  setTimeout(apply, 8000);

  try {
    var mo = new MutationObserver(function(mutations) {
      // Soften: ignore rapid thrash; skip while quietly locked on content
      var now = Date.now();
      if (now < moQuietUntil && lockedContentVideo && lockedContentVideo.isConnected) {
        // Still react if an ad overlay / skip button appears
        var interesting = false;
        for (var i = 0; i < mutations.length; i++) {
          var m = mutations[i];
          var nodes = [];
          if (m.addedNodes) {
            for (var a = 0; a < m.addedNodes.length; a++) nodes.push(m.addedNodes[a]);
          }
          for (var n = 0; n < nodes.length; n++) {
            var node = nodes[n];
            if (!node || node.nodeType !== 1) continue;
            var tag = (node.tagName || '').toLowerCase();
            var h = haystackFor(node);
            if (tag === 'video' || tag === 'iframe' || (h && AD_HINT_RE.test(h)) ||
                (h && /skip|ima|vast|preroll/i.test(h))) {
              interesting = true;
              break;
            }
          }
          if (interesting) break;
        }
        if (!interesting) return;
      }
      if (moTimer) clearTimeout(moTimer);
      moTimer = setTimeout(function() {
        moTimer = null;
        apply();
      }, 600);
    });
    mo.observe(document.documentElement, { childList: true, subtree: true });
  } catch (e) {}

  document.addEventListener('fullscreenchange', function(){}, true);
})();
        """.trimIndent()
    }
}
