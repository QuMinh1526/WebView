package com.example.webview.browser

import java.net.URI

enum class UrlDecision {
    ALLOW_WEB,
    CONFIRM_EXTERNAL,
    BLOCK,
}

object UrlPolicy {
    fun classify(url: String, hasUserGesture: Boolean): UrlDecision {
        val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
            ?: return UrlDecision.BLOCK
        if (scheme in RESTRICTED_SCHEMES) return UrlDecision.BLOCK
        return when {
            scheme == "https" || scheme == "http" -> UrlDecision.ALLOW_WEB
            hasUserGesture -> UrlDecision.CONFIRM_EXTERNAL
            else -> UrlDecision.BLOCK
        }
    }

    fun isVerificationAllowed(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return when {
            host == "challenges.cloudflare.com" -> true
            host == "www.google.com" -> uri.path.orEmpty().startsWith("/recaptcha")
            host == "www.gstatic.com" -> true
            host == "hcaptcha.com" || host.endsWith(".hcaptcha.com") -> true
            else -> false
        }
    }

    fun isBlockedByDomain(url: String, blockedDomains: Set<String>): Boolean {
        if (isVerificationAllowed(url)) return false
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return blockedDomains.any { blocked ->
            val normalized = blocked.trim().removePrefix("*.").lowercase()
            normalized.isNotEmpty() && (host == normalized || host.endsWith(".$normalized"))
        }
    }

    private val RESTRICTED_SCHEMES =
        setOf("javascript", "file", "content", "data", "blob", "about")
}
