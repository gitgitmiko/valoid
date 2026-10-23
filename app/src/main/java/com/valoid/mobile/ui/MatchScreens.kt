package com.valoid.mobile.ui

import androidx.activity.compose.BackHandler
import com.valoid.mobile.data.RoundOutcome
import com.valoid.mobile.data.RoundInfo
import com.valoid.mobile.data.Duel
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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

private enum class DetailTab(val label: String) { SCOREBOARD("Papan skor"), ROUNDS("Ronde"), DUELS("Duel") }

@Composable
fun MatchDetailScreen(match: Match, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var tab by rememberSaveable { mutableStateOf(DetailTab.SCOREBOARD) }
    val myTeam = match.me?.teamId
    val teamMode = match.teams.size == 2
    val tabs = if (teamMode) DetailTab.entries else listOf(DetailTab.SCOREBOARD)

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { MatchHeader(match, onBack) }
        item { MatchInfoCard(match) }
        if (tabs.size > 1) item { PillTabs(tabs, tab) { tab = it } }
        when (tab) {
            DetailTab.SCOREBOARD -> {
                if (match.rounds.isNotEmpty() && teamMode) item { RoundTimeline(match) }
                val groups = if (teamMode) {
                    match.players.groupBy { it.teamId }.toList().sortedBy { (team, _) -> if (team == myTeam) 0 else 1 }
                } else {
                    listOf("" to match.players)
                }
                groups.forEach { (team, players) ->
                    item {
                        Text(
                            when {
                                team.isEmpty() -> "PEMAIN"
                                team == myTeam -> "TIMMU"
                                else -> "TIM LAWAN"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (team == myTeam || team.isEmpty()) WinColor else LossColor,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    items(players, key = { "p" + it.puuid }) { PlayerCard(it, accent = if (it.teamId == myTeam || !teamMode) WinColor else LossColor) }
                }
            }
            DetailTab.ROUNDS -> {
                if (match.rounds.isEmpty()) item { EmptyNote("Data ronde tidak tersedia untuk match ini.") }
                items(match.rounds, key = { "r" + it.number }) { RoundRow(it, won = it.winningTeam == myTeam) }
            }
            DetailTab.DUELS -> {
                if (match.duels.isEmpty()) item { EmptyNote("Data duel tidak tersedia untuk match ini.") }
                items(match.duels, key = { "d" + it.opponent.puuid }) { DuelRow(it) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MatchHeader(match: Match, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(240.dp)) {
        AsyncImage(match.mapSplash ?: match.mapImage, match.mapName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.5f to Color.Black.copy(alpha = 0.2f), 1f to MaterialTheme.colorScheme.background),
            ),
        )
        Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White) }
            Text(match.mapName, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.align(Alignment.Center).padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (match.myRounds != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${match.myRounds}", color = WinColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.displayMedium)
                    Text("  -  ", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text("${match.enemyRounds}", color = LossColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.displayMedium)
                }
            } else {
                Text(match.resultLabel(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.displayMedium)
            }
        }
    }
}

@Composable
private fun MatchInfoCard(match: Match) {
    val seconds = match.lengthMillis / 1000
    val date = java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale("id", "ID")).format(java.util.Date(match.startedAtMillis))
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(match.queue, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text("${seconds / 60}m ${seconds % 60}s • $date", style = MaterialTheme.typography.bodySmall)
                match.rrChange?.let {
                    Text(if (it >= 0) "+$it RR" else "$it RR", color = if (it >= 0) WinColor else LossColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
            Surface(shape = RoundedCornerShape(10.dp), color = match.resultColor().copy(alpha = 0.18f)) {
                Text(
                    match.resultLabel(),
                    color = match.resultColor(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PillTabs(tabs: List<DetailTab>, selected: DetailTab, onSelect: (DetailTab) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface).padding(4.dp),
    ) {
        tabs.forEach { t ->
            val active = t == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.label,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

private fun RoundOutcome.symbol() = when (this) {
    RoundOutcome.ELIMINATION -> "✕"
    RoundOutcome.DEFUSE -> "✂"
    RoundOutcome.DETONATE -> "✹"
    RoundOutcome.TIME -> "⏱"
    RoundOutcome.SURRENDER -> "⚑"
    RoundOutcome.OTHER -> "•"
}

/** Dua baris kotak per ronde: baris atas timmu, bawah lawan; kotak berwarna = tim itu menang ronde tersebut. */
@Composable
private fun RoundTimeline(match: Match) {
    val myTeam = match.me?.teamId
    Column(Modifier.padding(start = 16.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(end = 16.dp)) {
            Column(Modifier.padding(end = 6.dp)) {
                Text(" ", style = MaterialTheme.typography.labelSmall)
                Box(Modifier.height(34.dp), contentAlignment = Alignment.CenterStart) { Text("Kamu", color = WinColor, style = MaterialTheme.typography.labelMedium) }
                Box(Modifier.padding(top = 4.dp).height(34.dp), contentAlignment = Alignment.CenterStart) { Text("Lawan", color = LossColor, style = MaterialTheme.typography.labelMedium) }
            }
            match.rounds.forEach { r ->
                val mineWon = r.winningTeam == myTeam
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
                    Text("${r.number}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    RoundCell(if (mineWon) r.outcome.symbol() else null, WinColor)
                    Spacer(Modifier.height(4.dp))
                    RoundCell(if (!mineWon) r.outcome.symbol() else null, LossColor)
                }
            }
        }
        Text(
            "✕ eliminasi   ✂ dijinakkan   ✹ meledak   ⏱ waktu habis",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun RoundCell(symbol: String?, color: Color) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(if (symbol != null) color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (symbol != null) Text(symbol, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlayerCard(p: MatchPlayer, accent: Color) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (p.isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.width(4.dp).height(118.dp).background(accent))
        Column(Modifier.weight(1f).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(p.agentIcon, p.agentName, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(p.name, fontWeight = FontWeight.Bold, maxLines = 1)
                        p.tag?.let { Text(" #$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1) }
                    }
                    p.level?.let { Text("Level $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
                }
                AsyncImage(p.tierIcon, p.tierName, modifier = Modifier.size(34.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Stat("K/D", String.format(java.util.Locale.US, "%.2f", p.kd), if (p.kd >= 1) WinColor else LossColor, Modifier.weight(1f))
                Stat("K/D/A", "${p.kills}/${p.deaths}/${p.assists}", null, Modifier.weight(1.3f))
                Stat("HS%", p.headshotPercent?.let { "$it%" } ?: "-", null, Modifier.weight(1f))
                Stat("ACS", "${p.acs}", null, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.SemiBold, color = color ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun RoundRow(r: RoundInfo, won: Boolean) {
    val color = if (won) WinColor else LossColor
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Text("${r.number}", color = color, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("${if (won) "Menang" else "Kalah"} • ${r.outcome.symbol()} ${r.outcome.label}", fontWeight = FontWeight.SemiBold, color = color)
            val notes = listOfNotNull(r.planter?.let { "Tanam: $it" }, r.defuser?.let { "Jinakkan: $it" })
            if (notes.isNotEmpty()) Text(notes.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
            Text(
                "Kill kamu: ${r.myKills}" + (r.mySpent?.let { " • Belanja: ${formatAmount(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun DuelRow(d: Duel) {
    val total = (d.kills + d.deaths).coerceAtLeast(1)
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(d.opponent.agentIcon, d.opponent.agentName, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(d.opponent.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(d.opponent.agentName ?: "", style = MaterialTheme.typography.bodySmall)
            }
            Text("${d.kills}", color = WinColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("  :  ", style = MaterialTheme.typography.titleMedium)
            Text("${d.deaths}", color = LossColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }
        Row(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
            if (d.kills > 0) Box(Modifier.weight(d.kills.toFloat() / total).fillMaxHeight().background(WinColor))
            if (d.deaths > 0) Box(Modifier.weight(d.deaths.toFloat() / total).fillMaxHeight().background(LossColor))
            if (d.kills + d.deaths == 0) Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
        }
        Text(
            "Kamu membunuh ${d.kills}× • dibunuh ${d.deaths}×",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
}

@Composable
private fun TitleBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
