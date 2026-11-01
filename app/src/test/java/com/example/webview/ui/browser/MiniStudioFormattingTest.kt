package com.example.webview.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class MiniStudioFormattingTest {
    @Test
    fun percentValuesRenderWithoutTreatingUnitAsFormatSpecifier() {
        assertEquals("30 %", formatVoiceFxValue("chorusMix", 30.0))
    }

    @Test
    fun physicalUnitsRemainInFormattedValues() {
        assertEquals("1200 Hz", formatVoiceFxValue("highPassHz", 1200.0))
        assertEquals("2.5 st", formatVoiceFxValue("pitchSemitones", 2.5))
    }
}
