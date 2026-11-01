package com.example.webview.data

data class BrowserSettings(
    val desktopByDefault: Boolean = false,
    val acceptThirdPartyCookies: Boolean = true,
    val blockPopups: Boolean = true,
    val domainBlocklistEnabled: Boolean = false,
    val blockedDomains: Set<String> = emptySet(),
    val biometricLock: Boolean = false,
    val searchTemplate: String = "https://www.google.com/search?q=%s",
)
