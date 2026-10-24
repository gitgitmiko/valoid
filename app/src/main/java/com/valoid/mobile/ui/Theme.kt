package com.valoid.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip

/**
 * Palet bernuansa Killjoy: kuning teknis sebagai warna utama, aksen ungu lavender,
 * dan latar arang keunguan. Teks di atas kuning memakai arang agar tetap terbaca.
 */
private val Colors = darkColorScheme(
    primary = Color(0xFFFFD23F),
    onPrimary = Color(0xFF16141C),
    primaryContainer = Color(0xFF3A3220),
    onPrimaryContainer = Color(0xFFFFE58A),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF16141C),
    tertiary = Color(0xFF7C5CFF),
    background = Color(0xFF121118),
    surface = Color(0xFF1E1C26),
    surfaceVariant = Color(0xFF2A2733),
    onSurfaceVariant = Color(0xFFCAC4D6),
    outline = Color(0xFF5A5468),
    onBackground = Color(0xFFF2EFE6),
    onSurface = Color(0xFFF2EFE6),
    error = Color(0xFFFF6B6B),
)

@Composable
fun ValoidTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors) {
        // Surface memberi warna teks bawaan (onBackground) untuk layar di luar Scaffold.
        Surface(color = Colors.background, contentColor = Colors.onBackground, content = content)
    }
}

/** Logo Valoid (ikon aplikasi) untuk layar login dan Tentang. */
@Composable
fun ValoidLogo(size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(size * 0.28f))
            .background(Color(0xFF16141C)),
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.valoid.mobile.R.drawable.ic_launcher_foreground),
            contentDescription = "Valoid",
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        )
    }
}
