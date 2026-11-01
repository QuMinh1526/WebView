package com.example.webview.browser

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.webview.data.VoiceFxOrigin
import com.example.webview.data.VoiceFxSettings
import org.json.JSONObject
import java.net.URI
import java.util.WeakHashMap

@SuppressLint("RequiresFeature") // Every WebKit extension is guarded by its feature check.
class VoiceFxPageScriptInjector(
    context: Context,
    private val onStats: (String) -> Unit = {},
) : PageScriptInjector {
    private val appContext = context.applicationContext
    private val mappingTable = appContext.assets.open("voicefx-mapping.json")
        .bufferedReader().use { it.readText() }
    private val scriptTemplate = appContext.assets.open("voicefx.js")
        .bufferedReader().use { it.readText() }
    private val installed = WeakHashMap<WebView, InstalledScript>()
    private val allowedOriginByView = WeakHashMap<WebView, Set<String>>()
    private val settingsByView = WeakHashMap<WebView, VoiceFxSettings>()
    private val runtimeBypass = java.util.Collections.newSetFromMap(
        WeakHashMap<WebView, Boolean>(),
    )

    override fun configure(webView: WebView, settings: VoiceFxSettings) {
        if (settingsByView[webView] == settings) return
        if (settingsByView[webView]?.enabled == false && settings.enabled) {
            runtimeBypass.remove(webView)
        }
        settingsByView[webView] = settings
        val origins = settings.allowedOrigins.mapNotNull(VoiceFxOrigin::normalize).toSet()
        val allowedRules = origins.mapNotNull(::toOriginRule).toSet()
        if (!settings.enabled || allowedRules.isEmpty() ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        ) {
            webView.evaluateJavascript(
                "window.__voicefx&&window.__voicefx.set({enabled:false,hudVisible:false})",
                null,
            )
            remove(webView, clearBypass = true)
            return
        }

        val initialConfig = JSONObject()
            .put("enabled", settings.enabled && webView !in runtimeBypass)
            .put("hudVisible", settings.hudVisible)
            .put("origins", org.json.JSONArray(origins.toList()))
            .put("parameters", parametersJson(settings))
            .put("blocks", org.json.JSONArray(settings.enabledBlocks.toList()))
        val script = scriptTemplate
            .replace("__VOICEFX_MAPPING__", mappingTable)
            .replace("__VOICEFX_INITIAL__", initialConfig.toString())
        val old = installed[webView]
        if (old == null || old.origins != allowedRules || old.script != script) {
            remove(webView)
            val handler = WebViewCompat.addDocumentStartJavaScript(webView, script, allowedRules)
            WebViewCompat.addWebMessageListener(
                webView,
                BRIDGE_NAME,
                allowedRules,
            ) { _, message: WebMessageCompat, sourceOrigin, isMainFrame, _ ->
                val origin = sourceOrigin.toString()
                if (isMainFrame && origin in origins) {
                    if (runCatching {
                            JSONObject(message.data.orEmpty()).optString("type") == "panic"
                        }.getOrDefault(false)
                    ) {
                        runtimeBypass.add(webView)
                        webView.evaluateJavascript(
                            "window.__voicefx&&window.__voicefx.set({enabled:false})",
                            null,
                        )
                    } else {
                        onStats(message.data.orEmpty())
                    }
                }
            }
            installed[webView] = InstalledScript(allowedRules, script, handler)
        }
        allowedOriginByView[webView] = origins
        inject(webView, webView.url.orEmpty(), isMainFrame = true)
    }

    override fun inject(webView: WebView, pageUrl: String, isMainFrame: Boolean) {
        if (!isMainFrame) return
        val origin = runCatching {
            URI(pageUrl).let { uri ->
                "${uri.scheme}://${uri.host}${if (uri.port == -1) "" else ":${uri.port}"}"
            }
        }
            .getOrNull()
        if (origin !in allowedOriginByView[webView].orEmpty()) {
            webView.evaluateJavascript(
                "window.__voicefx&&window.__voicefx.set({enabled:false,hudVisible:false})",
                null,
            )
            return
        }
        val settings = settingsByView[webView] ?: return
        val update = JSONObject()
            .put("enabled", settings.enabled && webView !in runtimeBypass)
            .put("hudVisible", settings.hudVisible)
            .put("parameters", parametersJson(settings))
            .put("blocks", org.json.JSONArray(settings.enabledBlocks.toList()))
        webView.evaluateJavascript(
            "window.__voicefx&&window.__voicefx.set($update)",
            null,
        )
    }

    override fun onPause() {
        installed.keys.toList().forEach { webView ->
            webView.evaluateJavascript("window.__voicefx&&window.__voicefx.set({hudVisible:false})", null)
        }
    }

    override fun onResume() {
        installed.keys.toList().forEach { webView ->
            inject(webView, webView.url.orEmpty(), isMainFrame = true)
        }
    }

    private fun parametersJson(settings: VoiceFxSettings) = JSONObject().also { values ->
        settings.normalizedParameters.forEach { (key, value) ->
            values.put(key, value.coerceIn(0, VoiceFxScale.NORMAL_MAX))
        }
    }

    private fun remove(webView: WebView, clearBypass: Boolean = false) {
        installed.remove(webView)?.let { script ->
            script.handler.remove()
            WebViewCompat.removeWebMessageListener(webView, BRIDGE_NAME)
        }
        allowedOriginByView.remove(webView)
        if (clearBypass) runtimeBypass.remove(webView)
    }

    private fun toOriginRule(origin: String): String? {
        val normalized = VoiceFxOrigin.normalize(origin) ?: return null
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        if (UrlPolicy.isVerificationAllowed("$normalized/")) return null
        return "https://$host${if (uri.port == -1) "" else ":${uri.port}"}"
    }

    private data class InstalledScript(
        val origins: Set<String>,
        val script: String,
        val handler: ScriptHandler,
    )

    private companion object {
        const val BRIDGE_NAME = "__voiceFxBridge"
    }
}
