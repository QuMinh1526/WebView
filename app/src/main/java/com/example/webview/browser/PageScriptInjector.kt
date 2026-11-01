package com.example.webview.browser

import android.webkit.WebView
import java.net.URI

interface PageScriptInjector {
    fun inject(webView: WebView, pageUrl: String, isMainFrame: Boolean)
}

class NoOpPageScriptInjector(
    private val allowedOrigins: Set<String> = emptySet(),
) : PageScriptInjector {
    override fun inject(webView: WebView, pageUrl: String, isMainFrame: Boolean) {
        val origin = runCatching {
            val uri = URI(pageUrl)
            "${uri.scheme}://${uri.host}${if (uri.port == -1) "" else ":${uri.port}"}"
        }.getOrNull()
        if (!isMainFrame || origin !in allowedOrigins) return
        // Add explicitly scoped page scripts here when a feature requires them.
    }
}
