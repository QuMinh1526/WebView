package com.example.webview.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.webview.ui.theme.rememberMotionEnabled

@Composable
fun ScreenFade(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    content: @Composable () -> Unit,
) {
    val motionEnabled = rememberMotionEnabled()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (motionEnabled) fadeIn(tween(150, easing = LinearEasing)) else EnterTransition.None,
        exit = if (motionEnabled) fadeOut(tween(150, easing = LinearEasing)) else ExitTransition.None,
    ) {
        content()
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ScreenFadePreview() {
    com.example.webview.ui.theme.BrowserTheme {
        ScreenFade { androidx.compose.material3.Text("Screen") }
    }
}
