package com.example.webview

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import com.example.webview.browser.BrowserViewModel
import com.example.webview.ui.browser.BrowserScreen
import com.example.webview.ui.components.ScreenFade
import com.example.webview.ui.theme.BrowserTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private lateinit var browserViewModel: BrowserViewModel
    private val appLocked = MutableStateFlow(false)
    private var authenticationInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        browserViewModel = ViewModelProvider(this)[BrowserViewModel::class.java]
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            BrowserTheme {
                val locked = appLocked.collectAsState().value
                ScreenFade {
                    BrowserScreen(
                        browserViewModel = browserViewModel,
                        appLocked = locked,
                        onBiometricChange = ::setBiometricLock,
                        onRequestUnlock = {
                            authenticate { appLocked.value = false }
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!::browserViewModel.isInitialized) return
        lifecycleScope.launch {
            val settings = browserViewModel.state.first { it.settingsLoaded }.settings
            if (settings.biometricLock) {
                appLocked.value = true
                authenticate { appLocked.value = false }
            }
        }
    }

    override fun onStop() {
        CookieManager.getInstance().flush()
        if (::browserViewModel.isInitialized) {
            appLocked.value = browserViewModel.state.value.settings.biometricLock
        }
        super.onStop()
    }

    private fun setBiometricLock(enabled: Boolean) {
        if (!enabled) {
            browserViewModel.updateSettings { it.copy(biometricLock = false) }
            return
        }
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (BiometricManager.from(this).canAuthenticate(authenticators) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            browserViewModel.showMessage("A strong biometric is not available on this device.")
            return
        }
        authenticate {
            browserViewModel.updateSettings { it.copy(biometricLock = true) }
        }
    }

    private fun authenticate(onSuccess: () -> Unit) {
        if (authenticationInProgress) return
        authenticationInProgress = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    authenticationInProgress = false
                    onSuccess()
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    authenticationInProgress = false
                    if (browserViewModel.state.value.settings.biometricLock) {
                        appLocked.value = true
                    }
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock browser")
            .setSubtitle("Authenticate to access your tabs")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(promptInfo)
    }
}
