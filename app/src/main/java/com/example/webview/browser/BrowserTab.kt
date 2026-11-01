package com.example.webview.browser

data class BrowserTab(
    val id: String,
    val url: String = "",
    val title: String = "New tab",
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val desktopMode: Boolean = false,
    val isPrivate: Boolean = false,
    val pageError: PageError? = null,
    val reloadGeneration: Int = 0,
)

data class PageError(
    val title: String,
    val message: String,
    val ssl: Boolean = false,
)

data class DownloadRequest(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
)

data class SitePermissionPrompt(
    val id: String,
    val origin: String,
    val resources: Set<String>,
    val isGeolocation: Boolean = false,
)
