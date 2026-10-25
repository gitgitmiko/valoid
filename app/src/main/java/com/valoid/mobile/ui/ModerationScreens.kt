package com.valoid.mobile.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.valoid.mobile.data.BlockedUser
import com.valoid.mobile.data.Report
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Persetujuan aturan komunitas, disimpan per akun Valoid di perangkat. Naikkan [VERSION] bila aturannya berubah. */
object CommunityRules {
    private const val VERSION = 1
    private const val PREFS = "community_rules"

    fun isAccepted(context: Context, uid: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(uid, 0) >= VERSION

    fun accept(context: Context, uid: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(uid, VERSION).apply()
    }

    val items = listOf(
        "Hormati pemain lain. Dilarang menghina, melecehkan, atau ujaran kebencian (SARA, gender, dan sejenisnya).",
        "Dilarang konten seksual, kekerasan, atau yang tidak pantas.",
        "Dilarang spam, iklan, link mencurigakan, dan promosi berulang.",
        "Dilarang jual-beli atau tukar akun, joki, cheat, dan penipuan.",
        "Jangan membagikan data pribadi, milikmu maupun orang lain.",
        "Gunakan room untuk mencari party dan ngobrol seputar VALORANT.",
    )
}

@Composable
fun CommunityRulesDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aturan komunitas") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CommunityRules.items.forEachIndexed { i, rule -> Text("${i + 1}. $rule") }
                Text(
                    "Kami tidak menoleransi pelanggaran. Pesan yang melanggar dihapus dan pelakunya diblokir dari chat. " +
                        "Tekan lama sebuah pesan untuk melaporkan atau memblokir pengirimnya.",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Riot ID, rank, wilayah, dan kartu pemainmu akan terlihat oleh pengguna lain di chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Saya setuju") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun Page(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
fun BlockedUsersScreen(users: List<BlockedUser>, onUnblock: (BlockedUser) -> Unit, onBack: () -> Unit) {
    Page("Pengguna diblokir", onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (users.isEmpty()) item { Text("Belum ada pengguna yang diblokir.") }
            items(users, key = { it.uid }) { u ->
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${u.name} #${u.tag}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onUnblock(u) }) { Text("Buka blokir") }
                    }
                }
            }
        }
    }
}

/** Laporan chat untuk admin, dikelompokkan per pesan. */
@Composable
fun ReportsScreen(
    reports: List<Report>,
    onDeleteMessage: (Report) -> Unit,
    onBan: (Report) -> Unit,
    onDismiss: (List<Report>) -> Unit,
    onBack: () -> Unit,
) {
    val grouped = reports.groupBy { it.messageId }.values.sortedByDescending { group -> group.maxOf { it.createdAtMillis } }
    Page("Laporan chat", onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (grouped.isEmpty()) item { Text("Tidak ada laporan.") }
            items(grouped, key = { it.first().messageId }) { group ->
                val r = group.first()
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${r.targetName} · ${r.room}", fontWeight = FontWeight.Bold)
                        Text("\"${r.text}\"")
                        Text(
                            "${group.size} laporan: " + group.map { it.reason.label }.distinct().joinToString(", "),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        group.mapNotNull { it.detail.takeIf(String::isNotBlank) }.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            DAY_TIME.format(Instant.ofEpochMilli(group.maxOf { it.createdAtMillis }).atZone(ZoneId.systemDefault())),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { onDeleteMessage(r) }) { Text("Hapus") }
                            OutlinedButton(onClick = { onBan(r) }) { Text("Ban") }
                            TextButton(onClick = { onDismiss(group) }) { Text("Abaikan") }
                        }
                    }
                }
            }
        }
    }
}

private val DAY_TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm")
