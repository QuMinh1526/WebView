package com.example.webview.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.webview.ui.theme.EdgeTint
import com.example.webview.ui.theme.Surface as SurfaceColor
import com.example.webview.ui.theme.UiSpacing

@Composable
fun AcrylicPanel(
    modifier: Modifier = Modifier,
    radius: Dp = UiSpacing.CardCorner,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Surface(
        modifier = modifier.shadow(
            elevation = 20.dp,
            shape = shape,
            ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.16f),
            spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.16f),
        ),
        shape = shape,
        color = SurfaceColor,
        border = BorderStroke(UiSpacing.Outline, EdgeTint),
    ) {
        Column(
            modifier = Modifier.padding(UiSpacing.Medium),
            content = content,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun AcrylicPanelPreview() {
    AcrylicPanel {
        androidx.compose.material3.Text("Acrylic panel")
    }
}
