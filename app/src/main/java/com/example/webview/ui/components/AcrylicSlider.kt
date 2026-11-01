package com.example.webview.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.webview.ui.theme.ActionBlue
import com.example.webview.ui.theme.SubtleWhite
import com.example.webview.ui.theme.UiSpacing

@Composable
fun AcrylicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = UiSpacing.TouchTarget),
        enabled = enabled,
        valueRange = 0f..1f,
        colors = SliderDefaults.colors(
            thumbColor = ActionBlue,
            activeTrackColor = ActionBlue,
            inactiveTrackColor = SubtleWhite,
            disabledThumbColor = ActionBlue.copy(alpha = 0.38f),
            disabledActiveTrackColor = ActionBlue.copy(alpha = 0.38f),
            disabledInactiveTrackColor = SubtleWhite.copy(alpha = 0.38f),
        ),
    )
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun AcrylicSliderPreview() {
    AcrylicSlider(value = 0.4f, onValueChange = {})
}
