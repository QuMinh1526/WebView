package com.example.webview.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.example.webview.ui.theme.EdgeTint
import com.example.webview.ui.theme.StrongTint
import com.example.webview.ui.theme.TextPrimary
import com.example.webview.ui.theme.TextSecondary
import com.example.webview.ui.theme.UiSpacing

@Composable
fun AcrylicChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .sizeIn(minWidth = UiSpacing.TouchTarget, minHeight = UiSpacing.TouchTarget)
            .clip(CircleShape)
            .background(if (selected) StrongTint else androidx.compose.ui.graphics.Color.Transparent)
            .border(UiSpacing.Outline, EdgeTint, CircleShape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = UiSpacing.Medium),
            color = if (enabled) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun AcrylicChipPreview() {
    AcrylicChip(label = "Tab 1", selected = true, onClick = {})
}
