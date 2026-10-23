package com.valoid.mobile.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

/** Menampilkan [player] dengan kontrol bawaan Media3, termasuk tombol layar penuh. */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(player: Player, onFullscreenToggle: () -> Unit, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                setShowNextButton(false)
                setShowPreviousButton(false)
                setFullscreenButtonClickListener { onFullscreenToggle() }
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
    )
}
