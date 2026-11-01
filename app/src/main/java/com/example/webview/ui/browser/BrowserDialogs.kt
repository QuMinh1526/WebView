package com.example.webview.ui.browser

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.webview.BuildConfig
import com.example.webview.browser.BrowserDiagnostics
import com.example.webview.browser.BrowserTab
import com.example.webview.browser.BrowserUiState
import com.example.webview.browser.BrowserViewModel
import com.example.webview.browser.BrowserWebViewPool
import com.example.webview.browser.SitePermissionPrompt
import com.example.webview.data.BrowserSettings
import com.example.webview.data.SettingsRepository
import com.example.webview.ui.components.ActionButton
import com.example.webview.ui.components.AcrylicChip
import com.example.webview.ui.components.AcrylicPanel
import com.example.webview.ui.components.AcrylicSwitch
import com.example.webview.ui.theme.ActionBlue
import com.example.webview.ui.theme.EdgeTint
import com.example.webview.ui.theme.TextPrimary
import com.example.webview.ui.theme.TextSecondary
import com.example.webview.ui.theme.UiSpacing

@Composable
fun BrowserDialogs(
    state: BrowserUiState,
    browserViewModel: BrowserViewModel,
    webViews: BrowserWebViewPool,
    context: Context,
    onResolvePermission: (SitePermissionPrompt, Boolean, Boolean) -> Unit,
    onClearData: () -> Unit,
    onRevokeOrigin: (String) -> Unit,
    onBiometricChange: (Boolean) -> Unit,
) {
    var clearConfirmation by remember { mutableStateOf(false) }
    val settingsRepository = remember(context) {
        SettingsRepository(context.applicationContext)
    }
    val siteGrants by settingsRepository.siteGrants.collectAsState(initial = emptyMap())

    if (state.menuVisible) {
        BrowserMenuDialog(
            desktopMode = state.activeTab.desktopMode,
            debugBuild = BuildConfig.DEBUG,
            onDesktopMode = {
                browserViewModel.showMenu(false)
                browserViewModel.setDesktopMode(it)
            },
            onNewTab = {
                browserViewModel.showMenu(false)
                browserViewModel.addTab()
            },
            onPrivateTab = {
                browserViewModel.showMenu(false)
                browserViewModel.addTab(privateMode = true)
            },
            onShowTabs = {
                browserViewModel.showMenu(false)
                browserViewModel.showTabs(true)
            },
            onSettings = {
                browserViewModel.showMenu(false)
                browserViewModel.showSettings(true)
            },
            onMiniStudio = {
                browserViewModel.showMenu(false)
                browserViewModel.showMiniStudio(true)
            },
            onClearData = {
                browserViewModel.showMenu(false)
                clearConfirmation = true
            },
            onDiagnostics = {
                browserViewModel.showMenu(false)
                browserViewModel.showDiagnostics(true)
            },
            onDismiss = { browserViewModel.showMenu(false) },
        )
    }

    if (state.settingsVisible) {
        SettingsDialog(
            settings = state.settings,
            siteGrants = siteGrants,
            onDismiss = { browserViewModel.showSettings(false) },
            onSettingsChanged = browserViewModel::updateSettings,
            onBiometricChange = onBiometricChange,
            onClearData = { clearConfirmation = true },
            onRevokeOrigin = onRevokeOrigin,
        )
    }

    if (state.tabsVisible) {
        TabsDialog(
            tabs = state.tabs,
            activeTabId = state.activeTabId,
            onCreateTab = { browserViewModel.addTab() },
            onCreatePrivateTab = { browserViewModel.addTab(privateMode = true) },
            onSwitch = browserViewModel::switchTab,
            onClose = { tab -> closeTab(tab, state.tabs, webViews, browserViewModel) },
            onDismiss = { browserViewModel.showTabs(false) },
        )
    }

    state.pendingDownload?.let { download ->
        ConfirmDialog(
            title = "Download file?",
            message = download.url,
            confirmText = "Download",
            onConfirm = { browserViewModel.resolveDownload(true) },
            onDismiss = { browserViewModel.resolveDownload(false) },
        )
    }
    state.pendingExternalUrl?.let { url ->
        ConfirmDialog(
            title = "Open external link?",
            message = "Continue to $url",
            confirmText = "Open",
            onConfirm = { browserViewModel.resolveExternal(true) },
            onDismiss = { browserViewModel.resolveExternal(false) },
        )
    }
    state.pendingPermission?.let { prompt ->
        SitePermissionDialog(prompt) { allow, remember ->
            onResolvePermission(prompt, allow, remember)
        }
    }

    if (state.diagnosticsVisible && BuildConfig.DEBUG) {
        DiagnosticsDialog(
            diagnostics = webViews.diagnostics(state.activeTabId),
            onOpenHeaders = {
                webViews.openUrl(state.activeTabId, "https://httpbin.org/headers")
                browserViewModel.showDiagnostics(false)
            },
            onDismiss = { browserViewModel.showDiagnostics(false) },
        )
    }

    if (clearConfirmation) {
        ConfirmDialog(
            title = "Clear browsing data?",
            message = "Cookies, cache, local storage, history, form data, and saved site permissions will be removed.",
            confirmText = "Clear data",
            onConfirm = {
                clearConfirmation = false
                onClearData()
            },
            onDismiss = { clearConfirmation = false },
        )
    }
}

