package com.liuli.browser.web.agent

import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

data class AgentElement(
    val ref: String,
    val role: String,
    val name: String,
    val tag: String,
    val inputType: String,
    val editable: Boolean
)

data class AgentPageSnapshot(
    val title: String,
    val url: String,
    val text: String,
    val selection: String,
    val language: String,
    val elements: List<AgentElement>,
    val linkCount: Int,
    val buttonCount: Int,
    val formCount: Int,
    val imageCount: Int,
    val truncated: Boolean
)

enum class AgentActionKind(val jsName: String) {
    Scroll("scroll"),
    Click("click"),
    Type("type")
}

data class AgentPageAction(
    val kind: AgentActionKind,
    val ref: String? = null,
    val text: String? = null,
    val direction: String? = null
)

data class AgentActionResult(
    val ok: Boolean,
    val message: String
)

/** 只读页面审计结果：只有计数与耗时，不包含正文、链接地址或输入值。 */
data class AgentPageAudit(
    val nodeCount: Int,
    val imageCount: Int,
    val imagesMissingAlt: Int,
    val interactiveCount: Int,
    val unnamedControls: Int,
    val headingCount: Int,
    val headingSkips: Int,
    val h1Count: Int,
    val hasLanguage: Boolean,
    val linkCount: Int,
    val formCount: Int,
    val passwordFields: Int,
    val resourceCount: Int,
    val transferKb: Int,
    val domReadyMs: Int,
    val loadMs: Int,
    val horizontalOverflow: Boolean
)

/** 只读页面结构：标题、表格与链接，均在本机解析。 */
data class AgentHeading(
    val level: Int,
    val text: String
)

data class AgentTable(
    val caption: String,
    val rows: List<List<String>>,
    val truncated: Boolean
)

data class AgentLink(
    val text: String,
    val url: String
)

data class AgentPageStructure(
    val headings: List<AgentHeading>,
    val tables: List<AgentTable>,
    val links: List<AgentLink>,
    val supported: Boolean
)

/** 页面高亮结果；CSS Highlight 不修改正文节点，只叠加临时视觉标记与一个可移除的构造样式表。 */
data class AgentHighlightResult(
    val count: Int,
    val supported: Boolean
)

/**
 * Read-only page snapshot plus a confirmation-gated action channel.
 *
 * Snapshots never read input values, cookies, local storage or the clipboard.
 * Actions are executed only after the UI asks the user, and every action is a
 * single step: the bridge cannot chain, submit or retry by itself.
 */
object AgentPageBridge {

    const val MAX_TEXT_CHARS = 12_000

    private const val SCRIPT_VERSION = 7

    private const val READY_CHECK =
        "typeof window.__liuliAgent === 'object' && window.__liuliAgent.version === $SCRIPT_VERSION"

