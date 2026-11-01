package com.example.webview.ui.browser

import android.app.DownloadManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.key
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.webview.browser.BrowserTab
import com.example.webview.browser.BrowserViewModel
import com.example.webview.browser.BrowserWebViewPool
import com.example.webview.browser.DownloadRequest
import com.example.webview.browser.PageError
import com.example.webview.browser.VoiceFxPageScriptInjector
import com.example.webview.browser.SitePermissionPrompt
import com.example.webview.data.SettingsRepository
import com.example.webview.data.VoiceFxOrigin
import com.example.webview.data.VoiceFxSettings
import com.example.webview.ui.components.ActionButton
import com.example.webview.ui.components.AcrylicChip
import com.example.webview.ui.components.AcrylicPanel
import com.example.webview.ui.components.BottomBar
import com.example.webview.ui.components.AcrylicSwitch
import com.example.webview.ui.components.SearchField
import com.example.webview.ui.theme.ActionBlue
import com.example.webview.ui.theme.Canvas
import com.example.webview.ui.theme.EdgeTint
import com.example.webview.ui.theme.SurfaceRaised
import com.example.webview.ui.theme.TextPrimary
import com.example.webview.ui.theme.TextSecondary
import com.example.webview.ui.theme.UiSpacing
import com.example.webview.ui.theme.rememberMotionEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    browserViewModel: BrowserViewModel = viewModel(),
    onBiometricChange: ((Boolean) -> Unit)? = null,
    appLocked: Boolean = false,
    onRequestUnlock: () -> Unit = {},
) {
    val state by browserViewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var browserControlsVisible by rememberSaveable { mutableStateOf(true) }
    val motionEnabled = rememberMotionEnabled()
    val settingsRepository = remember(context) { SettingsRepository(context.applicationContext) }
    val voiceFxSettings by settingsRepository.voiceFxSettings.collectAsState(
        initial = VoiceFxSettings(),
    )
    val scriptInjector = remember(activity) { VoiceFxPageScriptInjector(activity) }
    val voiceFxStats by scriptInjector.stats.collectAsState(initial = null)
    val currentOrigin = remember(state.activeTab.url) {
        runCatching {
            java.net.URI(state.activeTab.url).let { uri ->
                VoiceFxOrigin.normalize(
                    "${uri.scheme}://${uri.host}${if (uri.port == -1) "" else ":${uri.port}"}",
                )
            }
        }.getOrNull()
    }
    val voiceFxActive = voiceFxSettings.enabled &&
        currentOrigin != null && currentOrigin in voiceFxSettings.allowedOrigins
    var microphonePermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    var pendingRuntimePermission by remember { mutableStateOf<Pair<SitePermissionPrompt, Boolean>?>(null) }
    val webViews = remember(activity, browserViewModel) {
        BrowserWebViewPool(
            activity = activity,
            settingsRepository = settingsRepository,
            lifecycleScope = lifecycleOwner.lifecycleScope,
            viewModel = browserViewModel,
            scriptInjector = scriptInjector,
        )
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris -> webViews.completeFileChooser(uris) }
    LaunchedEffect(state.fileChooserPending) {
        if (state.fileChooserPending) filePicker.launch("*/*")
    }
    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        val request = pendingRuntimePermission
        pendingRuntimePermission = null
        if (request != null) {
            val permissions = webViews.requiredAndroidPermissions(request.first)
            val accepted = if (request.first.isGeolocation) {
                permissions.any {
                    ContextCompat.checkSelfPermission(context, it) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            } else {
                permissions.all {
                    ContextCompat.checkSelfPermission(context, it) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }
            webViews.resolvePermission(
                request.first.id,
                allow = true,
                remember = request.second,
                runtimePermissionsGranted = accepted,
            )
        }
    }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        microphonePermissionGranted = granted
        browserViewModel.showMessage(
            if (granted) {
                "Android microphone permission granted. A website must still request microphone access."
            } else {
                "Microphone permission was not granted."
            },
        )
    }

    DisposableEffect(webViews) {
        onDispose { webViews.destroyAll() }
    }

    DisposableEffect(lifecycleOwner, webViews, state.activeTabId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webViews.onResume(state.activeTabId)
                Lifecycle.Event.ON_PAUSE -> webViews.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(webViews, state.activeTabId) {
        webViews.onResume(state.activeTabId)
    }

    val window = activity.window
    LaunchedEffect(state.activeTab.isPrivate) {
        if (state.activeTab.isPrivate) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    BackHandler(enabled = state.activeTab.canGoBack || state.tabs.size > 1) {
        if (state.activeTab.canGoBack) {
            webViews.goBack(state.activeTabId)
        } else if (state.tabs.size > 1) {
            closeTab(state.activeTab, state.tabs, webViews, browserViewModel)
        }
    }
    BackHandler(enabled = state.miniStudioVisible) {
        browserViewModel.showMiniStudio(false)
    }

    browserViewModel.onDownloadConfirmed = { request ->
        enqueueDownload(context, request)
    }
    browserViewModel.onExternalConfirmed = { url ->
        if (!openExternalUrl(activity, url)) {
            browserViewModel.showMessage("No compatible app could open this link.")
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AnimatedVisibility(
            visible = browserControlsVisible,
            enter = if (motionEnabled) {
                fadeIn(animationSpec = tween(150)) +
                    expandVertically(
                        animationSpec = tween(150),
                        expandFrom = Alignment.Top,
                    )
            } else EnterTransition.None,
            exit = if (motionEnabled) {
                fadeOut(animationSpec = tween(150)) +
                    shrinkVertically(
                        animationSpec = tween(150),
                        shrinkTowards = Alignment.Top,
                    )
            } else ExitTransition.None,
        ) {
            Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = UiSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pageTitle = state.activeTab.title.ifBlank { "Browser" }
            Icon(
                imageVector = if (
                    state.activeTab.url.startsWith("https://", ignoreCase = true) &&
                    state.activeTab.pageError?.ssl != true
                ) {
                    Icons.Default.Lock
                } else {
                    Icons.Default.LockOpen
                },
                contentDescription = if (
                    state.activeTab.url.startsWith("https://", ignoreCase = true) &&
                    state.activeTab.pageError?.ssl != true
                ) {
                    "HTTPS secure connection"
                } else {
                    "Not an HTTPS page"
                },
                tint = if (
                    state.activeTab.url.startsWith("https://", ignoreCase = true) &&
                    state.activeTab.pageError?.ssl != true
                ) ActionBlue else TextSecondary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = pageTitle,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = UiSpacing.Small),
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = {
                    if (state.activeTab.progress in 1..99) {
                        webViews.stop(state.activeTabId)
                        browserViewModel.stop()
                    }
                    else browserViewModel.reload()
                },
            ) {
                Icon(
                    imageVector = if (state.activeTab.progress in 1..99) {
                        Icons.Default.Stop
                    } else {
                        Icons.Default.Refresh
                    },
                    contentDescription = if (state.activeTab.progress in 1..99) "Stop" else "Reload",
                    tint = TextPrimary,
                )
            }
            IconButton(onClick = { browserViewModel.showMenu(true) }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Browser menu",
                    tint = TextPrimary,
                )
            }
            IconButton(
                onClick = { browserControlsVisible = false },
                modifier = Modifier.size(UiSpacing.TouchTarget),
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Hide browser toolbar",
                    tint = TextPrimary,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small),
        ) {
            SearchField(
                value = state.addressInput,
                onValueChange = browserViewModel::setAddressInput,
                modifier = Modifier.weight(1f),
                onSearch = { browserViewModel.submitAddress() },
            )
            IconButton(
                onClick = { browserViewModel.submitAddress() },
                modifier = Modifier.size(UiSpacing.TouchTarget),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Go to address",
                    tint = ActionBlue,
                )
            }
            if (voiceFxActive) {
                AcrylicChip(
                    label = "FX",
                    selected = true,
                    onClick = { browserViewModel.showMiniStudio(true) },
                )
            }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = UiSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(state.tabs, key = BrowserTab::id) { tab ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AcrylicChip(
                        label = tab.title.ifBlank { "New tab" }.take(18),
                        selected = tab.id == state.activeTabId,
                        onClick = { browserViewModel.switchTab(tab.id) },
                    )
                    IconButton(
                        onClick = {
                            closeTab(tab, state.tabs, webViews, browserViewModel)
                        },
                        modifier = Modifier.size(UiSpacing.TouchTarget),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close tab",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            item {
                IconButton(
                    onClick = { browserViewModel.addTab() },
                    modifier = Modifier.size(UiSpacing.TouchTarget),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New tab", tint = ActionBlue)
                }
            }
        }

        if (state.activeTab.progress in 1..99) {
            LinearProgressIndicator(
                progress = { state.activeTab.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = ActionBlue,
                trackColor = Canvas,
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(UiSpacing.TouchTarget)
                .padding(horizontal = UiSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { webViews.goForward(state.activeTabId) },
                enabled = state.activeTab.canGoForward,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = if (state.activeTab.canGoForward) TextPrimary else TextSecondary,
                )
            }
            Text(
                "Desktop site",
                modifier = Modifier.weight(1f),
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            AcrylicSwitch(
                checked = state.activeTab.desktopMode,
                onCheckedChange = browserViewModel::setDesktopMode,
            )
        }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            PullToRefreshBox(
                isRefreshing = state.activeTab.progress in 1..99,
                onRefresh = { browserViewModel.reload() },
                modifier = Modifier.fillMaxSize(),
            ) {
                key(state.activeTabId) {
                    AndroidView(
                        factory = { webViews.get(state.activeTab) },
                        modifier = Modifier.fillMaxSize(),
                        update = { webView ->
                            webViews.update(state.activeTab, webView, voiceFxSettings)
                        },
                        onRelease = { webViews.onTabReleased(state.activeTabId) },
                    )
                }
            }
            if (state.activeTab.url.isBlank() && state.activeTab.pageError == null) {
                AcrylicPanel(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(UiSpacing.Large),
                ) {
                    Text(
                        "Start browsing",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(UiSpacing.Small))
                    Text(
                        "Search the web or enter an address above.",
                        color = TextSecondary,
                    )
                }
            }
            state.activeTab.pageError?.let { error ->
                ErrorPanel(
                    error = error,
                    onRetry = { browserViewModel.reload() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            state.message?.let { message ->
                AcrylicPanel(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(UiSpacing.Medium)
                        .fillMaxWidth(),
                ) {
                    Text(message, color = TextPrimary)
                }
                LaunchedEffect(message) {
                    delay(3500)
                    browserViewModel.consumeMessage()
                }
            }
        }

        BottomBar(
            onBack = { webViews.goBack(state.activeTabId) },
            onHome = {
                webViews.get(state.activeTab).loadUrl("about:blank")
                browserViewModel.openHome()
            },
            onTabs = { browserViewModel.showTabs(true) },
            tabCount = state.tabs.size,
        )
    }

        if (!browserControlsVisible) {
            AcrylicPanel(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = UiSpacing.Medium, top = UiSpacing.Small)
                    .zIndex(1f),
                radius = UiSpacing.CardCorner,
            ) {
                IconButton(
                    onClick = { browserControlsVisible = true },
                    modifier = Modifier.size(UiSpacing.TouchTarget),
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Show browser toolbar",
                        tint = TextPrimary,
                    )
                }
            }
        }

    BrowserDialogs(
        state = state,
        browserViewModel = browserViewModel,
        webViews = webViews,
        context = context,
        onResolvePermission = { prompt, allow, remember ->
            if (!allow) {
                webViews.resolvePermission(prompt.id, false, false, false)
            } else {
                val permissions = webViews.requiredAndroidPermissions(prompt)
                val missing = permissions.filter {
                    ContextCompat.checkSelfPermission(context, it) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) {
                    webViews.resolvePermission(prompt.id, true, remember, true)
                } else {
                    pendingRuntimePermission = prompt to remember
                    runtimePermissionLauncher.launch(missing.toTypedArray())
                }
            }
        },
        onClearData = {
            browserViewModel.showMenu(false)
            browserViewModel.showSettings(false)
            scope.launch {
                webViews.clearBrowsingData()
                browserViewModel.showMessage("Browsing data cleared.")
            }
        },
        onRevokeOrigin = { origin ->
            scope.launch { settingsRepository.revokeSiteGrant(origin) }
        },
        onBiometricChange = onBiometricChange ?: { enabled ->
            browserViewModel.updateSettings { it.copy(biometricLock = enabled) }
        },
    )
    if (voiceFxActive) {
        VoiceFxFloatingHud(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = UiSpacing.Medium, vertical = UiSpacing.Large)
                .zIndex(2f),
            stats = voiceFxStats,
            hudVisible = voiceFxSettings.hudVisible,
            origin = currentOrigin.orEmpty(),
            onOpenStudio = { browserViewModel.showMiniStudio(true) },
            onPanic = {
                webViews.get(state.activeTab).evaluateJavascript(
                    "window.__voicefx&&window.__voicefx.panic()",
                    null,
                )
                scope.launch {
                    settingsRepository.updateVoiceFx { it.copy(enabled = false) }
                }
            },
            onToggleHud = {
                scope.launch {
                    settingsRepository.updateVoiceFx {
                        it.copy(hudVisible = !it.hudVisible)
                    }
                }
            },
        )
    }
    if (state.miniStudioVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f),
        ) {
            MiniStudioScreen(
                settingsRepository = settingsRepository,
                currentOrigin = currentOrigin,
                microphonePermissionGranted = microphonePermissionGranted,
                onRequestMicrophonePermission = {
                    microphonePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                },
                onDismiss = { browserViewModel.showMiniStudio(false) },
            )
        }
    }
    if (appLocked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Canvas)
                .zIndex(1f),
            contentAlignment = Alignment.Center,
        ) {
            AcrylicPanel(modifier = Modifier.padding(UiSpacing.Large)) {
                Text(
                    "Browser locked",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(UiSpacing.Medium))
                ActionButton("Unlock", onRequestUnlock)
            }
        }
    }
    }
}

