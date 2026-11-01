package com.example.webview.ui.theme

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun rememberMotionEnabled(): Boolean =
    remember { ValueAnimator.areAnimatorsEnabled() }
