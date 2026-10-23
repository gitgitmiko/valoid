package com.valoid.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFFFF4655),
    onPrimary = Color.White,
    background = Color(0xFF0F1923),
    surface = Color(0xFF1B2733),
    onBackground = Color(0xFFECE8E1),
    onSurface = Color(0xFFECE8E1),
)

@Composable
fun ValoidTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
