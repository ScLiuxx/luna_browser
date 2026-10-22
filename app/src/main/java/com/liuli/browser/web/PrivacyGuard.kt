package com.liuli.browser.web

import android.webkit.WebView

/**
 * 反浏览器指纹保护。在页面脚本执行前注入，统一 Canvas / WebGL /
 * navigator / 硬件 / 媒体 / 后台服务等指纹维度，并阻止 WebRTC、
 * Service Worker、推送与跨标签广播等追踪向量。所有注入幂等且容错。
 */
object PrivacyGuard {

    const val INSTALL_FLAG = "__liuliFingerprintGuard"
    const val PRIVATE_FLAG = "__liuliPrivateGuard"

    private const val SCRIPT = """
        (function () {
          if (window.__liuliFingerprintGuard) { return; }
          window.__liuliFingerprintGuard = true;

          // --- Canvas 指纹：toDataURL/toBlob 统一返回固定空白图像 ---
          try {
            var FIXED = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';
            HTMLCanvasElement.prototype.toDataURL = function () { return FIXED; };
            HTMLCanvasElement.prototype.toBlob = function (cb) {
              if (cb) { cb(new Blob([FIXED], { type: 'image/png' })); }
            };
          } catch (e) {}

          // --- WebGL 指纹：泛化厂商/渲染器，屏蔽调试扩展 ---
          try {
            var gl = WebGLRenderingContext.prototype;
            var gp = gl.getParameter;
            gl.getParameter = function (p) {
              if (p === 37445) { return 'Intel Inc.'; }
              if (p === 37446) { return 'Intel Iris OpenGL Engine'; }
              return gp.call(this, p);
            };
            var ge = gl.getExtension;
            gl.getExtension = function (n) {
              if (n === 'WEBGL_debug_renderer_info') { return null; }
              return ge.call(this, n);
            };
          } catch (e) {}

          // --- navigator 指纹：隐藏 webdriver，固定 plugins/languages/平台/硬件 ---
          try {
            Object.defineProperty(Navigator.prototype, 'webdriver', {
              get: function () { return false; }, configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(Navigator.prototype, 'plugins', {
              get: function () { return [{}, {}, {}, {}, {}]; }, configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(Navigator.prototype, 'languages', {
              get: function () { return ['en-US', 'en']; }, configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(Navigator.prototype, 'platform', {
              get: function () { return 'Linux x86_64'; }, configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(Navigator.prototype, 'hardwareConcurrency', {
              get: function () { return 4; }, configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(Navigator.prototype, 'deviceMemory', {
              get: function () { return 8; }, configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(Navigator.prototype, 'maxTouchPoints', {
              get: function () { return 0; }, configurable: true
            });
          } catch (e) {}

          // --- 媒体指纹与敏感 API：WebRTC / MediaStream / 剪贴板 / 语音 ---
          try {
            window.RTCPeerConnection = undefined;
            window.webkitRTCPeerConnection = undefined;
            window.RTCSessionDescription = undefined;
            window.RTCIceCandidate = undefined;
            window.MediaStream = undefined;
            window.webkitMediaStream = undefined;
            navigator.mediaDevices = undefined;
          } catch (e) {}
          try {
            Object.defineProperty(Navigator.prototype, 'clipboard', {
              get: function () { return undefined; }, configurable: true
            });
          } catch (e) {}
          try {
            window.speechSynthesis = undefined;
          } catch (e) {}

          // --- 后台服务与跨标签追踪：Service Worker / Push / Sync / 广播 ---
          try {
            Object.defineProperty(Navigator.prototype, 'serviceWorker', {
              get: function () { return undefined; }, configurable: true
            });
          } catch (e) {}
          try {
            window.PushManager = undefined;
            window.PeriodicSyncManager = undefined;
            window.SyncManager = undefined;
            window.BroadcastChannel = undefined;
            window.caches = undefined;
            window.SharedWorker = undefined;
          } catch (e) {}

          // --- 电池 / 通知 / 媒体会话 / 凭据 / 锁 / 屏幕等指纹 ---
          try {
            navigator.getBattery = undefined;
          } catch (e) {}
          try {
            window.Notification = undefined;
            window.ServiceWorkerNotifications = undefined;
          } catch (e) {}
          try {
            Object.defineProperty(Navigator.prototype, 'mediaSession', {
              get: function () { return null; }, configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(Navigator.prototype, 'credentials', {
              get: function () { return undefined; }, configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(Navigator.prototype, 'locks', {
              get: function () { return undefined; }, configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(Screen.prototype, 'colorDepth', {
              get: function () { return 24; }, configurable: true
            });
            Object.defineProperty(Screen.prototype, 'pixelDepth', {
              get: function () { return 24; }, configurable: true
            });
          } catch (e) {}
          try {
            window.history.length = 1;
          } catch (e) {}
          try {
            window.PresentationRequest = undefined;
            window.Presentation = undefined;
            window.Serial = undefined;
            window.USB = undefined;
            window.HID = undefined;
            window.Gamepad = undefined;
            window.Bluetooth = undefined;
          } catch (e) {}
          try {
            Object.defineProperty(Document.prototype, 'referrer', {
              get: function () { return ''; }, configurable: true
            });
          } catch (e) {}

          // --- 跨标签页 window.name 泄露 ---
          try {
            Object.defineProperty(window, 'name', {
              get: function () { return ''; },
              set: function () { },
              configurable: true
            });
          } catch (e) {}
        })()
    """.trimIndent()

    /**
     * 隐私标签的存储隔离：阻止 localStorage / sessionStorage / IndexedDB /
     * Cache / Service Worker 等任何持久化，避免隐私浏览与普通浏览交叉污染。
     */
    private const val PRIVATE_SCRIPT = """
        (function () {
          if (window.__liuliPrivateGuard) { return; }
          window.__liuliPrivateGuard = true;

          var deny = function () { throw new SecurityError('Storage is disabled in private tabs.'); };

          try {
            Object.defineProperty(window, 'localStorage', {
              get: deny, configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(window, 'sessionStorage', {
              get: deny, configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(window, 'indexedDB', {
              get: function () { return undefined; }, configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(window, 'caches', {
              get: function () { return undefined; }, configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(Navigator.prototype, 'serviceWorker', {
              get: function () { return undefined; }, configurable: true
            });
          } catch (e) {}
          try {
            window.openDatabase = undefined;
            window.webkitRequestFileSystem = undefined;
          } catch (e) {}
        })()
    """.trimIndent()

    fun install(webView: WebView) {
        webView.evaluateJavascript(SCRIPT, null)
    }

    fun installPrivate(webView: WebView) {
        webView.evaluateJavascript(PRIVATE_SCRIPT, null)
    }
}
