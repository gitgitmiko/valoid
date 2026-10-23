package com.valoid.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.valoid.mobile.data.ActStats
import com.valoid.mobile.data.CareerSummary
import com.valoid.mobile.data.RankInfo
import java.util.Locale

private val Good = Color(0xFF4CD4A0)
private val Bad = Color(0xFFFF6B6B)

private fun pct(v: Double) = String.format(Locale("id", "ID"), "%.1f%%", v)

@Composable
fun CareerScreen(career: CareerSummary?, loading: Boolean, error: String?, onRefresh: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var queueId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            Text("Ringkasan karier", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (career == null || career.queues.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: "Belum ada data karier.")
                        OutlinedButton(onClick = onRefresh, modifier = Modifier.padding(top = 12.dp)) { Text("Muat ulang") }
                    }
                }
            }
            return
        }
        val queue = career.queues.firstOrNull { it.queueId == queueId } ?: career.queues.first()
        val currentAct = queue.acts.firstOrNull { it.seasonId == career.currentActId }
        val previous = queue.acts.filter { it.seasonId != career.currentActId }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { QueuePicker(career, queue.queueId) { queueId = it } }
            item { CurrentActCard(career.currentActLabel ?: "Act sekarang", currentAct) }
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row {
                            Text("Sepanjang waktu", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${queue.acts.size} act", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        WinStats(queue.wins, queue.games, Modifier.padding(top = 12.dp))
                    }
                }
            }
            if (previous.isNotEmpty()) {
                item {
                    Text(
                        "ACT SEBELUMNYA",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(previous, key = { queue.queueId + it.seasonId }) { PreviousAct(it) }
            }
        }
    }
}

@Composable
private fun QueuePicker(career: CareerSummary, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = career.queues.first { it.queueId == selected }
    Box {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("MODE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(current.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                Icon(Icons.Filled.ArrowDropDown, null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            career.queues.forEach { q ->
                DropdownMenuItem(
                    text = { Text("${q.name}  •  ${q.games} game") },
                    onClick = {
                        onSelect(q.queueId)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CurrentActCard(label: String, act: ActStats?) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) {
                    Text("SEKARANG", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))
            if (act == null) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Belum ada game", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Kamu belum bermain di mode ini selama $label.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                ActBody(act, showRr = true)
            }
        }
    }
}

@Composable
private fun ActBody(act: ActStats, showRr: Boolean) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        act.rank?.let { rank ->
            RankBadge(rank, showRr, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(90.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
        }
        WinStats(act.wins, act.games, Modifier.weight(1f), vertical = act.rank != null)
    }
}

@Composable
private fun RankBadge(rank: RankInfo, showRr: Boolean, modifier: Modifier) {
    val color = rank.color?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurface
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            if (showRr && rank.rankedRating != null) {
                CircularProgressIndicator(
                    progress = { (rank.rankedRating / 100f).coerceIn(0f, 1f) },
                    color = color,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    strokeWidth = 5.dp,
                    modifier = Modifier.size(96.dp),
                )
            }
            AsyncImage(rank.iconUrl, rank.tierName, modifier = Modifier.size(60.dp))
        }
        Text(rank.tierName, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        if (showRr) rank.rankedRating?.let { Text("$it RR", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
    }
}

@Composable
private fun WinStats(wins: Int, games: Int, modifier: Modifier, vertical: Boolean = false) {
    val rate = if (games > 0) wins * 100.0 / games else 0.0
    val rateColor = if (rate >= 50) Good else Bad
    val content: @Composable (Modifier) -> Unit = { m ->
        Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(pct(rate), color = rateColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("Win rate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
    val games2: @Composable (Modifier) -> Unit = { m ->
        Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$wins / $games", color = Good, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("Menang / game", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content(Modifier.fillMaxWidth())
            games2(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            content(Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(44.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
            games2(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviousAct(act: ActStats) {
    var expanded by rememberSaveable(act.seasonId) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.clickable { expanded = !expanded }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (act.rank != null) {
                    AsyncImage(act.rank.iconUrl, act.rank.tierName, modifier = Modifier.size(40.dp))
                } else {
                    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Text("${act.games}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(act.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(act.rank?.tierName, "${act.games} game").joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
            }
            if (expanded) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))
                ActBody(act, showRr = false)
            }
        }
    }
}
