package com.personal.hammy

/**
 * Lite player assist injected into the official video page WebView.
 *
 * Goal: let the site's own player work like a normal WebView. Find the largest
 * <video>, gently try play() + scroll-into-view, and continuously poll for:
 *   1) Age-gate / 18+ confirm controls (priority)
 *   2) Site's own Skip Ads / Skip / Skip Ad control
 * When either appears: outline once, notify Kotlin via HammyBridge, and
 * auto-activate via native [HammyBridge.tapAt] (5–8× / ~700ms) with hardClick
 * as secondary. Prefers the exact age-enter CTA / largest red button.
 * Does NOT reparent the video, hide body siblings, force fullscreen
 * CSS, or run an aggressive ad classifier.
 *
 * Focus-ring / Center play-pause stay in [FocusInjectJs]. Native Close + Back/Menu
 * always finish() from PlayerActivity.
 */
object PlayerChromeJs {
    val SCRIPT: String = buildScript()

    /** Early inject on page start: set common age-confirm storage/cookie flags. */
    private val AGE_BYPASS = """
(function(){
  if (window.__hammyAgeBypassV1) return;
  window.__hammyAgeBypassV1 = true;
  function setStore(store, key, val) {
    try { if (store) store.setItem(key, val); } catch (e) {}
  }
  function setCookie(name, val) {
    try {
      var v = encodeURIComponent(val);
      var base = name + '=' + v + '; path=/; max-age=31536000; SameSite=Lax';
      document.cookie = base;
      try {
        var host = location.hostname || '';
        if (host.indexOf('xhamster') !== -1) {
          document.cookie = base + '; domain=.xhamster.com';
        } else if (host.indexOf('.') !== -1) {
          var parts = host.split('.');
          if (parts.length >= 2) {
            document.cookie = base + '; domain=.' + parts.slice(-2).join('.');
          }
        }
      } catch (eD) {}
    } catch (e) {}
  }
  // Small allowlist — do not wipe storage
  var keys = [
    'age_confirmed', 'ageConfirmed', 'age_verified', 'ageVerified',
    'age_gate', 'ageGate', 'ageGateConfirmed', 'age_gate_confirmed',
    'confirmAge', 'confirm_age', 'isAdult', 'is_adult', 'adult',
    'over18', 'over_18', 'eighteen', 'xh_age', 'xhAgeConfirmed',
    'cookie_accept', 'cookie_accept_v2', 'cookiesAccepted', 'disclaimerAccepted',
    'hasConfirmedAge', 'userAgeConfirmed', 'ageCheckPassed',
    'ageProtectAgreement', 'parental-control', 'age_protect_agreement'
  ];
  try {
    for (var i = 0; i < keys.length; i++) {
      var k = keys[i];
      setStore(window.localStorage, k, '1');
      setStore(window.localStorage, k, 'true');
      setStore(window.sessionStorage, k, '1');
      setStore(window.sessionStorage, k, 'true');
      setCookie(k, '1');
      setCookie(k, 'true');
    }
  } catch (eAll) {}
  // Honor any age-like keys already present in storage (set them true)
  try {
    var stores = [window.localStorage, window.sessionStorage];
    for (var s = 0; s < stores.length; s++) {
      var store = stores[s];
      if (!store) continue;
      for (var j = 0; j < store.length; j++) {
        var ek = store.key(j);
        if (!ek) continue;
        var low = String(ek).toLowerCase();
        if (/(age|adult|18|confirm|disclaimer|mature|nsfw)/.test(low)) {
          try { store.setItem(ek, '1'); } catch (eS) {}
        }
      }
    }
  } catch (eScan) {}
  // window.initials age flags when publicly present (no media scrape)
  try {
    var init = window.initials;
    if (init && typeof init === 'object') {
      var paths = [
        ['ageVerified'], ['ageConfirmed'], ['user', 'ageVerified'],
        ['user', 'isAdult'], ['settings', 'ageConfirmed'],
        ['xh', 'ageVerified'], ['extras', 'ageVerified']
      ];
      for (var p = 0; p < paths.length; p++) {
        try {
          var cur = init;
          var path = paths[p];
          for (var n = 0; n < path.length - 1; n++) {
            if (!cur[path[n]]) cur[path[n]] = {};
            cur = cur[path[n]];
          }
          cur[path[path.length - 1]] = true;
        } catch (eP) {}
      }
    }
  } catch (eInit) {}
})();
""".trimIndent()

