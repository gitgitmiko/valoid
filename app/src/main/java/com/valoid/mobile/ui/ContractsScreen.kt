package com.valoid.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.valoid.mobile.data.ContractProgress
import com.valoid.mobile.data.ContractsOverview
import com.valoid.mobile.data.Mission
import com.valoid.mobile.data.MissionType

private val Done = Color(0xFF4CD4A0)

@Composable
fun ContractsScreen(overview: ContractsOverview?, loading: Boolean, error: String?, onRefresh: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            Text("Kontrak & misi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                overview == null && loading -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                overview == null -> item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        Text(error ?: "Data kontrak belum dimuat.")
                        OutlinedButton(onClick = onRefresh, modifier = Modifier.padding(top = 12.dp)) { Text("Muat ulang") }
                    }
                }
                else -> {
                    overview.battlePass?.let { bp ->
                        item { Section("Battle Pass") }
                        item { ContractCard(bp) }
                    }
                    overview.agentContract?.let { c ->
                        item { Section("Kontrak agent") }
                        item { ContractCard(c) }
                    }
                    MissionType.entries.forEach { type ->
                        val list = overview.missions.filter { it.type == type }
                        if (list.isEmpty()) return@forEach
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Section("Misi ${type.label.lowercase()}", Modifier.weight(1f))
                                if (type == MissionType.WEEKLY) overview.weeklyRefillMillis?.let { MissionRefill(it) }
                            }
                        }
                        items(list, key = { it.id }) { MissionCard(it) }
                    }
                    if (overview.missions.isEmpty()) item { Text("Tidak ada misi aktif.") }
                    item {
                        OutlinedButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            Text(if (loading) "Memuat..." else "Muat ulang")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = modifier.padding(top = 4.dp),
    )
}

@Composable
private fun MissionRefill(endsAt: Long) {
    val left = rememberSecondsLeft(endsAt)
    Text("Baru dalam ${formatCountdown(left)}", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ContractCard(c: ContractProgress) {
    val done = c.levelReached >= c.totalLevels
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.iconUrl != null) {
                    AsyncImage(c.iconUrl, c.name, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                }
                Column(Modifier.weight(1f).padding(start = if (c.iconUrl != null) 12.dp else 0.dp)) {
                    Text(c.name, fontWeight = FontWeight.Bold)
                    Text(if (done) "Selesai" else "Tier ${c.levelReached} / ${c.totalLevels}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!done) {
                val needed = c.xpForNextLevel ?: 0
                LinearProgressIndicator(
                    progress = { if (needed > 0) (c.xpIntoLevel.toFloat() / needed).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "XP ${formatAmount(c.xpIntoLevel)} / ${if (needed > 0) formatAmount(needed) else "-"} menuju tier ${c.levelReached + 1}",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                LinearProgressIndicator(progress = { 1f }, color = Done, modifier = Modifier.fillMaxWidth())
            }
            c.nextReward?.takeIf { !done }?.let { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(width = 72.dp, height = 44.dp), contentAlignment = Alignment.Center) {
                        AsyncImage(r.iconUrl, r.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("Hadiah berikutnya", style = MaterialTheme.typography.labelSmall)
                        Text(r.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionCard(m: Mission) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (m.complete) Done.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (m.complete) Icon(Icons.Filled.CheckCircle, "Selesai", tint = Done)
                else Text("+${formatAmount(m.xp)} XP", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(
                progress = { (m.progress.toFloat() / m.target.coerceAtLeast(1)).coerceIn(0f, 1f) },
                color = if (m.complete) Done else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Text("${formatAmount(m.progress)} / ${formatAmount(m.target)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                m.expiresAtMillis?.takeIf { !m.complete }?.let { MissionExpiry(it) }
            }
        }
    }
}

@Composable
private fun MissionExpiry(endsAt: Long) {
    val left = rememberSecondsLeft(endsAt)
    Text("Berakhir ${formatCountdown(left)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
}
