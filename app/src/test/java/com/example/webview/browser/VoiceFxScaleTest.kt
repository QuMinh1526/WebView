package com.example.webview.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceFxScaleTest {
    private val ranges by lazy {
        val source = javaClass.classLoader!!
            .getResourceAsStream("voicefx-mapping.json")!!
            .bufferedReader()
            .use { it.readText() }
        VoiceFxScale.parseTable(source)
    }

    @Test
    fun allMappingsAreMonotonicContinuousAndBounded() {
        ranges.forEach { (_, range) ->
            val normal = (0..VoiceFxScale.NORMAL_MAX)
                .map { VoiceFxScale.map(it, false, range) }
            val extended = (0..VoiceFxScale.EXTENDED_MAX)
                .map { VoiceFxScale.map(it, true, range) }

            assertEquals(normal.first(), extended.first(), 0.0001)
            assertEquals(normal.last(), extended[VoiceFxScale.NORMAL_MAX], 0.0001)
            assertEquals(range.extendedMaximum, extended.last(), 0.0001)
            val increasing = range.normalMaximum >= range.minimum
            assertTrue(normal.zipWithNext().all { (left, right) ->
                if (increasing) left <= right else left >= right
            })
            assertTrue(extended.zipWithNext().all { (left, right) ->
                if (increasing) left <= right else left >= right
            })
        }
    }

    @Test
    fun sliderPositionsClampToModeRange() {
        val gain = ranges.getValue("outputGain")
        assertEquals(0.0, VoiceFxScale.map(-1, false, gain), 0.0001)
        assertEquals(1.0, VoiceFxScale.map(101, false, gain), 0.0001)
        assertEquals(1.0, VoiceFxScale.map(1001, true, gain), 0.0001)
    }
}
