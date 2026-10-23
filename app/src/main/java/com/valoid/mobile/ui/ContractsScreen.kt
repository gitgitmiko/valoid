package com.valoid.mobile.ui

import androidx.activity.compose.BackHandler
import com.valoid.mobile.data.XpMatch
import com.valoid.mobile.data.MissionPreview
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
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

private enum class ContractsTab(val label: String) { MISSIONS("Misi"), ACCOUNT("Akun"), CONTRACTS("Kontrak") }

@Composable
fun ContractsScreen(overview: ContractsOverview?, loading: Boolean, error: String?, onRefresh: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var tab by rememberSaveable { mutableStateOf(ContractsTab.MISSIONS) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            Text("Kontrak & misi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Filled.Refresh, "Muat ulang") }
        }
        Tabs(tab) { tab = it }
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
                tab == ContractsTab.MISSIONS -> missionsTab(overview)
                tab == ContractsTab.ACCOUNT -> accountTab(overview)
                else -> contractsTab(overview)
            }
        }
    }
}

@Composable
private fun Tabs(selected: ContractsTab, onSelect: (ContractsTab) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface).padding(4.dp),
    ) {
        ContractsTab.entries.forEach { t ->
            val active = t == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
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

private fun androidx.compose.foundation.lazy.LazyListScope.missionsTab(o: ContractsOverview) {
    val weekly = o.missions.filter { it.type == MissionType.WEEKLY }
    val others = o.missions.filter { it.type != MissionType.WEEKLY }
    val weeklyEnds = weekly.mapNotNull { it.expiresAtMillis }.minOrNull() ?: o.weeklyRefillMillis
    item {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Misi mingguan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    weeklyEnds?.let { CountdownChip(it) }
                }
                if (weekly.isEmpty()) Text("Tidak ada misi mingguan aktif.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                weekly.forEach { MissionLine(it) }
            }
        }
    }
    if (o.queuedWeeklies.isNotEmpty()) {
        item { PreviewGroup("Antre", "${o.queuedWeeklies.size} misi mingguan tersedia", o.queuedWeeklies, highlight = true, initiallyOpen = true) }
    }
    if (o.futureWeeklies.isNotEmpty()) {
        item { PreviewGroup(null, "${o.futureWeeklies.size} misi mingguan mendatang", o.futureWeeklies, highlight = false, initiallyOpen = false) }
    }
    if (others.isNotEmpty()) {
        item { Section("Misi lainnya") }
        items(others, key = { it.id }) { MissionCard(it) }
    }
}

@Composable
private fun CountdownChip(endsAt: Long) {
    val left = rememberSecondsLeft(endsAt)
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.background) {
        Text("⏱ ${formatCountdown(left)}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun MissionLine(m: Mission) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${m.title} (${formatAmount(m.target)}×)", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (m.complete) Icon(Icons.Filled.CheckCircle, "Selesai", tint = Done)
            else Text("+${formatAmount(m.xp)} XP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { (m.progress.toFloat() / m.target.coerceAtLeast(1)).coerceIn(0f, 1f) },
            color = if (m.complete) Done else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("${formatAmount(m.progress)} / ${formatAmount(m.target)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun PreviewGroup(badge: String?, title: String, list: List<MissionPreview>, highlight: Boolean, initiallyOpen: Boolean) {
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(Modifier.clickable { open = !open }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                badge?.let {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE8C468).copy(alpha = 0.2f)) {
                        Text(it, color = Color(0xFFE8C468), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = if (badge != null) 10.dp else 0.dp))
                Text("+${formatAmount(list.sumOf { it.xp })} XP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
            }
            if (open) {
                val dateFormat = java.text.SimpleDateFormat("d MMM", java.util.Locale("id", "ID"))
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    list.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${p.title} (${formatAmount(p.target)}×)")
                                if (!highlight) p.activatesAtMillis?.let {
                                    Text("Rilis ${dateFormat.format(java.util.Date(it))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                            Text("+${formatAmount(p.xp)} XP", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.accountTab(o: ContractsOverview) {
    val a = o.account
    if (a == null) {
        item { Text("Data XP akun belum tersedia.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
        return
    }
    item {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Level akun", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${a.level}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                }
                LinearProgressIndicator(progress = { (a.xp.toFloat() / a.xpPerLevel).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Row {
                    Text("${formatAmount(a.xp)} / ${formatAmount(a.xpPerLevel)} XP", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("${formatAmount((a.xpPerLevel - a.xp).coerceAtLeast(0))} lagi", style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Kemenangan pertama hari ini")
                        a.firstWinAvailableAtMillis?.let { FirstWinCountdown(it) }
                    }
                    if (a.firstWinClaimed) Icon(Icons.Filled.CheckCircle, "Sudah didapat", tint = Done)
                    else Text("Tersedia", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (a.recent.isNotEmpty()) {
        item {
            Row(Modifier.padding(top = 4.dp)) {
                Section("XP terbaru", Modifier.weight(1f))
                Text("${a.recent.size} match terakhir", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        items(a.recent, key = { it.startedAtMillis }) { XpRow(it) }
    }
}

@Composable
private fun FirstWinCountdown(at: Long) {
    val left = rememberSecondsLeft(at)
    Text("Tersedia lagi dalam ${formatCountdown(left)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
}

@Composable
private fun XpRow(x: XpMatch) {
    val date = java.text.SimpleDateFormat("d MMM yyyy · HH:mm", java.util.Locale("id", "ID")).format(java.util.Date(x.startedAtMillis))
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+${formatAmount(x.total)} XP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            x.sources.forEach { src ->
                Row {
                    Text(src.label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
                    Text("+${formatAmount(src.amount)}")
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.contractsTab(o: ContractsOverview) {
    o.battlePass?.let { bp ->
        item { Section("Battle Pass") }
        item { ContractCard(bp) }
    }
    item { Section("Kontrak agent") }
    val agents = o.agentContracts.ifEmpty { listOfNotNull(o.agentContract) }
    if (agents.isEmpty()) {
        item { Text("Tidak ada kontrak agent yang berjalan.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 16.dp)) }
    }
    items(agents, key = { it.id }) { ContractCard(it) }
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
