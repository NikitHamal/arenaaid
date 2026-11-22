package com.arena.aid.data.remote

import kotlinx.serialization.json.JsonPrimitive

/**
 * JavaScript executed inside the real arena.ai page (Chromium WebView session).
 * Running inside the genuine page means Cloudflare clearance, reCAPTCHA Enterprise scoring,
 * the `cx` client-interaction field and cookies are all produced by arena.ai itself.
 */
object ArenaPageScripts {
    /** Fallback reCAPTCHA Enterprise key (the real key is discovered from the page first). */
    const val V3_KEY = "6Led_uYrAAAAAKjxDIF58fgFtX3t8loNAK85bW9I"
    const val SIGNUP_KEY = "6LeTGMcsAAAAAALuIlkVwIxaAuZA8VledA6d3Nnb0"

    /** Safe JS string literal for arbitrary Kotlin text. */
    fun lit(s: String): String = JsonPrimitive(s).toString()

    private const val SITE_KEY_FN = """
function arenaSiteKey(dflt){
  try {
    var s = document.querySelectorAll('script[src*="recaptcha"]');
    for (var i = 0; i < s.length; i++) {
      var m = s[i].src.match(/[?&]render=([^&]+)/);
      if (m && m[1] && m[1] !== 'explicit') return m[1];
    }
  } catch (e) {}
  return dflt;
}
"""

    /** Returns e.g. "cf=0;ta=1;rc=1" describing whether the page is usable. */
    val STATE_JS = """
(function(){
  var t = document.title || '';
  var cf = /just a moment|attention required|verify you are human/i.test(t) ||
    !!document.querySelector('#challenge-form, #cf-challenge-running, .cf-turnstile, #challenge-running');
  var ta = !!document.querySelector('textarea, [contenteditable="true"]');
  var rc = !!(window.grecaptcha && window.grecaptcha.enterprise);
  return 'cf=' + (cf ? 1 : 0) + ';ta=' + (ta ? 1 : 0) + ';rc=' + (rc ? 1 : 0);
})()
"""

    fun signUp(provisionalId: String): String = """
(async function(){
  try {
    $SITE_KEY_FN
    for (var w = 0; w < 40; w++) {
      if (window.grecaptcha && window.grecaptcha.enterprise &&
          typeof window.grecaptcha.enterprise.ready === 'function') break;
      await new Promise(function(r){ setTimeout(r, 300); });
    }
    var g = window.grecaptcha && window.grecaptcha.enterprise;
    if (!g) return;
    await new Promise(function(r){ g.ready(r); });
    var proId = ${lit(provisionalId)};
    try {
      if (Array.isArray(window.__next_f)) {
        var joined = window.__next_f.map(function(x){ return Array.isArray(x) && typeof x[1] === 'string' ? x[1] : ''; }).join('');
        var m = joined.match(/"initialUserId":"([0-9a-fA-F-]{36})"/);
        if (m && m[1]) proId = m[1];
      }
    } catch (e) {}
    var keys = [arenaSiteKey(${lit(SIGNUP_KEY)}), ${lit(SIGNUP_KEY)}];
    for (var k = 0; k < keys.length; k++) {
      var tok = await g.execute(keys[k], {action: 'sign_up'});
      var res = await fetch('/nextjs-api/sign-up', {
        method: 'POST', headers: {'Content-Type': 'application/json'}, credentials: 'include',
        body: JSON.stringify({recaptchaToken: tok || '', provisionalUserId: proId})
      });
      if (res.status === 409) {
        var data = await res.json();
        if (data && data.provisionalUserId) {
          var tok2 = await g.execute(keys[k], {action: 'sign_up'});
          res = await fetch('/nextjs-api/sign-up', {
            method: 'POST', headers: {'Content-Type': 'application/json'}, credentials: 'include',
            body: JSON.stringify({recaptchaToken: tok2 || '', provisionalUserId: data.provisionalUserId, provisionalIdRetry: true})
          });
        }
      }
      if (res.ok) return;
    }
  } catch (e) {}
})();
"""

    /** Collects the live model list: model-catalog endpoint plus the page's own RSC payload. */
    val MODELS_JS = """
(async function(){
  var out = {catalog: '', rsc: ''};
  try {
    var r = await fetch('/nextjs-api/model-catalog', {cache: 'no-store', credentials: 'include'});
    if (r.ok) out.catalog = await r.text();
  } catch (e) {}
  try {
    for (var i = 0; i < 24; i++) {
      if (Array.isArray(window.__next_f)) {
        out.rsc = window.__next_f.map(function(x){ return Array.isArray(x) && typeof x[1] === 'string' ? x[1] : ''; }).join('');
        if (out.rsc.indexOf('initialModels') >= 0) break;
      }
      await new Promise(function(r){ setTimeout(r, 400); });
    }
  } catch (e) {}
  ArenaNative.onModels(JSON.stringify(out));
})();
"""

