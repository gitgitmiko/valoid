package com.valoid.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import coil.compose.AsyncImage
import com.valoid.mobile.data.PlayerProfile
import com.valoid.mobile.data.RankInfo

@Composable
fun ProfileScreen(
    profile: PlayerProfile?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenMatches: () -> Unit,
    onOpenContracts: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            profile == null && loading -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            profile == null -> item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(32.dp)) {
                    Text(error ?: "Profil belum dimuat.", textAlign = TextAlign.Center)
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.padding(top = 12.dp)) { Text("Muat ulang") }
                }
            }
            else -> {
                item { ProfileHeader(profile) }
                item { RankCard(profile.currentRank, profile.peakRank) }
                item { MenuRow("Riwayat match", "Hasil, skor, K/D/A, dan papan skor tiap match", onOpenMatches) }
                item { MenuRow("Kontrak & misi", "Progres Battle Pass, kontrak agent, dan misi harian/mingguan", onOpenContracts) }
                item {
                    OutlinedButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (loading) "Memuat..." else "Muat ulang profil")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: PlayerProfile) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(452f / 128f * 0.62f) // banner wideArt, sedikit lebih tinggi agar teks muat
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        AsyncImage(profile.cardWideArt, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                profile.cardSmallArt,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(profile.gameName ?: "Pemain", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    profile.tagLine?.let {
                        Text(" #$it", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
                profile.title?.let { Text(it, color = Color.White.copy(alpha = 0.85f)) }
            }
            profile.level?.let {
                Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.55f)) {
                    Text("Lvl $it", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun RankCard(current: RankInfo?, peak: RankInfo?) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 20.dp, horizontal = 8.dp), verticalAlignment = Alignment.Top) {
            RankColumn(current, caption = current?.rankedRating?.let { "$it RR" } ?: "Belum ber-rank", showProgress = true, modifier = Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
            RankColumn(peak, caption = "Peak rank", showProgress = false, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RankColumn(rank: RankInfo?, caption: String, showProgress: Boolean, modifier: Modifier) {
    val color = rank?.color?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurface
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.background) {
            Text(
                rank?.seasonLabel ?: "-",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
            if (showProgress && rank?.rankedRating != null) {
                CircularProgressIndicator(
                    progress = { (rank.rankedRating / 100f).coerceIn(0f, 1f) },
                    color = color,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    strokeWidth = 5.dp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AsyncImage(rank?.iconUrl, rank?.tierName, contentScale = ContentScale.Fit, modifier = Modifier.size(if (showProgress) 72.dp else 96.dp))
        }
        Text(rank?.tierName ?: "UNRATED", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(caption, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}
