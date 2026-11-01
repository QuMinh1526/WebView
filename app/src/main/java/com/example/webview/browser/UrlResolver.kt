package com.example.webview.browser

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object UrlResolver {
    fun resolve(input: String, searchTemplate: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null

        val scheme = SCHEME.find(value)?.value?.removeSuffix(":")?.lowercase()
        if (scheme == "http" || scheme == "https") {
            return webUrl(value)
        }

        val looksLikeHost = !value.any(Char::isWhitespace) &&
            (value.contains('.') || value.startsWith("localhost") || isIpAddress(value))
        if (scheme != null && !looksLikeHost) return null
        if (!looksLikeHost) {
            val query = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            return searchTemplate.replace("%s", query)
        }
        return webUrl("https://$value")
    }

    private fun webUrl(value: String): String? = runCatching {
        val uri = URI(value)
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
        uri.toASCIIString()
    }.getOrNull()

    private fun isIpAddress(value: String): Boolean =
        value.matches(Regex("""\d{1,3}(\.\d{1,3}){3}(:\d+)?(/.*)?"""))

    private val SCHEME = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*:""")
}
