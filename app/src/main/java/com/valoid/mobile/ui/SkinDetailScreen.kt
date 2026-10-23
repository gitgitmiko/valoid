package com.valoid.mobile.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.valoid.mobile.data.SkinDetail

@Composable
fun SkinDetailScreen(state: DetailUiState, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var videoUrl by remember { mutableStateOf<String?>(null) }
    var fullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(videoUrl) {
        val url = videoUrl
        if (url == null) {
            player.stop()
        } else {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    val activity = context as? Activity
    DisposableEffect(fullscreen) {
        activity?.requestedOrientation =
            if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    BackHandler {
        when {
            fullscreen -> fullscreen = false
            else -> onBack()
        }
    }

    if (fullscreen && videoUrl != null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoPlayer(player, onFullscreenToggle = { fullscreen = false }, modifier = Modifier.fillMaxSize())
        }
        return
    }

    val accent = state.detail?.tier?.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            Text(
                state.offer.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.detail == null -> Text(
                state.error ?: "Detail tidak tersedia",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
            else -> DetailContent(
                state = state,
                detail = state.detail,
                accent = accent,
                videoUrl = videoUrl,
                onPlay = { videoUrl = it },
                player = player,
                onFullscreen = { fullscreen = true },
            )
        }
    }
}

@Composable
private fun DetailContent(
    state: DetailUiState,
    detail: SkinDetail,
    accent: Color,
    videoUrl: String?,
    onPlay: (String?) -> Unit,
    player: ExoPlayer,
    onFullscreen: () -> Unit,
) {
    var chroma by remember { mutableIntStateOf(0) }
    val secondsLeft = rememberSecondsLeft(state.endsAtMillis)
    val offer = state.offer

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent))),
                contentAlignment = Alignment.Center,
            ) {
                if (videoUrl != null) {
                    VideoPlayer(player, onFullscreenToggle = onFullscreen, modifier = Modifier.fillMaxSize())
                } else {
                    AsyncImage(
                        model = detail.chromas.getOrNull(chroma)?.imageUrl ?: detail.imageUrl,
                        contentDescription = detail.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.tier?.let { tier ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(tier.iconUrl, tier.name, Modifier.size(22.dp))
                            Text(tier.name, color = accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${offer.discountedCost ?: offer.cost} VP",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (offer.discountedCost != null) {
                            Text(
                                "${offer.cost} VP  -${offer.discountPercent}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                            )
                        }
                    }
                    Text("Keluar dari toko dalam ${formatCountdown(secondsLeft)}")
                }
            }
        }

        if (detail.chromas.size > 1) {
            item { SectionTitle("Chroma") }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                ) {
                    itemsIndexed(detail.chromas) { i, c ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                            AsyncImage(
                                model = c.swatchUrl,
                                contentDescription = c.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        if (i == chroma) 3.dp else 1.dp,
                                        if (i == chroma) accent else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable {
                                        chroma = i
                                        onPlay(c.videoUrl)
                                    },
                            )
                            Text(c.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }

        item { SectionTitle("Level") }
        itemsIndexed(detail.levels) { _, level ->
            val playing = level.videoUrl != null && level.videoUrl == videoUrl
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (playing) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) { Text("${level.number}", fontWeight = FontWeight.Bold) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Level ${level.number}", fontWeight = FontWeight.SemiBold)
                    Text(level.feature ?: "Tampilan dasar", style = MaterialTheme.typography.bodySmall)
                }
                if (level.videoUrl != null) {
                    FilledTonalIconButton(onClick = { onPlay(if (playing) null else level.videoUrl) }) {
                        Icon(if (playing) Icons.Filled.Close else Icons.Filled.PlayArrow, if (playing) "Tutup video" else "Putar video")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
