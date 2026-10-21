# WebView JavaScript bridges, if any, must survive R8.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.liuli.browser.web.WebBridge { *; }

# Keep line numbers for crash reports without exposing the full mapping.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
