package com.valoid.mobile.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import com.valoid.mobile.data.PartyMode
import com.valoid.mobile.data.PartyPost
import com.valoid.mobile.data.PartySize
import com.valoid.mobile.data.RiotRegion
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.valoid.mobile.data.ChatMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Room chat; pesan terbaru di bawah. Ketuk nama pengirim untuk menyalin Riot ID-nya. */
@Composable
fun ChatRoomScreen(
    title: String,
    online: Int?,
    messages: List<ChatMessage>,
    myUid: String,
    sending: Boolean,
    error: String?,
    onSend: (text: String, party: PartyPost?, onSent: () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var partyDialog by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                online?.let { OnlineLabel(it, "online di room ini") }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
        ) {
            if (messages.isEmpty()) {
                item { Text("Belum ada pesan. Sapa yang lain!", modifier = Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
            }
            items(messages, key = { it.id }) { m ->
                MessageRow(m, mine = m.uid == myUid, onCopyId = {
                    clipboard.setText(AnnotatedString(m.riotId))
                    Toast.makeText(context, "Riot ID ${m.riotId} disalin. Kirim invite atau request di game.", Toast.LENGTH_SHORT).show()
                })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { partyDialog = true }, enabled = !sending) {
                Icon(Icons.Filled.AddCircle, "Cari party", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(ChatViewModel.MAX_MESSAGE) },
                placeholder = { Text("Tulis pesan") },
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.padding(start = 8.dp).size(48.dp), contentAlignment = Alignment.Center) {
                if (sending) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    IconButton(onClick = { onSend(input, null) { input = "" } }, enabled = input.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
    if (partyDialog) {
        PartyDialog(
            onPost = { party ->
                partyDialog = false
                val text = listOf("Cari ${party.size.label} · ${party.mode.label} · butuh ${party.need}", party.note).filter { it.isNotBlank() }.joinToString("\n")
                onSend(text, party) {}
            },
            onDismiss = { partyDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartyDialog(onPost: (PartyPost) -> Unit, onDismiss: () -> Unit) {
    var size by rememberSaveable { mutableStateOf(PartySize.DUO) }
    var mode by rememberSaveable { mutableStateOf(PartyMode.COMPETITIVE) }
    var need by rememberSaveable { mutableIntStateOf(1) }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cari party") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Party", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PartySize.entries.forEach { s ->
                        FilterChip(selected = size == s, onClick = { size = s; need = need.coerceIn(1, s.players - 1) }, label = { Text(s.label) })
                    }
                }
                Text("Mode", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PartyMode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Butuh pemain", modifier = Modifier.weight(1f))
                    TextButton(onClick = { need = (need - 1).coerceAtLeast(1) }, enabled = need > 1) { Text("−") }
                    Text("$need", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { need = (need + 1).coerceAtMost(size.players - 1) }, enabled = need < size.players - 1) { Text("+") }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(PARTY_NOTE_MAX) },
                    label = { Text("Catatan (opsional)") },
                    placeholder = { Text("Main jam 8, butuh Controller") },
                    maxLines = 3,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPost(PartyPost(size, mode, need, note.trim())) }) { Text("Posting") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Kartu ajakan party; setelah [PARTY_TTL_MILLIS] ditampilkan redup karena kemungkinan sudah penuh. */
@Composable
private fun PartyCard(m: ChatMessage, party: PartyPost, mine: Boolean, onJoin: () -> Unit) {
    val expired = System.currentTimeMillis() - m.createdAtMillis > PARTY_TTL_MILLIS
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (expired) 0.15f else 0.6f)),
        modifier = Modifier.padding(top = 4.dp).widthIn(min = 220.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "CARI ${party.size.label.uppercase()} · ${party.mode.label.uppercase()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (expired) 0.5f else 1f),
            )
            Text("Butuh ${party.need} pemain lagi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            m.tierName?.let { Text("Rank ${it.lowercase().replaceFirstChar { c -> c.uppercase() }}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
            if (party.note.isNotBlank()) Text(party.note)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    TIME.format(Instant.ofEpochMilli(m.createdAtMillis).atZone(ZoneId.systemDefault())) + if (expired) " · sudah lewat 30 menit" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.weight(1f),
                )
                if (!mine) {
                    Button(onClick = onJoin, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) { Text("Gabung") }
                }
            }
        }
    }
}

@Composable
private fun MessageRow(m: ChatMessage, mine: Boolean, onCopyId: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        if (!mine) {
            Avatar(m.cardArt, 36)
            Spacer(Modifier.size(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 300.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onCopyId)) {
                m.rankIcon?.let { AsyncImage(it, m.tierName, modifier = Modifier.size(18.dp)) }
                Text(m.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
                Text(" #${m.tag}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                m.region?.let { Text(" · ${RiotRegion.labelOf(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)) }
            }
            if (m.party != null) {
                PartyCard(m, m.party, mine, onJoin = onCopyId)
                return@Column
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(m.text)
                    Text(
                        TIME.format(Instant.ofEpochMilli(m.createdAtMillis).atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

@Composable
fun Avatar(url: String?, sizeDp: Int) {
    Box(Modifier.size(sizeDp.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun OnlineLabel(count: Int, suffix: String = "online") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4ADE80)))
        Text("$count $suffix", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(start = 6.dp))
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private const val PARTY_TTL_MILLIS = 30 * 60 * 1000L
const val PARTY_NOTE_MAX = 120
