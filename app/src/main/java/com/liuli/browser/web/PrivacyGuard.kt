package com.liuli.browser.web

import android.webkit.WebView

/**
 * 反浏览器指纹保护。在页面脚本执行前注入，统一 Canvas / WebGL /
 * navigator / 硬件信息等指纹维度，并阻止 WebRTC 与 MediaStream，
 * 防止真实 IP 与媒体设备特征泄露。所有注入幂等且容错。
 */
object PrivacyGuard {

    const val INSTALL_FLAG = "__liuliFingerprintGuard"

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

          // --- WebRTC：阻止，防止 STUN/TURN 泄露真实 IP ---
          try {
            window.RTCPeerConnection = undefined;
            window.webkitRTCPeerConnection = undefined;
            window.RTCSessionDescription = undefined;
            window.RTCIceCandidate = undefined;
          } catch (e) {}

          // --- MediaStream：阻止 getUserMedia 之外的媒体指纹采集 ---
          try {
            window.MediaStream = undefined;
            window.webkitMediaStream = undefined;
            window.navigator.mediaDevices = undefined;
          } catch (e) {}
        })()
    """.trimIndent()

    fun install(webView: WebView) {
        webView.evaluateJavascript(SCRIPT, null)
    }
}
