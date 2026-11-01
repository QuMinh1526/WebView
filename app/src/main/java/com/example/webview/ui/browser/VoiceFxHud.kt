package com.example.webview.ui.browser

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.webview.browser.VoiceFxStats
import com.example.webview.ui.components.ActionButton
import com.example.webview.ui.components.AcrylicChip
import com.example.webview.ui.components.AcrylicPanel
import com.example.webview.ui.theme.ActionBlue
import com.example.webview.ui.theme.TextPrimary
import com.example.webview.ui.theme.TextSecondary
import com.example.webview.ui.theme.UiSpacing

@Composable
fun VoiceFxFloatingHud(
    modifier: Modifier = Modifier,
    stats: VoiceFxStats?,
    hudVisible: Boolean,
    origin: String,
    onOpenStudio: () -> Unit,
    onPanic: () -> Unit,
    onToggleHud: () -> Unit,
) {
    AcrylicPanel(
        modifier = modifier.fillMaxWidth(),
        radius = UiSpacing.CardCorner,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small),
        ) {
            AcrylicChip(
                label = "FX",
                selected = true,
                onClick = onOpenStudio,
            )
            Text(
                origin,
                modifier = Modifier.weight(1f),
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
            ActionButton("PANIC", onClick = onPanic, primary = false)
            IconButton(onClick = onToggleHud) {
                Icon(
                    imageVector = if (hudVisible) Icons.Default.Close else Icons.Default.GraphicEq,
                    contentDescription = if (hudVisible) "Hide audio HUD" else "Show audio HUD",
                    tint = TextPrimary,
                )
            }
        }
        if (hudVisible) {
            if (stats == null) {
                Text(
                    "Waiting for a permitted microphone request.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                ) {
                    MeterLabel("IN", stats.inputDbfs)
                    MeterLabel("OUT", stats.outputDbfs)
                    MeterLabel("RMS", stats.rmsDbfs)
                    if (stats.clip) {
                        Icon(Icons.Default.Warning, "Clipping detected", tint = ActionBlue)
                    }
                }
                Text(
                    "Peak ${"%.1f".format(stats.peakDbfs)} dBFS · " +
                        "Comp −${"%.1f".format(stats.gainReductionDb)} dB · " +
                        "${stats.sampleRate} Hz · ${"%.1f".format(stats.latencyMs)} ms",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                SpectrumBars(stats.spectrum)
                Text(
                    "Active: ${stats.blocks.joinToString().ifBlank { "clean input" }}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MeterLabel(label: String, value: Double) {
    Column {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Text(
            "${"%.1f".format(value)} dBFS",
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SpectrumBars(values: List<Int>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(vertical = UiSpacing.Small),
    ) {
        if (values.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val barWidth = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { index, value ->
            val height = size.height * (value.coerceIn(0, 255) / 255f)
            drawRoundRect(
                color = ActionBlue,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = index * (barWidth + gap),
                    y = size.height - height,
                ),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
    }
}
