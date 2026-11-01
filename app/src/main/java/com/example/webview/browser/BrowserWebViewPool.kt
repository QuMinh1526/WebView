package com.example.webview.browser

import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SafeBrowsingResponse
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import android.webkit.WebSettings
import com.example.webview.data.SettingsRepository
import com.example.webview.data.VoiceFxSettings
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.UUID
import kotlin.coroutines.resume

@SuppressLint("RequiresFeature") // MULTI_PROFILE is checked before every profile API path.
class BrowserWebViewPool(
    private val activity: Activity,
    private val settingsRepository: SettingsRepository,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val viewModel: BrowserViewModel,
    private val scriptInjector: PageScriptInjector = NoOpPageScriptInjector(),
) {
    private val views = mutableMapOf<String, WebView>()
    private val desktopModes = mutableMapOf<String, Boolean>()
    private val reloadGenerations = mutableMapOf<String, Int>()
    private val thirdPartyCookieModes = mutableMapOf<String, Boolean>()
    private val defaultUserAgents = mutableMapOf<String, String>()
    private val pendingPermissions = mutableMapOf<String, PendingPermission>()
    private val profileApiSupported =
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var previousSystemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT

    fun get(tab: BrowserTab): WebView = views.getOrPut(tab.id) {
        createWebView(tab).also { webView ->
            viewModel.consumeWebState(tab.id)?.let(webView::restoreState)
            webView.onResume()
        }
    }

    fun update(tab: BrowserTab, webView: WebView) {
        update(tab, webView, VoiceFxSettings())
    }

    fun update(tab: BrowserTab, webView: WebView, voiceFxSettings: VoiceFxSettings) {
        scriptInjector.configure(webView, voiceFxSettings)
        val modeChanged = desktopModes.put(tab.id, tab.desktopMode) != tab.desktopMode
        val reloadChanged = reloadGenerations.put(tab.id, tab.reloadGeneration) !=
            tab.reloadGeneration
        val thirdPartyEnabled = viewModel.state.value.settings.acceptThirdPartyCookies
        if (thirdPartyCookieModes.put(tab.id, thirdPartyEnabled) != thirdPartyEnabled) {
            cookieManager(tab).setAcceptThirdPartyCookies(webView, thirdPartyEnabled)
        }

        if (modeChanged) {
            configureSettings(webView, tab)
            if (webView.url != null) webView.reload()
        } else if (reloadChanged) {
            if (webView.url.isNullOrBlank() || webView.url == "about:blank") {
                if (tab.url.isNotBlank()) webView.loadUrl(tab.url)
            } else {
                webView.reload()
            }
        } else if (tab.url.isNotBlank() && webView.url != tab.url) {
            webView.loadUrl(tab.url)
        }
    }

    fun onTabReleased(tabId: String) {
        views[tabId]?.let { webView ->
            saveState(tabId, webView)
            webView.onPause()
            (webView.parent as? ViewGroup)?.removeView(webView)
        }
    }

    fun closeTab(tabId: String, deletePrivateProfile: Boolean) {
        views.remove(tabId)?.let { destroy(it) }
        desktopModes.remove(tabId)
        reloadGenerations.remove(tabId)
        thirdPartyCookieModes.remove(tabId)
        defaultUserAgents.remove(tabId)
        pendingPermissions.entries.removeAll { (_, pending) ->
            if (pending.tabId == tabId) {
                pending.deny()
                true
            } else false
        }
        if (deletePrivateProfile && profileApiSupported) {
            ProfileStore.getInstance().deleteProfile(PRIVATE_PROFILE)
        } else if (deletePrivateProfile) {
            clearProfileData(PRIVATE_PROFILE)
        }
    }

    fun onPause() {
        views.values.forEach(WebView::onPause)
        scriptInjector.onPause()
        CookieManager.getInstance().flush()
        if (profileApiSupported) {
            ProfileStore.getInstance().getAllProfileNames().forEach { profileName ->
                ProfileStore.getInstance().getProfile(profileName)?.cookieManager?.flush()
            }
        }
    }

    fun onResume(activeTabId: String) {
        views.values.forEach { if (it === views[activeTabId]) it.onResume() }
        scriptInjector.onResume()
    }

    fun destroyAll() {
        views.forEach { (id, webView) -> saveState(id, webView) }
        views.values.toList().forEach(::destroy)
        views.clear()
        pendingPermissions.values.forEach(PendingPermission::deny)
        pendingPermissions.clear()
        dismissFullscreen()
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
    }

    fun reload(tabId: String) {
        views[tabId]?.reload()
    }

    fun goBack(tabId: String): Boolean {
        val webView = views[tabId] ?: return false
        return if (webView.canGoBack()) {
            webView.goBack()
            true
        } else false
    }

    fun goForward(tabId: String): Boolean {
        val webView = views[tabId] ?: return false
        return if (webView.canGoForward()) {
            webView.goForward()
            true
        } else false
    }

    fun stop(tabId: String) {
        views[tabId]?.stopLoading()
    }

    fun openUrl(tabId: String, url: String) {
        views[tabId]?.loadUrl(url)
    }

    fun diagnostics(tabId: String): BrowserDiagnostics {
        val webView = views[tabId]
        val version = WebView.getCurrentWebViewPackage()?.versionName ?: "Unavailable"
        return BrowserDiagnostics(
            userAgent = webView?.settings?.userAgentString ?: WebSettings.getDefaultUserAgent(activity),
            webViewVersion = version,
            cookiesEnabled = cookieManager(viewModel.state.value.activeTab).acceptCookie(),
            domStorageEnabled = webView?.settings?.domStorageEnabled ?: false,
        )
    }

    fun completeFileChooser(uris: List<Uri>) {
        fileChooserCallback?.onReceiveValue(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
        fileChooserCallback = null
        viewModel.setFileChooserPending(false)
    }

    fun requiredAndroidPermissions(prompt: SitePermissionPrompt): Array<String> {
        if (prompt.isGeolocation) {
            return arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
        return buildList {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in prompt.resources) {
                add(Manifest.permission.CAMERA)
            }
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in prompt.resources) {
                add(Manifest.permission.RECORD_AUDIO)
            }
        }.toTypedArray()
    }

    fun resolvePermission(
        promptId: String,
        allow: Boolean,
        remember: Boolean,
        runtimePermissionsGranted: Boolean,
    ) {
        val pending = pendingPermissions.remove(promptId) ?: return
        viewModel.setPermissionPrompt(null)
        if (!allow || !runtimePermissionsGranted) {
            if (allow) viewModel.showMessage("Android permission is required for this feature.")
            pending.deny()
            return
        }
        if (remember) {
            lifecycleScope.launch {
                settingsRepository.saveSiteGrant(pending.origin, pending.resources)
            }
        }
        pending.grant()
    }

    suspend fun clearBrowsingData() {
        views.values.forEach { webView ->
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
            webView.clearSslPreferences()
        }
        WebStorage.getInstance().deleteAllData()
        if (profileApiSupported) {
            ProfileStore.getInstance().getAllProfileNames().forEach { profileName ->
                ProfileStore.getInstance().getProfile(profileName)?.let { profile ->
                    removeAllCookies(profile.cookieManager)
                    profile.cookieManager.flush()
                    profile.webStorage.deleteAllData()
                }
            }
        } else {
            removeAllCookies(CookieManager.getInstance())
            CookieManager.getInstance().flush()
        }
        settingsRepository.clearSiteGrants()
        views.values.forEach { it.loadUrl("about:blank") }
        viewModel.clearHistory()
    }

    private fun createWebView(tab: BrowserTab): WebView {
        val webView = WebView(activity)
        if (profileApiSupported) {
            val profileName = if (tab.isPrivate) PRIVATE_PROFILE else PRIMARY_PROFILE
            ProfileStore.getInstance().getOrCreateProfile(profileName)
            WebViewCompat.setProfile(webView, profileName)
        }
        configureSettings(webView, tab)
        desktopModes[tab.id] = tab.desktopMode
        reloadGenerations[tab.id] = tab.reloadGeneration
        thirdPartyCookieModes[tab.id] =
            viewModel.state.value.settings.acceptThirdPartyCookies
        webView.webViewClient = createWebViewClient(tab.id)
        webView.webChromeClient = createWebChromeClient(tab.id)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.startsWith("https://", ignoreCase = true) &&
                UrlPolicy.classify(url, hasUserGesture = false) == UrlDecision.ALLOW_WEB
            ) {
                viewModel.requestDownload(
                    DownloadRequest(url, userAgent, contentDisposition, mimeType),
                )
            } else {
                viewModel.showMessage("Downloads require a secure HTTPS connection.")
            }
        }
        return webView
    }

    @Suppress("DEPRECATION") // Explicit WebSettings hardening and requested-with policy.
    private fun configureSettings(webView: WebView, tab: BrowserTab) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.saveFormData = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(true)
        settings.useWideViewPort = tab.desktopMode
        settings.loadWithOverviewMode = tab.desktopMode
        settings.setSupportZoom(tab.desktopMode)
        settings.builtInZoomControls = tab.desktopMode
        settings.displayZoomControls = false

        val defaultUa = defaultUserAgents.getOrPut(tab.id) { settings.userAgentString }
        settings.userAgentString = if (tab.desktopMode) {
            UserAgent.desktop(defaultUa)
        } else {
            UserAgent.mobile(defaultUa)
        }

        val cookies = cookieManager(tab)
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(
            webView,
            viewModel.state.value.settings.acceptThirdPartyCookies,
        )

        if (WebViewFeature.isFeatureSupported("REQUESTED_WITH_HEADER_ALLOW_LIST")) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(settings, true)
        }
    }

    private fun createWebViewClient(tabId: String) = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val url = request.url.toString()
            if (request.isForMainFrame && isGoogleSignInUrl(url)) {
                if (request.hasGesture()) viewModel.requestExternal(url)
                return true
            }
            return when (UrlPolicy.classify(url, request.hasGesture())) {
                UrlDecision.ALLOW_WEB -> false
                UrlDecision.CONFIRM_EXTERNAL -> {
                    if (request.hasGesture()) viewModel.requestExternal(url)
                    true
                }
                UrlDecision.BLOCK -> true
            }
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            val settings = viewModel.state.value.settings
            if (settings.domainBlocklistEnabled &&
                UrlPolicy.isBlockedByDomain(request.url.toString(), settings.blockedDomains)
            ) {
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    403,
                    "Blocked by browser settings",
                    emptyMap(),
                    ByteArrayInputStream(ByteArray(0)),
                )
            }
            return null
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            if (url == "about:blank") return
            scriptInjector.inject(view, url, isMainFrame = true)
            viewModel.clearPageError(tabId)
            viewModel.setPageState(
                tabId, url, view.title, 1, view.canGoBack(), view.canGoForward(),
            )
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (url == "about:blank") return
            viewModel.setPageState(
                tabId, url, view.title, 100, view.canGoBack(), view.canGoForward(),
            )
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (!request.isForMainFrame) return
            if (viewModel.state.value.tabs.firstOrNull { it.id == tabId }?.pageError?.ssl == true) {
                return
            }
            val details = if (error.errorCode == ERROR_HOST_LOOKUP ||
                error.errorCode == ERROR_CONNECT || error.errorCode == ERROR_TIMEOUT
            ) {
                "Check your internet connection and try again."
            } else if (request.url.scheme.equals("http", ignoreCase = true)) {
                "Unencrypted HTTP connections are blocked."
            } else {
                "The page could not be loaded."
            }
            val isSslError = error.errorCode == ERROR_FAILED_SSL_HANDSHAKE
            viewModel.setPageError(
                tabId,
                PageError(
                    if (isSslError) "Secure connection blocked" else "Page unavailable",
                    if (isSslError) {
                        "The site's certificate could not be verified. For your safety, this connection was cancelled."
                    } else details,
                    ssl = isSslError,
                ),
            )
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: android.webkit.SslErrorHandler,
            error: android.net.http.SslError,
        ) {
            handler.cancel()
            viewModel.setPageError(
                tabId,
                PageError(
                    title = "Secure connection blocked",
                    message = "The site's certificate could not be verified. For your safety, this connection was cancelled.",
                    ssl = true,
                ),
            )
        }

        override fun onSafeBrowsingHit(
            view: WebView,
            request: WebResourceRequest,
            threatType: Int,
            callback: SafeBrowsingResponse,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                callback.backToSafety(true)
            }
        }
    }

    private fun createWebChromeClient(tabId: String) = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, progress: Int) {
            viewModel.setPageState(
                tabId, view.url.orEmpty(), view.title, progress,
                view.canGoBack(), view.canGoForward(),
            )
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            viewModel.setPageState(
                tabId, view.url.orEmpty(), title, view.progress,
                view.canGoBack(), view.canGoForward(),
            )
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            if (!isUserGesture || viewModel.state.value.settings.blockPopups) return false
            val popupTab = viewModel.createPopupTab()
            val popupView = get(popupTab)
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            transport.webView = popupView
            resultMsg.sendToTarget()
            return true
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            this@BrowserWebViewPool.fileChooserCallback?.onReceiveValue(null)
            this@BrowserWebViewPool.fileChooserCallback = filePathCallback
            viewModel.setFileChooserPending(true)
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            activity.runOnUiThread {
                val resources = request.resources.toSet()
                val supported = resources.all {
                    it == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                }
                if (!supported) {
                    request.deny()
                    return@runOnUiThread
                }
                val origin = request.origin.toString()
                val prompt = SitePermissionPrompt(
                    id = UUID.randomUUID().toString(),
                    origin = origin,
                    resources = resources,
                )
                lifecycleScope.launch {
                    val alreadyAllowed = settingsRepository.hasSiteGrant(origin, resources)
                    val permissions = requiredAndroidPermissions(prompt)
                    val androidAllowed = if (prompt.isGeolocation) {
                        permissions.any {
                            ContextCompat.checkSelfPermission(activity, it) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                    } else permissions.all {
                        ContextCompat.checkSelfPermission(activity, it) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (alreadyAllowed && androidAllowed) {
                        request.grant(resources.toTypedArray())
                    } else {
                        pendingPermissions[prompt.id] = PendingPermission(
                            tabId = tabId,
                            origin = origin,
                            resources = resources,
                            grantAction = { request.grant(resources.toTypedArray()) },
                            denyAction = request::deny,
                        )
                        viewModel.setPermissionPrompt(prompt)
                    }
                }
            }
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback,
        ) {
            activity.runOnUiThread {
                val prompt = SitePermissionPrompt(
                    id = UUID.randomUUID().toString(),
                    origin = origin,
                    resources = setOf(GEOLOCATION_RESOURCE),
                    isGeolocation = true,
                )
                lifecycleScope.launch {
                    val alreadyAllowed = settingsRepository.hasSiteGrant(
                        origin, prompt.resources,
                    )
                    val androidAllowed = requiredAndroidPermissions(prompt).any {
                        ContextCompat.checkSelfPermission(activity, it) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (alreadyAllowed && androidAllowed) {
                        callback.invoke(origin, true, false)
                    } else {
                        pendingPermissions[prompt.id] = PendingPermission(
                            tabId = tabId,
                            origin = origin,
                            resources = prompt.resources,
                            grantAction = { callback.invoke(origin, true, false) },
                            denyAction = { callback.invoke(origin, false, false) },
                        )
                        viewModel.setPermissionPrompt(prompt)
                    }
                }
            }
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (fullscreenView != null) {
                callback.onCustomViewHidden()
                return
            }
            fullscreenView = view
            fullscreenCallback = callback
            val decor = activity.window.decorView as ViewGroup
            (view.parent as? ViewGroup)?.removeView(view)
            decor.addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            WindowInsetsControllerCompat(activity.window, decor).also { controller ->
                previousSystemBarsBehavior = controller.systemBarsBehavior
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        override fun onHideCustomView() {
            dismissFullscreen()
        }
    }

    private fun dismissFullscreen() {
        fullscreenView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            fullscreenCallback?.onCustomViewHidden()
        }
        fullscreenView = null
        fullscreenCallback = null
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).also {
            it.systemBarsBehavior = previousSystemBarsBehavior
            it.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun saveState(tabId: String, webView: WebView) {
        val state = android.os.Bundle()
        webView.saveState(state)
        viewModel.saveWebState(tabId, state)
    }

    private fun destroy(webView: WebView) {
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.removeAllViews()
        webView.destroy()
    }

    private fun clearProfileData(profileName: String) {
        if (profileApiSupported) {
            ProfileStore.getInstance().getProfile(profileName)?.let { profile ->
                profile.cookieManager.removeAllCookies(null)
                profile.webStorage.deleteAllData()
            }
        } else {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
        CookieManager.getInstance().flush()
    }

    private fun cookieManager(tab: BrowserTab): CookieManager {
        if (!profileApiSupported) return CookieManager.getInstance()
        val profileName = if (tab.isPrivate) PRIVATE_PROFILE else PRIMARY_PROFILE
        return ProfileStore.getInstance().getProfile(profileName)?.cookieManager
            ?: CookieManager.getInstance()
    }

    private suspend fun removeAllCookies(cookieManager: CookieManager) {
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            cookieManager.removeAllCookies {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    private fun isGoogleSignInUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("accounts.google.com", ignoreCase = true)
    }

    private data class PendingPermission(
        val tabId: String,
        val origin: String,
        val resources: Set<String>,
        val grantAction: () -> Unit,
        val denyAction: () -> Unit,
    ) {
        fun grant() = grantAction()
        fun deny() = denyAction()
    }

    companion object {
        const val PRIMARY_PROFILE = "Primary"
        const val PRIVATE_PROFILE = "Private"
        const val GEOLOCATION_RESOURCE = "GEOLOCATION"
    }
}

data class BrowserDiagnostics(
    val userAgent: String,
    val webViewVersion: String,
    val cookiesEnabled: Boolean,
    val domStorageEnabled: Boolean,
)
