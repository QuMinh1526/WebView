package com.example.webview.browser

import com.example.webview.data.BrowserSettings

data class BrowserUiState(
    val tabs: List<BrowserTab>,
    val activeTabId: String,
    val addressInput: String = "",
    val settings: BrowserSettings = BrowserSettings(),
    val settingsVisible: Boolean = false,
    val tabsVisible: Boolean = false,
    val diagnosticsVisible: Boolean = false,
    val menuVisible: Boolean = false,
    val miniStudioVisible: Boolean = false,
    val settingsLoaded: Boolean = false,
    val pendingDownload: DownloadRequest? = null,
    val pendingExternalUrl: String? = null,
    val pendingPermission: SitePermissionPrompt? = null,
    val fileChooserPending: Boolean = false,
    val message: String? = null,
) {
    val activeTab: BrowserTab
        get() = tabs.first { it.id == activeTabId }
}
