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

          // --- WebGL / WebGL2 指纹：泛化厂商、渲染器、版本与能力参数 ---
          try {
            var patchGL = function (proto, version, glsl) {
              if (!proto) { return; }
              var gp = proto.getParameter;
              proto.getParameter = function (p) {
                if (p === 37445) { return 'Intel Inc.'; }
                if (p === 37446) { return 'Intel Iris OpenGL Engine'; }
                if (p === 7936) { return 'WebKit'; }
                if (p === 7937) { return 'WebKit WebGL'; }
                if (p === 7938) { return version; }
                if (p === 35724) { return glsl; }
                if (p === 3379) { return 4096; }
                if (p === 34024) { return 4096; }
                if (p === 3386) { return new Int32Array([4096, 4096]); }
                return gp.call(this, p);
              };
              var ge = proto.getExtension;
              proto.getExtension = function (n) {
                if (n === 'WEBGL_debug_renderer_info' || n === 'WEBGL_debug_shaders') { return null; }
                return ge.call(this, n);
              };
            };
            patchGL(
              window.WebGLRenderingContext && WebGLRenderingContext.prototype,
              'WebGL 1.0 (OpenGL ES 2.0 Chromium)',
              'WebGL GLSL ES 1.0 (OpenGL ES 2.0 Chromium)'
            );
            patchGL(
              window.WebGL2RenderingContext && WebGL2RenderingContext.prototype,
              'WebGL 2.0 (OpenGL ES 3.0 Chromium)',
              'WebGL GLSL ES 3.00 (OpenGL ES 3.0 Chromium)'
            );
          } catch (e) {}

          // --- Client Hints（UA-CH）：统一品牌与高熵值，阻止机型/架构泄露 ---
          try {
            var uaText = navigator.userAgent || '';
            var chromeMajor = (uaText.match(/Chrome\/([0-9]+)/) || [])[1] || '120';
            var uaPlatform = /Android/i.test(uaText) ? 'Android'
              : (/X11|Linux/i.test(uaText) ? 'Linux' : 'Unknown');
            var uaMobile = /Mobile/i.test(uaText);
            var uaArch = uaPlatform === 'Android' ? 'arm' : 'x86';
            var brandList = [
              { brand: 'Chromium', version: chromeMajor },
              { brand: 'Google Chrome', version: chromeMajor },
              { brand: 'Not_A Brand', version: '24' }
            ];
            var hintData = {
              brands: brandList,
              mobile: uaMobile,
              platform: uaPlatform,
              getHighEntropyValues: function () {
                return Promise.resolve({
                  architecture: uaArch,
                  bitness: '64',
                  brands: brandList,
                  fullVersionList: brandList.map(function (b) {
                    return { brand: b.brand, version: b.version + '.0.0.0' };
                  }),
                  mobile: uaMobile,
                  model: '',
                  platform: uaPlatform,
                  platformVersion: '',
                  uaFullVersion: chromeMajor + '.0.0.0',
                  wow64: false
                });
              },
              toJSON: function () {
                return { brands: brandList, mobile: uaMobile, platform: uaPlatform };
              }
            };
            Object.defineProperty(Navigator.prototype, 'userAgentData', {
              get: function () { return hintData; }, configurable: true
            });
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

          // --- 阻止 sendBeacon 跨站数据外泄 ---
          try {
            navigator.sendBeacon = undefined;
          } catch (e) {}

          // --- 拦截 window.open 到已知跟踪域名 ---
          try {
            var TRACKERS = [
              'doubleclick', 'googlesyndication', 'google-analytics', 'googletagmanager',
              'googleadservices', 'adservice.google', 'analytics.', 'facebook.net',
              'connect.facebook', 'twitter.com', 'criteo', 'taboola', 'outbrain',
              'scorecardresearch', 'quantserve', 'quantcount', 'hm.baidu', 'pos.baidu',
              'cpro.baidu', 'eclick.baidu', 'mobads.baidu', 'cnzz', 'umeng', 'umengcloud',
              'talkingdata', 'adjust', 'appsflyer', 'branch.io', 'bugly.qq', 'pingjs.qq',
              'beacon.qq', 'hotjar', 'mixpanel', 'segment', 'amplitude', 'sentry.io',
              'newrelic', 'mc.yandex', 'top.mail.ru', 'ads.yahoo', 'amazon-adsystem',
              'rubiconproject', 'pubmatic', 'openx.net', 'casalemedia', 'adnxs',
              '33across', 'mathtag', 'bluekai', 'demdex', 'omtrdc', 'everesttech',
              'agkn', 'crwdcntrl', 'lijit', 'rlcdn', 'sharethrough', 'teads.tv',
              'smartadserver', 'adform', 'adsrvr', 'zeotap', 'id5-sync',
              'adsafeprotected', 'moatads', 'doubleverify', 'research.de',
              'fullstory', 'logrocket', 'bugsnag', 'rollbar', 'airbrake', 'posthog',
              'heap.io', 'heapanalytics', 'countly', 'plausible.io', 'fathom.co',
              'simpleanalytics', 'clarity.ms', 'mouseflow', 'freshmarketer', 'adroll',
              'permutive', 'parsely', 'leadfeeder', 'clearbit', 'statsig',
              'launchdarkly', 'split.io', 'optimizely', 'dynamicyield', 'sitecatalyst',
              'adobe.io', 'sharedid', 'eyeota', 'braze', 'iterable', 'customer.io',
              'onesignal', 'pushwoosh', 'urbanairship', 'appcenter', 'hockeyapp',
              'fabric.io', 'crashlytics', 'instabug', 'mgid', 'triplelift', 'bshift',
              '3lift', 'revcontent', 'statcounter', 'clicky', 'statowl',
              'contentsquare', 'smartlook', 'mail.ru', '2domain'
            ];
            var origOpen = window.open;
            window.open = function (url) {
              try {
                if (url) {
                  var lower = String(url).toLowerCase();
                  for (var i = 0; i < TRACKERS.length; i++) {
                    if (lower.indexOf(TRACKERS[i]) >= 0) { return null; }
                  }
                }
              } catch (e) {}
              return origOpen.apply(this, arguments);
            };
          } catch (e) {}

          // --- 字体指纹：阻止 document.fonts 枚举可用字体 ---
          try {
            Object.defineProperty(Document.prototype, 'fonts', {
              get: function () { return undefined; }, configurable: true
            });
          } catch (e) {}

          // --- 网络信息指纹：固定 NetworkInformation 属性 ---
          try {
            Object.defineProperty(Navigator.prototype, 'connection', {
              get: function () {
                return { effectiveType: '4g', downlink: 10, rtt: 50, saveData: false };
              }, configurable: true
            });
          } catch (e) {}

          // --- 音频指纹：统一 AudioContext 参数 ---
          try {
            var AC = window.AudioContext || window.webkitAudioContext;
            if (AC) {
              var sampleRate = AC.prototype.sampleRate;
              Object.defineProperty(AC.prototype, 'sampleRate', {
                get: function () { return 44100; }, configurable: true
              });
            }
          } catch (e) {}

          // --- 存储指纹：阻止 storage.estimate 暴露容量信息 ---
          try {
            Object.defineProperty(Navigator.prototype, 'storage', {
              get: function () {
                return { estimate: function () { return Promise.resolve({ usage: 0, quota: 0 }); } };
              }, configurable: true
            });
          } catch (e) {}

          // --- 其他 navigator 指纹维度 ---
          try {
            Object.defineProperty(Navigator.prototype, 'pdfViewerEnabled', {
              get: function () { return false; }, configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(Navigator.prototype, 'userActivation', {
              get: function () { return { isActive: false, hasBeenActive: false }; }, configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(Navigator.prototype, 'scheduling', {
              get: function () { return { isInputPending: function () { return false; } }; }, configurable: true
            });
          } catch (e) {}

          // --- iframe 检测：阻止 frameElement 暴露嵌入关系 ---
          try {
            Object.defineProperty(Window.prototype, 'frameElement', {
              get: function () { return null; }, configurable: true
            });
          } catch (e) {}

          // --- window.opener：阻断跨窗口引用与标签页关联 ---
          try {
            Object.defineProperty(window, 'opener', {
              get: function () { return null; },
              set: function () { },
              configurable: true
            });
          } catch (e) {}

          // --- Permissions API：统一返回 denied，避免权限状态被探测 ---
          try {
            var deniedPermission = function () {
              return {
                state: 'denied',
                onchange: null,
                addEventListener: function () { },
                removeEventListener: function () { },
                dispatchEvent: function () { return false; }
              };
            };
            var permissionProbe = {
              query: function () { return Promise.resolve(deniedPermission()); },
              addEventListener: function () { },
              removeEventListener: function () { },
              dispatchEvent: function () { return false; }
            };
            Object.defineProperty(Navigator.prototype, 'permissions', {
              get: function () { return permissionProbe; }, configurable: true
            });
            Object.defineProperty(Navigator.prototype, 'doNotTrack', {
              get: function () { return '1'; }, configurable: true
            });
            Object.defineProperty(Navigator.prototype, 'globalPrivacyControl', {
              get: function () { return true; }, configurable: true
            });
          } catch (e) {}

          // --- Storage Access API：拒绝跨站存储访问，阻止第三方 Cookie 复活 ---
          try {
            Document.prototype.hasStorageAccess = function () { return Promise.resolve(false); };
            Document.prototype.requestStorageAccess = function () {
              return Promise.reject(new Error('Storage access denied.'));
            };
          } catch (e) {}

          // --- 时区指纹：统一为 UTC，避免暴露真实地理位置 ---
          try {
            Date.prototype.getTimezoneOffset = function () { return 0; };
            var resolvedOptions = Intl.DateTimeFormat.prototype.resolvedOptions;
            Intl.DateTimeFormat.prototype.resolvedOptions = function () {
              var result = resolvedOptions.call(this);
              result.timeZone = 'UTC';
              return result;
            };
          } catch (e) {}

          // --- DNS 预取 / 预连接 / 预渲染：阻止提前解析第三方域名 ---
          try {
            var dropLink = function (link) {
              var rel = String(link.rel || '').toLowerCase();
              if (rel === 'dns-prefetch' || rel === 'preconnect' || rel === 'prerender' ||
                  rel === 'prefetch') {
                return true;
              }
              if (rel === 'preload') {
                try {
                  return new URL(link.href, location.href).origin !== location.origin;
                } catch (e) {
                  return false;
                }
              }
              return false;
            };
            var scrubLinks = function () {
              try {
                var links = document.querySelectorAll('link[rel]');
                for (var i = 0; i < links.length; i++) {
                  if (dropLink(links[i]) && links[i].parentNode) {
                    links[i].parentNode.removeChild(links[i]);
                  }
                }
              } catch (e) {}
            };
            scrubLinks();
            document.addEventListener('DOMContentLoaded', scrubLinks);
            if (window.MutationObserver) {
              var linkObserver = new MutationObserver(function (records) {
                for (var i = 0; i < records.length; i++) {
                  var added = records[i].addedNodes;
                  for (var j = 0; j < added.length; j++) {
                    var node = added[j];
                    if (node && node.tagName === 'LINK' && dropLink(node) && node.parentNode) {
                      node.parentNode.removeChild(node);
                    }
                  }
                }
              });
              linkObserver.observe(document, { childList: true, subtree: true });
            }
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

          // --- 隐私标签禁止脚本读写 Cookie，避免写入可跨标签复用的标识 ---
          try {
            Object.defineProperty(Document.prototype, 'cookie', {
              get: function () { return ''; },
              set: function () { },
              configurable: true
            });
          } catch (e) {}
          try {
            Object.defineProperty(Navigator.prototype, 'cookieEnabled', {
              get: function () { return false; }, configurable: true
            });
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
