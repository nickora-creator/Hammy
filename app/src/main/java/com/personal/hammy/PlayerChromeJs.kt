package com.personal.hammy

/**
 * Injected into the official video page WebView: hide site chrome, scale the main
 * player/video to fill the viewport, attempt autoplay + fullscreen. Idempotent with
 * retries (same pattern as [FocusInjectJs]). Does not scrape CDNs or change media URLs.
 *
 * Chrome-hide only runs after a real video is present, and the player root is
 * reparented onto document.body so sibling-hide CSS cannot blank the screen.
 */
object PlayerChromeJs {
    val SCRIPT: String = buildScript()

    private fun buildScript(): String {
        return """
(function(){
  if (window.__hammyChromeV2) {
    try { window.__hammyChromeRefresh && window.__hammyChromeRefresh(); } catch(e) {}
    return;
  }
  window.__hammyChromeV2 = true;

  var STYLE_ID = 'hammy-chrome-css';
  var lastFsAt = 0;
  var unmuted = false;
  var playAttempts = 0;
  var moTimer = null;
  var reparented = false;

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
    var videos = document.querySelectorAll('video');
    var best = null;
    var bestArea = 0;
    for (var i = 0; i < videos.length; i++) {
      var v = videos[i];
      var r = v.getBoundingClientRect();
      var area = Math.max(r.width, v.videoWidth || 0) * Math.max(r.height, v.videoHeight || 0);
      var bonus = (v.readyState >= 1 || (v.currentSrc && v.currentSrc.length > 0)) ? 100000 : 0;
      var score = area + bonus;
      if (score > bestArea) {
        bestArea = score;
        best = v;
      }
    }
    return best;
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
        if (c) return c;
      } catch (e) {}
    }
    var el = video.parentElement;
    for (var d = 0; d < 8 && el && el !== document.body; d++) {
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
      /* Keep focus helpers above chrome-hide */
      '#hammy-focus-ring, #hammy-video-hint { z-index: 2147483647 !important; }'
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
    if (!video) return null;
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
    if (!video) return;
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

  function apply() {
    var consent = isConsentVisible();
    var video = findMainVideo();
    var real = videoIsReal(video);

    // Never hide chrome until a real video exists (avoids black screen)
    var hideChrome = !consent && real;

    ensureStyle(hideChrome);
    document.documentElement.classList.add('hammy-fs');
    if (document.body) document.body.classList.add('hammy-fs');

    if (video) {
      // Reparent before applying sibling-hide
      promotePlayer(video);
      if (!consent && real) {
        tryPlay(video);
        tryFullscreen(video);
        try { video.focus(); } catch (e) {}
      }
    }
  }

  window.__hammyChromeRefresh = apply;

  apply();
  setTimeout(apply, 300);
  setTimeout(apply, 800);
  setTimeout(apply, 1500);
  setTimeout(apply, 3000);
  setTimeout(apply, 5000);

  try {
    var mo = new MutationObserver(function() {
      if (moTimer) clearTimeout(moTimer);
      moTimer = setTimeout(function() {
        moTimer = null;
        apply();
      }, 250);
    });
    mo.observe(document.documentElement, { childList: true, subtree: true });
  } catch (e) {}

  document.addEventListener('fullscreenchange', function(){}, true);
})();
        """.trimIndent()
    }
}