    private val INSTALL_SCRIPT = """
        (function () {
          if (window.__liuliAgent && window.__liuliAgent.version === __VERSION__) { return 'ready'; }
          function clean(value) {
            return String(value == null ? '' : value).replace(/\s+/g, ' ').trim();
          }
          function visible(el) {
            if (!el) { return false; }
            var style = window.getComputedStyle(el);
            if (!style || style.display === 'none' || style.visibility === 'hidden') { return false; }
            if (parseFloat(style.opacity || '1') === 0) { return false; }
            var rect = el.getBoundingClientRect();
            return rect.width > 1 && rect.height > 1;
          }
          var refPrefix = 'r' + Math.random().toString(36).slice(2, 8);
          window.__liuliAgent = {
            version: __VERSION__,
            refMap: new WeakMap(),
            nextRef: 1,
            byRef: new Map(),
            snapshot: function (maxChars) {
              var limit = Math.max(2000, Math.min(maxChars || 12000, 20000));
              var body = document.body;
              var text = body ? (body.innerText || body.textContent || '') : '';
              text = text.replace(/\r/g, '').replace(/[ \t\u00a0]+/g, ' ').replace(/\n{3,}/g, '\n\n').trim();
              var truncated = text.length > limit;
              if (truncated) { text = text.slice(0, limit); }

              var selector = 'a,button,input,select,textarea,[role="button"],[role="link"],[role="checkbox"],[role="radio"],[role="tab"],[contenteditable="true"]';
              var nodes = document.querySelectorAll(selector);
              var elements = [];
              var byRef = new Map();
              var inspected = 0;
              for (var i = 0; i < nodes.length && elements.length < 60 && inspected < 400; i++) {
                var el = nodes[i];
                inspected++;
                if (!visible(el)) { continue; }
                var tag = (el.tagName || '').toLowerCase();
                var inputType = tag === 'input' ? clean(el.getAttribute('type')).toLowerCase() : '';
                if (tag === 'input' && inputType === 'hidden') { continue; }
                var role = clean(el.getAttribute('role')) || tag;
                var name = clean(el.getAttribute('aria-label')) || clean(el.getAttribute('title'));
                if (!name && tag === 'input') {
                  name = clean(el.getAttribute('placeholder')) || inputType || '输入框';
                }
                if (!name && tag === 'textarea') {
                  name = clean(el.getAttribute('placeholder')) || '多行输入框';
                }
                if (!name) { name = clean(el.innerText); }
                if (!name) { name = role; }
                // ref 在元素存活期间保持稳定，并带文档前缀，跨页面导航后不会误匹配。
                var ref = window.__liuliAgent.refMap.get(el);
                if (!ref) {
                  ref = refPrefix + 'e' + window.__liuliAgent.nextRef;
                  window.__liuliAgent.nextRef += 1;
                  window.__liuliAgent.refMap.set(el, ref);
                }
                byRef.set(ref, el);
                // Deliberately never touch el.value: existing form data stays private.
                elements.push({
                  ref: ref,
                  role: role,
                  name: name.slice(0, 80),
                  tag: tag,
                  type: inputType,
                  editable: clean(el.getAttribute('contenteditable')) === 'true'
                });
              }

              var selection = '';
              try {
                selection = clean(window.getSelection ? window.getSelection().toString() : '').slice(0, 500);
              } catch (error) {}
              var lang = '';
              try { lang = clean(document.documentElement.getAttribute('lang')); } catch (error) {}

              window.__liuliAgent.byRef = byRef;
              return JSON.stringify({
                title: clean(document.title).slice(0, 300),
                url: String(location.href || '').slice(0, 2048),
                text: text,
                selection: selection,
                language: lang.slice(0, 24),
                elements: elements,
                links: document.querySelectorAll('a[href]').length,
                buttons: document.querySelectorAll('button,[role="button"]').length,
                forms: document.querySelectorAll('form').length,
                images: document.querySelectorAll('img').length,
                truncated: truncated
              });
            },
            act: function (ref, kind, text, direction) {
              try {
                if (kind === 'scroll') {
                  var viewport = window.innerHeight || 600;
                  var bodyHeight = document.body ? document.body.scrollHeight : 0;
                  var rootHeight = document.documentElement ? document.documentElement.scrollHeight : 0;
                  var maxHeight = Math.max(bodyHeight, rootHeight);
                  if (direction === 'bottom') {
                    window.scrollTo({ top: maxHeight, behavior: 'smooth' });
                  } else if (direction === 'top') {
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                  } else if (direction === 'up') {
                    window.scrollBy({ top: -viewport * 0.8, behavior: 'smooth' });
                  } else {
                    window.scrollBy({ top: viewport * 0.8, behavior: 'smooth' });
                  }
                  return JSON.stringify({ ok: true, message: '已滚动页面' });
                }

                var el = window.__liuliAgent.byRef ? window.__liuliAgent.byRef.get(String(ref || '')) : null;
                if (!el || !el.isConnected) {
                  return JSON.stringify({ ok: false, message: '元素已失效，请重新读取页面后再试' });
                }
                if (!visible(el)) {
                  return JSON.stringify({ ok: false, message: '目标元素当前不可见' });
                }
                try { el.scrollIntoView({ block: 'center', inline: 'center', behavior: 'smooth' }); } catch (error) { }
                var tag = (el.tagName || '').toLowerCase();
                var label = clean(el.getAttribute('aria-label')) ||
                  clean(el.getAttribute('title')) ||
                  clean(el.innerText) || tag;

                if (kind === 'click') {
                  if (el.disabled || clean(el.getAttribute('aria-disabled')) === 'true') {
                    return JSON.stringify({ ok: false, message: '目标元素当前不可用' });
                  }
                  el.click();
                  return JSON.stringify({ ok: true, message: '已点击“' + label.slice(0, 60) + '”' });
                }
                if (kind === 'type') {
                  var inputType = clean(el.getAttribute('type')).toLowerCase();
                  if (tag === 'input' && (inputType === 'password' || inputType === 'hidden' || inputType === 'file')) {
                    return JSON.stringify({ ok: false, message: '出于隐私保护，不填写密码、隐藏或文件字段' });
                  }
                  if (el.disabled || el.readOnly) {
                    return JSON.stringify({ ok: false, message: '目标字段当前不可编辑' });
                  }
                  var autocomplete = clean(el.getAttribute('autocomplete')).toLowerCase();
                  if (autocomplete.indexOf('cc-') === 0 || autocomplete.indexOf('password') >= 0) {
                    return JSON.stringify({ ok: false, message: '出于隐私保护，不填写密码或支付字段' });
                  }
                  var editable = tag === 'input' || tag === 'textarea' ||
                    clean(el.getAttribute('contenteditable')) === 'true';
                  if (!editable) {
                    return JSON.stringify({ ok: false, message: '目标不是可输入字段' });
                  }
                  var value = String(text == null ? '' : text).slice(0, 2000);
                  try { el.focus(); } catch (error) { }
                  if (tag === 'input' || tag === 'textarea') {
                    var proto = tag === 'input' ? window.HTMLInputElement.prototype : window.HTMLTextAreaElement.prototype;
                    var descriptor = Object.getOwnPropertyDescriptor(proto, 'value');
                    if (descriptor && descriptor.set) {
                      descriptor.set.call(el, value);
                    } else {
                      el.value = value;
                    }
                  } else {
                    el.textContent = value;
                  }
                  try { el.dispatchEvent(new Event('input', { bubbles: true })); } catch (error) { }
                  try { el.dispatchEvent(new Event('change', { bubbles: true })); } catch (error) { }
                  return JSON.stringify({ ok: true, message: '已填入文本，未提交表单' });
                }
                return JSON.stringify({ ok: false, message: '不支持的动作' });
              } catch (error) {
                return JSON.stringify({
                  ok: false,
                  message: '执行失败：' + (error && error.message ? error.message : '未知错误')
                });
              }
            },
            audit: function () {
              try {
                var images = document.querySelectorAll('img');
                var missingAlt = 0;
                for (var i = 0; i < images.length; i++) {
                  var img = images[i];
                  if (clean(img.getAttribute('alt'))) { continue; }
                  if (clean(img.getAttribute('aria-label')) || clean(img.getAttribute('aria-labelledby'))) { continue; }
                  var role = clean(img.getAttribute('role'));
                  if (role === 'presentation' || role === 'none') { continue; }
                  missingAlt++;
                }

                var controls = document.querySelectorAll('a[href],button,input:not([type="hidden"]),select,textarea,[role="button"],[role="link"]');
                var unnamed = 0;
                var visibleControls = 0;
                for (var j = 0; j < controls.length && j < 800; j++) {
                  var el = controls[j];
                  if (!visible(el)) { continue; }
                  visibleControls++;
                  var name = clean(el.getAttribute('aria-label')) ||
                    clean(el.getAttribute('aria-labelledby')) ||
                    clean(el.getAttribute('title')) ||
                    clean(el.getAttribute('placeholder')) ||
                    clean(el.innerText) || clean(el.textContent);
                  if (!name) {
                    var tag = (el.tagName || '').toLowerCase();
                    var type = clean(el.getAttribute('type')).toLowerCase();
                    if (tag === 'input' && (type === 'submit' || type === 'button' || type === 'reset')) {
                      name = clean(el.value);
                    }
                  }
                  if (!name) { unnamed++; }
                }

                var headings = document.querySelectorAll('h1,h2,h3,h4,h5,h6');
                var skips = 0;
                var lastLevel = 0;
                var h1 = 0;
                for (var k = 0; k < headings.length; k++) {
                  var level = parseInt((headings[k].tagName || 'h6').slice(1), 10) || 6;
                  if (level === 1) { h1++; }
                  if (lastLevel > 0 && level > lastLevel + 1) { skips++; }
                  lastLevel = level;
                }

                var nav = null;
                var resources = 0;
                try {
                  var entries = performance.getEntriesByType ? performance.getEntriesByType('navigation') : [];
                  nav = entries && entries.length ? entries[0] : null;
                  resources = performance.getEntriesByType ? performance.getEntriesByType('resource').length : 0;
                } catch (error) {}
                var transfer = nav && typeof nav.transferSize === 'number' ? Math.round(nav.transferSize / 1024) : -1;
                var domMs = nav && nav.domContentLoadedEventEnd ? Math.round(nav.domContentLoadedEventEnd) : -1;
                var loadMs = nav && nav.loadEventEnd ? Math.round(nav.loadEventEnd) : -1;

                return JSON.stringify({
                  nodes: document.querySelectorAll('*').length,
                  images: images.length,
                  missingAlt: missingAlt,
                  controls: visibleControls,
                  unnamed: unnamed,
                  headings: headings.length,
                  skips: skips,
                  h1: h1,
                  lang: !!clean(document.documentElement.getAttribute('lang')),
                  links: document.querySelectorAll('a[href]').length,
                  forms: document.querySelectorAll('form').length,
                  passwords: document.querySelectorAll('input[type="password"]').length,
                  resources: resources,
                  transferKb: transfer,
                  domMs: domMs,
                  loadMs: loadMs,
                  overflow: document.documentElement.scrollWidth > window.innerWidth + 8
                });
              } catch (error) {
                return 'null';
              }
            },
            structure: function () {
              try {
                var headings = [];
                var hs = document.querySelectorAll('h1,h2,h3,h4,h5,h6');
                for (var i = 0; i < hs.length && headings.length < 60; i++) {
                  var h = hs[i];
                  if (!visible(h)) { continue; }
                  var headingText = clean(h.innerText || h.textContent);
                  if (!headingText) { continue; }
                  headings.push({
                    level: parseInt((h.tagName || 'h6').slice(1), 10) || 6,
                    text: headingText.slice(0, 120)
                  });
                }

                var tables = [];
                var tableNodes = document.querySelectorAll('table');
                for (var j = 0; j < tableNodes.length && tables.length < 6; j++) {
                  var table = tableNodes[j];
                  if (!visible(table)) { continue; }
                  var caption = clean(table.getAttribute('aria-label')) ||
                    clean(table.caption ? table.caption.innerText : '');
                  var rowNodes = table.querySelectorAll('tr');
                  var rows = [];
                  var rowLimit = Math.min(rowNodes.length, 12);
                  for (var r = 0; r < rowLimit; r++) {
                    var cells = rowNodes[r].querySelectorAll('th,td');
                    var row = [];
                    for (var c = 0; c < cells.length && c < 8; c++) {
                      row.push(clean(cells[c].innerText || cells[c].textContent).slice(0, 160));
                    }
                    if (row.length) { rows.push(row); }
                  }
                  if (!rows.length) { continue; }
                  tables.push({
                    caption: caption.slice(0, 120),
                    rows: rows,
                    truncated: rowNodes.length > rowLimit
                  });
                }

                var links = [];
                var seen = {};
                var linkNodes = document.querySelectorAll('a[href]');
                for (var k = 0; k < linkNodes.length && links.length < 80; k++) {
                  var a = linkNodes[k];
                  if (!visible(a)) { continue; }
                  var href = '';
                  try {
                    var parsed = new URL(a.getAttribute('href'), location.href);
                    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') { continue; }
                    // 去掉查询参数与锚点，避免把跟踪参数或会话令牌带进本地结果。
                    href = parsed.origin + parsed.pathname;
                  } catch (error) { continue; }
                  if (!href || seen[href]) { continue; }
                  seen[href] = true;
                  var linkText = clean(a.getAttribute('aria-label')) ||
                    clean(a.getAttribute('title')) ||
                    clean(a.innerText || a.textContent);
                  links.push({
                    text: (linkText || href).slice(0, 120),
                    url: href.slice(0, 1024)
                  });
                }

                return JSON.stringify({
                  headings: headings,
                  tables: tables,
                  links: links,
                  supported: true
                });
              } catch (error) {
                return JSON.stringify({
                  headings: [], tables: [], links: [], supported: false
                });
              }
            },
            highlight: function (phrases) {
              try {
                if (!window.CSS || !CSS.highlights || typeof Highlight !== 'function' ||
                    typeof CSSStyleSheet !== 'function') {
                  return JSON.stringify({ count: 0, supported: false });
                }
                CSS.highlights.delete('liuli-agent');
                var oldStyle = document.getElementById('liuli-agent-highlight-style');
                if (oldStyle) { oldStyle.remove(); }
                // 构造样式表不进入 DOM 树，也不受页面 style-src CSP 限制。
                var oldSheet = window.__liuliAgentHighlightSheet;
                if (oldSheet) {
                  document.adoptedStyleSheets = document.adoptedStyleSheets.filter(
                    function (item) { return item !== oldSheet; }
                  );
                }
                var sheet = new CSSStyleSheet();
                sheet.replaceSync(
                  '::highlight(liuli-agent){background-color:rgba(255,214,10,0.55);color:inherit;}'
                );
                document.adoptedStyleSheets = document.adoptedStyleSheets.concat(sheet);
                window.__liuliAgentHighlightSheet = sheet;

                var ranges = [];
                var texts = [];
                var walker = document.createTreeWalker(
                  document.body || document.documentElement,
                  NodeFilter.SHOW_TEXT,
                  null
                );
                while (walker.nextNode()) { texts.push(walker.currentNode); }
                var list = Array.isArray(phrases) ? phrases : [];
                for (var p = 0; p < list.length && ranges.length < 20; p++) {
                  var phrase = clean(list[p]).slice(0, 160);
                  // 中文词组两个字即可，英文至少四个字符，避免过于宽泛的单字匹配。
                  var minLength = /[\u4e00-\u9fa5]/.test(phrase) ? 2 : 4;
                  if (phrase.length < minLength) { continue; }
                  var probe = phrase.slice(0, Math.min(40, phrase.length));
                  var perPhrase = 0;
                  for (var t = 0; t < texts.length && ranges.length < 20 && perPhrase < 4; t++) {
                    var node = texts[t];
                    var parent = node.parentElement;
                    if (!parent) { continue; }
                    var tag = (parent.tagName || '').toLowerCase();
                    if (tag === 'script' || tag === 'style' || tag === 'noscript' ||
                        tag === 'textarea' || tag === 'input' || tag === 'select') { continue; }
                    var raw = node.nodeValue || '';
                    var index = raw.indexOf(phrase);
                    var length = phrase.length;
                    if (index < 0) {
                      index = raw.indexOf(probe);
                      length = probe.length;
                    }
                    if (index < 0) { continue; }
                    var range = document.createRange();
                    range.setStart(node, index);
                    range.setEnd(node, index + length);
                    ranges.push(range);
                    perPhrase++;
                  }
                }
                if (ranges.length) {
                  CSS.highlights.set('liuli-agent', new Highlight(...ranges));
                }
                return JSON.stringify({ count: ranges.length, supported: true });
              } catch (error) {
                return JSON.stringify({ count: 0, supported: false });
              }
            },
            clearHighlight: function () {
              try {
                if (window.CSS && CSS.highlights) {
                  CSS.highlights.delete('liuli-agent');
                }
                var sheet = window.__liuliAgentHighlightSheet;
                if (sheet && 'adoptedStyleSheets' in document) {
                  document.adoptedStyleSheets = document.adoptedStyleSheets.filter(
                    function (item) { return item !== sheet; }
                  );
                }
                window.__liuliAgentHighlightSheet = null;
                var style = document.getElementById('liuli-agent-highlight-style');
                if (style) { style.remove(); }
                return JSON.stringify({ ok: true });
              } catch (error) {
                return JSON.stringify({ ok: false });
              }
            }
          };
          return 'ready';
        })()
    """.trimIndent().replace("__VERSION__", SCRIPT_VERSION.toString())

