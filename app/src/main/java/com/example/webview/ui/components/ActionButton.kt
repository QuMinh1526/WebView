package com.example.webview.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.webview.ui.theme.ActionBlue
import com.example.webview.ui.theme.EdgeTint
import com.example.webview.ui.theme.SurfacePressed
import com.example.webview.ui.theme.PressTint
import com.example.webview.ui.theme.TextPrimary
import com.example.webview.ui.theme.UiSpacing
import com.example.webview.ui.theme.rememberMotionEnabled

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motionEnabled = rememberMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && motionEnabled) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 1400f),
        label = "action-press",
    )
    val shape = RoundedCornerShape(UiSpacing.CardCorner)
    val color = if (primary) ActionBlue else SurfacePressed
    val buttonColor = when {
        !enabled -> color.copy(alpha = 0.38f)
        pressed -> PressTint.compositeOver(color)
        else -> color
    }

    Box(
        modifier = modifier
            .sizeIn(minWidth = 88.dp, minHeight = UiSpacing.TouchTarget)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 88.dp)
                .height(36.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(buttonColor)
                .then(
                    if (primary) Modifier
                    else Modifier.border(UiSpacing.Outline, EdgeTint, shape),
                )
                .padding(horizontal = UiSpacing.Medium),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (enabled) TextPrimary else TextPrimary.copy(alpha = 0.38f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ActionButtonPreview() {
    androidx.compose.foundation.layout.Row {
        ActionButton(text = "Open", onClick = {})
        ActionButton(text = "Cancel", onClick = {}, primary = false)
    }
}