@Composable
private fun ErrorPanel(
    error: PageError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Canvas)
            .padding(UiSpacing.Large),
        contentAlignment = Alignment.Center,
    ) {
        AcrylicPanel(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = error.title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(UiSpacing.Small))
            Text(
                text = error.message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (error.ssl) {
                Spacer(Modifier.height(UiSpacing.Small))
                Text(
                    text = "This certificate warning cannot be bypassed.",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(UiSpacing.Medium))
            ActionButton(text = "Try again", onClick = onRetry)
        }
    }
}

internal fun closeTab(
    tab: BrowserTab,
    tabs: List<BrowserTab>,
    webViews: BrowserWebViewPool,
    viewModel: BrowserViewModel,
) {
    val deletePrivateProfile = tab.isPrivate && tabs.none {
        it.isPrivate && it.id != tab.id
    }
    webViews.closeTab(tab.id, deletePrivateProfile)
    viewModel.closeTab(tab.id)
}

private fun enqueueDownload(context: Context, download: DownloadRequest) {
    if (download.url.toUri().scheme?.equals("https", ignoreCase = true) != true) {
        return
    }
    val filename = android.webkit.URLUtil.guessFileName(
        download.url,
        download.contentDisposition,
        download.mimeType,
    )
    val request = DownloadManager.Request(download.url.toUri())
        .setTitle(filename)
        .setDescription("Downloading file")
        .setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
        )
        .setAllowedOverMetered(true)
    download.mimeType?.let(request::setMimeType)
    download.userAgent?.let { request.addRequestHeader("User-Agent", it) }
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}