    val AGE_BYPASS_SCRIPT: String = AGE_BYPASS

    private fun buildScript(): String {
        return """
(function(){
  if (window.__hammyChromeV9) {
    try { window.__hammyChromeRefresh && window.__hammyChromeRefresh(); } catch(e) {}
    return;
  }
  window.__hammyChromeV9 = true;
  try { delete window.__hammyChromeV8; } catch(e) {}
    try { delete window.__hammyChromeV7; } catch(e) {}
  try { delete window.__hammyChromeV6; } catch(e) {}
  try { delete window.__hammyChromeV5; } catch(e) {}
  try { delete window.__hammyChromeV4; } catch(e) {}
  try { delete window.__hammyChromeV3; } catch(e) {}
  try { delete window.__hammyChromeV2; } catch(e) {}

  // Re-run age bypass on full chrome inject (page may have overwritten storage)
  try {
    window.__hammyAgeBypassV1 = false;
  } catch (eB) {}
  try {
    /* inline early bypass again */
    (function(){
      function setStore(store, key, val) {
        try { if (store) store.setItem(key, val); } catch (e) {}
      }
      function setCookie(name, val) {
        try {
          var v = encodeURIComponent(val);
          var base = name + '=' + v + '; path=/; max-age=31536000; SameSite=Lax';
          document.cookie = base;
          try {
            var host = location.hostname || '';
            if (host.indexOf('xhamster') !== -1) {
              document.cookie = base + '; domain=.xhamster.com';
            } else if (host.indexOf('.') !== -1) {
              var parts = host.split('.');
              if (parts.length >= 2) {
                document.cookie = base + '; domain=.' + parts.slice(-2).join('.');
              }
            }
          } catch (eD) {}
        } catch (e) {}
      }
      var keys = [
        'age_confirmed', 'ageConfirmed', 'age_verified', 'ageVerified',
        'age_gate', 'ageGate', 'ageGateConfirmed', 'age_gate_confirmed',
        'confirmAge', 'confirm_age', 'isAdult', 'is_adult', 'adult',
        'over18', 'over_18', 'eighteen', 'xh_age', 'xhAgeConfirmed',
        'cookie_accept', 'cookie_accept_v2', 'cookiesAccepted', 'disclaimerAccepted',
        'hasConfirmedAge', 'userAgeConfirmed', 'ageCheckPassed',
        'ageProtectAgreement', 'parental-control', 'age_protect_agreement'
      ];
      for (var i = 0; i < keys.length; i++) {
        var k = keys[i];
        setStore(window.localStorage, k, '1');
        setStore(window.sessionStorage, k, '1');
        setCookie(k, '1');
      }
      try {
        var stores = [window.localStorage, window.sessionStorage];
        for (var s = 0; s < stores.length; s++) {
          var store = stores[s];
          if (!store) continue;
          for (var j = 0; j < store.length; j++) {
            var ek = store.key(j);
            if (!ek) continue;
            var low = String(ek).toLowerCase();
            if (/(age|adult|18|confirm|disclaimer|mature|nsfw)/.test(low)) {
              try { store.setItem(ek, '1'); } catch (eS) {}
            }
          }
        }
      } catch (eScan) {}
      try {
        var init = window.initials;
        if (init && typeof init === 'object') {
          try { init.ageVerified = true; } catch (e1) {}
          try { init.ageConfirmed = true; } catch (e2) {}
          try { if (!init.user) init.user = {}; init.user.ageVerified = true; init.user.isAdult = true; } catch (e3) {}
        }
      } catch (eInit) {}
      window.__hammyAgeBypassV1 = true;
    })();
  } catch (eBy) {}

  var STYLE_ID = 'hammy-chrome-lite-css';
  var unmuted = false;
  var skipAutoClicked = false;
  var skipClickScheduled = false;
  var ageAutoClicked = false;
  var ageClickScheduled = false;
  var ageFocusDone = false;
  var ageNativeTapCount = 0;
  var ageNativeTapTimer = null;
  var lastPlayAt = 0;
  var moTimer = null;
  var pollTimer = null;
  var lastSkipEl = null;
  var lastAgeEl = null;
  var lastReportedSkip = null;
  var lastReportedAge = null;
  var lastOutlinedAge = null;
  window.__hammySkipVisible = false;
  window.__hammyAgeGateVisible = false;
  window.__hammyBlockingCtaVisible = false;

  function ensureLiteStyle() {
    var s = document.getElementById(STYLE_ID);
    if (!s) {
      s = document.createElement('style');
      s.id = STYLE_ID;
      (document.head || document.documentElement).appendChild(s);
    }
    var nl = String.fromCharCode(10);
    s.textContent = [
      '#hammy-focus-ring, #hammy-video-hint { z-index: 2147483647 !important; }',
      /* Stable outline — avoid transition thrashing / flash */
      '.hammy-escape-btn {',
      '  outline: 4px solid #00E5FF !important;',
      '  outline-offset: 3px !important;',
      '  box-shadow: 0 0 0 2px #39FF14, 0 0 16px 4px rgba(0,229,255,0.75) !important;',
      '  position: relative !important;',
      '  z-index: 2147483600 !important;',
      '  pointer-events: auto !important;',
      '  transition: none !important;',
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

  function ancestorContext(el, depth) {
    var bits = [];
    var cur = el;
    var max = typeof depth === 'number' ? depth : 4;
    for (var i = 0; i < max && cur; i++) {
      try {
        bits.push(cur.id || '');
        bits.push((cur.className && cur.className.toString) ? cur.className.toString() : '');
        bits.push(cur.getAttribute && (cur.getAttribute('aria-label') || '') || '');
        bits.push(cur.getAttribute && (cur.getAttribute('data-role') || '') || '');
      } catch (e) {}
      cur = cur.parentElement;
    }
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

  function findAgeGateControls() {
    var out = [];
    var candidates = [];
    try {
      candidates = document.querySelectorAll(
        'button, a, [role="button"], input[type="button"], input[type="submit"], div[tabindex], span[tabindex],' +
        ' [class*="age" i], [id*="age" i], [class*="gate" i], [id*="gate" i],' +
        ' [class*="consent" i], [id*="consent" i], [aria-label*="18" i], [aria-label*="age" i],' +
        ' [class*="Age" i], [class*="Protect" i], [class*="overlay" i]'
      );
    } catch (e) {
      return out;
    }
    /* Exact Insignia/TV overlay: "I'm 18 or older — enter xHamster" (em/en dash or hyphen). */
    var exactEnterRe = /i['’`]?m\s*18\s*or\s*older\s*[\u2014\u2013\-]\s*enter\s*xhamster/i;
    var strongRe = /\b(i['’`]?m\s+18(\s+or\s+older)?|i\s+am\s+18(\s+or\s+older)?|18\s+or\s+older|over\s+18|18\s*\+|enter\s+xhamster)\b/i;
    var softCtaRe = /\b(enter|i\s+agree|accept|continue|confirm)\b/i;
    var ageCtxRe = /\b(age|gate|18|adult|nsfw|mature|over.?18|confirm.?age|age.?verif|disclaimer|xhamster)\b/i;
    for (var i = 0; i < candidates.length; i++) {
      var el = candidates[i];
      if (!isVisibleClickable(el)) continue;
      var label = normalizeLabel(el);
      if (!label) continue;
      if (/\bskip(\s+ads?)?\b/i.test(label)) continue;
      if (/\b(cookie|subscribe|sign in|login|register|sign up)\b/i.test(label) && !strongRe.test(label) && !exactEnterRe.test(label)) continue;
      var exactEnter = exactEnterRe.test(label);
      var strong = exactEnter || strongRe.test(label);
      var soft = softCtaRe.test(label);
      var ctx = ageCtxRe.test(label) || ageCtxRe.test(ancestorContext(el, 5));
      if (!(strong || (soft && ctx))) continue;
      var area = 0;
      var redScore = 0;
      try {
        var r = el.getBoundingClientRect();
        area = Math.max(0, r.width) * Math.max(0, r.height);
        var st = window.getComputedStyle(el);
        var bg = (st.backgroundColor || '') + ' ' + (st.backgroundImage || '') + ' ' + (st.borderColor || '');
        var m = bg.match(/rgba?\((\d+)\s*,\s*(\d+)\s*,\s*(\d+)/i);
        if (m) {
          var rr = parseInt(m[1], 10), gg = parseInt(m[2], 10), bb = parseInt(m[3], 10);
          if (rr > 140 && rr > gg + 40 && rr > bb + 40) redScore = 1000000 + (rr - gg) + (rr - bb);
        }
        if (/#?(e|f|c|d|a)[0-9a-f]{0,2}0{1,2}[0-9a-f]{0,2}|red|tomato|crimson|ff0000|e50914|f44336/i.test(bg)) {
          redScore = Math.max(redScore, 900000);
        }
      } catch (eM) {}
      out.push({
        el: el,
        exact: exactEnter || strong,
        exactEnter: exactEnter,
        clearlyAge: strong || (soft && ctx),
        label: label,
        area: area,
        redScore: redScore
      });
    }
    return out;
  }

  function findSkipControls() {
    var out = [];
    var candidates = [];
    try {
      candidates = document.querySelectorAll(
        'button, a, [role="button"], input[type="button"], div[tabindex], span[tabindex], [class*="skip" i], [id*="skip" i], [aria-label*="skip" i]'
      );
    } catch (e) {
      return out;
    }
    var skipRe = /^(skip(\s+ads?)?|skip\s+advertisement)$/i;
    var softSkipRe = /\b(skip(\s+ads?)?|skip\s+advertisement)\b/i;
    for (var i = 0; i < candidates.length; i++) {
      var el = candidates[i];
      if (!isVisibleClickable(el)) continue;
      var label = normalizeLabel(el);
      if (!label) continue;
      var exact = skipRe.test(label.trim());
      var soft = softSkipRe.test(label);
      var ariaHint = false;
      try {
        var aria = (el.getAttribute('aria-label') || '').toLowerCase();
        ariaHint = /\bskip(\s+ads?)?\b/.test(aria);
      } catch (eA) {}
      var clsIdHint = /skip/.test(label) && /\b(ad|ads|advertisement)\b/.test(label);
      if (!(exact || soft || ariaHint || clsIdHint)) continue;
      if (/\b(accept all|agree|cookie|subscribe|sign in|login|register)\b/i.test(label) && !/\bskip\b/i.test(label)) continue;
      var clearlySkipAd = /\bskip(\s+ads?|\s+advertisement)?\b/i.test(label) || ariaHint;
      out.push({ el: el, exact: exact || clsIdHint || ariaHint, clearlySkipAd: clearlySkipAd, label: label });
    }
    return out;
  }

  function notifyAgeGateVisible(visible) {
    window.__hammyAgeGateVisible = !!visible;
    window.__hammyBlockingCtaVisible = !!(visible || window.__hammySkipVisible);
    if (lastReportedAge === !!visible) return;
    lastReportedAge = !!visible;
    try {
      if (window.HammyBridge && typeof window.HammyBridge.ageGateVisible === 'function') {
        window.HammyBridge.ageGateVisible(!!visible);
      }
    } catch (e) {}
    try {
      if (window.HammyBridge && typeof window.HammyBridge.blockingCtaVisible === 'function') {
        window.HammyBridge.blockingCtaVisible(!!(visible || window.__hammySkipVisible));
      }
    } catch (e2) {}
  }

  function notifySkipVisible(visible) {
    window.__hammySkipVisible = !!visible;
    window.__hammyBlockingCtaVisible = !!(visible || window.__hammyAgeGateVisible);
    if (lastReportedSkip === !!visible) return;
    lastReportedSkip = !!visible;
    try {
      if (window.HammyBridge && typeof window.HammyBridge.skipVisible === 'function') {
        window.HammyBridge.skipVisible(!!visible);
      }
    } catch (e) {}
    try {
      if (window.HammyBridge && typeof window.HammyBridge.blockingCtaVisible === 'function') {
        window.HammyBridge.blockingCtaVisible(!!(visible || window.__hammyAgeGateVisible));
      }
    } catch (e2) {}
  }

  function resolveClickTarget(el) {
    if (!el) return null;
    var cur = el;
    for (var i = 0; i < 4 && cur; i++) {
      try {
        var tag = (cur.tagName || '').toLowerCase();
        var role = '';
        try { role = (cur.getAttribute && cur.getAttribute('role')) || ''; } catch (eR) {}
        var hasOnclick = false;
        try { hasOnclick = !!(cur.onclick || (cur.getAttribute && cur.getAttribute('onclick'))); } catch (eO) {}
        if (tag === 'button' || tag === 'a' || tag === 'input' || role === 'button' || hasOnclick) {
          return cur;
        }
      } catch (e1) {}
      cur = cur.parentElement;
    }
    return el;
  }

  function elementCenter(el) {
    var cx = 0, cy = 0;
    try {
      var target = resolveClickTarget(el) || el;
      var r = target.getBoundingClientRect();
      cx = r.left + Math.max(r.width, 1) / 2;
      cy = r.top + Math.max(r.height, 1) / 2;
    } catch (e) {}
    return { x: cx, y: cy };
  }

  /** Prefer native MotionEvent tap via Kotlin bridge; fall back to JS hardClick. */
  function nativeTap(el) {
    if (!el) return false;
    try {
      var c = elementCenter(el);
      if (window.HammyBridge && typeof window.HammyBridge.tapAt === 'function') {
        window.HammyBridge.tapAt(c.x, c.y);
        return true;
      }
    } catch (e) {}
    return false;
  }

  function activateCta(el, doFocus) {
    if (!el) return false;
    if (doFocus) focusEscape(el, true);
    else markEscape(el);
    var tapped = nativeTap(el);
    // Secondary: synthetic pointer/mouse/click sequence
    hardClick(el);
    return tapped || true;
  }

  function firePointerMouseSequence(node, cx, cy) {
    if (!node) return false;
    var downOpts = { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy, button: 0, buttons: 1 };
    var upOpts = { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy, button: 0, buttons: 0 };
    try {
      if (typeof PointerEvent === 'function') {
        node.dispatchEvent(new PointerEvent('pointerdown', downOpts));
      }
    } catch (e1) {}
    try { node.dispatchEvent(new MouseEvent('mousedown', downOpts)); } catch (e2) {}
    try {
      if (typeof PointerEvent === 'function') {
        node.dispatchEvent(new PointerEvent('pointerup', upOpts));
      }
    } catch (e3) {}
    try { node.dispatchEvent(new MouseEvent('mouseup', upOpts)); } catch (e4) {}
    try { node.dispatchEvent(new MouseEvent('click', upOpts)); } catch (e5) {}
    try {
      node.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13, which: 13 }));
      node.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13, which: 13 }));
    } catch (e6) {}
    try { node.click(); } catch (e7) {}
    return true;
  }

  function hardClick(el) {
    if (!el) return false;
    try {
      var target = resolveClickTarget(el);
      var cx = 0, cy = 0;
      try {
        var r = target.getBoundingClientRect();
        cx = Math.floor(r.left + Math.max(r.width, 1) / 2);
        cy = Math.floor(r.top + Math.max(r.height, 1) / 2);
      } catch (eR) {}
      var fromPoint = null;
      try { fromPoint = document.elementFromPoint(cx, cy); } catch (eP) {}
      var ok = firePointerMouseSequence(target, cx, cy);
      if (fromPoint && fromPoint !== target) {
        try { firePointerMouseSequence(resolveClickTarget(fromPoint), cx, cy); } catch (eF) {}
      }
      try {
        var parentBtn = target.closest && target.closest('button, a, [role="button"]');
        if (parentBtn && parentBtn !== target) firePointerMouseSequence(parentBtn, cx, cy);
      } catch (eB) {}
      return !!ok;
    } catch (e) {
      try { el.click(); return true; } catch (e2) { return false; }
    }
  }

  function looksLikeAgeOrSkip(el) {
    if (!el) return false;
    try {
      if (el.classList && el.classList.contains('hammy-escape-btn')) return true;
    } catch (e0) {}
    var label = normalizeLabel(el);
    if (!label) {
      try { label = normalizeLabel(el.parentElement); } catch (e1) {}
    }
    if (!label) return false;
    if (/\bskip(\s+ads?)?\b/i.test(label)) return true;
    if (/i['’`]?m\s*18\s*or\s*older\s*[\u2014\u2013\-]\s*enter\s*xhamster/i.test(label)) return true;
    if (/\b(i['’`]?m\s+18(\s+or\s+older)?|i\s+am\s+18(\s+or\s+older)?|18\s+or\s+older|over\s+18|18\s*\+|enter\s+xhamster)\b/i.test(label)) return true;
    if (/\b(enter|i\s+agree|accept|continue|confirm)\b/i.test(label) &&
        (/\b(age|gate|18|adult|nsfw|mature|disclaimer)\b/i.test(label) ||
         /\b(age|gate|18|adult|nsfw|mature|disclaimer)\b/i.test(ancestorContext(el, 5)))) {
      return true;
    }
    if (/\b(age|gate|18\+|over.?18|confirm.?age|age.?verif)\b/i.test(label)) return true;
    return false;
  }

  /** Outline + tabindex without stealing focus (avoids focus flash loop). */
  function markEscape(el) {
    if (!el) return;
    try {
      el.setAttribute('tabindex', '0');
      el.classList.add('hammy-escape-btn');
    } catch (e1) {}
  }

  /**
   * Focus once per element. [force] used for OK key / first sighting only.
   * Repeated poll must NOT call focus again (causes highlight flash).
   */
  function focusEscape(el, force) {
    if (!el) return;
    markEscape(el);
    var shouldFocus = !!force || !ageFocusDone || lastOutlinedAge !== el;
    if (!shouldFocus) return;
    try {
      try {
        el.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'auto' });
      } catch (eScroll) {
        try { el.scrollIntoView(false); } catch (eScroll2) {}
      }
      try { el.focus({ preventScroll: true }); } catch (eF) {
        try { el.focus(); } catch (eF2) {}
      }
      ageFocusDone = true;
      lastOutlinedAge = el;
    } catch (e1) {}
  }

  function pickBestAge(controls) {
    if (!controls || !controls.length) return null;
    var best = controls[0];
    var bestScore = -1;
    for (var i = 0; i < controls.length; i++) {
      var c = controls[i];
      var score = 0;
      if (c.exactEnter) score += 50000000;
      if (c.exact) score += 10000000;
      if (c.clearlyAge) score += 1000000;
      score += (c.redScore || 0);
      score += Math.min(c.area || 0, 2000000);
      if (/enter\s*xhamster|18\s*or\s*older/i.test(c.label || '')) score += 5000000;
      if (score > bestScore) {
        bestScore = score;
        best = c;
      }
    }
    return best;
  }

  function pickBestSkip(controls) {
    if (!controls || !controls.length) return null;
    var best = controls[0];
    for (var i = 0; i < controls.length; i++) {
      if (controls[i].clearlySkipAd) { best = controls[i]; break; }
      if (controls[i].exact) best = controls[i];
    }
    return best;
  }

  function scheduleAgeNativeTaps(el) {
    if (ageClickScheduled || ageAutoClicked) return;
    ageClickScheduled = true;
    ageNativeTapCount = 0;
    if (ageNativeTapTimer) {
      try { clearInterval(ageNativeTapTimer); } catch (eT) {}
      ageNativeTapTimer = null;
    }
    function oneTap() {
      ageNativeTapCount++;
      var target = el;
      if (!isVisibleClickable(target)) {
        var still = findAgeGateControls();
        var best2 = pickBestAge(still);
        target = best2 ? best2.el : null;
        if (target) el = target;
      }
      if (target && isVisibleClickable(target)) {
        // Outline only; do not re-focus every attempt / never focus native Close
        markEscape(target);
        if (ageNativeTapCount === 1) focusEscape(target, true);
        nativeTap(target);
        hardClick(target);
      }
      if (ageNativeTapCount >= 8 || !window.__hammyAgeGateVisible) {
        ageAutoClicked = true;
        ageClickScheduled = false;
        if (ageNativeTapTimer) {
          try { clearInterval(ageNativeTapTimer); } catch (eC) {}
          ageNativeTapTimer = null;
        }
      }
    }
    // Immediate first tap, then every ~700ms up to 8 total (center of red CTA)
    setTimeout(oneTap, 0);
    ageNativeTapTimer = setInterval(oneTap, 700);
  }

  function pollAgeGate() {
    var controls = findAgeGateControls();
    var best = pickBestAge(controls);
    if (!best) {
      lastAgeEl = null;
      notifyAgeGateVisible(false);
      ageAutoClicked = false;
      ageClickScheduled = false;
      ageFocusDone = false;
      lastOutlinedAge = null;
      ageNativeTapCount = 0;
      if (ageNativeTapTimer) {
        try { clearInterval(ageNativeTapTimer); } catch (eC) {}
        ageNativeTapTimer = null;
      }
      return false;
    }

    lastAgeEl = best.el;
    notifyAgeGateVisible(true);
    lastSkipEl = null;
    notifySkipVisible(false);

    // Outline / focus once only — not every 500ms poll
    if (!ageFocusDone || lastOutlinedAge !== best.el) {
      focusEscape(best.el, true);
    } else {
      markEscape(best.el);
    }

    if (!ageAutoClicked && best.clearlyAge) {
      scheduleAgeNativeTaps(best.el);
    }
    return true;
  }

  function pollSkip() {
    var controls = findSkipControls();
    var best = pickBestSkip(controls);
    if (!best) {
      lastSkipEl = null;
      notifySkipVisible(false);
      skipAutoClicked = false;
      skipClickScheduled = false;
      return;
    }

    lastSkipEl = best.el;
    notifySkipVisible(true);
    markEscape(best.el);
    // Focus skip once when first seen (age gate takes priority when present)
    if (!window.__hammyAgeGateVisible) {
      try {
        if (!best.el.classList.contains('hammy-focused-once')) {
          best.el.classList.add('hammy-focused-once');
          focusEscape(best.el, true);
        }
      } catch (eF) { markEscape(best.el); }
    }

    if (!skipAutoClicked && !skipClickScheduled && best.clearlySkipAd) {
      skipClickScheduled = true;
      var target = best.el;
      setTimeout(function() {
        skipClickScheduled = false;
        if (skipAutoClicked) return;
        if (!isVisibleClickable(target)) return;
        var still = findSkipControls();
        var match = false;
        for (var j = 0; j < still.length; j++) {
          if (still[j].el === target) { match = true; break; }
        }
        if (!match) return;
        skipAutoClicked = true;
        activateCta(target, false);
      }, 400);
    }
  }

  function pollBlockingCtas() {
    if (pollAgeGate()) return;
    pollSkip();
  }

  window.__hammyClickAgeGate = function() {
    var el = lastAgeEl;
    if (!el || !isVisibleClickable(el)) {
      var controls = findAgeGateControls();
      var best = pickBestAge(controls);
      el = best ? best.el : null;
    }
    if (!el) return false;
    return activateCta(el, true);
  };

  window.__hammyClickSkip = function() {
    var el = lastSkipEl;
    if (!el || !isVisibleClickable(el)) {
      var controls = findSkipControls();
      var best = pickBestSkip(controls);
      el = best ? best.el : null;
    }
    if (!el) return false;
    return activateCta(el, true);
  };

  window.__hammyClickBlockingCta = function() {
    if (window.__hammyAgeGateVisible || lastAgeEl) {
      if (window.__hammyClickAgeGate()) return true;
    }
    if (window.__hammySkipVisible || lastSkipEl) {
      if (window.__hammyClickSkip()) return true;
    }
    if (window.__hammyClickAgeGate()) return true;
    return window.__hammyClickSkip();
  };

  /**
   * DPAD_CENTER / ENTER: native tap first, then hardClick age/skip or focused CTA.
   * Returns true if a blocking CTA was attempted (Kotlin skips play-pause).
   */
  window.__hammyActivateFocusedOrBlockingCta = function() {
    try {
      if (lastAgeEl && isVisibleClickable(lastAgeEl)) {
        activateCta(lastAgeEl, true);
        return true;
      }
      var ageControls = findAgeGateControls();
      var bestAge = pickBestAge(ageControls);
      if (bestAge && bestAge.el && isVisibleClickable(bestAge.el)) {
        lastAgeEl = bestAge.el;
        activateCta(bestAge.el, true);
        return true;
      }

      var ae = null;
      try { ae = document.activeElement; } catch (eAe) {}
      if (ae && ae !== document.body && ae !== document.documentElement && looksLikeAgeOrSkip(ae)) {
        activateCta(ae, false);
        return true;
      }

      if (ae && ae.getBoundingClientRect) {
        try {
          var r = ae.getBoundingClientRect();
          var cx = Math.floor(r.left + Math.max(r.width, 1) / 2);
          var cy = Math.floor(r.top + Math.max(r.height, 1) / 2);
          var ep = document.elementFromPoint(cx, cy);
          if (ep && looksLikeAgeOrSkip(ep)) {
            activateCta(ep, false);
            return true;
          }
          if (ae.classList && ae.classList.contains('hammy-escape-btn') && isVisibleClickable(ae)) {
            activateCta(ae, false);
            return true;
          }
        } catch (ePt) {}
      }

      if (lastSkipEl && isVisibleClickable(lastSkipEl)) {
        activateCta(lastSkipEl, true);
        return true;
      }
      var skipControls = findSkipControls();
      var bestSkip = pickBestSkip(skipControls);
      if (bestSkip && bestSkip.el && isVisibleClickable(bestSkip.el)) {
        lastSkipEl = bestSkip.el;
        activateCta(bestSkip.el, true);
        return true;
      }

      if (ae && ae.classList && ae.classList.contains('hammy-escape-btn') && isVisibleClickable(ae)) {
        activateCta(ae, false);
        return true;
      }
    } catch (eAll) {}
    return false;
  };

  window.__hammyHelpEscapeAd = pollBlockingCtas;

  function apply() {
    ensureLiteStyle();
    stripAggressiveChrome();

    var video = findLargestVideo();
    if (video) {
      tryPlay(video);
    }

    pollBlockingCtas();
  }

  window.__hammyChromeRefresh = apply;

  apply();
  setTimeout(apply, 400);
  setTimeout(apply, 1200);
  setTimeout(apply, 2500);
  setTimeout(apply, 5000);

  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(function() {
    try { pollBlockingCtas(); } catch (eP) {}
  }, 500);

  try {
    var mo = new MutationObserver(function() {
      if (moTimer) clearTimeout(moTimer);
      moTimer = setTimeout(function() {
        moTimer = null;
        try { pollBlockingCtas(); } catch (eM) {}
      }, 200);
    });
    mo.observe(document.documentElement, { childList: true, subtree: true, attributes: true });
  } catch (e) {}
})();
        """.trimIndent()
    }
}
