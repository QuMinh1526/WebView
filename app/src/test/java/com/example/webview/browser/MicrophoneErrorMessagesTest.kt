package com.example.webview.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrophoneErrorMessagesTest {
    @Test
    fun knownCodesHaveDistinctMessagesAndUnknownCodesHaveFallback() {
        val codes = listOf(
            "NotAllowed",
            "NotFound",
            "NotReadable",
            "Insecure",
            "AudioContextSuspended",
        )
        val messages = codes.map(::microphoneErrorMessage)

        assertEquals(codes.size, messages.toSet().size)
        assertTrue(messages.all(String::isNotBlank))
        assertTrue(microphoneErrorMessage("unknown").isNotBlank())
    }
}
