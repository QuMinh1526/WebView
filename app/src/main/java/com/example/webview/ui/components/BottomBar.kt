package com.example.webview.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.webview.ui.theme.EdgeTint
import com.example.webview.ui.theme.SurfaceRaised
import com.example.webview.ui.theme.TextPrimary
import com.example.webview.ui.theme.TextSecondary
import com.example.webview.ui.theme.UiSpacing

@Composable
fun BottomBar(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    modifier: Modifier = Modifier,
    tabCount: Int = 1,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(SurfaceRaised)
            .border(UiSpacing.Outline, EdgeTint),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomAction(Modifier.weight(1f), Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        BottomAction(Modifier.weight(1f), Icons.Default.Home, "Home", onHome, selected = true)
        BottomAction(Modifier.weight(1f), Icons.Default.Tab, "$tabCount tabs", onTabs)
    }
}

@Composable
private fun BottomAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Box(
        modifier = modifier
            .sizeIn(minHeight = UiSpacing.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected) TextPrimary else TextSecondary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun BottomBarPreview() {
    BottomBar(onBack = {}, onHome = {}, onTabs = {}, tabCount = 2)
}
