package com.example.webview.browser

import com.example.webview.data.VoiceFxOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceFxPresetTest {
    @Test
    fun presetJsonRoundTripsAndClampsImportedValues() {
        val preset = VoiceFxPreset(
            id = "custom",
            name = "My voice",
            normalizedParameters = mapOf("pitchSemitones" to 1250, "outputGain" to 80),
            enabledBlocks = setOf("pitch", "echo"),
        )

        val restored = VoiceFxPreset.parseExport(VoiceFxPreset.export(listOf(preset))).single()

        assertEquals("custom", restored.id)
        assertEquals("My voice", restored.name)
        assertEquals(1000, restored.normalizedParameters["pitchSemitones"])
        assertEquals(80, restored.normalizedParameters["outputGain"])
        assertEquals(setOf("echo", "pitch"), restored.enabledBlocks)
        assertFalse(VoiceFxPreset.builtIns.any { it.name.contains("Angry", ignoreCase = true) })
    }

    @Test
    fun originAllowlistAcceptsExactHttpsOriginsOnly() {
        assertEquals("https://meet.google.com", VoiceFxOrigin.normalize("meet.google.com"))
        assertEquals("https://example.com:8443", VoiceFxOrigin.normalize("https://EXAMPLE.com:8443/"))
        assertNull(VoiceFxOrigin.normalize("http://meet.google.com"))
        assertNull(VoiceFxOrigin.normalize("https://meet.google.com/path"))
        assertNull(VoiceFxOrigin.normalize("https://*.example.com"))
        assertNull(VoiceFxOrigin.normalize("https://challenges.cloudflare.com"))
        assertNull(VoiceFxOrigin.normalize("https://www.google.com/recaptcha"))
    }
}