    /**
     * Sends [prompt] through arena.ai's own composer. A fetch hook rewrites the page-built
     * create-evaluation request to use [model]/[modality] and tees the SSE stream to native.
     * If the composer cannot be driven, falls back to a direct fetch with a fresh reCAPTCHA token.
     */
    fun run(model: String, modality: String, prompt: String, fallbackBody: String): String = """
(function(MODEL, MODALITY, PROMPT, FALLBACK){
  $SITE_KEY_FN
  var N = window.ArenaNative;
  var finished = false, started = false, intercepted = false, directTried = false;
  var lastErr = '', errAt = 0, tick = 0, timer = null;
  var orig = window.__arenaOrigFetch || (window.__arenaOrigFetch = window.fetch.bind(window));
  function cleanup(){ finished = true; if (timer) clearInterval(timer); try { window.fetch = orig; } catch (e) {} }
  function fail(m){ if (finished) return; cleanup(); N.onError(String(m)); }
  function ok(){ if (finished) return; cleanup(); N.onDone(); }
  window.__arenaAbort = function(){ cleanup(); };

  function pump(resp){
    var reader = resp.body.getReader(), dec = new TextDecoder('utf-8'), buf = '';
    (function loop(){
      reader.read().then(function(p){
        if (p.done) { if (buf) N.onLine(buf); ok(); return; }
        buf += dec.decode(p.value, {stream: true});
        var parts = buf.split('\n'); buf = parts.pop() || '';
        for (var i = 0; i < parts.length; i++) if (parts[i]) N.onLine(parts[i]);
        loop();
      }).catch(function(e){ fail('Stream interrupted: ' + e); });
    })();
  }
  function rewrite(txt){
    try { var b = JSON.parse(txt); b.modelAId = MODEL; if (MODALITY) b.modality = MODALITY; return JSON.stringify(b); }
    catch (e) { return txt; }
  }
  function handle(resp){
    if (resp.ok) { started = true; pump(resp.clone()); }
    else { errAt = tick; resp.clone().text().then(function(t){ lastErr = resp.status + ': ' + String(t).slice(0, 400); }); }
    return resp;
  }
  window.fetch = function(input, init){
    var url = (typeof input === 'string') ? input : ((input && input.url) || '');
    if (finished || started || url.indexOf('create-evaluation') < 0) return orig(input, init);
    intercepted = true;
    if (init && typeof init.body === 'string') {
      return orig(input, Object.assign({}, init, {body: rewrite(init.body)})).then(handle);
    }
    if (typeof Request !== 'undefined' && input instanceof Request) {
      return input.clone().text().then(function(txt){
        return orig(url, {method: 'POST', headers: input.headers, credentials: 'include', body: rewrite(txt)});
      }).then(handle);
    }
    return orig(input, init).then(handle);
  };

  function composer(){ return document.querySelector('textarea') || document.querySelector('[contenteditable="true"]'); }
  function dismissGates(){
    var bs = document.querySelectorAll('button');
    for (var i = 0; i < bs.length; i++) {
      if (/^(agree|i agree|accept|accept all|got it|continue|ok)${'$'}/i.test((bs[i].textContent || '').trim()) && !bs[i].closest('form')) { bs[i].click(); return; }
    }
  }
  function fill(el){
    el.focus();
    if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
      var d = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value');
      d.set.call(el, PROMPT);
      el.dispatchEvent(new Event('input', {bubbles: true}));
    } else {
      document.execCommand('selectAll', false, null);
      document.execCommand('insertText', false, PROMPT);
    }
  }
  function send(el){
    var form = el.closest ? el.closest('form') : null;
    var btn = (form && form.querySelector('button[type="submit"]:not([disabled])')) ||
      document.querySelector('button[type="submit"]:not([disabled])') ||
      document.querySelector('button[aria-label*="send" i]:not([disabled])') ||
      document.querySelector('button[data-testid*="send" i]:not([disabled])');
    if (btn) { btn.click(); return; }
    if (form && form.requestSubmit) { form.requestSubmit(); return; }
    ['keydown', 'keypress', 'keyup'].forEach(function(t){
      el.dispatchEvent(new KeyboardEvent(t, {key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true}));
    });
  }
  async function direct(){
    try {
      var g = window.grecaptcha && window.grecaptcha.enterprise;
      var body = JSON.parse(FALLBACK);
      if (g) {
        await new Promise(function(r){ g.ready(r); });
        body.recaptchaV3Token = await g.execute(arenaSiteKey(${lit(V3_KEY)}), {action: 'chat_submit'});
      }
      var resp = await orig('/nextjs-api/stream/create-evaluation', {
        method: 'POST', headers: {'Content-Type': 'application/json'}, credentials: 'include', body: JSON.stringify(body)
      });
      if (!resp.ok) { var t = ''; try { t = await resp.text(); } catch (e) {} fail(resp.status + ': ' + String(t).slice(0, 400)); return; }
      started = true; pump(resp);
    } catch (e) { fail('Direct request failed: ' + e); }
  }

  var el = composer();
  if (el) { dismissGates(); fill(el); }
  timer = setInterval(function(){
    tick++;
    if (finished) return;
    if (!intercepted && !started && el && tick % 2 === 0 && tick <= 14) { dismissGates(); send(composer() || el); }
    if (!intercepted && !directTried && tick >= (el ? 18 : 2)) { directTried = true; direct(); }
    if (!started && lastErr && tick - errAt >= 10) fail(lastErr);
    if (!started && tick >= 80) fail(lastErr || 'No response from arena.ai');
  }, 1000);
})(${lit(model)}, ${lit(modality)}, ${lit(prompt)}, ${lit(fallbackBody)});
"""
}
