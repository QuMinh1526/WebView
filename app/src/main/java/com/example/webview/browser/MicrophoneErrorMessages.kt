package com.example.webview.browser

fun microphoneErrorMessage(code: String): String = when (code) {
    "NotAllowed" -> "Microphone permission was denied. Allow microphone access for this site."
    "NotFound" -> "No microphone was found."
    "NotReadable" -> "The microphone is unavailable or in use by another app."
    "Insecure" -> "Microphone access requires a secure HTTPS context."
    "AudioContextSuspended" -> "Audio is paused. Tap the page and try again."
    else -> "Microphone access failed."
}
