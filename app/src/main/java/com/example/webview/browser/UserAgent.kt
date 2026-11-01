package com.example.webview.browser

object UserAgent {
    fun mobile(defaultUserAgent: String): String =
        defaultUserAgent
            .replace(Regex(""";\s*wv\b"""), "")
            .replace(Regex("""\bVersion/4\.0\s*"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

    fun desktop(defaultUserAgent: String): String {
        val chromeVersion = CHROME_VERSION.find(defaultUserAgent)?.groupValues?.get(1)
            ?: return mobile(defaultUserAgent)
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"
    }

    fun chromiumVersion(userAgent: String): String? =
        CHROME_VERSION.find(userAgent)?.groupValues?.get(1)

    private val CHROME_VERSION = Regex("""Chrome/([0-9.]+)""")
}
