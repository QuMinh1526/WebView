package com.example.webview.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
    @Test
    fun filtersNavigationSchemesAndRequiresGestureForExternalApps() {
        assertEquals(UrlDecision.ALLOW_WEB, UrlPolicy.classify("https://example.com", false))
        assertEquals(UrlDecision.CONFIRM_EXTERNAL, UrlPolicy.classify("mailto:a@example.com", true))
        assertEquals(UrlDecision.BLOCK, UrlPolicy.classify("mailto:a@example.com", false))
        assertEquals(UrlDecision.BLOCK, UrlPolicy.classify("javascript:alert(1)", true))
        assertEquals(UrlDecision.BLOCK, UrlPolicy.classify("file:///tmp/x", true))
    }

    @Test
    fun neverBlocksVerificationOrigins() {
        assertTrue(UrlPolicy.isVerificationAllowed("https://challenges.cloudflare.com/abc"))
        assertTrue(UrlPolicy.isVerificationAllowed("https://www.google.com/recaptcha/api.js"))
        assertTrue(UrlPolicy.isVerificationAllowed("https://www.gstatic.com/recaptcha/x.js"))
        assertTrue(UrlPolicy.isVerificationAllowed("https://hcaptcha.com/1/api.js"))
        assertTrue(UrlPolicy.isVerificationAllowed("https://newassets.hcaptcha.com/x.js"))
        assertFalse(UrlPolicy.isVerificationAllowed("https://example.com/"))
        assertFalse(UrlPolicy.isVerificationAllowed("https://www.google.com/search"))
    }

    @Test
    fun verificationDomainsBypassConfiguredBlocklists() {
        assertFalse(
            UrlPolicy.isBlockedByDomain(
                "https://challenges.cloudflare.com/challenge",
                setOf("cloudflare.com"),
            ),
        )
        assertTrue(
            UrlPolicy.isBlockedByDomain(
                "https://sub.example.com/",
                setOf("example.com"),
            ),
        )
        assertFalse(
            UrlPolicy.isBlockedByDomain(
                "https://notexample.com/",
                setOf("example.com"),
            ),
        )
    }
}
