package com.liuli.browser

import android.app.Application
import android.webkit.WebView

class GlassBrowserApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Remote debugging is a debug-only capability; release builds keep the
        // WebView sandbox closed.
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }
}