    fun requestSnapshot(
        webView: WebView,
        maxChars: Int = MAX_TEXT_CHARS,
        onResult: (AgentPageSnapshot?) -> Unit
    ) {
        webView.evaluateJavascript(READY_CHECK) { value ->
            if (value == "true") {
                evaluate(webView, maxChars, onResult)
            } else {
                webView.evaluateJavascript(INSTALL_SCRIPT) {
                    evaluate(webView, maxChars, onResult)
                }
            }
        }
    }

    suspend fun snapshot(webView: WebView): AgentPageSnapshot? =
        suspendCancellableCoroutine { continuation ->
            requestSnapshot(webView) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }

    /** 只读审计：只返回计数、耗时与布尔特征，不返回正文或链接地址。 */
    suspend fun audit(webView: WebView): AgentPageAudit? =
        suspendCancellableCoroutine { continuation ->
            requestAudit(webView) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }

    /** 只读页面结构：标题层级、表格与去掉查询参数的链接。 */
    suspend fun structure(webView: WebView): AgentPageStructure? =
        suspendCancellableCoroutine { continuation ->
            requestStructure(webView) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }

    /** 用 CSS Highlight 在页面上叠加临时高亮，不修改正文与 DOM 树，仅临时注册可移除样式表；禁止站点不启用。 */
    suspend fun highlight(webView: WebView, phrases: List<String>): AgentHighlightResult? =
        suspendCancellableCoroutine { continuation ->
            requestHighlight(webView, phrases) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }

