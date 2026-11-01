package com.example.webview.data

import com.example.webview.browser.VoiceFxPreset

data class VoiceFxSettings(
    val enabled: Boolean = false,
    val presetId: String = "off",
    val normalizedParameters: Map<String, Int> = emptyMap(),
    val enabledBlocks: Set<String> = emptySet(),
    val allowedOrigins: Set<String> = emptySet(),
    val hudVisible: Boolean = true,
    val customPresets: List<VoiceFxPreset> = emptyList(),
)