private fun openExternalUrl(activity: android.app.Activity, url: String): Boolean {
    val scheme = url.toUri().scheme?.lowercase()
    if (scheme == "https" || scheme == "http") {
        return try {
            androidx.browser.customtabs.CustomTabsIntent.Builder()
                .build()
                .launchUrl(activity, url.toUri())
            true
        } catch (_: android.content.ActivityNotFoundException) {
            false
        }
    }
    val intent = if (url.startsWith("intent://")) {
        runCatching {
            android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME).apply {
                component = null
                selector = null
                `package` = null
                flags = 0
                action = android.content.Intent.ACTION_VIEW
                extras?.clear()
                data?.scheme?.lowercase()?.let { dataScheme ->
                    if (dataScheme in setOf("file", "content", "javascript", "data", "blob")) {
                        data = null
                    }
                }
                categories?.clear()
            }
        }.getOrNull()
    } else {
        android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
    }
    if (intent == null || intent.data == null) return false
    return try {
        activity.startActivity(intent)
        true
    } catch (_: android.content.ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private tailrec fun Context.findActivity(): android.app.Activity = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> error("BrowserScreen requires an Activity context.")
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun BrowserScreenPreview() {
    com.example.webview.ui.theme.BrowserTheme {
        BrowserScreen()
    }
}