    suspend fun clearHighlight(webView: WebView): Boolean =
        suspendCancellableCoroutine { continuation ->
            val script = """
                (function () {
                  try {
                    if (!window.__liuliAgent || typeof window.__liuliAgent.clearHighlight !== 'function') {
                      return JSON.stringify({ ok: false });
                    }
                    return window.__liuliAgent.clearHighlight();
                  } catch (error) {
                    return JSON.stringify({ ok: false });
                  }
                })()
            """.trimIndent()
            webView.evaluateJavascript(script) { raw ->
                val json = parseJson(raw)
                if (continuation.isActive) continuation.resume(json?.optBoolean("ok") == true)
            }
        }

    private fun requestStructure(webView: WebView, onResult: (AgentPageStructure?) -> Unit) {
        webView.evaluateJavascript(READY_CHECK) { value ->
            if (value == "true") {
                evaluateStructure(webView, onResult)
            } else {
                webView.evaluateJavascript(INSTALL_SCRIPT) {
                    evaluateStructure(webView, onResult)
                }
            }
        }
    }

    private fun evaluateStructure(webView: WebView, onResult: (AgentPageStructure?) -> Unit) {
        val script = """
            (function () {
              try {
                if (!window.__liuliAgent || typeof window.__liuliAgent.structure !== 'function') {
                  return 'null';
                }
                return window.__liuliAgent.structure();
              } catch (error) {
                return 'null';
              }
            })()
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            onResult(parseStructure(raw))
        }
    }

    private fun requestHighlight(
        webView: WebView,
        phrases: List<String>,
        onResult: (AgentHighlightResult?) -> Unit
    ) {
        val array = JSONArray()
        phrases.forEach { array.put(it) }
        webView.evaluateJavascript(READY_CHECK) { value ->
            if (value == "true") {
                evaluateHighlight(webView, array, onResult)
            } else {
                webView.evaluateJavascript(INSTALL_SCRIPT) {
                    evaluateHighlight(webView, array, onResult)
                }
            }
        }
    }

    private fun evaluateHighlight(
        webView: WebView,
        phrases: JSONArray,
        onResult: (AgentHighlightResult?) -> Unit
    ) {
        val script = """
            (function () {
              try {
                if (!window.__liuliAgent || typeof window.__liuliAgent.highlight !== 'function') {
                  return 'null';
                }
                return window.__liuliAgent.highlight(${phrases});
              } catch (error) {
                return 'null';
              }
            })()
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            onResult(parseHighlight(raw))
        }
    }

    private fun requestAudit(webView: WebView, onResult: (AgentPageAudit?) -> Unit) {
        webView.evaluateJavascript(READY_CHECK) { value ->
            if (value == "true") {
                evaluateAudit(webView, onResult)
            } else {
                webView.evaluateJavascript(INSTALL_SCRIPT) {
                    evaluateAudit(webView, onResult)
                }
            }
        }
    }

    private fun evaluateAudit(webView: WebView, onResult: (AgentPageAudit?) -> Unit) {
        val script = """
            (function () {
              try {
                if (!window.__liuliAgent || typeof window.__liuliAgent.audit !== 'function') {
                  return 'null';
                }
                return window.__liuliAgent.audit();
              } catch (error) {
                return 'null';
              }
            })()
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            onResult(parseAudit(raw))
        }
    }

    suspend fun execute(webView: WebView, action: AgentPageAction): AgentActionResult =
        suspendCancellableCoroutine { continuation ->
            val script = buildString {
                append("(function () { try {")
                append("if (!window.__liuliAgent || typeof window.__liuliAgent.act !== 'function') { return null; }")
                append("return window.__liuliAgent.act(")
                append(JSONObject.quote(action.ref.orEmpty())).append(',')
                append(JSONObject.quote(action.kind.jsName)).append(',')
                append(JSONObject.quote(action.text.orEmpty())).append(',')
                append(JSONObject.quote(action.direction.orEmpty()))
                append("); } catch (error) { return null; } })()")
            }
            webView.evaluateJavascript(script) { raw ->
                if (continuation.isActive) continuation.resume(parseActionResult(raw))
            }
        }

    private fun evaluate(
        webView: WebView,
        maxChars: Int,
        onResult: (AgentPageSnapshot?) -> Unit
    ) {
        val script = """
            (function () {
              try {
                if (!window.__liuliAgent) { return 'null'; }
                return window.__liuliAgent.snapshot($maxChars);
              } catch (error) {
                return 'null';
              }
            })()
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            onResult(parseSnapshot(raw))
        }
    }

    private fun parseSnapshot(raw: String?): AgentPageSnapshot? {
        val json = parseJson(raw) ?: return null
        val elements = mutableListOf<AgentElement>()
        val array = json.optJSONArray("elements") ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            if (name.isEmpty()) continue
            elements += AgentElement(
                ref = item.optString("ref"),
                role = item.optString("role"),
                name = name,
                tag = item.optString("tag"),
                inputType = item.optString("type"),
                editable = item.optBoolean("editable")
            )
        }
        return AgentPageSnapshot(
            title = json.optString("title").trim(),
            url = json.optString("url").trim(),
            text = json.optString("text"),
            selection = json.optString("selection").trim(),
            language = json.optString("language").trim(),
            elements = elements,
            linkCount = json.optInt("links"),
            buttonCount = json.optInt("buttons"),
            formCount = json.optInt("forms"),
            imageCount = json.optInt("images"),
            truncated = json.optBoolean("truncated")
        )
    }

    private fun parseAudit(raw: String?): AgentPageAudit? {
        val json = parseJson(raw) ?: return null
        return AgentPageAudit(
            nodeCount = json.optInt("nodes"),
            imageCount = json.optInt("images"),
            imagesMissingAlt = json.optInt("missingAlt"),
            interactiveCount = json.optInt("controls"),
            unnamedControls = json.optInt("unnamed"),
            headingCount = json.optInt("headings"),
            headingSkips = json.optInt("skips"),
            h1Count = json.optInt("h1"),
            hasLanguage = json.optBoolean("lang"),
            linkCount = json.optInt("links"),
            formCount = json.optInt("forms"),
            passwordFields = json.optInt("passwords"),
            resourceCount = json.optInt("resources"),
            transferKb = json.optInt("transferKb", -1),
            domReadyMs = json.optInt("domMs", -1),
            loadMs = json.optInt("loadMs", -1),
            horizontalOverflow = json.optBoolean("overflow")
        )
    }

    private fun parseStructure(raw: String?): AgentPageStructure? {
        val json = parseJson(raw) ?: return null
        val headings = ArrayList<AgentHeading>()
        json.optJSONArray("headings")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val text = item.optString("text").trim()
                if (text.isEmpty()) continue
                headings += AgentHeading(
                    level = item.optInt("level", 6).coerceIn(1, 6),
                    text = text
                )
            }
        }
        val tables = ArrayList<AgentTable>()
        json.optJSONArray("tables")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val rows = ArrayList<List<String>>()
                item.optJSONArray("rows")?.let { rowArray ->
                    for (rowIndex in 0 until rowArray.length()) {
                        val row = rowArray.optJSONArray(rowIndex) ?: continue
                        val cells = ArrayList<String>()
                        for (cellIndex in 0 until row.length()) {
                            cells += row.optString(cellIndex).trim()
                        }
                        if (cells.isNotEmpty()) rows += cells
                    }
                }
                if (rows.isEmpty()) continue
                tables += AgentTable(
                    caption = item.optString("caption").trim(),
                    rows = rows,
                    truncated = item.optBoolean("truncated")
                )
            }
        }
        val links = ArrayList<AgentLink>()
        json.optJSONArray("links")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url").trim()
                if (!url.startsWith("http://") && !url.startsWith("https://")) continue
                val text = item.optString("text").trim().ifBlank { url }
                links += AgentLink(text = text, url = url)
            }
        }
        return AgentPageStructure(
            headings = headings,
            tables = tables,
            links = links,
            supported = json.optBoolean("supported", true)
        )
    }

    private fun parseHighlight(raw: String?): AgentHighlightResult? {
        val json = parseJson(raw) ?: return null
        return AgentHighlightResult(
            count = json.optInt("count").coerceAtLeast(0),
            supported = json.optBoolean("supported")
        )
    }

    private fun parseActionResult(raw: String?): AgentActionResult {
        val json = parseJson(raw)
            ?: return AgentActionResult(false, "页面脚本不可用，请重新读取页面后再试")
        val ok = json.optBoolean("ok")
        val message = json.optString("message").trim().ifBlank {
            if (ok) "已执行" else "执行失败"
        }
        return AgentActionResult(ok, message)
    }

    private fun parseJson(raw: String?): JSONObject? {
        if (raw.isNullOrBlank() || raw == "null" || raw == "\"null\"") return null
        return runCatching {
            when (val value = JSONTokener(raw).nextValue()) {
                is JSONObject -> value
                is String -> JSONObject(value)
                else -> null
            }
        }.getOrNull()
    }
}
