package com.personal.hammy

import android.app.Application
import android.webkit.CookieManager

/**
 * Process-wide WebView cookie accept so ad frequency-cap / session cookies
 * survive across PlayerActivity instances (each opens a fresh WebView).
 */
class HammyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CookieManager.getInstance().setAcceptCookie(true)
    }
}
