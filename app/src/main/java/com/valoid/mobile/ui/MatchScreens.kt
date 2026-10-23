package com.valoid.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import coil.compose.AsyncImage
import com.valoid.mobile.data.Match
import com.valoid.mobile.data.MatchPlayer
import com.valoid.mobile.data.MatchResult

private val WinColor = Color(0xFF4CD4A0)
private val LossColor = Color(0xFFFF4655)
private val NeutralColor = Color(0xFFB0B8C1)

private fun Match.resultColor() = when (result) {
    MatchResult.WIN -> WinColor
    MatchResult.LOSS -> LossColor
    else -> NeutralColor
}

private fun Match.resultLabel() = when (result) {
    MatchResult.WIN -> "MENANG"
    MatchResult.LOSS -> "KALAH"
    MatchResult.DRAW -> "SERI"
    MatchResult.UNKNOWN -> placement?.let { "#$it" } ?: "-"
}

private fun timeAgo(millis: Long): String {
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        minutes < 60 -> "${minutes.coerceAtLeast(1)} menit lalu"
        minutes < 24 * 60 -> "${minutes / 60} jam lalu"
        else -> "${minutes / (24 * 60)} hari lalu"
    }
}

@Composable
fun MatchHistoryScreen(
    matches: List<Match>,
    loading: Boolean,
    error: String?,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onOpen: (Match) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TitleBar("Riwayat match", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(matches, key = { it.id }) { MatchRow(it, onClick = { onOpen(it) }) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator()
                        matches.isEmpty() && error == null -> Text("Belum ada match.")
                        canLoadMore -> OutlinedButton(onClick = onLoadMore) { Text("Muat lebih banyak") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchRow(match: Match, onClick: () -> Unit) {
    val color = match.resultColor()
    Row(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(color))
        Box(Modifier.width(120.dp).fillMaxHeight()) {
            AsyncImage(match.mapImage, match.mapName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface))))
            AsyncImage(
                match.me?.agentIcon,
                match.me?.agentName,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp).size(48.dp).clip(RoundedCornerShape(10.dp)),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(match.resultLabel(), color = color, fontWeight = FontWeight.Bold)
                if (match.myRounds != null) {
                    Text("  ${match.myRounds} - ${match.enemyRounds}", fontWeight = FontWeight.SemiBold)
                }
            }
            Text("${match.mapName} • ${match.queue}", style = MaterialTheme.typography.bodySmall)
            Text(
                timeAgo(match.startedAtMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
            match.me?.let {
                Text("${it.kills}/${it.deaths}/${it.assists}", fontWeight = FontWeight.Bold)
                Text("ACS ${it.acs}", style = MaterialTheme.typography.bodySmall)
            }
            match.rrChange?.let {
                Text(if (it >= 0) "+$it RR" else "$it RR", color = if (it >= 0) WinColor else LossColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun MatchDetailScreen(match: Match, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val myTeam = match.me?.teamId
    val groups = if (match.teams.size == 2) {
        match.players.groupBy { it.teamId }.toList().sortedBy { (team, _) -> if (team == myTeam) 0 else 1 }
    } else {
        listOf("" to match.players)
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TitleBar("${match.mapName} • ${match.queue}", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp))) {
                    AsyncImage(match.mapImage, match.mapName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(match.resultLabel(), color = match.resultColor(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        if (match.myRounds != null) {
                            Text("${match.myRounds} - ${match.enemyRounds}", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${match.lengthMillis / 60_000} menit • ${timeAgo(match.startedAtMillis)}" +
                                (match.rrChange?.let { if (it >= 0) " • +$it RR" else " • $it RR" } ?: ""),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            groups.forEach { (team, players) ->
                item {
                    val score = match.teams.firstOrNull { it.teamId == team }
                    Text(
                        when {
                            team.isEmpty() -> "PEMAIN"
                            team == myTeam -> "TIMMU" + (score?.let { " • ${it.roundsWon}" } ?: "")
                            else -> "TIM LAWAN" + (score?.let { " • ${it.roundsWon}" } ?: "")
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (team == myTeam || team.isEmpty()) WinColor else LossColor,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                item { ScoreHeader() }
                items(players, key = { it.puuid }) { PlayerRow(it) }
            }
        }
    }
}

@Composable
private fun ScoreHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        Text("Pemain", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
        Text("ACS", Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        Text("K/D/A", Modifier.width(76.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
    }
}

@Composable
private fun PlayerRow(p: MatchPlayer) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (p.isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(p.agentIcon, p.agentName, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
        AsyncImage(p.tierIcon, p.tierName, modifier = Modifier.padding(start = 6.dp).size(24.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(p.name, fontWeight = if (p.isMe) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
            p.tag?.let { Text("#$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        }
        Text("${p.acs}", Modifier.width(40.dp), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, maxLines = 1)
        Text("${p.kills}/${p.deaths}/${p.assists}", Modifier.width(76.dp), textAlign = TextAlign.End, maxLines = 1)
    }
}

@Composable
private fun TitleBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
