package com.example.webview.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.webview.ui.theme.ActionBlue
import com.example.webview.ui.theme.EdgeTint
import com.example.webview.ui.theme.Surface
import com.example.webview.ui.theme.TextPrimary
import com.example.webview.ui.theme.UiSpacing
import com.example.webview.ui.theme.rememberMotionEnabled

@Composable
fun AcrylicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val motionEnabled = rememberMotionEnabled()
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 520f),
        label = "switch-position",
    )
    Box(
        modifier = modifier
            .sizeIn(minWidth = UiSpacing.TouchTarget, minHeight = UiSpacing.TouchTarget)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 36.dp, height = 22.dp)) {
            val radius = size.height / 2f
            val trackColor = if (checked) ActionBlue else Surface
            drawRoundRect(
                color = trackColor.copy(alpha = if (enabled) 1f else 0.38f),
                cornerRadius = CornerRadius(radius, radius),
            )
            drawRoundRect(
                color = EdgeTint,
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(UiSpacing.Outline.toPx()),
            )
            drawCircle(
                color = if (enabled) TextPrimary else TextPrimary.copy(alpha = 0.38f),
                radius = radius - 2.dp.toPx(),
                center = Offset(
                    x = radius + (size.width - 2f * radius) * if (motionEnabled) progress else if (checked) 1f else 0f,
                    y = radius,
                ),
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun AcrylicSwitchPreview() {
    AcrylicSwitch(checked = true, onCheckedChange = {})
}
