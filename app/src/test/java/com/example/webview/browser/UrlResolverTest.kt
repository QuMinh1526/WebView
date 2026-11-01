package com.example.webview.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlResolverTest {
    private val search = "https://www.google.com/search?q=%s"

    @Test
    fun normalizesHostsAndPortsToHttps() {
        assertEquals("https://example.com", UrlResolver.resolve("example.com", search))
        assertEquals(
            "https://example.com:8443/path",
            UrlResolver.resolve("example.com:8443/path", search),
        )
    }

    @Test
    fun keepsHttpAndHttpsSchemes() {
        assertEquals("http://example.com/a", UrlResolver.resolve("http://example.com/a", search))
        assertEquals("https://example.com/a", UrlResolver.resolve("https://example.com/a", search))
    }

    @Test
    fun searchesWordsAndRejectsUnsafeSchemes() {
        assertEquals(
            "https://www.google.com/search?q=cats+%26+dogs",
            UrlResolver.resolve("cats & dogs", search),
        )
        assertNull(UrlResolver.resolve("javascript:alert(1)", search))
        assertNull(UrlResolver.resolve("file:///etc/passwd", search))
        assertNull(UrlResolver.resolve("data:text/html,test", search))
    }
}
