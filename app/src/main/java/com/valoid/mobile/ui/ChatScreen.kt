package com.valoid.mobile.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
    onSend: (text: String, onSent: () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
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
                    Toast.makeText(context, "Riot ID ${m.riotId} disalin", Toast.LENGTH_SHORT).show()
                })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    IconButton(onClick = { onSend(input) { input = "" } }, enabled = input.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
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
                m.region?.let { Text(" · ${it.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)) }
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