@Composable
private fun BrowserMenuDialog(
    desktopMode: Boolean,
    debugBuild: Boolean,
    onDesktopMode: (Boolean) -> Unit,
    onNewTab: () -> Unit,
    onPrivateTab: () -> Unit,
    onShowTabs: () -> Unit,
    onSettings: () -> Unit,
    onMiniStudio: () -> Unit,
    onClearData: () -> Unit,
    onDiagnostics: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        AcrylicPanel(radius = UiSpacing.SheetCorner) {
            Text(
                "Browser menu",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            SettingRow("Desktop site", desktopMode, onDesktopMode)
            ActionButton("New tab", onNewTab, modifier = Modifier.fillMaxWidth())
            ActionButton(
                "New private tab",
                onPrivateTab,
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )
            ActionButton(
                "Manage tabs",
                onShowTabs,
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )
            ActionButton(
                "Settings",
                onSettings,
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )
            ActionButton(
                "Mini Studio",
                onMiniStudio,
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )
            ActionButton(
                "Clear browsing data",
                onClearData,
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )
            if (debugBuild) {
                ActionButton(
                    "Diagnostics",
                    onDiagnostics,
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    settings: BrowserSettings,
    siteGrants: Map<String, Set<String>>,
    onDismiss: () -> Unit,
    onSettingsChanged: ((BrowserSettings) -> BrowserSettings) -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onClearData: () -> Unit,
    onRevokeOrigin: (String) -> Unit,
) {
    var domainsText by remember(settings.blockedDomains) {
        mutableStateOf(settings.blockedDomains.joinToString("\n"))
    }
    Dialog(onDismissRequest = onDismiss) {
        AcrylicPanel(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp),
            radius = UiSpacing.SheetCorner,
        ) {
            Text(
                "Settings",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(UiSpacing.Medium))
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.Small),
            ) {
                SettingRow("Desktop mode by default", settings.desktopByDefault) { enabled ->
                    onSettingsChanged { it.copy(desktopByDefault = enabled) }
                }
                SettingRow(
                    "Accept third-party cookies",
                    settings.acceptThirdPartyCookies,
                ) { enabled ->
                    onSettingsChanged { it.copy(acceptThirdPartyCookies = enabled) }
                }
                SettingRow("Block pop-ups", settings.blockPopups) { enabled ->
                    onSettingsChanged { it.copy(blockPopups = enabled) }
                }
                SettingRow(
                    "Enable domain blocklist",
                    settings.domainBlocklistEnabled,
                ) { enabled ->
                    onSettingsChanged { it.copy(domainBlocklistEnabled = enabled) }
                }
                SettingRow("Lock app with biometrics", settings.biometricLock) { enabled ->
                    onBiometricChange(enabled)
                }

                Text(
                    "Default search engine",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small)) {
                    SearchEngineChip(
                        "Google",
                        "https://www.google.com/search?q=%s",
                        settings.searchTemplate,
                        onSettingsChanged,
                    )
                    SearchEngineChip(
                        "Bing",
                        "https://www.bing.com/search?q=%s",
                        settings.searchTemplate,
                        onSettingsChanged,
                    )
                    SearchEngineChip(
                        "DuckDuckGo",
                        "https://duckduckgo.com/?q=%s",
                        settings.searchTemplate,
                        onSettingsChanged,
                    )
                }

                Text(
                    "Blocked domains (one per line)",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                OutlinedTextField(
                    value = domainsText,
                    onValueChange = { domainsText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = ActionBlue,
                        focusedBorderColor = EdgeTint,
                        unfocusedBorderColor = EdgeTint,
                    ),
                )
                ActionButton("Save blocklist", onClick = {
                    val domains = domainsText.lines()
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .toSet()
                    onSettingsChanged { it.copy(blockedDomains = domains) }
                })

                Text(
                    "Site permissions",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                if (siteGrants.isEmpty()) {
                    Text("No saved site permissions.", color = TextSecondary)
                } else {
                    siteGrants.forEach { (origin, grants) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    origin,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    grants.joinToString(),
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            ActionButton(
                                "Revoke",
                                primary = false,
                                onClick = { onRevokeOrigin(origin) },
                            )
                        }
                    }
                }
                ActionButton(
                    "Clear browsing data",
                    onClick = onClearData,
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(UiSpacing.Medium))
            ActionButton("Done", onClick = onDismiss, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = UiSpacing.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
        AcrylicSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SearchEngineChip(
    label: String,
    template: String,
    selectedTemplate: String,
    onSettingsChanged: ((BrowserSettings) -> BrowserSettings) -> Unit,
) {
    AcrylicChip(
        label = label,
        selected = selectedTemplate == template,
        onClick = { onSettingsChanged { it.copy(searchTemplate = template) } },
    )
}

@Composable
private fun TabsDialog(
    tabs: List<BrowserTab>,
    activeTabId: String,
    onCreateTab: () -> Unit,
    onCreatePrivateTab: () -> Unit,
    onSwitch: (String) -> Unit,
    onClose: (BrowserTab) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        AcrylicPanel(radius = UiSpacing.SheetCorner) {
            Text(
                "Tabs",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(UiSpacing.Medium))
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.Small),
            ) {
                tabs.forEach { tab ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AcrylicChip(
                            label = (if (tab.isPrivate) "Private · " else "") +
                                tab.title.ifBlank { "New tab" }.take(18),
                            selected = tab.id == activeTabId,
                            onClick = { onSwitch(tab.id) },
                            modifier = Modifier.weight(1f),
                        )
                        ActionButton(
                            "Close",
                            primary = false,
                            onClick = { onClose(tab) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(UiSpacing.Medium))
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small)) {
                ActionButton("New tab", onCreateTab)
                ActionButton("Private", primary = false, onClick = onCreatePrivateTab)
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        AcrylicPanel(radius = UiSpacing.SheetCorner) {
            Text(
                title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(UiSpacing.Small))
            Text(message, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(UiSpacing.Medium))
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small)) {
                ActionButton(confirmText, onConfirm)
                ActionButton("Cancel", primary = false, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun SitePermissionDialog(
    prompt: SitePermissionPrompt,
    onDecision: (Boolean, Boolean) -> Unit,
) {
    val resources = if (prompt.isGeolocation) {
        "Location"
    } else {
        prompt.resources.joinToString { resource ->
            when (resource) {
                android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE -> "Microphone"
                android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE -> "Camera"
                else -> resource
            }
        }
    }
    Dialog(onDismissRequest = { onDecision(false, false) }) {
        AcrylicPanel(radius = UiSpacing.SheetCorner) {
            Text(
                "Site permission",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(UiSpacing.Small))
            Text(prompt.origin, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Requests access to: $resources",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(UiSpacing.Medium))
            ActionButton(
                "Allow once",
                onClick = { onDecision(true, false) },
                modifier = Modifier.fillMaxWidth(),
            )
            ActionButton(
                "Always allow this site",
                primary = false,
                onClick = { onDecision(true, true) },
                modifier = Modifier.fillMaxWidth(),
            )
            ActionButton(
                "Deny",
                primary = false,
                onClick = { onDecision(false, false) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DiagnosticsDialog(
    diagnostics: BrowserDiagnostics,
    onOpenHeaders: () -> Unit,
    onDismiss: () -> Unit,
) {
    val minimumSupportedMajor = 120
    val major = Regex("""(\d+)""").find(diagnostics.webViewVersion)
        ?.groupValues?.get(1)?.toIntOrNull()
    Dialog(onDismissRequest = onDismiss) {
        AcrylicPanel(radius = UiSpacing.SheetCorner) {
            Text(
                "Diagnostics",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (major != null && major < minimumSupportedMajor) {
                Text(
                    "WebView is old. Update Android System WebView or Chrome.",
                    color = TextPrimary,
                )
            }
            Text("WebView: ${diagnostics.webViewVersion}", color = TextPrimary)
            Text(
                "Cookies: ${if (diagnostics.cookiesEnabled) "enabled" else "disabled"}",
                color = TextSecondary,
            )
            Text(
                "DOM storage: ${if (diagnostics.domStorageEnabled) "enabled" else "disabled"}",
                color = TextSecondary,
            )
            Text(
                "User agent: ${diagnostics.userAgent}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(UiSpacing.Medium))
            ActionButton(
                "Open header test",
                onClick = onOpenHeaders,
                modifier = Modifier.fillMaxWidth(),
            )
            ActionButton("Close", primary = false, onClick = onDismiss)
        }
    }
}
