package com.example.webview.data

import com.example.webview.browser.UrlPolicy
import java.net.URI

object VoiceFxOrigin {
    fun normalize(input: String): String? {
        val value = input.trim()
        if (value.isEmpty() || value.contains('*')) return null
        val candidate = if ("://" in value) value else "https://$value"
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            uri.userInfo != null ||
            (uri.path.isNotEmpty() && uri.path != "/") ||
            uri.query != null ||
            uri.fragment != null
        ) return null
        if (UrlPolicy.isVerificationAllowed("https://$host/")) return null
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        return "https://$host$port"
    }
}
