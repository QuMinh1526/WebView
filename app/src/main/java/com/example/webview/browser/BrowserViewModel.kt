package com.example.webview.browser

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.webview.data.BrowserSettings
import com.example.webview.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val _state = MutableStateFlow(
        BrowserUiState(
            tabs = listOf(newTab()),
            activeTabId = "initial",
        ),
    )
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()
    private val savedWebStates = mutableMapOf<String, Bundle>()

    init {
        val firstTab = _state.value.tabs.single()
        _state.value = _state.value.copy(activeTabId = firstTab.id)
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { current ->
                    val tabs = if (current.tabs.all { it.url.isBlank() }) {
                        current.tabs.map { it.copy(desktopMode = settings.desktopByDefault) }
                    } else {
                        current.tabs
                    }
                    current.copy(settings = settings, tabs = tabs, settingsLoaded = true)
                }
            }
        }
    }

    fun submitAddress(value: String = _state.value.addressInput) {
        val current = _state.value
        val target = UrlResolver.resolve(value, current.settings.searchTemplate)
        if (target == null) {
            _state.update { it.copy(message = "That address or URL scheme is not supported.") }
            return
        }
        _state.update { it.updateTab(it.activeTabId) { tab ->
            tab.copy(url = target, title = target, progress = 0, pageError = null)
        }.copy(addressInput = target) }
    }

    fun setAddressInput(value: String) {
        _state.update { it.copy(addressInput = value) }
    }

    fun setPageState(
        tabId: String,
        url: String,
        title: String?,
        progress: Int,
        canGoBack: Boolean,
        canGoForward: Boolean,
    ) {
        _state.update { current ->
            current.updateTab(tabId) { tab ->
                tab.copy(
                    url = url.ifBlank { tab.url },
                    title = title?.takeIf(String::isNotBlank) ?: tab.title,
                    progress = progress.coerceIn(0, 100),
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    pageError = tab.pageError,
                )
            }.let { updated ->
                if (updated.activeTabId == tabId) {
                    updated.copy(addressInput = url.ifBlank { updated.addressInput })
                } else updated
            }
        }
    }

    fun setPageError(tabId: String, error: PageError) {
        _state.update { current ->
            current.updateTab(tabId) { it.copy(pageError = error, progress = 0) }
        }
    }

    fun clearPageError(tabId: String) {
        _state.update { current ->
            current.updateTab(tabId) { it.copy(pageError = null) }
        }
    }

    fun reload() {
        _state.update { it.updateTab(it.activeTabId) { tab ->
            tab.copy(reloadGeneration = tab.reloadGeneration + 1, pageError = null)
        } }
    }

    fun stop() {
        setPageState(
            tabId = _state.value.activeTabId,
            url = _state.value.activeTab.url,
            title = _state.value.activeTab.title,
            progress = 0,
            canGoBack = _state.value.activeTab.canGoBack,
            canGoForward = _state.value.activeTab.canGoForward,
        )
    }

    fun setDesktopMode(enabled: Boolean) {
        _state.update { it.updateTab(it.activeTabId) { tab ->
            tab.copy(desktopMode = enabled, reloadGeneration = tab.reloadGeneration + 1)
        } }
    }

    fun addTab(privateMode: Boolean = false, initialUrl: String = "") {
        val current = _state.value
        val tab = newTab(
            desktopMode = current.settings.desktopByDefault,
            privateMode = privateMode,
            url = initialUrl,
        )
        _state.update {
            it.copy(
                tabs = it.tabs + tab,
                activeTabId = tab.id,
                addressInput = initialUrl,
                tabsVisible = false,
            )
        }
    }

    fun createPopupTab(): BrowserTab {
        val current = _state.value
        val tab = newTab(desktopMode = current.settings.desktopByDefault)
        _state.update {
            it.copy(
                tabs = it.tabs + tab,
                activeTabId = tab.id,
                addressInput = "",
                tabsVisible = false,
            )
        }
        return tab
    }

    fun switchTab(tabId: String) {
        _state.update { current ->
            if (current.tabs.none { it.id == tabId }) current
            else current.copy(
                activeTabId = tabId,
                addressInput = current.tabs.first { it.id == tabId }.url,
                tabsVisible = false,
            )
        }
    }

    fun closeTab(tabId: String) {
        _state.update { current ->
            val remaining = current.tabs.filterNot { it.id == tabId }
            if (remaining.isEmpty()) {
                val next = newTab(desktopMode = current.settings.desktopByDefault)
                current.copy(
                    tabs = listOf(next),
                    activeTabId = next.id,
                    addressInput = "",
                    tabsVisible = false,
                )
            } else {
                val active = if (current.activeTabId == tabId) remaining.last() else remaining
                    .first { it.id == current.activeTabId }
                current.copy(
                    tabs = remaining,
                    activeTabId = active.id,
                    addressInput = active.url,
                    tabsVisible = false,
                )
            }
        }
        savedWebStates.remove(tabId)
    }

    fun saveWebState(tabId: String, state: Bundle) {
        savedWebStates[tabId] = state
    }

    fun consumeWebState(tabId: String): Bundle? = savedWebStates.remove(tabId)

    fun showSettings(visible: Boolean) {
        _state.update { it.copy(settingsVisible = visible) }
    }

    fun showTabs(visible: Boolean) {
        _state.update { it.copy(tabsVisible = visible) }
    }

    fun showDiagnostics(visible: Boolean) {
        _state.update { it.copy(diagnosticsVisible = visible) }
    }

    fun showMiniStudio(visible: Boolean) {
        _state.update { it.copy(miniStudioVisible = visible) }
    }

    fun showMenu(visible: Boolean) {
        _state.update { it.copy(menuVisible = visible) }
    }

    fun openHome() {
        _state.update { current ->
            current.updateTab(current.activeTabId) {
                it.copy(
                    url = "",
                    title = "New tab",
                    progress = 0,
                    canGoBack = false,
                    canGoForward = false,
                    pageError = null,
                )
            }.copy(addressInput = "")
        }
    }

    fun updateSettings(transform: (BrowserSettings) -> BrowserSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun requestDownload(request: DownloadRequest) {
        _state.update { it.copy(pendingDownload = request) }
    }

    fun resolveDownload(confirmed: Boolean) {
        val download = _state.value.pendingDownload
        _state.update { it.copy(pendingDownload = null) }
        if (confirmed && download != null) {
            _state.update { it.copy(message = "Download started.") }
            onDownloadConfirmed?.invoke(download)
        }
    }

    var onDownloadConfirmed: ((DownloadRequest) -> Unit)? = null

    fun requestExternal(url: String) {
        _state.update { it.copy(pendingExternalUrl = url) }
    }

    fun resolveExternal(confirmed: Boolean) {
        val url = _state.value.pendingExternalUrl
        _state.update { it.copy(pendingExternalUrl = null) }
        if (confirmed && url != null) onExternalConfirmed?.invoke(url)
    }

    var onExternalConfirmed: ((String) -> Unit)? = null

    fun setFileChooserPending(pending: Boolean) {
        _state.update { it.copy(fileChooserPending = pending) }
    }

    fun clearHistory() {
        _state.update { current ->
            current.copy(
                tabs = current.tabs.map {
                    it.copy(
                        url = "",
                        title = "New tab",
                        progress = 0,
                        canGoBack = false,
                        canGoForward = false,
                        pageError = null,
                    )
                },
                addressInput = "",
            )
        }
    }

    fun setPermissionPrompt(prompt: SitePermissionPrompt?) {
        _state.update { it.copy(pendingPermission = prompt) }
    }

    fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun BrowserUiState.updateTab(
        id: String,
        transform: (BrowserTab) -> BrowserTab,
    ): BrowserUiState = copy(tabs = tabs.map { if (it.id == id) transform(it) else it })

    private fun newTab(
        desktopMode: Boolean = false,
        privateMode: Boolean = false,
        url: String = "",
    ) = BrowserTab(
        id = UUID.randomUUID().toString(),
        url = url,
        title = "New tab",
        desktopMode = desktopMode,
        isPrivate = privateMode,
    )
}
