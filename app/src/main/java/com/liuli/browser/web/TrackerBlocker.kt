package com.liuli.browser.web

import android.net.Uri

/**
 * Lightweight, on-device privacy shield. It never uploads the visited URL or
 * any identifier: the blocklist and parameter rules ship inside the APK.
 */
object TrackerBlocker {

    private val trackerHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googleadservices.com",
        "adservice.google.com",
        "analytics.google.com",
        "facebook.net",
        "connect.facebook.net",
        "analytics.twitter.com",
        "static.ads-twitter.com",
        "ads-twitter.com",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "scorecardresearch.com",
        "quantserve.com",
        "quantcount.com",
        "hm.baidu.com",
        "pos.baidu.com",
        "cpro.baidu.com",
        "eclick.baidu.com",
        "mobads.baidu.com",
        "cnzz.com",
        "umeng.com",
        "umengcloud.com",
        "uyunad.com",
        "talkingdata.com",
        "adjust.com",
        "appsflyer.com",
        "branch.io",
        "bugly.qq.com",
        "pingjs.qq.com",
        "beacon.qq.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.com",
        "amplitude.com",
        "sentry.io",
        "newrelic.com",
        "mc.yandex.ru",
        "top.mail.ru",
        "ads.yahoo.com",
        "analytics.yahoo.com",
        "amazon-adsystem.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "casalemedia.com",
        "adnxs.com",
        "33across.com",
        "mathtag.com",
        "bluekai.com",
        "demdex.net",
        "omtrdc.net",
        "everesttech.net",
        "agkn.com",
        "crwdcntrl.net",
        "lijit.com",
        "rlcdn.com",
        "sharethrough.com",
        "teads.tv",
        "smartadserver.com",
        "adform.net",
        "adsrvr.org",
        "zeotap.com",
        "id5-sync.com",
        "adsafeprotected.com",
        "moatads.com",
        "doubleverify.com",
        "research.de.com",
        "fullstory.com",
        "logrocket.com",
        "bugsnag.com",
        "rollbar.com",
        "airbrake.io",
        "posthog.com",
        "heap.io",
        "heapanalytics.com",
        "countly.com",
        "plausible.io",
        "fathom.co",
        "simpleanalytics.io",
        "clarity.ms",
        "mouseflow.com",
        "freshmarketer.com",
        "adroll.com",
        "permutive.com",
        "parsely.com",
        "leadfeeder.com",
        "clearbit.com",
        "statsig.com",
        "launchdarkly.com",
        "split.io",
        "optimizely.com",
        "dynamicyield.com",
        "sitecatalyst.com",
        "adobe.io",
        "omtrdc.net",
        "everesttech.net",
        "sharedid.org",
        "eyeota.com",
        "braze.com",
        "iterable.com",
        "customer.io",
        "onesignal.com",
        "pushwoosh.com",
        "urbanairship.com",
        "appcenter.ms",
        "hockeyapp.net",
        "fabric.io",
        "crashlytics.com",
        "instabug.com",
        "mgid.com",
        "triplelift.com",
        "bshift.com",
        "3lift.com",
        "revcontent.com",
        "statcounter.com",
        "clicky.com",
        "statowl.com",
        "contentsquare.com",
        "smartlook.com",
        "mail.ru",
        "2domain.ru"
    )

    private val trackingParameters = setOf(
        "gclid",
        "fbclid",
        "msclkid",
        "yclid",
        "dclid",
        "igshid",
        "mc_cid",
        "mc_eid",
        "_ga",
        "vd_source",
        "wickedid",
        "ttclid",
        "twclid",
        "_openstat",
        "ysc",
        "__twitter_impression",
        "li_fat_id",
        "vero_id",
        "vero_conv",
        "sb_referer_host",
        "_hsenc",
        "_hsmi",
        "__hstc",
        "__hsfp",
        "rdt",
        "rdc",
        "trk",
        "trk_contact",
        "trk_msg",
        "trk_module",
        "trk_sid",
        "utmid",
        "ck_visitor",
        "ck_keywords",
        "sc_client",
        "sc_ts",
        "sc_hydra"
    )

    fun isTracker(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return trackerHosts.any { blocked ->
            host == blocked || host.endsWith(".$blocked")
        }
    }

    /**
     * Removes known cross-site identifiers while preserving functional query
     * parameters (for example Taobao's spm navigation parameter).
     */
    fun sanitize(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val names = runCatching { uri.queryParameterNames }.getOrNull() ?: return url
        if (names.isEmpty()) return url
        val kept = names.filterNot { name ->
            val lower = name.lowercase()
            lower.startsWith("utm_") || lower in trackingParameters
        }
        if (kept.size == names.size) return url
        val builder = uri.buildUpon().clearQuery()
        kept.forEach { name ->
            runCatching {
                uri.getQueryParameters(name).forEach { value ->
                    builder.appendQueryParameter(name, value)
                }
            }
        }
        return runCatching { builder.build().toString() }.getOrDefault(url)
    }
}
