package com.example.webview.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.webview.ui.theme.EdgeTint
import com.example.webview.ui.theme.SurfaceRaised
import com.example.webview.ui.theme.UiSpacing

@Composable
fun AcrylicBottomSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = UiSpacing.SheetCorner,
            topEnd = UiSpacing.SheetCorner,
        ),
        color = SurfaceRaised,
        border = androidx.compose.foundation.BorderStroke(UiSpacing.Outline, EdgeTint),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = UiSpacing.Medium,
                vertical = UiSpacing.Medium,
            ),
            content = content,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun AcrylicBottomSheetPreview() {
    AcrylicBottomSheet {
        androidx.compose.material3.Text("Sheet content")
    }
}
