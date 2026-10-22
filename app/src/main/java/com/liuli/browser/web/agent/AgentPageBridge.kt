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

/**
 * Read-only page snapshot plus a confirmation-gated action channel.
 *
 * Snapshots never read input values, cookies, local storage or the clipboard.
 * Actions are executed only after the UI asks the user, and every action is a
 * single step: the bridge cannot chain, submit or retry by itself.
 */
object AgentPageBridge {

    const val MAX_TEXT_CHARS = 12_000

    private const val SCRIPT_VERSION = 4

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
