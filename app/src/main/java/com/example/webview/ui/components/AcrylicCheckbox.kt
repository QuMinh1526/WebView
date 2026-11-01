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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.webview.ui.theme.ActionBlue
import com.example.webview.ui.theme.EdgeTint
import com.example.webview.ui.theme.TextPrimary
import com.example.webview.ui.theme.UiSpacing
import com.example.webview.ui.theme.rememberMotionEnabled

@Composable
fun AcrylicCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val motionEnabled = rememberMotionEnabled()
    val animatedReveal by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(stiffness = 600f),
        label = "checkbox-reveal",
    )
    val reveal = if (motionEnabled) animatedReveal else if (checked) 1f else 0f
    Box(
        modifier = modifier
            .sizeIn(minWidth = UiSpacing.TouchTarget, minHeight = UiSpacing.TouchTarget)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val color = if (enabled) ActionBlue else ActionBlue.copy(alpha = 0.38f)
            val corner = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            drawRoundRect(
                color = if (reveal > 0f) color else androidx.compose.ui.graphics.Color.Transparent,
                cornerRadius = corner,
            )
            drawRoundRect(
                color = if (checked) color else EdgeTint,
                cornerRadius = corner,
                style = Stroke(2.dp.toPx()),
            )
            if (reveal > 0f) {
                val path = Path().apply {
                    moveTo(size.width * 0.22f, size.height * 0.52f)
                    lineTo(size.width * 0.43f, size.height * 0.72f)
                    lineTo(size.width * 0.79f, size.height * 0.31f)
                }
                drawPath(
                    path = path,
                    color = TextPrimary,
                    style = Stroke(width = 2.dp.toPx()),
                    alpha = reveal,
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun AcrylicCheckboxPreview() {
    AcrylicCheckbox(checked = true, onCheckedChange = {})
}
