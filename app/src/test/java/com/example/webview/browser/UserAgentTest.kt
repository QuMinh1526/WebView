package com.example.webview.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentTest {
    private val defaultUa =
        "Mozilla/5.0 (Linux; Android 15; Device Build/ABC; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
            "Chrome/134.0.6998.39 Mobile Safari/537.36"

    @Test
    fun stripsOnlyWebViewMarkersFromMobileUserAgent() {
        val mobile = UserAgent.mobile(defaultUa)
        assertFalse(mobile.contains("; wv"))
        assertFalse(mobile.contains("Version/4.0"))
        assertTrue(mobile.contains("Chrome/134.0.6998.39"))
        assertTrue(mobile.contains("Mobile Safari"))
    }

    @Test
    fun desktopUserAgentUsesInstalledChromiumVersion() {
        assertEquals("134.0.6998.39", UserAgent.chromiumVersion(defaultUa))
        assertTrue(UserAgent.desktop(defaultUa).contains("Chrome/134.0.6998.39"))
        assertFalse(UserAgent.desktop(defaultUa).contains("Mobile"))
    }
}
